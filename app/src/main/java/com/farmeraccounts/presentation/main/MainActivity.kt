package com.farmeraccounts.presentation.main

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.farmeraccounts.FarmerAccountsApp
import com.farmeraccounts.R
import com.farmeraccounts.databinding.ActivityMainBinding
import com.farmeraccounts.navigation.Navigator
import com.farmeraccounts.presentation.customers.AddEditCustomerFragment
import com.farmeraccounts.presentation.customers.CustomerDetailFragment
import com.farmeraccounts.presentation.customers.CustomerListFragment
import com.farmeraccounts.presentation.expense.RecordExpenseFragment
import com.farmeraccounts.presentation.home.HomeFragment
import com.farmeraccounts.presentation.history.HistoryFragment
import com.farmeraccounts.presentation.payment.RecordPaymentFragment
import com.farmeraccounts.presentation.settings.SettingsFragment
import com.farmeraccounts.presentation.work.AddWorkFragment
import com.farmeraccounts.presentation.work.WorkListFragment

class MainActivity : AppCompatActivity(), Navigator {

    private lateinit var binding: ActivityMainBinding
    private var isSyncingBottomNav = false

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        val light = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK != android.content.res.Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowInsetsControllerCompat(window,binding.root).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }

        setupToolbar()
        setupBottomNav()
        handleBackPress()

        if (savedInstanceState == null) {
            navigateToHome()
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as? FarmerAccountsApp
        app?.container?.syncManager?.triggerOneTimeSync(refreshCloud = true)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.topAppBar)
        supportFragmentManager.addOnBackStackChangedListener {
            updateToolbarForCurrentFragment()
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (isSyncingBottomNav) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.nav_home -> {
                    navigateToHome()
                    true
                }
                R.id.nav_work -> {
                    navigateToWorkList()
                    true
                }
                R.id.nav_customers -> {
                    navigateToCustomers()
                    true
                }
                R.id.nav_settings -> {
                    navigateToSettings()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateToolbarForCurrentFragment() {
        val isRoot = supportFragmentManager.backStackEntryCount == 0
        supportActionBar?.setDisplayHomeAsUpEnabled(!isRoot)
        if (!isRoot) {
            binding.topAppBar.setNavigationOnClickListener {
                navigateBack()
            }
        } else {
            binding.topAppBar.navigationIcon = null
        }

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        val navId = when (currentFragment) {
            is HomeFragment -> R.id.nav_home
            is WorkListFragment, is AddWorkFragment -> R.id.nav_work
            is CustomerListFragment, is CustomerDetailFragment, is AddEditCustomerFragment -> R.id.nav_customers
            is SettingsFragment -> R.id.nav_settings
            else -> null
        }
        navId?.let { updateBottomNavSelection(it) }
    }

    private fun updateBottomNavSelection(itemId: Int) {
        if (binding.bottomNav.selectedItemId != itemId) {
            isSyncingBottomNav = true
            binding.bottomNav.selectedItemId = itemId
            isSyncingBottomNav = false
        }
    }

    private fun clearBackStack() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    private fun handleBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navigateBack()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun navigateToHome() {
        clearBackStack()
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, HomeFragment.newInstance())
        }
        updateBottomNavSelection(R.id.nav_home)
    }

    override fun navigateToWorkList() {
        clearBackStack()
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, WorkListFragment.newInstance())
        }
        updateBottomNavSelection(R.id.nav_work)
    }

    override fun navigateToAddWork(preselectedCustomerId: String?) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, AddWorkFragment.newInstance(preselectedCustomerId))
            addToBackStack("add_work")
        }
    }

    override fun navigateToCustomers() {
        clearBackStack()
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, CustomerListFragment.newInstance())
        }
        updateBottomNavSelection(R.id.nav_customers)
    }

    override fun navigateToCustomerDetail(customerId: String) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, CustomerDetailFragment.newInstance(customerId))
            addToBackStack("customer_detail")
        }
    }

    override fun navigateToAddCustomer() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, AddEditCustomerFragment.newInstance())
            addToBackStack("add_customer")
        }
    }

    override fun navigateToEditCustomer(customerId: String) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, AddEditCustomerFragment.newInstance(customerId))
            addToBackStack("edit_customer")
        }
    }

    override fun navigateToRecordPayment(preselectedCustomerId: String?, preselectedWorkEntryId: String?) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, RecordPaymentFragment.newInstance(preselectedCustomerId, preselectedWorkEntryId))
            addToBackStack("record_payment")
        }
    }

    override fun navigateToRecordExpense() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, RecordExpenseFragment.newInstance())
            addToBackStack("record_expense")
        }
    }

    override fun navigateToHistory(deleted: Boolean) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, HistoryFragment.newInstance(deleted))
            addToBackStack(if (deleted) "deleted_history" else "expense_history")
        }
    }

    override fun navigateToSettings() {
        clearBackStack()
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, SettingsFragment.newInstance())
        }
        updateBottomNavSelection(R.id.nav_settings)
    }

    override fun navigateBack(): Boolean {
        return if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            true
        } else {
            false
        }
    }

    override fun setToolbarTitle(title: String) {
        binding.topAppBar.title = title
    }
}

