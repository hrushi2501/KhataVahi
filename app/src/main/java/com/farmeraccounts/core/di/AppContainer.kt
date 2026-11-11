package com.farmeraccounts.core.di

import android.content.Context
import com.farmeraccounts.core.network.SupabaseClientProvider
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.preferences.ThemePreferencesRepository
import com.farmeraccounts.data.repository.AuditRepositoryImpl
import com.farmeraccounts.data.repository.CustomerRepositoryImpl
import com.farmeraccounts.data.repository.ExpenseRepositoryImpl
import com.farmeraccounts.data.repository.PaymentRepositoryImpl
import com.farmeraccounts.data.repository.SupabaseAuthRepositoryImpl
import com.farmeraccounts.data.repository.TractorRepositoryImpl
import com.farmeraccounts.data.repository.WorkRepositoryImpl
import com.farmeraccounts.data.sync.CloudHydrationManager
import com.farmeraccounts.data.sync.OutboxSyncProcessor
import com.farmeraccounts.data.sync.SyncManager
import com.farmeraccounts.domain.repository.AuditRepository
import com.farmeraccounts.domain.repository.AuthRepository
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.repository.ExpenseRepository
import com.farmeraccounts.domain.repository.PaymentRepository
import com.farmeraccounts.domain.repository.TractorRepository
import com.farmeraccounts.domain.repository.WorkRepository
import com.farmeraccounts.domain.usecase.AddCustomerUseCase
import com.farmeraccounts.domain.usecase.AddWorkUseCase
import com.farmeraccounts.domain.usecase.CalculateCustomerBalanceUseCase
import com.farmeraccounts.domain.usecase.CalculateDashboardTotalsUseCase
import com.farmeraccounts.domain.usecase.CorrectEntryUseCase
import com.farmeraccounts.domain.usecase.GetCustomerLedgerUseCase
import com.farmeraccounts.domain.usecase.RecordExpenseUseCase
import com.farmeraccounts.domain.usecase.RecordPaymentUseCase
import io.github.jan.supabase.SupabaseClient

/**
 * Manual Dependency Injection container per project rules.
 * No external DI frameworks; simple, testable, and transparent lifecycle management.
 */
interface AppContainer {
    val database: AppDatabase
    val themePreferencesRepository: ThemePreferencesRepository
    val auditRepository: AuditRepository

    val customerRepository: CustomerRepository
    val tractorRepository: TractorRepository
    val workRepository: WorkRepository
    val paymentRepository: PaymentRepository
    val expenseRepository: ExpenseRepository

    val addCustomerUseCase: AddCustomerUseCase
    val addWorkUseCase: AddWorkUseCase
    val recordPaymentUseCase: RecordPaymentUseCase
    val recordExpenseUseCase: RecordExpenseUseCase
    val calculateCustomerBalanceUseCase: CalculateCustomerBalanceUseCase
    val calculateDashboardTotalsUseCase: CalculateDashboardTotalsUseCase
    val getCustomerLedgerUseCase: GetCustomerLedgerUseCase
    val correctEntryUseCase: CorrectEntryUseCase

    // Supabase Cloud Synchronization & Auth
    val supabaseClient: SupabaseClient?
    val authRepository: AuthRepository
    val outboxSyncProcessor: OutboxSyncProcessor?
    val cloudHydrationManager: CloudHydrationManager?
    val syncManager: SyncManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    override val themePreferencesRepository: ThemePreferencesRepository by lazy {
        ThemePreferencesRepository(context)
    }

    override val auditRepository: AuditRepository by lazy {
        AuditRepositoryImpl(database.auditLogDao())
    }

    override val customerRepository: CustomerRepository by lazy {
        CustomerRepositoryImpl(database)
    }

    override val tractorRepository: TractorRepository by lazy {
        TractorRepositoryImpl(database)
    }

    override val workRepository: WorkRepository by lazy {
        WorkRepositoryImpl(database)
    }

    override val paymentRepository: PaymentRepository by lazy {
        PaymentRepositoryImpl(database)
    }

    override val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepositoryImpl(database)
    }

    override val addCustomerUseCase: AddCustomerUseCase by lazy {
        AddCustomerUseCase(customerRepository)
    }

    override val addWorkUseCase: AddWorkUseCase by lazy {
        AddWorkUseCase(workRepository)
    }

    override val recordPaymentUseCase: RecordPaymentUseCase by lazy {
        RecordPaymentUseCase(paymentRepository)
    }

    override val recordExpenseUseCase: RecordExpenseUseCase by lazy {
        RecordExpenseUseCase(expenseRepository)
    }

    override val calculateCustomerBalanceUseCase: CalculateCustomerBalanceUseCase by lazy {
        CalculateCustomerBalanceUseCase(workRepository, paymentRepository)
    }

    override val calculateDashboardTotalsUseCase: CalculateDashboardTotalsUseCase by lazy {
        CalculateDashboardTotalsUseCase(customerRepository, workRepository, paymentRepository, expenseRepository)
    }

    override val getCustomerLedgerUseCase: GetCustomerLedgerUseCase by lazy {
        GetCustomerLedgerUseCase(customerRepository, workRepository, paymentRepository)
    }

    override val correctEntryUseCase: CorrectEntryUseCase by lazy {
        CorrectEntryUseCase(customerRepository, workRepository, paymentRepository, expenseRepository)
    }

    override val supabaseClient: SupabaseClient? by lazy {
        SupabaseClientProvider.createClient()
    }

    override val authRepository: AuthRepository by lazy {
        SupabaseAuthRepositoryImpl(context, supabaseClient)
    }

    override val outboxSyncProcessor: OutboxSyncProcessor? by lazy {
        supabaseClient?.let { OutboxSyncProcessor(database, it) }
    }

    override val cloudHydrationManager: CloudHydrationManager? by lazy {
        supabaseClient?.let { CloudHydrationManager(database, it, context) }
    }

    override val syncManager: SyncManager by lazy {
        SyncManager(
            context = context,
            database = database,
            authRepository = authRepository,
            outboxSyncProcessor = outboxSyncProcessor,
            cloudHydrationManager = cloudHydrationManager,
            supabaseClient = supabaseClient
        )
    }
}
