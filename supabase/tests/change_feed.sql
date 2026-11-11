-- TEST DATABASE ONLY: transactions create fake identities and roll back. Never run on the owner project.
BEGIN;
INSERT INTO auth.users VALUES ('11111111-1111-4111-8111-111111111111'), ('22222222-2222-4222-8222-222222222222');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
INSERT INTO public.customers(id,owner_id,name) VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','11111111-1111-4111-8111-111111111111','Test');
DO $$ BEGIN
 IF (SELECT count(*) FROM public.sync_changes) <> 1 THEN RAISE EXCEPTION 'Insert not captured'; END IF;
 UPDATE public.customers SET name=name;
 IF (SELECT count(*) FROM public.sync_changes) <> 1 THEN RAISE EXCEPTION 'No-op replay duplicated event'; END IF;
 UPDATE public.customers SET name='Changed',version=2;
 IF (SELECT count(*) FROM public.sync_changes) <> 2 THEN RAISE EXCEPTION 'Update not captured'; END IF;
 BEGIN
  INSERT INTO public.sync_changes(sequence_id,owner_id,table_name,record_id,operation) VALUES (9999,'11111111-1111-4111-8111-111111111111','customers','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','INSERT');
  RAISE EXCEPTION 'Client wrote feed';
 EXCEPTION WHEN insufficient_privilege THEN NULL;
 END;
END $$;
SELECT set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
DO $$ BEGIN
 IF (SELECT count(*) FROM public.sync_changes) <> 0 THEN RAISE EXCEPTION 'Feed leaked owner events'; END IF;
END $$;
ROLLBACK;
