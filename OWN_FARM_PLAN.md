# Final product plan — tractor work and own-farm accounts

Date: 11 November 2026. Status: finalized implementation specification; features below are planned, not implemented. Extends DEVELOPMENT_PLAN.md and follows AGENTS.md.

## 1. Product boundary

One private Android app for Dad's two connected activities:

1. Tractor work for customers: work charges, collections, customer dues/advances, tractor operating costs.
2. Own farm: farming expenses, amounts paid/unpaid, crop-season spending, improvements and optional crop sales/collections.

Keep one owner, one offline Room database, one backup, one synchronization system and one theme preference. Do not add stock management, payroll, accounting journals, tax filing, depreciation, procurement approvals or an ERP. No network is required for any local entry.

The first expanded release includes farm spending and its payment status. Simple crop sales and receipts follow as a separate verified slice before reports may call a result a crop surplus. Farm spending remains useful when Dad does not record crop income.

## 2. What Dad sees

Keep Home, Work, Customers and Settings as the four navigation destinations. Home has a large persistent mode selector: **બધું / All**, **ટ્રેક્ટર કામ / Tractor work**, **મારું ખેતર / My farm**. The selection changes the home summary and relevant expense/history filters; forms show the selected purpose explicitly so hidden mode state never decides financial ownership.

My farm opens a simple dashboard with:

- Add expense; record payment for an existing expense.
- Spending for the selected season/year, paid amount and remaining payable amount.
- Spending by category, field/crop season and general farm overhead.
- Improvement purchases shown separately from routine farming costs.
- Expense history with search and filters.
- Crop sales and receipts once that slice is available.

The first farm entry can be saved without creating a field or season. Use **General farm / સામાન્ય ખેતર ખર્ચ** until Dad chooses more detail. Fields and crop seasons live behind My farm; avoid new top-level tabs.

All important actions and financial status are Gujarati + simple English. Light default, immediate persistent Dark toggle, generally 18sp body, 56dp controls, large amounts, high contrast and readable empty/error states remain mandatory.

## 3. Expense categories

Use stable category codes; translated names are display text, never database identifiers. Preserve existing category codes and stored rows.

| Group | Categories |
| --- | --- |
| Crop inputs | Seeds/plants; fertilizer/manure; pesticides/crop protection |
| Field operations | Farm labour; hired tractor/machinery; irrigation/water; harvesting/threshing |
| Running costs | Electricity/light bill; diesel/fuel; oil/grease; repairs; maintenance; transport |
| Improvements | Irrigation/pipeline improvement; pump/motor; electrical installation; equipment/tools; farm structure/land improvement |
| Other | Other farm expense with an optional description |

Existing tractor categories remain available for tractor purpose. A shared fuel/repair bill can serve both activities. Do not add pesticide dosage, chemical-use recommendations or automatic crop assumptions.

Categories describe what was bought; purpose identifies who used it; cost class identifies **Routine cost** versus **Improvement purchase**. Defaults can follow category, but Dad can correct them with an audit reason. Improvement spending is not silently mixed into routine crop operating cost.

## 4. Fast entry flow

Required: purpose, category, amount and date. Default date to today in Asia/Kolkata. Remember useful defaults without changing old entries.

Optional details: field/crop season, tractor, supplier name, note, payment method and bill reference. Quantity/unit/unit price are optional for inputs and fuel, with deterministic preview; a typed invoice amount is authoritative when supplied. No mandatory supplier setup, photograph or product catalogue.

Payment choices: **Paid now**, **Partly paid**, **Pay later**. Paid now is the default. Partly paid asks for the received bill's paid portion; pay later saves the cost without a payment. Later payments select the existing expense instead of creating a new expense. Show a confirmation such as “₹3,000 expense · ₹1,000 paid · ₹2,000 remaining”.

Customer collections and supplier expense payments are separate flows with different labels. Both save locally in transactions and show pending cloud status independently.

For a bill split between farming and tractor work, reveal a small allocation panel only after **Shared** is selected. Enter rupee shares or percentages with a final paise preview. The sum must equal the bill exactly before save. Never choose an automatic 50/50 split.

## 5. Field and season tracking

A field has a name and optional area using scaled integers and an explicit unit. Do not convert Gujarat bigha into acres without a confirmed local conversion.

A crop season has a user-facing name, crop name, start date, optional end date and optional field. Examples: “Cotton — Kharif 2026” and “Wheat — Rabi 2026–27”. Season names are not globally unique. Support overlapping crops by separate season records; do not impose one crop per field.

Allow a general farm expense without a season. Electricity or repairs serving several crops stay under farm overhead unless Dad explicitly splits them. Reports always show unassigned overhead so a low season total cannot hide it. Closing/archiving a season hides it from new-entry defaults but preserves its records and permits an explicit audited correction.

If a shared bill needs multiple seasons, use allocation lines rather than duplicate expense records. Do not require area-based automatic allocations; Dad chooses the actual rupee allocation.

