package com.are.distribuidora.pedido.presentation.print

import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Diálogo que lista las impresoras Bluetooth ya emparejadas y permite seleccionar una.
 *
 * Uso:
 *   BluetoothPrinterDialog.show(fragmentManager, devices) { device -> ... }
 */
class BluetoothPrinterDialog : DialogFragment() {

    private var devices: List<BluetoothDevice> = emptyList()
    private var onDeviceSelected: ((BluetoothDevice) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val names = devices.map { device ->
            @Suppress("MissingPermission")
            device.name ?: device.address
        }.toTypedArray()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Seleccionar impresora")
            .setItems(names) { _, index ->
                onDeviceSelected?.invoke(devices[index])
                dismiss()
            }
            .setNegativeButton("Cancelar") { _, _ -> dismiss() }
            .create()
    }

    companion object {
        private const val TAG = "BluetoothPrinterDialog"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            devices: List<BluetoothDevice>,
            onDeviceSelected: (BluetoothDevice) -> Unit,
        ) {
            val dialog = BluetoothPrinterDialog().apply {
                this.devices = devices
                this.onDeviceSelected = onDeviceSelected
            }
            dialog.show(fragmentManager, TAG)
        }
    }
}


