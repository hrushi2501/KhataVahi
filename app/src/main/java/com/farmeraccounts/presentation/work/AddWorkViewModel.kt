package com.farmeraccounts.presentation.work

import com.farmeraccounts.core.util.DisplayLanguage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.core.util.NumericInputNormalizer
import com.farmeraccounts.core.util.ScaledQuantity
import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.model.PaymentMethod
import com.farmeraccounts.domain.model.QuantityUnit
import com.farmeraccounts.domain.model.Tractor
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.repository.TractorRepository
import com.farmeraccounts.domain.usecase.AddWorkUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AddWorkFormData(
    val customers: List<Customer> = emptyList(),
    val tractors: List<Tractor> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class AddWorkViewModel(
    private val addWorkUseCase: AddWorkUseCase,
    private val customerRepository: CustomerRepository,
    private val tractorRepository: TractorRepository
) : ViewModel() {

    val formData: StateFlow<AddWorkFormData> = combine(
        customerRepository.getAllActiveCustomers(),
        tractorRepository.getAllActiveTractors()
    ) { customerList, tractorList ->
        AddWorkFormData(
            customers = customerList,
            tractors = tractorList
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AddWorkFormData()
    )

    private val _saveState = MutableStateFlow<String?>(null)
    val saveState: StateFlow<String?> = _saveState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    fun calculateTotalPaise(quantityStr: String?, rateStr: String?): Long {
        val qtyScaled = NumericInputNormalizer.parseQuantityToScaled(quantityStr)
        val ratePaise = NumericInputNormalizer.parseRupeesToPaise(rateStr)
        return ScaledQuantity.calculateTotalPaise(qtyScaled, ratePaise)
    }

    fun calculateRemainingDue(totalPaise: Long, initialPaymentStr: String?): Long {
        val initialPaymentPaise = NumericInputNormalizer.parseRupeesToPaise(initialPaymentStr)
        return Math.subtractExact(totalPaise, initialPaymentPaise)
    }

    fun saveWork(
        customerId: String,
        tractorId: String,
        workType: String,
        date: Long,
        quantityStr: String,
        unit: QuantityUnit,
        rateStr: String,
        initialPaymentStr: String?,
        paymentMethod: PaymentMethod?,
        notes: String?,
        onSuccess: () -> Unit
    ) {
        if (_isSaving.value || _saveCompleted.value) return
        if (customerId.isBlank()) {
            _saveState.value = DisplayLanguage.text("ગ્રાહક પસંદ કરો", "Please select a customer")
            return
        }
        if (tractorId.isBlank()) {
            _saveState.value = DisplayLanguage.text("ટ્રેક્ટર પસંદ કરો", "Please select a tractor")
            return
        }

        val qtyScaled = NumericInputNormalizer.parseQuantityToScaled(quantityStr)
        if (qtyScaled <= 0L) {
            _saveState.value = DisplayLanguage.text("યોગ્ય માપ દાખલ કરો", "Please enter a valid quantity")
            return
        }

        val ratePaise = NumericInputNormalizer.parseRupeesToPaise(rateStr)
        if (ratePaise <= 0L) {
            _saveState.value = DisplayLanguage.text("યોગ્ય ભાવ દાખલ કરો", "Please enter a valid rate")
            return
        }

        if (!com.farmeraccounts.presentation.base.FormValidation.optionalPayment(initialPaymentStr)) {
            _saveState.value = DisplayLanguage.text("યોગ્ય જમા રકમ દાખલ કરો", "Enter a valid initial payment")
            return
        }
        val initialPaymentPaise = if (!initialPaymentStr.isNullOrBlank()) {
            NumericInputNormalizer.parseRupeesToPaise(initialPaymentStr)
        } else {
            null
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                addWorkUseCase(
                    customerId = customerId,
                    tractorId = tractorId,
                    workType = workType,
                    date = date,
                    quantityScaled = qtyScaled,
                    quantityUnit = unit,
                    ratePerUnitPaise = ratePaise,
                    notes = notes,
                    initialPaymentPaise = initialPaymentPaise,
                    initialPaymentMethod = paymentMethod
                )
                _saveCompleted.value = true
                onSuccess()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveState.value = DisplayLanguage.text("સાચવી શકાયું નથી. તમારી વિગતો અહીં જ છે. ફરી પ્રયત્ન કરો.", "Could not save. Your entries are still here. Please try again.")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearError() {
        _saveState.value = null
    }
}

