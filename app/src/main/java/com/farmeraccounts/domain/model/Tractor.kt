package com.farmeraccounts.domain.model

data class Tractor(
    val id: String,
    val displayName: String,
    val model: String? = null,
    val type: TractorType,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Long = 1L,
    val isDeleted: Boolean = false
) {
    fun localizedDisplayName(): String = when (displayName) {
        "મોટો ટ્રેક્ટર (Big Tractor)", "નાનો ટ્રેક્ટર (Mini Tractor)" -> type.bilingualTitle()
        else -> displayName
    }
}
