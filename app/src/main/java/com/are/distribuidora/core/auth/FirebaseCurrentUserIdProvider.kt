package com.are.distribuidora.core.auth

import com.google.firebase.auth.FirebaseAuth

/**
 * Implementación de [CurrentUserIdProvider] que delega en FirebaseAuth.
 */
class FirebaseCurrentUserIdProvider : CurrentUserIdProvider {
    override fun get(): String? = FirebaseAuth.getInstance().currentUser?.uid

    override fun getDisplayName(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return user.displayName?.takeIf { it.isNotBlank() } ?: user.email
    }
}

