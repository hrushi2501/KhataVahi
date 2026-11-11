-- Farmer Accounts (ખેડૂત ખાતાવહી) — Supabase PostgreSQL Schema & Security Migration
-- Version: 20261110000001_initial_schema.sql
-- Enforces:
-- 1. UUID primary keys across all synchronized records
-- 2. Owner-scoping via auth.users(id)
-- 3. Strict Row Level Security (RLS) on every table
-- 4. Financial and land quantity check constraints (paise, scaled integers)
-- 5. Explicit foreign keys and performance indexes

-- ==============================================================================
-- 1. CUSTOMERS TABLE
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.customers (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    nickname TEXT,
    father_name TEXT,
    village TEXT,
    phone TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_customers_owner_id ON public.customers(owner_id);
CREATE INDEX IF NOT EXISTS idx_customers_is_deleted ON public.customers(is_deleted);
CREATE INDEX IF NOT EXISTS idx_customers_name ON public.customers(name);
CREATE INDEX IF NOT EXISTS idx_customers_phone ON public.customers(phone);

-- ==============================================================================
-- 2. TRACTORS TABLE
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.tractors (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    model TEXT,
    type TEXT NOT NULL, -- BIG, MINI
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_tractors_owner_id ON public.tractors(owner_id);
CREATE INDEX IF NOT EXISTS idx_tractors_type ON public.tractors(type);
CREATE INDEX IF NOT EXISTS idx_tractors_is_deleted ON public.tractors(is_deleted);

-- ==============================================================================
-- 3. WORK ENTRIES TABLE
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.work_entries (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customers(id) ON DELETE RESTRICT,
    tractor_id UUID NOT NULL REFERENCES public.tractors(id) ON DELETE RESTRICT,
    work_type TEXT NOT NULL,
    date TIMESTAMPTZ NOT NULL,
    quantity_scaled BIGINT NOT NULL,
    quantity_unit TEXT NOT NULL,
    rate_per_unit_paise BIGINT NOT NULL,
    total_paise BIGINT NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT check_work_quantity_positive CHECK (quantity_scaled > 0),
    CONSTRAINT check_work_rate_positive CHECK (rate_per_unit_paise >= 0),
    CONSTRAINT check_work_total_non_negative CHECK (total_paise >= 0)
);

CREATE INDEX IF NOT EXISTS idx_work_entries_owner_id ON public.work_entries(owner_id);
CREATE INDEX IF NOT EXISTS idx_work_entries_customer_id ON public.work_entries(customer_id);
CREATE INDEX IF NOT EXISTS idx_work_entries_tractor_id ON public.work_entries(tractor_id);
CREATE INDEX IF NOT EXISTS idx_work_entries_date ON public.work_entries(date);
CREATE INDEX IF NOT EXISTS idx_work_entries_is_deleted ON public.work_entries(is_deleted);

-- ==============================================================================
-- 4. PAYMENTS TABLE
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.payments (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customers(id) ON DELETE RESTRICT,
    work_entry_id UUID REFERENCES public.work_entries(id) ON DELETE SET NULL,
    amount_paise BIGINT NOT NULL,
    payment_date TIMESTAMPTZ NOT NULL,
    payment_method TEXT NOT NULL, -- CASH, UPI, BANK_TRANSFER
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT check_payment_amount_positive CHECK (amount_paise > 0)
);

CREATE INDEX IF NOT EXISTS idx_payments_owner_id ON public.payments(owner_id);
CREATE INDEX IF NOT EXISTS idx_payments_customer_id ON public.payments(customer_id);
CREATE INDEX IF NOT EXISTS idx_payments_work_entry_id ON public.payments(work_entry_id);
CREATE INDEX IF NOT EXISTS idx_payments_payment_date ON public.payments(payment_date);
CREATE INDEX IF NOT EXISTS idx_payments_is_deleted ON public.payments(is_deleted);

-- ==============================================================================
-- 5. EXPENSES TABLE
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.expenses (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    tractor_id UUID REFERENCES public.tractors(id) ON DELETE SET NULL,
    category TEXT NOT NULL, -- DIESEL, MAINTENANCE, REPAIRS, SPARE_PARTS, LABOUR, OTHER
    amount_paise BIGINT NOT NULL,
    expense_date TIMESTAMPTZ NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT check_expense_amount_positive CHECK (amount_paise > 0)
);

CREATE INDEX IF NOT EXISTS idx_expenses_owner_id ON public.expenses(owner_id);
CREATE INDEX IF NOT EXISTS idx_expenses_tractor_id ON public.expenses(tractor_id);
CREATE INDEX IF NOT EXISTS idx_expenses_expense_date ON public.expenses(expense_date);
CREATE INDEX IF NOT EXISTS idx_expenses_is_deleted ON public.expenses(is_deleted);

-- ==============================================================================
-- 6. AUDIT LOGS TABLE
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    entity_type TEXT NOT NULL,
    entity_id UUID NOT NULL,
    action TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    details TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_owner_id ON public.audit_logs(owner_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON public.audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON public.audit_logs(timestamp);

-- ==============================================================================
-- 7. ROW LEVEL SECURITY (RLS) POLICIES
-- Strict owner isolation: auth.uid() = owner_id
-- ==============================================================================

-- Enable RLS on all tables
ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tractors ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.work_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- 7.1 Customers RLS
CREATE POLICY "customers_select_owner" ON public.customers
    FOR SELECT USING (auth.uid() = owner_id);

CREATE POLICY "customers_insert_owner" ON public.customers
    FOR INSERT WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "customers_update_owner" ON public.customers
    FOR UPDATE USING (auth.uid() = owner_id) WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "customers_delete_owner" ON public.customers
    FOR DELETE USING (auth.uid() = owner_id);

-- 7.2 Tractors RLS
CREATE POLICY "tractors_select_owner" ON public.tractors
    FOR SELECT USING (auth.uid() = owner_id);

CREATE POLICY "tractors_insert_owner" ON public.tractors
    FOR INSERT WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "tractors_update_owner" ON public.tractors
    FOR UPDATE USING (auth.uid() = owner_id) WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "tractors_delete_owner" ON public.tractors
    FOR DELETE USING (auth.uid() = owner_id);

-- 7.3 Work Entries RLS
CREATE POLICY "work_entries_select_owner" ON public.work_entries
    FOR SELECT USING (auth.uid() = owner_id);

CREATE POLICY "work_entries_insert_owner" ON public.work_entries
    FOR INSERT WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "work_entries_update_owner" ON public.work_entries
    FOR UPDATE USING (auth.uid() = owner_id) WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "work_entries_delete_owner" ON public.work_entries
    FOR DELETE USING (auth.uid() = owner_id);

-- 7.4 Payments RLS
CREATE POLICY "payments_select_owner" ON public.payments
    FOR SELECT USING (auth.uid() = owner_id);

CREATE POLICY "payments_insert_owner" ON public.payments
    FOR INSERT WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "payments_update_owner" ON public.payments
    FOR UPDATE USING (auth.uid() = owner_id) WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "payments_delete_owner" ON public.payments
    FOR DELETE USING (auth.uid() = owner_id);

-- 7.5 Expenses RLS
CREATE POLICY "expenses_select_owner" ON public.expenses
    FOR SELECT USING (auth.uid() = owner_id);

CREATE POLICY "expenses_insert_owner" ON public.expenses
    FOR INSERT WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "expenses_update_owner" ON public.expenses
    FOR UPDATE USING (auth.uid() = owner_id) WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "expenses_delete_owner" ON public.expenses
    FOR DELETE USING (auth.uid() = owner_id);

-- 7.6 Audit Logs RLS
CREATE POLICY "audit_logs_select_owner" ON public.audit_logs
    FOR SELECT USING (auth.uid() = owner_id);

CREATE POLICY "audit_logs_insert_owner" ON public.audit_logs
    FOR INSERT WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "audit_logs_update_owner" ON public.audit_logs
    FOR UPDATE USING (auth.uid() = owner_id) WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "audit_logs_delete_owner" ON public.audit_logs
    FOR DELETE USING (auth.uid() = owner_id);

-- Owner provisioning and disabling public signups are Auth configuration tasks.
-- Do not create registration triggers on Supabase-managed auth.users.
