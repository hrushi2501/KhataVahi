-- Owner-scoped incremental change feed. All writers take the same transaction
-- advisory lock before assigning a sequence, so a later committed sequence
-- cannot become visible before an earlier uncommitted sequence.
BEGIN;

CREATE SEQUENCE IF NOT EXISTS public.sync_change_sequence AS BIGINT;

CREATE TABLE IF NOT EXISTS public.sync_changes (
    sequence_id BIGINT PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    table_name TEXT NOT NULL CHECK (table_name IN (
        'customers', 'tractors', 'work_entries', 'payments', 'expenses', 'audit_logs'
    )),
    record_id UUID NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_sync_changes_owner_sequence
    ON public.sync_changes(owner_id, sequence_id);

ALTER TABLE public.sync_changes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_changes FORCE ROW LEVEL SECURITY;
CREATE POLICY sync_changes_select_owner ON public.sync_changes
    FOR SELECT TO authenticated USING ((SELECT auth.uid()) = owner_id);
REVOKE ALL ON public.sync_changes FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.sync_changes TO authenticated;
REVOKE ALL ON SEQUENCE public.sync_change_sequence FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.capture_sync_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    changed_owner UUID;
    changed_id UUID;
    changed_table TEXT := TG_TABLE_NAME;
    changed_operation TEXT := TG_OP;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW IS NOT DISTINCT FROM OLD THEN
        RETURN NEW;
    END IF;
    -- Transaction-scoped and global across the six source tables.
    PERFORM pg_advisory_xact_lock(761934821, 1);
    IF TG_OP = 'DELETE' THEN
        changed_owner := OLD.owner_id;
        changed_id := OLD.id;
    ELSE
        changed_owner := NEW.owner_id;
        changed_id := NEW.id;
    END IF;

    INSERT INTO public.sync_changes(sequence_id, owner_id, table_name, record_id, operation)
    VALUES (nextval('public.sync_change_sequence'), changed_owner, changed_table, changed_id, changed_operation);
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.capture_sync_change() FROM PUBLIC, anon, authenticated;

DO $$
DECLARE
    source_table TEXT;
BEGIN
    FOREACH source_table IN ARRAY ARRAY[
        'customers', 'tractors', 'work_entries', 'payments', 'expenses', 'audit_logs'
    ] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS capture_sync_change ON public.%I', source_table);
        EXECUTE format(
            'CREATE TRIGGER capture_sync_change AFTER INSERT OR UPDATE OR DELETE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.capture_sync_change()',
            source_table
        );
    END LOOP;
END;
$$;

-- Backfill a stable baseline for rows that existed before this migration.
-- DDL and backfill run in this transaction; acquire the same lock so trigger
-- events from subsequent writes cannot overtake the baseline.
SELECT pg_advisory_xact_lock(761934821, 1);
INSERT INTO public.sync_changes(sequence_id, owner_id, table_name, record_id, operation)
SELECT nextval('public.sync_change_sequence'), owner_id, table_name, record_id, 'INSERT'
FROM (
    SELECT owner_id, 'customers'::TEXT AS table_name, id AS record_id FROM public.customers
    UNION ALL SELECT owner_id, 'tractors', id FROM public.tractors
    UNION ALL SELECT owner_id, 'work_entries', id FROM public.work_entries
    UNION ALL SELECT owner_id, 'payments', id FROM public.payments
    UNION ALL SELECT owner_id, 'expenses', id FROM public.expenses
    UNION ALL SELECT owner_id, 'audit_logs', id FROM public.audit_logs
) existing_rows
ORDER BY owner_id,
    CASE table_name
        WHEN 'customers' THEN 1
        WHEN 'tractors' THEN 2
        WHEN 'work_entries' THEN 3
        WHEN 'payments' THEN 4
        WHEN 'expenses' THEN 5
        ELSE 6
    END,
    record_id;
COMMIT;
