package com.are.distribuidora.screenaccess.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.screenaccess.domain.model.ScreenAccess
import com.are.distribuidora.screenaccess.domain.usecase.ObserveScreenAccessUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Expone los permisos de pantalla del usuario actual.
 *
 * Se obtiene con scope de Activity (en [com.are.distribuidora.presentation.home.HomeActivity]
 * vía `by viewModels()` y en los fragments hijos vía `by activityViewModels()`), de modo
 * que un único listener de Firestore alimenta a todos los consumidores.
 *
 * Valor inicial = permitir todo (default-allow optimista mientras carga la cache).
 */
@HiltViewModel
class ScreenAccessViewModel @Inject constructor(
    observeScreenAccessUseCase: ObserveScreenAccessUseCase,
) : ViewModel() {

    val access: StateFlow<ScreenAccess> =
        observeScreenAccessUseCase().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ScreenAccess.allowAll(),
        )
}
