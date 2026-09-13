package com.are.distribuidora.pedido.presentation.common

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/**
 * Animaciones de entrada y realce reutilizables del flujo de pedido.
 *
 * No reemplazan la transición de fragment (slide+fade genérico); son la capa de
 * detalle que se ejecuta DENTRO de cada pantalla para que la entrada se sienta
 * coreografiada y no estática: revelado en cascada, "pop" con rebote, pulso de
 * énfasis y conteo numérico animado.
 *
 * Todas las funciones cancelan cualquier animación previa de la vista para que
 * sean seguras de re-invocar (p.ej. al re-colectar un StateFlow).
 */
object FlowAnimations {

    private fun View.dp(value: Float): Float = value * resources.displayMetrics.density

    /** Revela una vista: sube [distanceDp] dp + fade-in, con desaceleración suave. */
    fun revealUp(
        view: View,
        delay: Long = 0L,
        duration: Long = 440L,
        distanceDp: Float = 24f,
    ) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = view.dp(distanceDp)
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(1.7f))
            .start()
    }

    /** Revela una lista de vistas en cascada escalonada (efecto "fall down" deliberado). */
    fun staggerUp(
        views: List<View>,
        startDelay: Long = 0L,
        stagger: Long = 72L,
        duration: Long = 440L,
        distanceDp: Float = 24f,
    ) {
        views.forEachIndexed { i, v ->
            revealUp(v, delay = startDelay + i * stagger, duration = duration, distanceDp = distanceDp)
        }
    }

    /** "Pop" con rebote: escala 0 → 1 con overshoot + fade. Pensado para íconos de éxito. */
    fun popIn(
        view: View,
        delay: Long = 0L,
        duration: Long = 520L,
        tension: Float = 2.4f,
    ) {
        view.animate().cancel()
        view.alpha = 0f
        view.scaleX = 0f
        view.scaleY = 0f
        view.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(delay)
            .setDuration(duration)
            .setInterpolator(OvershootInterpolator(tension))
            .start()
    }

    /** Pulso de énfasis: escala 1 → [peak] → 1, con un pequeño rebote al volver. */
    fun pulse(view: View, peak: Float = 1.12f, duration: Long = 420L, delay: Long = 0L) {
        view.animate().cancel()
        view.animate()
            .scaleX(peak).scaleY(peak)
            .setStartDelay(delay)
            .setDuration(duration / 2)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(duration / 2)
                    .setInterpolator(OvershootInterpolator(3f))
                    .start()
            }
            .start()
    }

    /**
     * Anima un valor numérico de [from] → [to], invocando [onUpdate] cada frame.
     * Devuelve el [ValueAnimator] por si el caller necesita cancelarlo (p.ej. al
     * recibir un nuevo valor antes de que termine el anterior).
     */
    fun animateValue(
        from: Double,
        to: Double,
        duration: Long = 700L,
        delay: Long = 0L,
        onUpdate: (Double) -> Unit,
    ): ValueAnimator =
        ValueAnimator.ofFloat(from.toFloat(), to.toFloat()).apply {
            this.duration = duration
            this.startDelay = delay
            interpolator = DecelerateInterpolator(1.9f)
            addUpdateListener { onUpdate((it.animatedValue as Float).toDouble()) }
            start()
        }

    /** Conteo animado [from] → [to] sobre un [TextView], formateando cada frame con [format]. */
    fun countUp(
        textView: TextView,
        from: Double,
        to: Double,
        duration: Long = 700L,
        delay: Long = 0L,
        format: (Double) -> String,
    ): ValueAnimator =
        animateValue(from, to, duration, delay) { textView.text = format(it) }
}
