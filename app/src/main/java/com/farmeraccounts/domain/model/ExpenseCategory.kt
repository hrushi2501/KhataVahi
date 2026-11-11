package com.farmeraccounts.domain.model

enum class ExpenseCategory(val gujarati: String, val english: String) {
    DIESEL("ડીઝલ", "Diesel"),
    MAINTENANCE("સર્વિસ", "Maintenance"),
    REPAIRS("રિપેરિંગ", "Repairs"),
    SPARE_PARTS("સ્પેરપાર્ટ્સ", "Spare Parts"),
    LABOR_SALARY("ડ્રાઇવર / મજૂરી પગાર", "Driver / Labor Salary"),
    OIL_GREASE("ઓઇલ અને ગ્રીસ", "Oil & Grease"),
    INSURANCE_TAX("વીમો / ટેક્સ", "Insurance & Tax"),
    OTHER("અન્ય ખર્ચ", "Other Expense");

    fun bilingualTitle(): String = com.farmeraccounts.core.util.DisplayLanguage.text(gujarati, english)

    companion object {
        fun fromString(value: String?): ExpenseCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
        }
    }
}

