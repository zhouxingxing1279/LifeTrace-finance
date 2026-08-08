# EPIC-07 Test Matrix

| 能力 | 测试/门禁 | 状态 |
|---|---|---|
| 金额整数分 | `core:CoreTests.moneyUsesIntegerCents` | LOCAL PASS |
| Synthetic 通知正负样本 | `NotificationFixtureTests` + local smoke | LOCAL PASS |
| Retry-After / 413 | `CoreTests.retryPolicyHonorsRetryAfterAnd413` | LOCAL PASS |
| 本地写入 + Outbox | `FinanceRepositoryTest.localWriteAndOutboxAreCommittedTogether` | CI REQUIRED |
| 通知去重 | `FinanceRepositoryTest.duplicateNotificationIsStoredOnceWithoutRawText` | CI REQUIRED |
| LocalProfile/CloudUser 绑定 | `FinanceRepositoryTest.cloudBindingKeepsStableLocalProfileId` | CI REQUIRED |
| Compose 启动 | `LaunchTest` API 34 emulator | CI REQUIRED |
| Android Lint | `:app:lintDebug` | CI REQUIRED |
| Debug APK | `:app:assembleDebug` | CI REQUIRED |
| Release R8 build | `:app:assembleRelease` | CI REQUIRED |
| 正式账单 importer/对账 | 真实脱敏账单 | DEFERRED_TO_EPIC06 |

本文件在 GitHub Actions 通过后更新 CI evidence。
