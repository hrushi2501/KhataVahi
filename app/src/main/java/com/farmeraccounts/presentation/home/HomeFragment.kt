package com.farmeraccounts.presentation.home

import android.os.Bundle
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
import com.farmeraccounts.core.util.FinancialFormatters
import com.farmeraccounts.core.util.DisplayLanguage
import com.farmeraccounts.databinding.FragmentHomeBinding
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.base.ViewModelFactory
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        val app = requireActivity().application as FarmerAccountsApp
        ViewModelFactory(app.container)
    }

    private lateinit var activityAdapter: RecentActivityAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? Navigator)?.setToolbarTitle(getString(R.string.title_home))

        binding.btnRetryHome.text = DisplayLanguage.text("ફરી પ્રયાસ કરો", "Try again")
        binding.btnRetryHome.setOnClickListener { viewModel.refreshDashboard() }
        setupRecyclerView()
        setupButtons()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDashboard()
    }

    private fun setupRecyclerView() {
        activityAdapter = RecentActivityAdapter()
        binding.rvRecentActivity.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = activityAdapter
        }
    }

    private fun setupButtons() {
        val navigator = activity as? Navigator

        binding.btnAddWork.setOnClickListener {
            navigator?.navigateToAddWork()
        }

        binding.btnAddPayment.setOnClickListener {
            navigator?.navigateToRecordPayment()
        }

        binding.btnAddExpense.setOnClickListener {
            navigator?.navigateToRecordExpense()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val unavailable = state.isLoading || state.hasError
                    for (index in 0 until binding.homeContent.childCount) {
                        val child = binding.homeContent.getChildAt(index)
                        if (child !== binding.tvHomeStatus && child !== binding.btnRetryHome) {
                            child.visibility = if (unavailable) View.GONE else View.VISIBLE
                        }
                    }
                    binding.skeletonLoading.root.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.tvHomeStatus.visibility = if (unavailable) View.VISIBLE else View.GONE
                    binding.btnRetryHome.visibility = if (state.hasError) View.VISIBLE else View.GONE
                    binding.tvHomeStatus.text = if (state.hasError)
                        DisplayLanguage.text("હિસાબ ખોલી શકાયો નથી. તમારી નોંધો બદલાઈ નથી.", "Could not open your accounts. Your records have not changed.")
                    else DisplayLanguage.text("હિસાબ ખોલી રહ્યા છીએ…", "Loading accounts…")
                    if (unavailable) return@collect
                    val totals = state.totals

                    // Real dynamic repository figures — zero hardcoded numbers
                    binding.tvTotalOutstanding.text = FinancialFormatters.formatPaiseForLanguage(totals.totalReceivablesPaise)
                    binding.tvTotalRevenue.text = FinancialFormatters.formatPaiseToRupees(totals.totalWorkRevenuePaise)
                    binding.tvTotalPayments.text = FinancialFormatters.formatPaiseToRupees(totals.totalPaymentsCollectedPaise)
                    binding.tvTotalAdvances.text = FinancialFormatters.formatPaiseToRupees(totals.totalAdvancesPaise)
                    binding.tvTotalExpenses.text = FinancialFormatters.formatPaiseToRupees(totals.totalExpensesPaise)

                    // Recent activities
                    activityAdapter.submitList(state.recentActivities)
                    binding.tvEmptyActivity.visibility =
                        if (state.recentActivities.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
