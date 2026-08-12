# LifeTrace Finance Android — BeeCount-inspired bookkeeping domain

Status: **implemented and verified**

Branch: `feature/beecount-domain-model`
Cloud counterpart: `zhouxingxing1279/LifeTrace` branch `feature/beecount-domain-model`
Reference: `TNT-Likely/BeeCount`

## Backend rule

The Android app synchronizes to the **existing LifeTrace Cloud only**. No new backend, database service, authentication flow, sync API or BeeCount Cloud dependency was introduced.

The path remains:

`Room -> sync_outbox -> existing LifeTrace /api/v1/sync/push -> LifeTrace Cloud -> pull/snapshot -> Room`

## Implemented bookkeeping model

New Room entities:

- `LedgerEntity` / `finance_ledgers`
- `RecurringTransactionEntity` / `finance_recurring_transactions`
- `TagEntity` / `finance_tags`
- `TransactionTagEntity` / `finance_transaction_tags`
- `BudgetEntity` / `finance_budgets`
- `TransactionAttachmentEntity` / `finance_transaction_attachments`

Existing entities were expanded with BeeCount-inspired fields.

Accounts now support ledger scope, ordering, credit limit, billing/payment-due days, bank name, note and hidden state. Categories support ledger scope, hierarchy level, ordering and icon metadata. Transactions support ledger scope, recurring-template linkage, independent `excludeFromStats` / `excludeFromBudget` flags and native-currency snapshots.

The existing `finance_transaction_evidence` model remains separate from attachments: evidence records capture/import provenance; attachments represent files linked to a transaction.

## Room v1 -> v2 migration

A non-destructive `MIGRATION_1_2` was implemented.

- Existing accounts, categories, transactions and evidence are preserved.
- Each local profile gets a deterministic default ledger ID: `default-ledger-<profileId>`.
- Existing accounts/categories/transactions are backfilled to that ledger.
- Existing stable string IDs are preserved.
- New indexes cover ledger scope, recurrence, category/budget lookups and transaction-tag uniqueness.
- A migration-created default ledger is automatically placed into the existing LifeTrace `sync_outbox` exactly once when the repository first uses it.

## Existing LifeTrace sync integration

`LifeTraceContract.FINANCE_ENTITY_TYPES`, `SyncEngine`, server-version handling, remote deletion and `RemoteMapper` now cover all ten finance entity types:

- `finance.ledger`
- `finance.account`
- `finance.category`
- `finance.transaction`
- `finance.recurring_transaction`
- `finance.tag`
- `finance.transaction_tag`
- `finance.budget`
- `finance.transaction_attachment`
- `finance.transaction_evidence`

No second sync state machine was added. New/updated entities continue to use `sync_outbox`, existing conflict handling, pull/snapshot and tombstones.

Old LifeTrace Cloud v1 account/category/transaction payloads without `ledgerId` remain compatible: the Android mapper preserves an existing local ledger or falls back to the deterministic default ledger.

## Implemented repository behavior

- create/select default ledger;
- create accounts and hierarchical categories in a ledger;
- expense/income/transfer/refund/fee transaction model;
- recurring transaction templates;
- normalized tags and transaction-tag relations;
- total/category budgets;
- hidden accounts;
- independent statistics and budget exclusions;
- existing notification candidate/evidence flow retained;
- new entities generate ordinary LifeTrace outbox changes and relationship dependencies.

## Money semantics

LifeTrace keeps integer money rather than copying BeeCount `double` amounts:

- `amount_cents`: transaction/account currency;
- `currency`: transaction currency;
- `native_amount_cents`: frozen amount converted to ledger base currency;
- `native_currency`: ledger base currency snapshot;
- `exchange_rate`: decimal string snapshot.

## Verification result

Final code head before this documentation-only update: `041b9a81f58295c0d1180c2c6fae1becabf6501e`.

**EPIC07 Android CI #63 — success.** The run passed:

- Core/JVM tests;
- Android lint;
- Debug build;
- Release/R8 build;
- emulator/instrumentation tests;
- Room v1->v2 migration test;
- migrated-default-ledger outbox bootstrap test.

The migration test creates a v1-style database, inserts existing profile/account/category/transaction data, executes `MIGRATION_1_2`, and verifies row preservation, deterministic ledger backfill and new-table defaults. The bootstrap test verifies the migrated ledger is enqueued for existing LifeTrace Cloud synchronization exactly once.

## Scope intentionally deferred

Shared/family ledger collaboration, BeeCount Cloud, BeeCount authentication, BeeCount AI chat persistence and a cloud exchange-rate service are not part of this implementation.

## Merge gate result

The paired Android and LifeTrace Cloud code heads have passed their relevant CI suites. Merge order should be **LifeTrace Cloud first, Android second**, so the server recognizes the new entity types before an Android client can emit them.