# Room to Supabase schema contract audit

Audit date: 11 November 2026. Compared Room v3 export (`schemas/.../3.json`), `NetworkDtos.kt`, `DtoMapper.kt`, sync processor and `supabase/migrations/20261110000001_initial_schema.sql`. SQL proposed separately as `supabase/migrations/20261111000001_security_and_contract_hardening.sql`; it is a review artifact and was not executed.

## Current mapping

- Customers and tractors map column-for-column; UUID-shaped IDs are strings in Room/DTO and PostgreSQL UUIDs remotely.
- Work entries map quantities/paise as BIGINT-compatible Long and dates from epoch milliseconds to ISO timestamps. Unit/type remain unconstrained text.
- Customer payments map as expected, but FK ownership is not composited and SQL permits a work/customer mismatch. The DTO has no payment receipt or mutation ID.
- Expenses map the current legacy schema exactly: optional tractor, category, amount, expense date, notes, created/updated/version/deleted. It cannot represent farm-vs-tractor purpose, unpaid balance, crop season, field, allocation or expense settlements.
- Audit DTO/entity preserve only entity type/ID, action, timestamp and free-text details. They have no before/after snapshot or immutable mutation identity. Existing mapper/upsert path attempts to update audit rows.
- Outbox is local only. It has sequence/entity/op/status/retry/error, no immutable payload, request UUID, expected server revision, receipt, owner binding or lease token. Current worker uploads the *current* entity state using plain upsert and deletes outbox after a successful response. This cannot safely handle lost responses, intervening edits, stale writes or a server-side version conflict.

## Proposed migration behavior

Migration adds composite unique keys and owner-aware foreign keys without rewriting existing records; replaces owner policies with policies for the `authenticated` role; revokes hard DELETE from client roles; makes audit rows append-only; validates work/payment/expense values and cross-owner links. Existing owner FK cascades on auth user deletion are retained as an administrative account-deletion boundary. Client roles get only the table operations needed for current SDK reads/upserts; SQL alone cannot deliver production-grade conflict/idempotency guarantees.

Before any table is exposed to an authenticated client, verify the actual Supabase default grants and explicitly apply the migration through the reviewed deployment process. RLS must not be treated as a substitute for table grants. Never use an APK secret key.

## Required app changes before enabling sync

1. Make `id` strings valid UUIDs for every table including audit. Audit currently permits arbitrary entity IDs; avoid constraining polymorphic `entity_id` with a FK, but enforce UUID syntax.
2. Preserve business calendar dates consistently. Current timestamp mapper serializes epoch millis to UTC ISO; the UI uses local dates. Define date-only semantics for expense/work/payment date to avoid dates shifting around Asia/Kolkata midnight. `created_at` and `updated_at` remain instants.
3. Enforce same-owner customer/work/tractor references locally and remotely. A plain FK by ID does not enforce owner equality; SQL contract uses `(owner_id, id)` composite references. Payment `work_entry_id` must reference a work entry for that same customer and owner.
4. Keep `AuditLogDto` immutable and include correction before/after payload (or a canonical encrypted/sanitized JSON snapshot), mutation UUID and event ordering. Replace audit upsert with insert-once receipt semantics.
5. Add immutable outbox mutation envelope and idempotency receipt, base server revision, submitted local revision and acknowledgment state. A `version` column that the client freely supplies is not optimistic concurrency control.
6. Route mutations through a reviewed transactional RPC or equivalent guarded server operation that checks owner, idempotency key, expected revision, business constraints, audit append and receipt atomically. Direct PostgREST upsert bypasses these checks. Return server revision and durable receipt before acknowledging Room.
7. Change hydration to page by a server commit-order checkpoint/snapshot boundary; client timestamps and UUID ordering are not reliable change cursors. Preserve pending local state on download.
8. Add farm-specific DTOs/tables once the local model is stable: expense allocations/payments, fields and crop seasons. Existing `ExpenseDto` cannot encode these semantics; do not cloud-sync farm expenses through its legacy shape.
9. Model audit/sync support tables and RLS explicitly. Do not expose local outbox or auth session secrets as public tables.
10. SQL migration should be exercised against an isolated Supabase instance with pgTAP/RLS tests, API owner-isolation tests and migration replay review before production use.

## Security notes

- `audit_logs.entity_id` is polymorphic; owner-scoped RLS applies, while application/server validation checks that a referenced object belongs to that owner. Prefer explicit entity-specific mutation functions over dynamic SQL.
- RLS policies use `TO authenticated`, owner checks in `USING` and `WITH CHECK`; anonymous gets no table privilege. No client hard-delete permission or policy is granted.
- `auth.users` cascade deletion is not a user-facing delete flow; protect account administration and verify backup/retention policy before provisioned-owner deletion.
- Single-owner provisioning/sign-up shutdown belongs in Auth configuration/administration. The current initial migration's trigger on `auth.users` is fragile and grants no client security; do not rely on it as the authorization boundary.
