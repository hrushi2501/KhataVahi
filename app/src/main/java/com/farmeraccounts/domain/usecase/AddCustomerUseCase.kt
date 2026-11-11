package com.farmeraccounts.domain.usecase

import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.repository.CustomerRepository
import java.util.UUID

class AddCustomerUseCase(
    private val customerRepository: CustomerRepository
) {
    suspend operator fun invoke(
        name: String,
        nickname: String? = null,
        fatherName: String? = null,
        village: String? = null,
        phone: String? = null
    ): Customer {
        require(name.isNotBlank()) { "Customer name cannot be empty (ગ્રાહકનું નામ જરૂરી છે)" }

        val customer = Customer(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            nickname = nickname?.trim()?.ifEmpty { null },
            fatherName = fatherName?.trim()?.ifEmpty { null },
            village = village?.trim()?.ifEmpty { null },
            phone = phone?.trim()?.ifEmpty { null }
        )
        customerRepository.addCustomer(customer)
        return customer
    }
}

