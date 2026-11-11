package com.farmeraccounts.presentation.customers

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.farmeraccounts.FarmerAccountsApp
import com.farmeraccounts.R
import com.farmeraccounts.databinding.FragmentCustomerListBinding
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.base.ViewModelFactory
import kotlinx.coroutines.launch

class CustomerListFragment : Fragment() {

    private var _binding: FragmentCustomerListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CustomerListViewModel by viewModels {
        val app = requireActivity().application as FarmerAccountsApp
        ViewModelFactory(app.container)
    }

    private lateinit var customerAdapter: CustomerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? Navigator)?.setToolbarTitle(getString(R.string.title_customers))

        setupRecyclerView()
        setupSearch()
        setupFab()
        binding.btnRetryList.setOnClickListener { viewModel.retry() }
        observeUiState()
    }

    private fun setupRecyclerView() {
        customerAdapter = CustomerAdapter { item ->
            (activity as? Navigator)?.navigateToCustomerDetail(item.customer.id)
        }
        binding.rvCustomers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = customerAdapter
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onSearchQueryChanged(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFab() {
        binding.fabAddCustomer.setOnClickListener {
            (activity as? Navigator)?.navigateToAddCustomer()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.skeletonLoading.root.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.rvCustomers.visibility = if (!state.isLoading && state.customers.isNotEmpty()) View.VISIBLE else View.GONE
                    binding.btnRetryList.visibility = if (state.hasError) View.VISIBLE else View.GONE
                    binding.tvEmptyCustomers.setText(if (state.hasError) R.string.history_load_failed else R.string.empty_customers)
                    customerAdapter.submitList(state.customers)
                    binding.tvEmptyCustomers.visibility =
                        if (state.customers.isEmpty() && !state.isLoading) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CustomerListFragment()
    }
}

