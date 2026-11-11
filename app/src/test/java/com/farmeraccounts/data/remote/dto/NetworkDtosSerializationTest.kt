package com.farmeraccounts.data.remote.dto

import com.farmeraccounts.data.local.db.entity.CustomerEntity
import com.farmeraccounts.data.local.db.entity.PaymentEntity
import com.farmeraccounts.data.local.db.entity.WorkEntryEntity
import com.farmeraccounts.data.remote.mapper.TimestampIsoConverter
import com.farmeraccounts.data.remote.mapper.toDto
import com.farmeraccounts.data.remote.mapper.toEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

class NetworkDtosSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val sampleOwnerId = UUID.randomUUID().toString()

    @Test
    fun `CustomerDto serializes to and from valid JSON matching Supabase schema`() {
        val customerId = UUID.randomUUID().toString()
        val dto = CustomerDto(
            id = customerId,
            ownerId = sampleOwnerId,
            name = "ધનશ્યામભાઈ પટેલ (Ghanshyambhai Patel)",
            nickname = "કાનાભાઈ",
            fatherName = "મોહનભાઈ",
            village = "આણંદ (Anand)",
            phone = "9825012345",
            createdAt = "2026-11-10T12:00:00Z",
            updatedAt = "2026-11-10T12:00:00Z",
            version = 1,
            isDeleted = false
        )

        val jsonString = json.encodeToString(dto)
        assertNotNull(jsonString)

        val decoded = json.decodeFromString<CustomerDto>(jsonString)
        assertEquals(dto.id, decoded.id)
        assertEquals(dto.ownerId, decoded.ownerId)
        assertEquals(dto.name, decoded.name)
        assertEquals(dto.nickname, decoded.nickname)
        assertEquals(dto.fatherName, decoded.fatherName)
        assertEquals(dto.village, decoded.village)
        assertEquals(dto.phone, decoded.phone)
        assertEquals(dto.version, decoded.version)
        assertFalse(decoded.isDeleted)
    }

    @Test
    fun `WorkEntryDto serializes to JSON with integer paise and scaled quantity`() {
        val workId = UUID.randomUUID().toString()
        val customerId = UUID.randomUUID().toString()
        val tractorId = UUID.randomUUID().toString()

        val dto = WorkEntryDto(
            id = workId,
            ownerId = sampleOwnerId,
            customerId = customerId,
            tractorId = tractorId,
            workType = "ખેડ (Ploughing)",
            date = "2026-11-10T08:30:00Z",
            quantityScaled = 2500L, // 2.500 Bigha
            quantityUnit = "BIGHA",
            ratePerUnitPaise = 50000L, // 500 Rs
            totalPaise = 125000L, // 1250 Rs
            notes = "First cut ploughing",
            createdAt = "2026-11-10T08:30:00Z",
            updatedAt = "2026-11-10T08:30:00Z",
            version = 1L,
            isDeleted = false
        )

        val jsonString = json.encodeToString(dto)
        val decoded = json.decodeFromString<WorkEntryDto>(jsonString)

        assertEquals(125000L, decoded.totalPaise)
        assertEquals(2500L, decoded.quantityScaled)
        assertEquals(50000L, decoded.ratePerUnitPaise)
    }

    @Test
    fun `CustomerEntity toDto and toEntity round trip preserves exact values`() {
        val entity = CustomerEntity(
            id = UUID.randomUUID().toString(),
            name = "હર્ષદભાઈ",
            nickname = "મોટા",
            fatherName = "રણછોડભાઈ",
            village = "મોગરી",
            phone = "9898011223",
            createdAt = 1700000000000L,
            updatedAt = 1700001000000L,
            version = 2L,
            isDeleted = false
        )

        val dto = entity.toDto(sampleOwnerId)
        val restored = dto.toEntity()

        assertEquals(entity.id, restored.id)
        assertEquals(entity.name, restored.name)
        assertEquals(entity.nickname, restored.nickname)
        assertEquals(entity.fatherName, restored.fatherName)
        assertEquals(entity.village, restored.village)
        assertEquals(entity.phone, restored.phone)
        assertEquals(entity.createdAt, restored.createdAt)
        assertEquals(entity.updatedAt, restored.updatedAt)
        assertEquals(entity.version, restored.version)
        assertEquals(entity.isDeleted, restored.isDeleted)
    }

    @Test
    fun `TimestampIsoConverter converts epoch millis to ISO and back losslessly`() {
        val millis = 1731234567890L
        val iso = TimestampIsoConverter.toIsoString(millis)
        val restoredMillis = TimestampIsoConverter.toEpochMillis(iso)

        assertEquals(millis, restoredMillis)
    }
}

