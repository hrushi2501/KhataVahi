# Live emulator sync verification — 11 November 2026

## Result

The opt-in two-stage Android instrumentation workflow passed against the configured live Supabase project on ARM64 emulator-5554. The complete final run was at approximately 20:30–20:32 IST. Neither a physical phone nor a manual sync button was used. Existing application storage was preserved.

## Verified path

1. Restored the signed-in owner session and let initial automatic synchronization complete.
2. Disabled emulator Wi-Fi and mobile data.
3. Saved a marked QA customer, a work entry for 100 paise, and a linked partial payment for 40 paise through production repositories.
4. Verified local Room records and durable pending outbox identities while offline.
5. Force-stopped the application, restored connectivity, and reopened it.
6. Waited for automatic WorkManager upload without invoking performSync or a manual sync action.
7. Queried Supabase through the production Kotlin client: exactly one row per fixture UUID, correct owner, preserved relationships, and exact integer amounts. Outbox acknowledgement followed server confirmation.
8. Replayed the identical confirmed mutation RPC twice: the existing receipt was returned and no additional change-feed events appeared.
9. Refreshed the authentication session successfully and verified the owner identity persisted.
10. Hydrated a separate, empty Room database with an independent cursor using the production hydration manager. Customer, work, and payment relationships/amounts matched. No local mutations were created by restoration.
11. Repeated hydration: zero unchanged records downloaded.
12. Soft-deleted the QA payment, work, and customer with reasons through production repositories. Automatic uploads completed.
13. Pulled the changes into the separate restored database: active queries no longer returned those records, while the customer tombstone remained stored.

Final runner results: `PASS: offline-save` and `PASS: automatic-upload-restore-delete`.

A separate read-only live PostgreSQL probe switched to the authenticated role with an unrelated JWT subject. It saw zero rows across all nine exposed RLS tables, against a nonempty customer baseline. This probe created no users or rows. Anonymous table access is also denied.

## Defects fixed during verification

- A previously rejected child FK request blocked pending parent tombstones. Conclusively rejected FK envelopes now recover behind queued parents. Uncertain/lost-response requests retain their original frozen envelope.
- Confirmed deletions need children before parents; never-uploaded records/tombstones need parents before children. The bounded outbox query now distinguishes these cases, including coalesced corrections.
- Lazy sync initialization depended on activity/worker access. Application startup now starts the outbox/auth observers for every process entry point.

The isolated restore test injects its own cursor store; it does not reuse or reset the real ledger's cursor.

## Repeat

Sign in to the owner account in the emulator first. These tests intentionally add clearly marked fixtures and retain their soft-delete/audit history. Run only against an owner-approved project; prefer a dedicated QA project for repeated runs.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 python3 scripts/qa_live_sync.py emulator-5554
python3 scripts/verify_live_rls.py
```

The runner refuses physical-device serials, checks JUnit results rather than relying on shell exit status, preserves storage, restores emulator connectivity in finally, and stores raw logs in a private temporary directory. Do not publish raw SDK HTTP exception logs because they can contain headers. To resume an interrupted second stage, use `--stage online` before replacing the fixture file with another offline run.

## Other verification

- 102 unit tests: zero failures, errors, or skips.
- Debug and instrumentation compilation: passed.
- Debug lint: passed with existing warnings.
- R8 release APK compilation and release lint: passed with existing warnings.
- Regression tests cover deletion ordering (including coalesced corrections), FK recovery, and frozen request replay after a lost response.

## Scope limits

This verifies the normal offline/reopen/online tractor-accounting path and production hydration into a fresh Room projection. It does not certify a complete uninstall/reinstall and fresh UI login, days-long token expiry, simultaneous edits from two installations, vendor-specific battery restrictions, low-memory hardware behavior, or every expense-screen interaction. Live network loss exactly after server commit remains covered by a deterministic unit test plus live duplicate RPC replay, not by a fault-injected live transport test. RLS isolation used a read-only unrelated JWT subject, rather than provisioning a second real account.
