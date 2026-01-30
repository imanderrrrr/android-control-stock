package com.are.distribuidora.core.glide

import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule

/**
 * Glide module de la app.
 *
 * Esto elimina el warning:
 * "Failed to find GeneratedAppGlideModule" y asegura que las integraciones/opts
 * de Glide se apliquen correctamente.
 */
@GlideModule
class AppGlideModuleImpl : AppGlideModule()
