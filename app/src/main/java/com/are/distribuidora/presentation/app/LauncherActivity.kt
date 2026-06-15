package com.are.distribuidora.presentation.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.are.distribuidora.R
import com.are.distribuidora.application.appstart.StartDestination
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    @Inject
    lateinit var appStartDecider: AppStartDecider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Splash "Bosque Pro": UI de marca mientras se decide el destino.
        setContentView(R.layout.activity_splash)

        // Redirección (lógica de destino y auth intacta). Solo se garantiza un
        // mínimo de visibilidad del splash; no cambia a dónde se navega.
        lifecycleScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val destination = appStartDecider.decideStartDestination()

            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (elapsed < SPLASH_MIN_VISIBLE_MS) {
                delay(SPLASH_MIN_VISIBLE_MS - elapsed)
            }

            val intent = when (destination) {
                StartDestination.Home -> Intent(
                    this@LauncherActivity,
                    com.are.distribuidora.presentation.home.HomeActivity::class.java,
                )

                StartDestination.Login -> Intent(
                    this@LauncherActivity,
                    com.are.distribuidora.presentation.login.LoginActivity::class.java,
                )
            }
            startActivity(intent)
            finish()
        }
    }

    private companion object {
        /** Tiempo mínimo que el splash permanece visible (ms). */
        const val SPLASH_MIN_VISIBLE_MS = 900L
    }
}
