# Local backup and restore specification

Scope extension (11 November 2026): follow [OWN_FARM_PLAN.md](../OWN_FARM_PLAN.md) for fields, crop seasons, expense allocations, expense payments/payment allocations and adjustment events. Include these in the next backup format with complete ownership, sum and relationship validation. Preserve older backup support through the documented legacy expense-to-paid-bill mapping. If farm sales/receipts are implemented, include those too. This extension is planned, not implemented.

Status: design only; no backup or restore code exists yet. Keep this as a separate implementation slice after local transaction and schema migrations are stable. The application must remain usable offline if export fails.

## Data inventory

The Room database currently stores customers, tractors, work entries, payments, expenses, audit logs and the sync outbox. A useful recovery backup needs the complete ledger, not just active rows or a CSV. Include soft-deleted rows (tombstones), immutable audit history, relationship IDs and exact integer values. Preserve money as paise and quantities in their scaled integer representation; never round through floating point.

Outbox entries currently contain only entity type/ID, operation, sequence, status and retry count. They do not hold a mutation payload or base revision. Therefore a backup cannot promise to replay historical pending edits as exact operations. Back up their identity/status only if the next schema has sufficient semantics; otherwise retain ledger state and mark the restored ledger as requiring reconciliation. Do not synthesize audit history or mark pending data as cloud-confirmed.

## File format

Use one versioned archive with:

- `manifest.json`: format version, app/database schema version, creation time, owner binding if present, row counts and per-file checksums.
- Separate deterministic UTF-8 JSONL files for each entity type: customers, tractors, work, payments, expenses, audits and supported sync metadata. One record per line keeps streaming and diagnosis practical.
- Explicit schema/version fields and stable enum names. Nullable fields remain explicit. Do not serialize SDK sessions, refresh tokens, publishable keys, keystores or signing credentials.
- Authenticated encryption for the archive, using a user-entered recovery passphrase and a documented password-based key derivation/encryption format supported by Android. Store a random salt and nonce in a small unencrypted header; authenticate the manifest and payload. Set sensible iteration parameters and archive/file size bounds. A lost password is unrecoverable; communicate this before export.
- Checksums for accidental corruption plus authenticated encryption for tamper detection. A checksum alone is not a security boundary.

Use Android Storage Access Framework `ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT`; request access to the chosen URI only. Stream to the document provider rather than writing a temporary plaintext database to shared storage. Android Keystore may protect a local cache key, but cannot be the only recovery key because a clean installation/new device cannot use the old device's Keystore.

## Export procedure

1. Require explicit owner action and passphrase confirmation. Explain where the file will be saved and that anyone with both file and password can read it.
2. Acquire one consistent snapshot. Prefer a read-only transaction/snapshot query that includes all tables; ensure audit and rows cannot be observed at different logical times. Do not copy a live SQLite main file while WAL changes are in progress.
3. Write an encrypted temporary document payload when supported, finalize authentication/checksums, close, and only then report success. A provider may not support atomic rename; on failure clearly identify the incomplete document and never claim backup success.
4. Verify the completed output by reopening it through the same URI and validating header, authentication, checksums, counts and relationships without changing the database.
5. Display export time and verified row counts. Local backup is distinct from confirmed cloud sync.

## Restore procedure

1. Use the system document picker. Enforce compressed and expanded size limits before allocation; reject path traversal, duplicate archive members, unknown critical fields, unsupported newer versions and malformed records.
2. Authenticate/decrypt and validate the complete archive before opening any write transaction. Validate checksums, record counts, unique IDs, enum values, nonnegative money/quantity rules, customer/work/tractor relationships, payment customer-work agreement, and ownership consistency.
3. Restore only into an empty ledger in the first release. If records already exist, do not clear or merge automatically; explain how to export first and provide the safe recovery path. A future merge feature must use explicit conflict rules and be separately tested.
4. Insert in dependency order in one Room transaction: tractors/customers, work, payments/expenses, audit, then supported sync metadata. Validate FK checks before commit. Never use `REPLACE` for financial parents; never drop/recreate the database.
5. Do not publish a partial ledger. If any insert or verification fails, rollback the entire transaction; keep the app's existing data untouched.
6. Re-read the restored records and compare counts/totals/relationships against the manifest. Surface successful local restore separately from later cloud reconciliation.
7. Until outbox payloads and server revisions are part of the schema, conservatively preserve pending status for reconciliation and never blindly re-upload a restored ledger. Bind it to the same verified owner or leave it local/unbound; reject a different owner.

## Minimum acceptance tests

- Round-trip every entity, tombstone, audit row, nullable field, Gujarati string, paise amount and scaled quantity; compare stable identifiers and dashboard/ledger totals.
- Restore into a clean database and confirm all FK relationships and SQLite `foreign_key_check` pass.
- Wrong password, truncated file, altered authenticated content, invalid checksum, unsupported schema, duplicate IDs, invalid enum, orphan reference, owner mismatch, oversized archive and malicious archive path all fail before touching current records.
- Inject a failure after each restore insertion stage; verify transaction rollback leaves row counts and totals unchanged.
- Cancel document picker, revoke URI access, simulate provider write failure/low storage, process death and interrupted export; never report incomplete output as successful.
- Restore the exact file on a second clean emulator installation using only the owner passphrase. Keystore state from the exporting emulator must not be required.
- Verify CSV separately: UTF-8 Gujarati, commas/newlines/quotes, fixed integer amount columns, and spreadsheet formula-injection escaping. CSV is readable export and is not a substitute for full encrypted restore.

## Build sequence

1. Freeze the Room schema/version and add complete suspend export queries.
2. Implement an archive codec with unit tests independent of Android document UI.
3. Implement snapshot export through SAF and round-trip validation.
4. Implement preflight validation and empty-ledger transactional restore.
5. Add Settings actions and concise bilingual warnings/status, then emulator QA on both profiles.

Do not add a backup dependency until encryption/format requirements, minimum API 26 support, maintenance status and exact compatibility are reviewed. Prefer platform cryptography plus a small well-maintained primitive if the platform does not expose an appropriate KDF; do not invent a cipher or KDF.
