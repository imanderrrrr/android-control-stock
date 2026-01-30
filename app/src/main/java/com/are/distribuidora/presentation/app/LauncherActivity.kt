package com.are.distribuidora.presentation.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.are.distribuidora.application.appstart.StartDestination
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    @Inject
    lateinit var appStartDecider: AppStartDecider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sin UI: solo redirección.
        lifecycleScope.launch {
            val destination = appStartDecider.decideStartDestination()
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
}
