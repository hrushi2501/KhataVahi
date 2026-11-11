-- Preserve financial history for client-issued DELETE and server-side tombstones.
-- Review/test on an isolated project before applying to production.
BEGIN;

ALTER TABLE public.sync_changes DROP CONSTRAINT IF EXISTS sync_changes_table_name_check;
ALTER TABLE public.sync_changes ADD CONSTRAINT sync_changes_table_name_check
    CHECK (table_name IN ('customers','tractors','work_entries','payments','expenses','audit_logs','deleted_logs'));

CREATE TABLE public.deleted_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    entity_type TEXT NOT NULL CHECK (entity_type IN ('CUSTOMER','TRACTOR','WORK_ENTRY','PAYMENT','EXPENSE')),
    entity_id UUID NOT NULL,
    snapshot_json TEXT NOT NULL,
    reason TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX deleted_logs_owner_time_idx ON public.deleted_logs(owner_id, timestamp, id);
CREATE INDEX deleted_logs_owner_entity_idx ON public.deleted_logs(owner_id, entity_type, entity_id);
ALTER TABLE public.deleted_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.deleted_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY deleted_logs_select_owner ON public.deleted_logs
    FOR SELECT TO authenticated USING ((SELECT auth.uid()) = owner_id);
REVOKE ALL ON public.deleted_logs FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.deleted_logs TO authenticated;

CREATE OR REPLACE FUNCTION public.keep_financial_rows_as_tombstones()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    has_active_children BOOLEAN := FALSE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.is_deleted THEN
            RETURN NULL;
        END IF;

        IF TG_TABLE_NAME = 'customers' THEN
            SELECT EXISTS (SELECT 1 FROM public.work_entries WHERE owner_id = OLD.owner_id AND customer_id = OLD.id AND NOT is_deleted)
                OR EXISTS (SELECT 1 FROM public.payments WHERE owner_id = OLD.owner_id AND customer_id = OLD.id AND NOT is_deleted)
              INTO has_active_children;
            IF has_active_children THEN
                RAISE EXCEPTION 'Customer has active financial records; archive the customer instead'
                    USING ERRCODE = '23503';
            END IF;
        ELSIF TG_TABLE_NAME = 'tractors' THEN
            SELECT EXISTS (SELECT 1 FROM public.work_entries WHERE owner_id = OLD.owner_id AND tractor_id = OLD.id AND NOT is_deleted)
                OR EXISTS (SELECT 1 FROM public.expenses WHERE owner_id = OLD.owner_id AND tractor_id = OLD.id AND NOT is_deleted)
              INTO has_active_children;
            IF has_active_children THEN
                RAISE EXCEPTION 'Tractor has active financial records; archive the tractor instead'
                    USING ERRCODE = '23503';
            END IF;
        END IF;

        EXECUTE format(
            'UPDATE public.%I SET is_deleted = TRUE, version = version + 1, updated_at = clock_timestamp() WHERE owner_id = $1 AND id = $2 AND NOT is_deleted',
            TG_TABLE_NAME
        ) USING OLD.owner_id, OLD.id;
        -- The UPDATE triggers record the tombstone and immutable pre-delete snapshot.
        RETURN NULL;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF OLD.is_deleted AND NOT NEW.is_deleted THEN
            RAISE EXCEPTION 'Restoring a deleted financial record is not supported'
                USING ERRCODE = '23514';
        END IF;
        IF OLD.is_deleted AND NEW IS DISTINCT FROM OLD THEN
            RAISE EXCEPTION 'Deleted financial records are immutable'
                USING ERRCODE = '23514';
        END IF;
        IF NOT OLD.is_deleted AND NEW.is_deleted THEN
            IF TG_TABLE_NAME = 'customers' AND (
                EXISTS (SELECT 1 FROM public.work_entries WHERE owner_id=OLD.owner_id AND customer_id=OLD.id AND NOT is_deleted)
                OR EXISTS (SELECT 1 FROM public.payments WHERE owner_id=OLD.owner_id AND customer_id=OLD.id AND NOT is_deleted)) THEN
                RAISE EXCEPTION 'Customer has active financial records' USING ERRCODE='23503';
            END IF;
            IF TG_TABLE_NAME = 'tractors' AND (
                EXISTS (SELECT 1 FROM public.work_entries WHERE owner_id=OLD.owner_id AND tractor_id=OLD.id AND NOT is_deleted)
                OR EXISTS (SELECT 1 FROM public.expenses WHERE owner_id=OLD.owner_id AND tractor_id=OLD.id AND NOT is_deleted)) THEN
                RAISE EXCEPTION 'Tractor has active financial records' USING ERRCODE='23503';
            END IF;
        END IF;
        IF NOT OLD.is_deleted AND NEW.is_deleted THEN
            NEW.version := GREATEST(OLD.version + 1, NEW.version);
            NEW.updated_at := clock_timestamp();
        ELSIF NEW IS DISTINCT FROM OLD THEN
            NEW.version := GREATEST(OLD.version + 1, NEW.version);
            NEW.updated_at := clock_timestamp();
        ELSIF NEW.version < OLD.version THEN
            RAISE EXCEPTION 'Financial record version cannot move backwards'
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.keep_financial_rows_as_tombstones() FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.capture_deleted_snapshot()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    delete_reason TEXT;
BEGIN
    IF OLD.is_deleted OR NOT NEW.is_deleted THEN
        RETURN NEW;
    END IF;
    delete_reason := left(nullif(current_setting('app.deletion_reason', TRUE), ''), 500);
    IF delete_reason IS NULL THEN delete_reason := 'Deleted by owner'; END IF;
    INSERT INTO public.deleted_logs(id, owner_id, entity_type, entity_id, snapshot_json, reason, timestamp)
    VALUES (COALESCE(nullif(current_setting('app.deletion_log_id', TRUE), '')::UUID, gen_random_uuid()), NEW.owner_id, upper(CASE TG_TABLE_NAME
        WHEN 'work_entries' THEN 'WORK_ENTRY'
        ELSE left(TG_TABLE_NAME, length(TG_TABLE_NAME) - 1)
    END), OLD.id, to_jsonb(OLD)::TEXT, delete_reason, NEW.updated_at)
    ;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.capture_deleted_snapshot() FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.reject_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
    RAISE EXCEPTION 'Audit history is immutable' USING ERRCODE = '23514';