## 6. Exact financial rules

All amounts are Long paise, all measurable quantities use documented scaled integers, and all parsing rejects invalid precision and overflow. Totals use checked arithmetic. Amounts on ordinary rows are positive; refunds use an explicit event type rather than unexplained negative expenses.

- Expense amount = the recorded cost, regardless of how much has been paid.
- Paid amount = confirmed local payment events minus payment reversals/refunds as applicable.
- Payable = cost minus valid applied settlements; never count the remaining payable as a second expense.
- Paying an existing bill changes cash outflow and payable, not recorded cost.
- Enforce settlements against the same expense/owner; do not permit an ordinary payment to exceed the outstanding amount. Supplier advances are outside this release.
- Shared allocation totals equal the expense amount exactly. Payment attribution uses deterministic cumulative proportional allocation with a stable remainder rule, so paying ₹1 twice has the same final distribution as paying ₹2 once. Store the resulting payment allocation paise; replay must not recalculate differently.
- Refunds/cancellations preserve the original bill and settlements. Use explicit credit/reversal events with a required reason and validation; do not silently turn an existing paid expense into an unpaid/deleted expense.
- Corrections preserve before/after values and reason. Changes to a paid/shared bill must reconcile settlements/allocations transactionally. Disallow lowering a bill below its net paid amount until the linked refund/reversal is recorded.

Own tractor work on own land does not create a customer, receivable, sale or collection. Record actual external costs and optionally a nonfinancial own-use note. A user-entered estimated machinery cost may appear in a clearly separate estimate report later; it never becomes real cash spending or tractor revenue.

## 7. Reports and honest labels

| View | Meaning |
| --- | --- |
| Tractor collections | Actual customer receipts during the selected period |
| Tractor outstanding/advances | Customer ledger balances as of the selected date; unchanged by farm costs |
| Own farm spending | Farm allocation of recorded costs by expense date, separated into routine/improvement |
| Own farm paid | Farm allocation of settled expense payments by payment date |
| Unpaid bills | Bill balances as of the selected date, with supplier/reference where supplied |
| Season cost | Explicit allocations to that crop season, plus separately visible unassigned farm overhead |
| Combined cash movement | Actual tractor/farm receipts minus actual payments, each event counted once |

Reports must identify period and date basis. Do not subtract customer receivables from cash or label collections minus expenses as profit. The existing dashboard's expense-based cash-flow calculation must change when unpaid bills are supported: cash outflow comes from expense payment events, not expense amounts.

Optional crop-income slice: record crop sale amount, date, season, optional buyer/quantity, then separate receipts for paid/part-paid/unpaid sale. Multiple collections never duplicate the sale. Home consumption and unsold harvest are not cash income. Customer and crop-sale ledgers remain distinct even when the buyer is also a tractor customer; no implicit balance netting.

Once crop sales exist, show **Recorded crop surplus = recorded sales minus allocated routine costs**, with improvement spending and unallocated overhead separately disclosed. Show cash movement separately. It is a summary of recorded entries, not a formal profit statement. Hide the surplus figure when income tracking is unused; do not infer yield, stock value or missing sales.

## 8. Data model and migration

Retain existing customers, tractors, work_entries and customer payments. Evolve the existing expense ledger rather than creating disconnected tractor and farm expense tables.

| Entity | Purpose / key additions |
| --- | --- |
| expenses | Original bill: category, positive total paise, bill date, optional supplier/reference/note, routine/improvement class |
| expense_allocations | UUID; expense ID; purpose TRACTOR_BUSINESS or OWN_FARM; allocation paise; optional tractor, field and season links |
| expense_payments | UUID; expense ID; amount, payment date/method; explicit settlement/reversal semantics |
| expense_payment_allocations | Exact paise attributed to expense allocation lines; part of the same settlement transaction |
| farm_fields | UUID, name, optional scaled area/unit; archive/tombstone metadata |
| crop_seasons | UUID, name, crop, dates, optional field link; archive/tombstone metadata |
| expense_adjustments | Explicit credits/refunds/reversals and references necessary to retain the original financial event; freeze exact event contract before implementation |
| farm_sales / farm_sale_receipts | Added only in the later income slice; sale cost/receipt separation and season links |

Use one allocation even for an ordinary unsplit bill. “Shared” is a UI choice derived from multiple purpose allocations, not a third bucket counted alongside farm and tractor. Validate allowed references: season/field belongs to farm purpose; tractor identity is optional where relevant. Never infer that a nullable tractor means farm purpose.

Every synchronized row gets locally generated UUID, owner binding, creation/update times, revision and tombstone metadata as appropriate. All links must stay within the owner; allocation/settlement mutations and audit events must be atomic locally and on the server. Room queries filter tombstones without losing historical balances.

Migration of current data:

