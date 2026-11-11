package com.farmeraccounts.presentation.customers

import com.farmeraccounts.core.util.DisplayLanguage
import com.farmeraccounts.core.util.CustomerMatching
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.usecase.AddCustomerUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AddEditCustomerUiState(
    val existingCustomer: Customer? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isSaved: Boolean = false,
    val duplicateCandidates: List<Customer> = emptyList(),
    val errorMessage: String? = null
)

class AddEditCustomerViewModel(
    private val addCustomerUseCase: AddCustomerUseCase,
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditCustomerUiState())
    val uiState: StateFlow<AddEditCustomerUiState> = _uiState.asStateFlow()

    private data class CustomerDraft(
        val name: String,
        val nickname: String?,
        val fatherName: String?,
        val village: String?,
        val phone: String?
    )

    private data class DuplicateWarning(
        val draft: CustomerDraft,
        val candidates: List<Pair<String, Long>>
    )

    private var duplicateWarning: DuplicateWarning? = null
    private var hasLoaded = false

    fun loadCustomer(customerId: String?, retry: Boolean = false) {
        if (hasLoaded && !retry) return
        hasLoaded = true
        if (customerId.isNullOrBlank()) {
            _uiState.value = AddEditCustomerUiState(isEditing = false)
            return
        }
        _uiState.value = _uiState.value.copy(isEditing = true, isLoading = true, loadFailed = false, errorMessage = null)
        viewModelScope.launch {
            try {
                val customer = customerRepository.getCustomerById(customerId)
                _uiState.value = _uiState.value.copy(
                    existingCustomer = customer,
                    isEditing = true,
                    isLoading = false,
                    loadFailed = customer == null,
                    errorMessage = if (customer == null) "customer_load_failed" else null
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, loadFailed = true, errorMessage = "customer_load_failed")
            }
        }
    }

    fun saveCustomer(
        name: String,
        nickname: String?,
        fatherName: String?,
        village: String?,
        phone: String?,
        confirmDuplicate: Boolean = false,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.isSaving || _uiState.value.isSaved || _uiState.value.isLoading || _uiState.value.loadFailed) return
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = DisplayLanguage.text("ગ્રાહકનું નામ દાખલ કરો", "Please enter customer name"))
            return
        }

        val draft = CustomerDraft(name.trim(), nickname?.trim()?.ifEmpty { null },
            fatherName?.trim()?.ifEmpty { null }, village?.trim()?.ifEmpty { null }, phone?.trim()?.ifEmpty { null })
        val confirmedWarning = if (confirmDuplicate) duplicateWarning else null
        duplicateWarning = null
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null, duplicateCandidates = emptyList())
        viewModelScope.launch {
            try {
                val existing = _uiState.value.existingCustomer
                val candidates = CustomerMatching.duplicateCandidates(
                    customerRepository.getAllActiveCustomers().first(), draft.name, draft.phone, existing?.id
                )
                val currentWarning = DuplicateWarning(draft, candidates.map { it.id to it.version }.sortedBy { it.first })
                if (candidates.isNotEmpty() && confirmedWarning != currentWarning) {
                    duplicateWarning = currentWarning
                    _uiState.value = _uiState.value.copy(isSaving = false, duplicateCandidates = candidates)
                    return@launch
                }
                if (existing != null) {
                    val updated = existing.copy(
                        name = draft.name,
                        nickname = draft.nickname,
                        fatherName = draft.fatherName,
                        village = draft.village,
                        phone = draft.phone
                    )
                    customerRepository.updateCustomer(updated, "Customer info updated")
                } else {
                    addCustomerUseCase(
                        name = draft.name,
                        nickname = draft.nickname,
                        fatherName = draft.fatherName,
                        village = draft.village,
                        phone = draft.phone
                    )
                }
                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
                onSuccess()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.value = _uiState.value.copy(isSaving = false)
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = DisplayLanguage.text("સાચવી શકાયું નથી. તમારી વિગતો અહીં જ છે. ફરી પ્રયત્ન કરો.", "Could not save. Your entries are still here. Please try again.")
                )
            }
        }
    }
    fun cancelDuplicateWarning() {
        duplicateWarning = null
        _uiState.value = _uiState.value.copy(duplicateCandidates = emptyList())
    }
}
