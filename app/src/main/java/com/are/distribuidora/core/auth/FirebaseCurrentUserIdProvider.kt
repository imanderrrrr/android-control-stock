package com.are.distribuidora.core.auth

import com.google.firebase.auth.FirebaseAuth

/**
 * Implementación de [CurrentUserIdProvider] que delega en FirebaseAuth.
 */
class FirebaseCurrentUserIdProvider : CurrentUserIdProvider {
    override fun get(): String? = FirebaseAuth.getInstance().currentUser?.uid
}