1. Preserve every expense ID, amount, date, category, tractor link and audit record.
2. Mark legacy purpose as tractor business, consistent with the original app. Surface “Review imported purpose” for legacy costs with no tractor link; Dad may reclassify them without changing totals.
3. Create one allocation for each existing expense and one legacy paid settlement equal to its amount, since the old model treated expenses as paid outflows. Record this assumption as migration provenance; do not invent a historical payment method (use unspecified).
4. Preserve original audit events. Add migration provenance rather than pretending the generated settlements were manually entered.
5. Validate old/new aggregate amounts, references and cash-flow equivalence. No destructive database reset.
6. Determine the next actual Room schema version from the checkout at implementation time; coordinate with the tractor UUID migration rather than shipping competing v3 migrations.

## 9. Sync, backup and security changes

Extend the planned immutable outbox protocol to all new entities/events. Upload field before season; bill before allocations/settlements; referenced tractor before tractor allocations. Use an atomic expense mutation group so a bill cannot be cloud-confirmed while its allocations or initial settlement are missing. Download/hydrate in corresponding dependency order.

Conflict handling must treat the complete bill/allocations/settlements as one consistency boundary. Preserve both versions on conflicting changes; never add amounts together or choose a winner using device time. Server constraints must reject cross-owner links, over-settlement and invalid allocation sums; multi-row sums need a reviewed transactional server function/constraint strategy, not only Android checks.

Full backup includes new tables, adjustments, tombstones and audit history. Bump the backup format, validate all sums/links before restore and support old archives through the same documented legacy mapping. CSV exports distinguish cost, payment, allocation, purpose and date basis. A CSV is not the recovery format.

No new account or backend is needed. Preserve secure owner sessions, RLS, publishable-only APK credentials and offline access. Attachments/receipt photos are deferred; they would require separate private-storage, retention and restore design.

## 10. Implementation sequence and gates

1. **Local integrity first:** finish numeric boundaries, duplicate-save prevention, relationship validation and auditable corrections from the original roadmap.
2. **Expense contract/migration:** freeze payment/adjustment/allocation invariants; implement additive Room migration and repository transactions. Gate: existing tractor balances and cash summaries preserved, migration and rollback tests pass.
3. **Farm spending MVP:** categories, explicit purpose, general farm expense, paid/part-paid/unpaid flow, histories and later bill payment. Gate: fully usable offline across restart; bill cost never duplicated by payment.
4. **Fields/seasons and shared costs:** optional setup, allocation editor and filters. Gate: every paise accounted for once across combined and individual views, including partial settlements/refunds.
5. **Reports and usability:** farm dashboard, date basis, category/season/general-overhead reports, review imported purposes and accessible bilingual controls. Gate: screenshot/runtime checks on both planned AVDs and exact report reconciliation.
6. **Independent recovery:** implement expanded encrypted export/restore and CSV. Gate: clean-install restore reproduces tractor and farm records, totals, tombstones and audits.
7. **Optional crop-income slice:** simple sales/receipts and accurate outstanding/cash/surplus displays. Implement before first release only if income tracking is wanted; it does not block a useful spending release.
8. **Supabase and hydration:** extend cloud schema and outbox dependencies to the finalized expense model before production integration. Gate: real isolated-backend replay, interruption, conflict, owner isolation and clean-device restore tests.
9. **Release:** complete original emulator QA, R8/signing and private APK handoff with one combined recovery guide.

Each implementation slice runs compilation, unit/database tests, lint and relevant manual emulator checks per AGENTS.md. No passing compilation substitutes for accounting or restore evidence.

## 11. Required acceptance examples

- Pesticides ₹3,000, paid ₹1,000 today and ₹2,000 next week: cost stays ₹3,000; dated cash payments total ₹3,000; payable becomes zero.
- General farm electricity bill ₹2,400 paid in full: appears in farm spending and overhead, never in customer dues or automatically under one crop.
- Diesel ₹2,000 split ₹1,200 tractor / ₹800 farm: combined cost ₹2,000; individual totals ₹1,200/₹800. A ₹500 settlement attributes ₹300/₹200.
- Farm pipeline upgrade ₹25,000: improvement spending, separate from routine season cost; full payment still appears in cash outflow.
- One farm bill allocated to two seasons: combined total equals the bill, not the sum of duplicated bills.
- Own tractor used on own field: no new customer charge or fictional receipt.
- Bill corrected, cancelled or refunded after partial payment: original and reversal history retained; no lost cash movement or negative unexplained balance.
- Legacy expense migration: IDs, original totals, audit history and previous paid-outflow totals preserved.
- Offline save, duplicate tap, kill/relaunch, interrupted sync and duplicate replay: exactly one financial event with intact allocations and settlements.
- Clean installation from cloud or independent backup: complete tractor and farm accounts restored with matching relationships and totals.

## Final scope decision

Finalize **own-farm spending, paid/unpaid tracking, optional field/season tagging, explicit shared costs, improvement separation and combined recovery** as required scope. Crop sales/collections are a separately deliverable extension. Inventory, farm loans, supplier advances, payroll, GST/tax, depreciation, yield prediction and agronomic advice stay outside this application scope.
