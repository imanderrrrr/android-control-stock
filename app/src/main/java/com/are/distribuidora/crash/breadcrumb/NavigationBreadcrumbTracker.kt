package com.are.distribuidora.crash.breadcrumb

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.are.distribuidora.domain.core.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registra automáticamente cada navegación entre Activities y Fragments
 * en el [Logger] inyectado, que a su vez alimenta el ring buffer del
 * módulo de crash reporter.
 *
 * El objetivo es que cualquier crash futuro incluya en su reporte el camino
 * exacto de pantallas que el usuario visitó antes del fallo — sin requerir
 * que cada Activity/Fragment se acuerde de logguearse a sí misma.
 *
 * ## Qué se loguea
 *
 *  - Actividades: `onCreate`, `onResume`, `onPause`, `onDestroy`.
 *  - Fragments: `onAttached`, `onViewCreated`, `onResumed`, `onPaused`,
 *    `onViewDestroyed`, `onDetached`.
 *
 * Se omiten los callbacks intermedios (`onPreAttached`, `onPreCreated`,
 * `onActivityCreated`, etc.) para no llenar el ring buffer con ruido.
 *
 * ## Tag y formato
 *
 * Todos los breadcrumbs usan el tag `NAV` para que sean fácilmente
 * distinguibles de los logs de feature en el reporte de crash:
 *
 * ```
 * 09:46:02 D NAV: Activity onResume HomeActivity
 * 09:46:08 D NAV: Fragment onResumed OrderCatalogFragment in HomeActivity
 * 09:46:14 D NAV: Fragment onPaused OrderCatalogFragment in HomeActivity
 * 09:46:14 D NAV: Fragment onResumed ProductDetailOrderFragment in HomeActivity
 * ```
 *
 * ## ProGuard / R8
 *
 * Para que los nombres de clase queden legibles en release builds (en vez
 * de "w4.g"), el `proguard-rules.pro` debe contener:
 *
 * ```
 * -keepnames class * extends androidx.fragment.app.Fragment
 * -keepnames class * extends androidx.appcompat.app.AppCompatActivity
 * -keepnames class * extends androidx.activity.ComponentActivity
 * ```
 *
 * ## Privacidad
 *
 * Solo registramos nombres de clases — NUNCA argumentos de bundles ni
 * intents. Datos sensibles (id de cliente, monto, etc.) no caen en el
 * buffer por este tracker.
 */
@Singleton
class NavigationBreadcrumbTracker @Inject constructor(
    private val logger: Logger,
) {

    /**
     * Callback que se registra en cada Activity para escuchar transiciones
     * de Fragments dentro de ella.
     */
    private val fragmentCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentAttached(fm: FragmentManager, f: Fragment, ctx: android.content.Context) {
            logger.d(TAG, "Fragment onAttached ${f.javaClass.simpleName} in ${ctx.javaClass.simpleName}")
        }

        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: android.view.View,
            savedInstanceState: Bundle?,
        ) {
            logger.d(TAG, "Fragment onViewCreated ${f.javaClass.simpleName}")
        }

        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            logger.d(TAG, "Fragment onResumed ${f.javaClass.simpleName}")
        }

        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
            logger.d(TAG, "Fragment onPaused ${f.javaClass.simpleName}")
        }

        override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
            logger.d(TAG, "Fragment onViewDestroyed ${f.javaClass.simpleName}")
        }

        override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
            logger.d(TAG, "Fragment onDetached ${f.javaClass.simpleName}")
        }
    }

    /**
     * Callback que se registra en el [Application] una sola vez al arranque.
     * Por cada Activity que nace, engancha [fragmentCallbacks] a su
     * FragmentManager (recursivo, así también captura fragments anidados).
     */
    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            logger.d(TAG, "Activity onCreate ${activity.javaClass.simpleName}")
            if (activity is FragmentActivity) {
                // recursive=true: incluye también child fragment managers
                // (útil cuando usamos NavHostFragment / fragments anidados).
                activity.supportFragmentManager
                    .registerFragmentLifecycleCallbacks(fragmentCallbacks, /* recursive = */ true)
            }
        }

        override fun onActivityStarted(activity: Activity) {
            // No registramos onStart para evitar ruido — onResume es suficiente.
        }

        override fun onActivityResumed(activity: Activity) {
            logger.d(TAG, "Activity onResume ${activity.javaClass.simpleName}")
        }

        override fun onActivityPaused(activity: Activity) {
            logger.d(TAG, "Activity onPause ${activity.javaClass.simpleName}")
        }

        override fun onActivityStopped(activity: Activity) {
            // Omitido para no duplicar con onPause.
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            // No interesante para breadcrumbs.
        }

        override fun onActivityDestroyed(activity: Activity) {
            logger.d(TAG, "Activity onDestroy ${activity.javaClass.simpleName}")
            // Los FragmentLifecycleCallbacks se desregistran solos al morir el
            // FragmentManager, no necesitamos limpiarlos manualmente.
        }
    }

    /**
     * Instala el tracker en el [Application]. Idempotente: llamarlo varias
     * veces no apila listeners.
     */
    fun install(application: Application) {
        if (installed) return
        installed = true

        application.registerActivityLifecycleCallbacks(activityCallbacks)
        logger.d(TAG, "NavigationBreadcrumbTracker installed")
    }

    @Volatile
    private var installed: Boolean = false

    companion object {
        private const val TAG = "NAV"
    }
}
