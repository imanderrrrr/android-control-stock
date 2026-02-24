package com.are.distribuidora.pedido.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.domain.model.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductDetailOrderViewModel @Inject constructor(
    private val presetStore: PresetStore,
    private val detailTemplatesStore: DetailTemplatesStore,
) : ViewModel() {

    // ── Producto cargado (se setea desde Fragment con .load()) ────────────────
    private val _product = MutableStateFlow<Product?>(null)
    val product: StateFlow<Product?> = _product.asStateFlow()

    // ── Presets ───────────────────────────────────────────────────────────────
    private val _presets = MutableStateFlow<List<Int>>(emptyList())
    val presets: StateFlow<List<Int>> = _presets.asStateFlow()

    // ── Cantidad seleccionada (null = ninguna) ────────────────────────────────
    private val _selectedQty = MutableStateFlow<Int?>(null)
    val selectedQty: StateFlow<Int?> = _selectedQty.asStateFlow()

    // ── Notas ─────────────────────────────────────────────────────────────────
    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    // ── Plantillas de detalle ──────────────────────────────────────────────────
    private val _templates = MutableStateFlow<List<String>>(emptyList())
    val templates: StateFlow<List<String>> = _templates.asStateFlow()

    private val _selectedTemplate = MutableStateFlow<String?>(null)
    val selectedTemplate: StateFlow<String?> = _selectedTemplate.asStateFlow()

    // ── Eventos one-shot al Fragment ──────────────────────────────────────────
    sealed interface Event {
        data object ShowOtherDialog : Event
        data object ShowAddPresetDialog : Event
        data object ShowCreateTemplateDialog : Event
        data class AddedToCart(val qty: Int) : Event
        data class Error(val message: String) : Event
    }

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadPresets()
        loadTemplates()
    }

    fun load(product: Product) {
        _product.value = product
    }

    private fun loadPresets() {
        viewModelScope.launch {
            _presets.value = presetStore.getPresets()
        }
    }

    private fun loadTemplates() {
        viewModelScope.launch {
            detailTemplatesStore.templatesFlow().collect {
                _templates.value = it
            }
        }
    }

    fun onPresetClicked(qty: Int) {
        _selectedQty.value = if (_selectedQty.value == qty) null else qty
    }

    fun onOtherClicked() {
        viewModelScope.launch { _events.send(Event.ShowOtherDialog) }
    }

    fun onOtherQtyConfirmed(qty: Int) {
        if (qty in 1..999) {
            _selectedQty.value = qty
        } else {
            viewModelScope.launch { _events.send(Event.Error("Cantidad inválida (1–999)")) }
        }
    }

    fun onAddPresetClicked() {
        viewModelScope.launch { _events.send(Event.ShowAddPresetDialog) }
    }

    fun onAddPresetConfirmed(qty: Int) {
        if (qty !in 1..999) {
            viewModelScope.launch { _events.send(Event.Error("Preset inválido (1–999)")) }
            return
        }
        presetStore.saveCustomPreset(qty)
        loadPresets()
        _selectedQty.value = qty
    }

    fun onNotesChanged(text: String) {
        _notes.value = text
        // Si el usuario edita manualmente y el texto ya no coincide con la plantilla seleccionada,
        // se deselecciona la plantilla
        if (_selectedTemplate.value != null && text != _selectedTemplate.value) {
            _selectedTemplate.value = null
        }
    }

    // ── Plantillas ─────────────────────────────────────────────────────────────

    fun onTemplateSelected(text: String) {
        // Toggle: si ya estaba seleccionada, deseleccionar
        if (_selectedTemplate.value == text) {
            _selectedTemplate.value = null
        } else {
            _selectedTemplate.value = text
            _notes.value = text
        }
    }

    fun onCreateTemplateClicked() {
        viewModelScope.launch { _events.send(Event.ShowCreateTemplateDialog) }
    }

    fun onTemplateCreateConfirmed(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            viewModelScope.launch { _events.send(Event.Error("El detalle no puede estar vacío")) }
            return
        }
        if (trimmed.length > DetailTemplatesStore.MAX_LENGTH) {
            viewModelScope.launch { _events.send(Event.Error("Máximo ${DetailTemplatesStore.MAX_LENGTH} caracteres")) }
            return
        }
        detailTemplatesStore.addTemplate(trimmed)
        // Auto-seleccionar y poner en input
        _selectedTemplate.value = trimmed
        _notes.value = trimmed
    }

    fun onTemplateDeleteConfirmed(text: String) {
        detailTemplatesStore.deleteTemplate(text)
        if (_selectedTemplate.value == text) {
            _selectedTemplate.value = null
        }
    }

    /**
     * Valida y emite evento AddedToCart.
     * El Fragment es quien llama al flowViewModel.addToCart().
     */
    fun onAddToCartClicked() {
        val qty = _selectedQty.value
        if (qty == null || qty <= 0) {
            viewModelScope.launch {
                _events.send(Event.Error("Selecciona una cantidad antes de agregar"))
            }
            return
        }
        viewModelScope.launch {
            _events.send(Event.AddedToCart(qty))
        }
    }
}