END;
$$;
REVOKE ALL ON FUNCTION public.reject_audit_mutation() FROM PUBLIC, anon, authenticated;

DO $$
DECLARE business_table TEXT;
BEGIN
    FOREACH business_table IN ARRAY ARRAY['customers','tractors','work_entries','payments','expenses'] LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS protect_tombstones ON public.%I', business_table);
        EXECUTE format('CREATE TRIGGER protect_tombstones BEFORE UPDATE OR DELETE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.keep_financial_rows_as_tombstones()', business_table);
        EXECUTE format('DROP TRIGGER IF EXISTS capture_deleted_snapshot ON public.%I', business_table);
        EXECUTE format('CREATE TRIGGER capture_deleted_snapshot AFTER UPDATE ON public.%I FOR EACH ROW WHEN (OLD.is_deleted IS DISTINCT FROM NEW.is_deleted) EXECUTE FUNCTION public.capture_deleted_snapshot()', business_table);
    END LOOP;
END;
$$;

DROP TRIGGER IF EXISTS audit_logs_immutable ON public.audit_logs;
CREATE TRIGGER audit_logs_immutable BEFORE UPDATE OR DELETE ON public.audit_logs
    FOR EACH ROW EXECUTE FUNCTION public.reject_audit_mutation();
CREATE TRIGGER deleted_logs_immutable BEFORE UPDATE OR DELETE ON public.deleted_logs
    FOR EACH ROW EXECUTE FUNCTION public.reject_audit_mutation();

-- The deleted-log rows themselves are an append-only server record. The
-- sync_changes trigger from the preceding migration records their insertion.
CREATE TRIGGER capture_sync_change AFTER INSERT OR UPDATE OR DELETE ON public.deleted_logs
    FOR EACH ROW EXECUTE FUNCTION public.capture_sync_change();

COMMIT;
