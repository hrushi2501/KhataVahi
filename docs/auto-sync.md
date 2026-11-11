# KhataVahi automatic backup

Implemented 11 November 2026. Room saves are independent of connectivity. Sign-in is required for cloud backup, never for local accounting.

## Scheduling

- App foreground and successful sign-in request persistent WorkManager sync.
- New outbox sequence identities automatically schedule work after a 1.5-second coalescing window. Status/count invalidations do not repeatedly schedule uploads.
- WorkManager waits for connected network and resumes when connectivity returns, including when the app process has exited. No periodic HTTP polling loop or always-connected listener is used.
- A six-hour periodic job is a safety net. Android controls exact background execution timing; force-stopped apps resume after reopening.
- Unique appended work preserves a save arriving while the previous worker exits. One manager mutex serializes periodic/foreground runs; cancellation propagates.
- WorkManager retries transient failures with exponential backoff starting at 30 seconds. Missing configuration or sign-in does not repeatedly send data.

## Uploads

A run snapshots at most 200 pending mutations and coalesces them by entity. Multiple pending edits upload the current immutable in-memory row snapshot once. New records and never-uploaded tombstones send customers/tractors before dependent work/payments/expenses. Confirmed soft deletes send payments/work before customers/tractors, including coalesced pending corrections; audit UUID replay uses insert-ignore semantics. Only captured outbox sequence IDs are acknowledged after server success; edits arriving during a request remain pending.

Abandoned in-progress claims recover on the next serialized run. Network failures remain pending. Authentication failure waits for sign-in; permanent validation failures remain visible and block dependent progress. A newer local correction for the same entity automatically requeues its failed mutations; unrelated failures are not silently discarded. A conclusively rejected FK request can be rebuilt behind a still-pending parent; uncertain requests retain their frozen identity. Observers start in Application.onCreate, including process entry points that do not create an Activity.

Default tractor parents now seed transactionally with outbox rows. A one-time DataStore enrollment covers legacy seed rows so their work cannot fail merely because the tractor was never uploaded.

Uploads now freeze an immutable request envelope in Room before sending. A lost response replays the same request UUID/payload; the server returns its existing receipt. Conditional base-version checks reject stale writes, and the same confirmation transaction acknowledges only captured outbox IDs. The local ledger is bound to one owner. Physical delivery may retry; server mutations are receipt-idempotent. Conflicts still require review; no automatic financial merge is performed.

## Downloads

- Removed full six-table fetch after every upload.
- Each owner has a durable DataStore cursor into the RLS-protected sync_changes table.
- Pages contain at most 250 change references. Changed record IDs are deduplicated and fetched in batches of at most 100; unchanged tables are not queried.
- Current snapshots can reference parents outside the page. Hydration resolves those dependencies under the same owner and validates them before transactional application.
- Room applies pages before cursor persistence. A crash before cursor persistence safely replays the page.
- Pending or divergent local records stop the cursor before the conflict; no cursor skips unmerged changes.
- Normal background empty checks are throttled for 30 minutes across app restarts, per owner. Foreground checks can discover remote changes with a 10-second cooldown; sign-in requests a refresh. Healthy unfinished pages continue through immediately queued bounded jobs; network failures use backoff. A conflict is not reported as complete and does not cause a tight download loop.
- Server writes are serialized around change sequence allocation with a transaction advisory lock, preventing a later committed sequence from overtaking an earlier uncommitted sequence. Index (owner_id, sequence_id) serves incremental reads. No-op updates emit no change event.

Migration 20261111000002_incremental_sync_changes.sql was reviewed, exercised in temporary PostgreSQL, then committed live. Tests confirmed event capture, no-op suppression, owner isolation and rejection of client writes to the feed. Live read-only checks confirm RLS enabled for all seven exposed tables. No financial rows were created for live testing.

## Minimal feedback

Settings has one localized backup status; no Sync Now button or technical stages/counters. It distinguishes saved on phone, online copy pending, signed out and confirmed online. Server confirmation is required for the online state. Ordinary accounting screens remain local and responsive.

Visible app/share name is KhataVahi. Forms provide field validation, retain input on failure, prevent repeated submissions and acknowledge successful local writes explicitly. Home and ledger loading/error states provide retry instead of showing failed queries as empty/zero data.

## Verification limits

Automated scheduling, coalescing, captured acknowledgement, retry/cancellation, cursor boundaries, input validation and database tests are recorded in the test suite. Emulator results and final counts are reported in the task response. The owner account has been provisioned and API login verified. Full authenticated emulator upload/restoration and long-offline refresh still need qualification. Android and OEM background restrictions mean automatic sync is eventual, not an exact-time guarantee.
