package com.are.distribuidora.presentation.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.are.distribuidora.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by lazy {
        ViewModelProvider(this)[LoginViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val form = findViewById<View>(R.id.loginForm)
        val emailLayout = findViewById<TextInputLayout>(R.id.emailLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordLayout)
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val loginButton = findViewById<MaterialButton>(R.id.loginButton)
        val progress = findViewById<ProgressBar>(R.id.loginProgress)
        val errorText = findViewById<TextView>(R.id.errorText)

        // Animación simple (fade/slide) al entrar.
        form.startAnimation(AnimationUtils.loadAnimation(this, R.anim.login_form_enter))

        fun setLoading(isLoading: Boolean) {
            // Deshabilitar interacción.
            emailLayout.isEnabled = !isLoading
            passwordLayout.isEnabled = !isLoading
            loginButton.isEnabled = !isLoading

            // Crossfade entre botón y progress (sin mover lógica al Main extra).
            if (isLoading) {
                progress.isVisible = true
                progress.alpha = 0f
                progress.animate().alpha(1f).setDuration(180).start()

                loginButton.animate().alpha(0f).setDuration(140).withEndAction {
                    loginButton.visibility = View.INVISIBLE
                }.start()
            } else {
                loginButton.visibility = View.VISIBLE
                loginButton.alpha = 0f
                loginButton.animate().alpha(1f).setDuration(180).start()

                progress.animate().alpha(0f).setDuration(140).withEndAction {
                    progress.isVisible = false
                }.start()
            }
        }

        fun showError(message: String?) {
            val text = message?.trim().orEmpty()
            val pretty = when {
                text.contains("credencial", ignoreCase = true) ||
                    text.contains("email", ignoreCase = true) && text.contains("contrase", ignoreCase = true) ||
                    text.contains("password", ignoreCase = true) -> getString(R.string.login_error_invalid_credentials)

                text.contains("conex", ignoreCase = true) ||
                    text.contains("red", ignoreCase = true) ||
                    text.contains("network", ignoreCase = true) -> getString(R.string.login_error_network)

                text.isBlank() -> ""
                else -> getString(R.string.login_error_unknown)
            }

            errorText.text = pretty
            val shouldShow = pretty.isNotBlank()
            if (shouldShow && !errorText.isVisible) {
                errorText.isVisible = true
                errorText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
            } else if (!shouldShow && errorText.isVisible) {
                errorText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_out))
                errorText.isVisible = false
            }

            // Opcional: también marcamos error en los inputs para mejor UX.
            emailLayout.error = null
            passwordLayout.error = null
            if (shouldShow) {
                passwordLayout.error = " "
            }
        }

        loginButton.setOnClickListener {
            // Feedback táctil simple (sin librerías externas)
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

            viewModel.onEmailChange(emailInput.text?.toString().orEmpty())
            viewModel.onPasswordChange(passwordInput.text?.toString().orEmpty())
            viewModel.submitLogin()
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                setLoading(state.isLoading)
                showError(state.errorMessage)

                if (state.navigateToHome) {
                    viewModel.consumeNavigation()
                    startActivity(
                        Intent(
                            this@LoginActivity,
                            com.are.distribuidora.presentation.home.HomeActivity::class.java,
                        )
                    )
                    // Sin animaciones bruscas
                    overridePendingTransition(0, 0)
                    finish()
                }
            }
        }
    }
}
