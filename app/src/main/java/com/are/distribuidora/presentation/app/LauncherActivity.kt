package com.are.distribuidora.presentation.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.are.distribuidora.R
import com.are.distribuidora.application.appstart.StartDestination
import com.are.distribuidora.screenaccess.domain.repository.ScreenAccessRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    @Inject
    lateinit var appStartDecider: AppStartDecider

    @Inject
    lateinit var screenAccessRepository: ScreenAccessRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Splash "Bosque Pro": UI de marca mientras se decide el destino.
        setContentView(R.layout.activity_splash)

        // Redirección (lógica de destino y auth intacta). Solo se garantiza un
        // mínimo de visibilidad del splash; no cambia a dónde se navega.
        lifecycleScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val destination = appStartDecider.decideStartDestination()

            if (destination == StartDestination.Home) {
                // Roles 4.0: espera (con tope corto) la primera lectura remota del rol para
                // que un admin recién instalado no vea la app un instante como vendedor.
                // Sin red o si vence el tope, se sigue con la cache (mínimo privilegio si
                // no hay nada cacheado).
                val warmUp = async { withTimeoutOrNull(ACCESS_WARMUP_MS) { screenAccessRepository.refreshFromRemote() } }
                warmUp.await()
            }

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
        /** Tope de espera por la primera lectura del rol desde Firestore (ms). */
        const val ACCESS_WARMUP_MS = 2_500L
    }
}
