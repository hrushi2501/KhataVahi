-- Confirmed Supabase state is authoritative. Clients submit conditional mutations;
-- they never overwrite a newer server revision with a blind upsert.
BEGIN;
CREATE TABLE public.mutation_receipts (
    request_id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    entity_type text NOT NULL,
    entity_id uuid NOT NULL,
    request_payload jsonb NOT NULL,
    resulting_version bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX mutation_receipts_owner_id ON public.mutation_receipts(owner_id);
ALTER TABLE public.mutation_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mutation_receipts FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.mutation_receipts FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.mutation_receipts TO authenticated;
CREATE POLICY mutation_receipts_owner ON public.mutation_receipts FOR SELECT TO authenticated
 USING ((SELECT auth.uid()) = owner_id);

CREATE FUNCTION public.apply_ledger_mutation(p_request_id uuid, p_entity_type text,
 p_entity_id uuid, p_expected_version bigint, p_payload jsonb,
 p_deletion_log_id uuid DEFAULT NULL, p_deletion_reason text DEFAULT NULL, p_deletion_snapshot text DEFAULT NULL)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public AS $$
DECLARE
 uid uuid := auth.uid();
 target_table text;
 old_row jsonb;
 candidate jsonb;
 result_version bigint;
 prior public.mutation_receipts%ROWTYPE;
 envelope jsonb;
 cols text;
 previous_log_id text := current_setting('app.deletion_log_id',true);
 previous_reason text := current_setting('app.deletion_reason',true);
BEGIN
 IF uid IS NULL THEN RAISE EXCEPTION 'Sign in required' USING ERRCODE='42501'; END IF;
 target_table := CASE p_entity_type
  WHEN 'CUSTOMER' THEN 'customers' WHEN 'TRACTOR' THEN 'tractors'
  WHEN 'WORK_ENTRY' THEN 'work_entries' WHEN 'PAYMENT' THEN 'payments'
  WHEN 'EXPENSE' THEN 'expenses' WHEN 'AUDIT_LOG' THEN 'audit_logs'
  WHEN 'DELETED_LOG' THEN 'deleted_logs' ELSE NULL END;
 IF target_table IS NULL OR p_payload->>'id' IS DISTINCT FROM p_entity_id::text
 THEN RAISE EXCEPTION 'Invalid mutation' USING ERRCODE='22023'; END IF;
 -- Same lock as the change feed: serialize confirmation and change sequence allocation.
 PERFORM pg_advisory_xact_lock(761934821, 1);
 envelope := jsonb_build_object('entity_type',p_entity_type,'entity_id',p_entity_id,
   'base_version',p_expected_version,'payload',p_payload,'deletion_log_id',p_deletion_log_id,
   'deletion_reason',p_deletion_reason,'deletion_snapshot',p_deletion_snapshot);
 SELECT * INTO prior FROM public.mutation_receipts WHERE request_id=p_request_id;
 IF FOUND THEN
  IF prior.owner_id<>uid OR prior.request_payload<>envelope THEN
   RAISE EXCEPTION 'Request identity conflict' USING ERRCODE='23505';
  END IF;
  RETURN jsonb_build_object('request_id',p_request_id,'version',prior.resulting_version);
 END IF;
 EXECUTE format('SELECT to_jsonb(t) FROM public.%I t WHERE id=$1 AND owner_id=$2 FOR UPDATE',target_table)
  INTO old_row USING p_entity_id,uid;
 EXECUTE format('SELECT to_jsonb(jsonb_populate_record(NULL::public.%I,$1))',target_table)
  INTO candidate USING (p_payload || jsonb_build_object('owner_id',uid));
 IF target_table='deleted_logs' THEN
  -- A business tombstone trigger has already created this canonical log. Client
  -- snapshots are only pending projections and cannot rewrite server history.
  IF old_row IS NULL THEN RAISE EXCEPTION 'Deletion is not confirmed' USING ERRCODE='23503'; END IF;
  result_version:=1;
 ELSIF target_table='audit_logs' THEN
  IF old_row IS NOT NULL AND old_row<>candidate THEN
   RAISE EXCEPTION 'Audit history is immutable' USING ERRCODE='23514';
  END IF;
  IF old_row IS NULL THEN
   EXECUTE format('INSERT INTO public.%I SELECT (jsonb_populate_record(NULL::public.%I,$1)).*',target_table,target_table)
    USING candidate;
  END IF;
  result_version:=1;
 ELSE
  IF old_row IS NOT NULL AND (old_row->>'is_deleted')::boolean AND candidate<>old_row THEN
   RAISE EXCEPTION 'Record was deleted online' USING ERRCODE='23514';
  END IF;
  IF old_row IS NULL THEN
   IF p_expected_version IS NOT NULL THEN
    RAISE EXCEPTION 'Server record is missing; refresh required' USING ERRCODE='40001';
   END IF;
  ELSIF candidate<>old_row THEN
   IF p_expected_version IS NULL OR (old_row->>'version')::bigint<>p_expected_version THEN
    RAISE EXCEPTION 'Newer server record; refresh required' USING ERRCODE='40001';
   END IF;
   IF (candidate->>'version')::bigint <= p_expected_version THEN
    RAISE EXCEPTION 'Version must increase' USING ERRCODE='23514';
   END IF;
  END IF;
  IF (candidate->>'is_deleted')::boolean AND (old_row IS NULL OR NOT (old_row->>'is_deleted')::boolean) THEN
   IF p_deletion_log_id IS NULL OR length(trim(coalesce(p_deletion_reason,'')))=0 THEN
    RAISE EXCEPTION 'Deletion reason required' USING ERRCODE='23514';
   END IF;
   PERFORM set_config('app.deletion_log_id',p_deletion_log_id::text,true);
   PERFORM set_config('app.deletion_reason',p_deletion_reason,true);
  END IF;
  IF old_row IS NULL THEN
   EXECUTE format('INSERT INTO public.%I SELECT (jsonb_populate_record(NULL::public.%I,$1)).* RETURNING version',target_table,target_table)
    INTO result_version USING candidate;
  ELSIF candidate=old_row THEN result_version:=(old_row->>'version')::bigint;
  ELSE
   SELECT string_agg(format('%I = v.%I',a.attname,a.attname),',') INTO cols
    FROM pg_attribute a WHERE a.attrelid=format('public.%I',target_table)::regclass
    AND a.attnum>0 AND NOT a.attisdropped AND a.attname NOT IN ('id','owner_id');
   EXECUTE format('UPDATE public.%I AS t SET %s FROM jsonb_populate_record(NULL::public.%I,$1) v WHERE t.id=$2 AND t.owner_id=$3 RETURNING t.version',target_table,cols,target_table)
    INTO result_version USING candidate,p_entity_id,uid;
  END IF;
 END IF;
 IF target_table IN ('customers','tractors','work_entries','payments','expenses')
    AND (candidate->>'is_deleted')::boolean AND p_deletion_log_id IS NOT NULL THEN
  IF old_row IS NULL THEN
   INSERT INTO public.deleted_logs(id,owner_id,entity_type,entity_id,snapshot_json,reason,timestamp)
   VALUES(p_deletion_log_id,uid,p_entity_type,p_entity_id,coalesce(p_deletion_snapshot,candidate::text),p_deletion_reason,clock_timestamp())
   ON CONFLICT(id) DO NOTHING;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM public.deleted_logs WHERE id=p_deletion_log_id AND owner_id=uid AND entity_type=p_entity_type AND entity_id=p_entity_id) THEN
   RAISE EXCEPTION 'Deletion history identity conflict' USING ERRCODE='23514';
  END IF;
 END IF;
 PERFORM set_config('app.deletion_log_id',coalesce(previous_log_id,''),true);
 PERFORM set_config('app.deletion_reason',coalesce(previous_reason,''),true);
 INSERT INTO public.mutation_receipts(request_id,owner_id,entity_type,entity_id,request_payload,resulting_version)
 VALUES(p_request_id,uid,p_entity_type,p_entity_id,envelope,result_version);
 RETURN jsonb_build_object('request_id',p_request_id,'version',result_version);
END $$;
REVOKE ALL ON FUNCTION public.apply_ledger_mutation(uuid,text,uuid,bigint,jsonb,uuid,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.apply_ledger_mutation(uuid,text,uuid,bigint,jsonb,uuid,text,text) TO authenticated;
-- All mobile writes now go through the owner/revision/receipt checks above.
REVOKE INSERT,UPDATE ON public.customers,public.tractors,public.work_entries,
 public.payments,public.expenses,public.audit_logs FROM authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;
