package com.farmeraccounts.domain.model

/**
 * Customer / Party domain model.
 */
data class Customer(
    val id: String,
    val name: String,
    val nickname: String? = null,
    val fatherName: String? = null,
    val village: String? = null,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Long = 1L,
    val isDeleted: Boolean = false
) {
    fun getDisplayName(): String {
        val parts = mutableListOf<String>()
        parts.add(name)
        if (!nickname.isNullOrBlank()) {
            parts.add("($nickname)")
        }
        if (!village.isNullOrBlank()) {
            parts.add("- $village")
        }
        return parts.joinToString(" ")
    }
}

