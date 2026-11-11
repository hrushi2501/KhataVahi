-- TEST DATABASE ONLY: transactions create fake identities and roll back. Never run on the owner project.
BEGIN;
INSERT INTO auth.users VALUES ('11111111-1111-4111-8111-111111111111');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
DO $$
DECLARE payload jsonb; result jsonb; n bigint;
BEGIN
 payload := jsonb_build_object('id','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','owner_id',auth.uid(),'name','A','nickname',NULL,'father_name',NULL,'village',NULL,'phone',NULL,'created_at',now(),'updated_at',now(),'version',1,'is_deleted',false);
 result:=public.apply_ledger_mutation('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','CUSTOMER','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',NULL,payload);
 IF (result->>'version')::bigint<>1 THEN RAISE EXCEPTION 'Bad insert receipt'; END IF;
 payload:=payload||'{"name":"B","version":2}'::jsonb;
 result:=public.apply_ledger_mutation('cccccccc-cccc-4ccc-8ccc-cccccccccccc','CUSTOMER','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',1,payload);
 SELECT count(*) INTO n FROM public.sync_changes;
 PERFORM public.apply_ledger_mutation('cccccccc-cccc-4ccc-8ccc-cccccccccccc','CUSTOMER','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',1,payload);
 IF (SELECT count(*) FROM public.sync_changes)<>n THEN RAISE EXCEPTION 'Replay emitted duplicate'; END IF;
 BEGIN
  PERFORM public.apply_ledger_mutation('dddddddd-dddd-4ddd-8ddd-dddddddddddd','CUSTOMER','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',1,payload||'{"name":"Stale","version":3}');
  RAISE EXCEPTION 'Stale overwrite accepted';
 EXCEPTION WHEN serialization_failure THEN NULL;
 END;
 SELECT to_jsonb(c) INTO payload FROM public.customers c WHERE id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
 result:=public.apply_ledger_mutation('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee','CUSTOMER','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',2,payload||'{"is_deleted":true,"version":3}', 'ffffffff-ffff-4fff-8fff-ffffffffffff','Mistake');
 IF NOT (SELECT is_deleted FROM public.customers WHERE id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa') THEN RAISE EXCEPTION 'Not soft deleted'; END IF;
 IF (SELECT count(*) FROM public.deleted_logs)<>1 THEN RAISE EXCEPTION 'History missing/duplicated'; END IF;
 IF (SELECT snapshot_json::jsonb->>'name' FROM public.deleted_logs LIMIT 1)<>'B' THEN RAISE EXCEPTION 'Wrong prior snapshot'; END IF;
 BEGIN
  PERFORM public.apply_ledger_mutation('dddddddd-dddd-4ddd-8ddd-dddddddddddd','CUSTOMER','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',3,payload||'{"is_deleted":false,"version":4}');
  RAISE EXCEPTION 'Resurrection accepted';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
 BEGIN
  UPDATE public.customers SET name='Bypass';
  RAISE EXCEPTION 'Direct write bypass';
 EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
END $$;
RESET ROLE;
INSERT INTO public.customers(id,owner_id,name) VALUES('99999999-9999-4999-8999-999999999999','11111111-1111-4111-8111-111111111111','Dashboard deletion');
DELETE FROM public.customers WHERE id='99999999-9999-4999-8999-999999999999';
DO $$ BEGIN
 IF NOT (SELECT is_deleted FROM public.customers WHERE id='99999999-9999-4999-8999-999999999999') THEN RAISE EXCEPTION 'Dashboard delete not retained'; END IF;
 IF (SELECT count(*) FROM public.deleted_logs)<>2 THEN RAISE EXCEPTION 'Dashboard delete history missing'; END IF;
END $$;
ROLLBACK;
