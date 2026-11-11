package com.farmeraccounts.navigation

/**
 * Complete application navigation contract.
 * Keeps navigation lightweight, testable, and decoupled from presentation fragments.
 */
interface Navigator {
    fun navigateToHome()
    fun navigateToWorkList()
    fun navigateToAddWork(preselectedCustomerId: String? = null)
    fun navigateToCustomers()
    fun navigateToCustomerDetail(customerId: String)
    fun navigateToAddCustomer()
    fun navigateToEditCustomer(customerId: String)
    fun navigateToRecordPayment(preselectedCustomerId: String? = null, preselectedWorkEntryId: String? = null)
    fun navigateToRecordExpense()
    fun navigateToHistory(deleted: Boolean)
    fun navigateToSettings()
    fun navigateBack(): Boolean
    fun setToolbarTitle(title: String)
}
