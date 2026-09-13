package com.are.distribuidora.pedido.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.domain.pedido.PedidoDraftRepository
import com.are.distribuidora.domain.pedido.model.PedidoDraft
import com.are.distribuidora.domain.pedido.usecase.ValidateDraftForRecoveryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Decide si hay un pedido a medias que ofrecer al vendedor tras un cierre inesperado.
 *
 * No depende de que exista un reporte de crash: cuando Android mata el proceso por
 * memoria — el caso más frecuente en los teléfonos de los vendedores — no se genera
 * ninguno. La señal es la existencia del borrador; el reporte de crash, si lo hay,
 * es solo información extra para el texto del diálogo.
 */
@HiltViewModel
class DraftRecoveryViewModel @Inject constructor(
    private val draftRepository: PedidoDraftRepository,
    private val authRepository: AuthRepository,
    private val validateDraft: ValidateDraftForRecoveryUseCase,
    private val gate: DraftRecoveryGate,
) : ViewModel() {

    sealed interface State {
        object Idle : State
        /** Hay un borrador recuperable: se debe mostrar el diálogo. */
        data class Offer(
            val draft: PedidoDraft,
            val notices: List<ValidateDraftForRecoveryUseCase.Notice>,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Busca un borrador del usuario ACTUAL y, si sobrevive a la revalidación, lo ofrece.
     *
     * Se puede llamar varias veces sin miedo: el [DraftRecoveryGate] garantiza que el
     * diálogo solo se ofrezca una vez por sesión.
     */
    fun checkForRecoverableDraft() {
        if (!gate.tryConsume()) return
        viewModelScope.launch {
            // Filtrado por uid: con roles en producción, el borrador de un vendedor
            // NUNCA se le ofrece a otro. La consulta va por vendedorId, así que un uid
            // distinto simplemente no encuentra nada.
            val uid = authRepository.getCurrentSession()?.userId ?: return@launch
            val draft = draftRepository.get(uid) ?: return@launch

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            when (val outcome = runCatching { validateDraft(draft, today) }.getOrNull()) {
                is ValidateDraftForRecoveryUseCase.Outcome.Restore -> {
                    _state.value = State.Offer(outcome.draft, outcome.notices)
                }
                is ValidateDraftForRecoveryUseCase.Outcome.Discard -> {
                    // Ya no sirve (vencido, duplicado o sin ítems vivos): se limpia en
                    // silencio para que no vuelva a aparecer en el próximo arranque.
                    draftRepository.delete(uid)
                }
                null -> Unit // fallo al revalidar: se deja el borrador para el próximo intento
            }
        }
    }

    /** El vendedor eligió "Descartar". */
    fun discard(draft: PedidoDraft) {
        _state.value = State.Idle
        viewModelScope.launch { runCatching { draftRepository.delete(draft.vendedorId) } }
    }

    /** El vendedor ignoró el diálogo (tocó fuera). Se conserva para el siguiente arranque. */
    fun dismiss() {
        _state.value = State.Idle
    }

    /** El vendedor eligió "Retomar"; la Activity ya rehidrató el flujo. */
    fun consumed() {
        _state.value = State.Idle
    }
}
