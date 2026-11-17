package com.example.data.model

enum class UserRole(val displayName: String) {
    ADMINISTRADOR("Administrador"),
    CAPTURISTA("Capturista")
}

object AccessPolicy {
    const val ADMIN_EMAIL = "angelfernandoantonioflores4@gmail.com"
    const val MAX_CAPTURISTAS = 5
    val CAPTURISTA_SLOTS = listOf("1", "2", "3", "4", "5")
}

data class AppUser(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole
) {
    val isAdmin: Boolean get() = role == UserRole.ADMINISTRADOR
    val isCapturista: Boolean get() = role == UserRole.CAPTURISTA
}

data class AuthorizedAccount(
    val email: String,
    val displayName: String,
    val role: UserRole,
    val slotId: String,
    val uid: String = "",
    val addedBy: String = AccessPolicy.ADMIN_EMAIL,
    val addedAtMillis: Long = 0L,
    val lastActiveMillis: Long = 0L
)
