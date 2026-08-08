# EPIC-07 Android 架构

```text
Compose / Tile / Widget / Share / NotificationListener
                    ↓
             FinanceRepository
                    ↓
          Room + Sync Outbox (atomic)
                    ↓
        WorkManager → SyncEngine
                    ↓
 AuthManager → LifeTrace Cloud Auth/Sync v1
```

## 模块

- `core`：无 Android 依赖的金额、Finance wire 常量、通知解析、重试策略，可直接 JVM 测试。
- `app/data`：Room entity/DAO、诊断事件。
- `app/domain`：所有本地业务写入的唯一入口；UI/Service 不直接写 DAO。
- `app/auth`：短期 Access Token 仅内存；Refresh Token 由 Android Keystore AES-GCM 保护。
- `app/sync`：Push/Pull/Snapshot、Cursor、Conflict、Tombstone、413/429/401。
- `app/platform`：NotificationListener、Quick Tile、Widget、Share Receiver。
- `app/ui`：Compose 快速记账、账单、待确认、账户分类、报表、设置和冲突处理。

未使用 Hilt：当前仓库为单 App、依赖图很小，使用一个显式 `AppGraph` 保留同样的职责边界并减少生成代码。若后续模块继续扩张，再引入 Hilt，不影响 Domain/Repository 接口。
