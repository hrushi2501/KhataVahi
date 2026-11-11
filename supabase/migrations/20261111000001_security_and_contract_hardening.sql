-- Review-only hardening for initial schema. Apply first on isolated Supabase and test.
-- No live database was contacted by the author of this migration.
BEGIN;

-- Owner-aware candidate keys allow relational links to prove same-owner membership.
ALTER TABLE public.customers ADD CONSTRAINT customers_owner_id_id_key UNIQUE (owner_id, id);
ALTER TABLE public.tractors ADD CONSTRAINT tractors_owner_id_id_key UNIQUE (owner_id, id);
ALTER TABLE public.work_entries ADD CONSTRAINT work_entries_owner_id_id_key UNIQUE (owner_id, id);

-- Drop globally-scoped FKs, then install tenant-scoped references.
ALTER TABLE public.work_entries DROP CONSTRAINT work_entries_customer_id_fkey;
ALTER TABLE public.work_entries DROP CONSTRAINT work_entries_tractor_id_fkey;
ALTER TABLE public.work_entries ADD CONSTRAINT work_entries_customer_owner_fk
  FOREIGN KEY (owner_id, customer_id) REFERENCES public.customers(owner_id, id) ON DELETE RESTRICT;
ALTER TABLE public.work_entries ADD CONSTRAINT work_entries_tractor_owner_fk
  FOREIGN KEY (owner_id, tractor_id) REFERENCES public.tractors(owner_id, id) ON DELETE RESTRICT;

ALTER TABLE public.payments DROP CONSTRAINT payments_customer_id_fkey;
ALTER TABLE public.payments DROP CONSTRAINT payments_work_entry_id_fkey;
ALTER TABLE public.payments ADD CONSTRAINT payments_customer_owner_fk
  FOREIGN KEY (owner_id, customer_id) REFERENCES public.customers(owner_id, id) ON DELETE RESTRICT;
ALTER TABLE public.work_entries ADD CONSTRAINT work_entries_owner_id_id_customer_key
  UNIQUE (owner_id, id, customer_id);

ALTER TABLE public.payments ADD CONSTRAINT payments_work_customer_owner_fk
  FOREIGN KEY (owner_id, work_entry_id, customer_id)
  REFERENCES public.work_entries(owner_id, id, customer_id) ON DELETE RESTRICT;


ALTER TABLE public.expenses DROP CONSTRAINT expenses_tractor_id_fkey;
ALTER TABLE public.expenses ADD CONSTRAINT expenses_tractor_owner_fk
  FOREIGN KEY (owner_id, tractor_id) REFERENCES public.tractors(owner_id, id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_work_entries_owner_customer ON public.work_entries(owner_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_work_entries_owner_tractor ON public.work_entries(owner_id, tractor_id);
CREATE INDEX IF NOT EXISTS idx_payments_owner_customer ON public.payments(owner_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_payments_owner_work_customer ON public.payments(owner_id, work_entry_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_expenses_owner_tractor ON public.expenses(owner_id, tractor_id);

-- Audit is append-only through the client API. Existing update/delete policies are removed.
DROP POLICY IF EXISTS audit_logs_update_owner ON public.audit_logs;
DROP POLICY IF EXISTS audit_logs_delete_owner ON public.audit_logs;

DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['customers','tractors','work_entries','payments','expenses','audit_logs'] LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', t || '_select_owner', t);
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', t || '_insert_owner', t);
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', t || '_update_owner', t);
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', t || '_delete_owner', t);
    EXECUTE format('CREATE POLICY %I ON public.%I FOR SELECT TO authenticated USING ((select auth.uid()) = owner_id)', t || '_select_owner', t);
    EXECUTE format('CREATE POLICY %I ON public.%I FOR INSERT TO authenticated WITH CHECK ((select auth.uid()) = owner_id)', t || '_insert_owner', t);
    IF t <> 'audit_logs' THEN
      EXECUTE format('CREATE POLICY %I ON public.%I FOR UPDATE TO authenticated USING ((select auth.uid()) = owner_id) WITH CHECK ((select auth.uid()) = owner_id)', t || '_update_owner', t);
    END IF;
    -- Deliberately no DELETE policy: financial rows use tombstones; audit is immutable.
  END LOOP;
END $$;

-- RLS does not grant table privileges. Explicitly constrain client-role table access.
REVOKE ALL ON public.customers, public.tractors, public.work_entries,
  public.payments, public.expenses, public.audit_logs FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.customers, public.tractors,
  public.work_entries, public.payments, public.expenses TO authenticated;
GRANT SELECT, INSERT ON public.audit_logs TO authenticated;
REVOKE DELETE ON public.customers, public.tractors, public.work_entries,
  public.payments, public.expenses, public.audit_logs FROM anon, authenticated;

-- Numeric arithmetic avoids intermediate BIGINT overflow and matches local half-up rounding.
ALTER TABLE public.work_entries ADD CONSTRAINT work_total_matches_quantity_rate
 CHECK (total_paise::numeric = floor((quantity_scaled::numeric * rate_per_unit_paise::numeric + 500) / 1000));
ALTER TABLE public.work_entries ADD CONSTRAINT work_quantity_unit_valid
 CHECK (quantity_unit IN ('BIGHA', 'HOUR', 'TRIP'));
DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['customers','tractors','work_entries','payments','expenses'] LOOP
  EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I CHECK (version > 0)', t, t || '_positive_version');
 END LOOP;
END $$;
COMMIT;
