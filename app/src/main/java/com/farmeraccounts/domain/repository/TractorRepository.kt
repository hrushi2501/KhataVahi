package com.farmeraccounts.domain.repository

import com.farmeraccounts.domain.model.Tractor
import com.farmeraccounts.domain.model.TractorType
import kotlinx.coroutines.flow.Flow

interface TractorRepository {
    fun getAllActiveTractors(): Flow<List<Tractor>>
    suspend fun getTractorById(id: String): Tractor?
    suspend fun getTractorByType(type: TractorType): Tractor?
    suspend fun addTractor(tractor: Tractor)
    suspend fun updateTractor(tractor: Tractor, reason: String? = null)
    suspend fun softDeleteTractor(id: String, reason: String? = null)
}

