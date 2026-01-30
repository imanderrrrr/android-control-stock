package com.are.distribuidora

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity mínima SOLO para debug.
 * Propósito: permitir que Android Studio lance la app y dispare Application.onCreate().
 * No muestra UI y se cierra inmediatamente.
 */
@AndroidEntryPoint
class DebugLauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

