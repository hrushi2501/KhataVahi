package com.farmeraccounts.presentation.work

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
import com.farmeraccounts.databinding.FragmentWorkListBinding
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.base.ViewModelFactory
import com.farmeraccounts.presentation.base.RecordDeletionViewModel
import com.farmeraccounts.presentation.base.RecordKind
import com.farmeraccounts.presentation.base.DeleteRecordDialog
import com.farmeraccounts.presentation.base.showRecordActions
import android.widget.Toast
import kotlinx.coroutines.launch

class WorkListFragment : Fragment() {

    private var _binding: FragmentWorkListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WorkListViewModel by viewModels {
        val app = requireActivity().application as FarmerAccountsApp
        ViewModelFactory(app.container)
    }

    private val deletions: RecordDeletionViewModel by viewModels {
        ViewModelFactory((requireActivity().application as FarmerAccountsApp).container)
    }

    private lateinit var workAdapter: WorkAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? Navigator)?.setToolbarTitle(getString(R.string.title_work))

        setupRecyclerView()
        setupFab()
        binding.btnRetryList.setOnClickListener { viewModel.retry() }
        observeUiState()
    }

    private fun setupRecyclerView() {
        workAdapter = WorkAdapter(
            onWorkClick = { item -> (activity as? Navigator)?.navigateToCustomerDetail(item.workEntry.customerId) },
            onWorkActions = { item, anchor ->
                showRecordActions(anchor, RecordKind.WORK, item.workEntry.id,
                    "${item.customerName} · ${com.farmeraccounts.core.util.DisplayLanguage.workType(item.workEntry.workType)}")
            }
        )
        binding.rvWorkEntries.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = workAdapter
        }
    }

    private fun setupFab() {
        binding.fabAddWork.setOnClickListener {
            (activity as? Navigator)?.navigateToAddWork()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state ->
                    binding.skeletonLoading.root.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.rvWorkEntries.visibility = if (!state.isLoading && state.workEntries.isNotEmpty()) View.VISIBLE else View.GONE
                    binding.btnRetryList.visibility = if (state.hasError) View.VISIBLE else View.GONE
                    binding.tvEmptyWork.setText(if (state.hasError) R.string.history_load_failed else R.string.empty_work)
                    workAdapter.submitList(state.workEntries)
                    binding.tvEmptyWork.visibility =
                        if (state.workEntries.isEmpty() && !state.isLoading) View.VISIBLE else View.GONE
                } }
                launch {
                    deletions.state.collect { result ->
                        if (result.deletedId != null) {
                            (childFragmentManager.findFragmentByTag("delete_record") as? androidx.fragment.app.DialogFragment)?.dismiss()
                            Toast.makeText(requireContext(), R.string.record_removed, Toast.LENGTH_SHORT).show()
                            deletions.prepare()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = WorkListFragment()
    }
}

