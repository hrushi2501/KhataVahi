package com.farmeraccounts.presentation.customers

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.farmeraccounts.core.util.CustomerMatching
import com.farmeraccounts.domain.model.Customer

/** Dropdown selection retains the actual customer identity after filtering. */
class CustomerSearchAdapter(context: Context, customers: List<Customer>) :
    ArrayAdapter<Customer>(context, android.R.layout.simple_dropdown_item_1line, customers.take(20).toMutableList()) {
    private val source = customers.toList()
    private val matchingFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val found = CustomerMatching.search(source, constraint?.toString().orEmpty()).take(20)
            return FilterResults().apply { values = found; count = found.size }
        }
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            val rows = (results?.values as? List<*>)?.filterIsInstance<Customer>().orEmpty()
            setNotifyOnChange(false)
            clear()
            addAll(rows)
            notifyDataSetChanged()
        }
        override fun convertResultToString(resultValue: Any?): CharSequence =
            (resultValue as? Customer)?.let(::label).orEmpty()
    }
    override fun getFilter(): Filter = matchingFilter
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        view.findViewById<TextView>(android.R.id.text1)?.apply {
            text = getItem(position)?.let(::label).orEmpty()
            textSize = 18f
            minHeight = (56 * context.resources.displayMetrics.density).toInt()
        }
        return view
    }
    companion object {
        fun label(customer: Customer): String = listOfNotNull(
            customer.getDisplayName(), customer.fatherName?.takeIf { it.isNotBlank() }, customer.phone?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
    }
}
