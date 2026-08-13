# BeeCount full feature parity matrix

Reference baseline: `TNT-Likely/BeeCount` public repository, re-audited 2026-08-13 against the checked-out reference source; keep this matrix synchronized with upstream public user-facing features.

LifeTrace Finance uses an independent Kotlin/Jetpack Compose implementation and the existing
LifeTrace Cloud protocol. The reference repository is used to identify user-visible behavior,
not as a source folder copied into this project.

## Already available

- Local-first transaction storage, expense/income/transfer editing and search.
- Multiple ledgers and accounts, hierarchical category data, tags and attachments.
- Monthly/category analytics, budgets and recurring transactions.
- WeChat/Alipay/bank CSV and XLSX import.
- Notification capture, screenshot monitoring and Vision extraction.
- LifeTrace authentication, offline outbox, snapshot/push/pull sync and conflict handling.
- Quick Settings entry point and Android home-screen widget.

## Phase 1 — navigation and reliability

- [x] Server settings opens from the Mine page.
- [x] Login is available after reinstall and triggers cloud sync.
- [x] Sync explains the unauthenticated state instead of silently doing nothing.
- [x] Category management and About entries no longer use empty callbacks.
- [~] Primary tabs, quick entry and all Mine-page destinations have reachability coverage; dialog-level control coverage is still incomplete.
- [x] Sync page shows active progress, last push/pull success times, pending/conflict counts, actionable failure details, retry and full snapshot recovery.

## Phase 2 — complete local bookkeeping surfaces

- [x] Account details, balance correction, six-month account trend and current-ledger net-worth trend with internal transfers cancelled.
- [x] Parent/child category create/edit/archive, same-level ordering, icon picker and same-type historical transaction migration.
- [x] Calendar/month/day view and advanced date/type/category/account filtering.
- [x] Budget overview/create/edit/delete, usage progress and threshold/overrun notifications.
- [x] Recurring transaction list/create/edit/delete, enable/disable and daily/weekly/monthly/yearly generation.
- [x] Tag create/edit/color/archive, transaction assignment and tag filtering.
- [x] Local transaction attachment add/preview/delete with cloud metadata sync.
- [x] Monthly/year/all-time income/expense category ranking and annual monthly trend.
- [x] Product scope intentionally uses CNY only; all creation/editing paths reject non-CNY values per user requirement.
- [x] Current-ledger CSV export, full local database/attachment backup/validated restore and selective YAML configuration portability without secrets.
- [x] Storage inspection, safe cache cleanup and orphan attachment scan/confirm/delete with a second reference check before deletion.

## Phase 3 — smart capture

- [x] Camera/gallery recognition calls the configured OpenAI-compatible endpoint directly from Android; original images do not pass through LifeTrace Cloud.
- [x] System voice input bookkeeping with review-before-save.
- [x] Conversational AI bookkeeping plus editable prompt/provider/text-model/vision-model settings.
- [x] Android automatic screenshot capture permission, lifecycle and status workflow.
- [x] BeeCount-style candidate review, local classification suggestions, import/manual duplicate merge and explicit candidate-to-candidate merge.

## Phase 4 — personalization, privacy and platform

- [x] Light/dark/system modes, four theme colors and all 19 reference header skins (plus the no-skin option).
- [ ] Simplified Chinese, Traditional Chinese, English and Korean resources.
- [~] Global font scaling is complete; localized date/number formats remain tied to the multi-language work.
- [x] Screenshot/recording/recent-task privacy protection plus salted PBKDF2 PIN, biometric/device-credential unlock and background relock timeout.
- [x] Configurable daily bookkeeping reminder, shortcut guide, searchable/filterable/exportable log center and detailed safe storage management.
- [x] Six widget content families, 12 reference size entries, responsive updates and an in-app widget catalog with supported-launcher pin requests.
- [x] Offline in-app help, privacy/data-boundary and detailed about/license surfaces.

## Phase 5 — LifeTrace Cloud extensions

- [ ] Near-real-time multi-device updates.
- [ ] Shared ledgers, invite/join, owner/editor roles and member statistics.
- [ ] Device/session management.
- [ ] Encrypted export/backup destinations supported by LifeTrace Cloud.
- [ ] Web/API parity for new entities and operations.

## Acceptance

Each checkbox requires a working UI entry, persisted behavior, unit/integration coverage where
appropriate, successful Android build/lint, and a real-device smoke test. A decorative screen or
an empty click handler does not count as implemented.
