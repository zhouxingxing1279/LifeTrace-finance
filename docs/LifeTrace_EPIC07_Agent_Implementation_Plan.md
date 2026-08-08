# LifeTrace EPIC-07：LifeTrace Finance Android App —— Agent 具体实施方案

> 目标仓库：`zhouxingxing1279/LifeTrace-finance`  
> 目标分支基线：`main`  
> Android 包名：`com.lifetrace.finance`  
> 云端 App ID：`lifetrace-finance-android`  
> 上游主仓库：`zhouxingxing1279/LifeTrace`  
> 上游路线图：`docs/LifeTrace_Complete_Roadmap_v2.md`（文件正文当前为 v3）  
> 前置依赖：EPIC-04 账号/认证/设备管理、EPIC-05 同步协议与 Windows 同步行为规范、EPIC-06 手工记账/自动记账/账单对账  
> 核心原则：**本地优先、离线可写、金额整数分、Android 原生、统一领域语义、与主仓库契约严格一致、通知最小采集、同步可恢复、凭据不落明文、3 秒快速记账、不可静默丢账。**

---

# 1. EPIC-07 最终目标

EPIC-07 完成后，`LifeTrace Finance` 应成为一个可以独立安装、独立登录、独立离线运行、独立同步和独立发布的 Android 财务客户端。

完整闭环应为：

```text
用户手工记账 / 通知捕获 / 快捷入口
        ↓
Android Domain Service
        ↓
Room 本地数据库
        +
同一事务写入 Sync Outbox
        ↓
界面立即更新，不等待网络
        ↓
WorkManager 驱动 Push / Pull / Snapshot
        ↓
LifeTrace Cloud
        ↓
Windows / Web / 其他已授权客户端
```

EPIC-07 必须最终做到：

1. 普通手工支出、收入和转账可以在约 3 秒内完成。
2. 无网络时仍可记账、修改、删除和查看本地数据。
3. Android 端本地业务写入与 Outbox 保持原子性。
4. 网络恢复后自动同步，不要求用户手动点击同步。
5. Access Token 过期自动刷新，Refresh Token 不以明文保存。
6. Finance App 只能访问 Finance 权限范围内的实体。
7. 通知监听仅采集记账所需最少信息，并且可关闭、可清理、可诊断。
8. 通知识别结果进入候选/暂记流程，不因解析器误判直接不可逆入账。
9. 正式账单导入/对账后的确认结果可同步回手机。
10. 冲突不会静默覆盖，删除不会被旧离线设备重新复活。
11. 账户、分类、最近账单、待确认箱、预算、订阅、报表和同步状态均有明确产品入口。
12. Quick Settings Tile、App Shortcuts、Home Screen Widget、Share Receiver 形成快速输入入口。
13. Release 构建可重复，签名密钥和生产凭据不进入 Git 仓库。
14. 用户遇到“点了没反应”“没有请求”“自动同步失败”时，可以通过本地诊断日志定位到明确阶段，而不是只显示模糊的网络错误。

---

# 2. 当前真实前置状态

Agent 开工时不得只读取本仓库。本仓库在 EPIC-07 启动时仅有最小 README，所有协议和业务语义必须以上游 `LifeTrace` 主仓库为准。

当前已知上游状态：

## 2.1 EPIC-04 已完成

主仓库已有 EPIC-04 完成报告，已具备：

- Native 登录、刷新、退出；
- App Grant + Scope；
- Device / Session / Token 管理；
- Refresh Token 轮换与重放检测；
- 服务端按 Scope 限制同步实体；
- `lifetrace-finance-android` 独立 App Grant 语义。

## 2.2 EPIC-05 已完成 Windows 同步，但 Android 尚未实现

EPIC-05 完成报告明确包含：

- Outbox；
- Cursor Pull；
- Snapshot；
- Tombstone；
- Conflict；
- Token Refresh；
- Retry / 429 / 413；
- 本地 Profile 与 Cloud User 分离；
- Windows Credential Manager。

同时明确未实现：

```text
Android
Kotlin SDK
Room
WorkManager
Android Credential Storage
Android UI
```

因此 EPIC-07 必须实现 **Android/Kotlin 版本的同步客户端**，而不是假定 EPIC-05 已经提供可直接使用的 Android SDK。

## 2.3 当前 Finance 上游契约

开工时至少确认这些实体仍存在且 schemaVersion 未发生不兼容变化：

```text
finance.account
finance.category
finance.transaction
finance.transaction_evidence
```

当前 `finance.transaction` 语义包括：

```text
transactionType
amountCents
currency
accountId
toAccountId
categoryId
counterparty
merchant
item
note
occurredAt
localDate
status
sourceType
externalTransactionId
```

其中：

```text
TransactionType:
expense / income / transfer / refund / fee

TransactionStatus:
candidate / provisional / confirmed / ignored
```

金额必须始终使用整数分 `amountCents`，禁止 Android DTO、Room Entity 或 UI Domain Model 中使用 `Double` 作为真实金额。

## 2.4 EPIC-06 是功能门禁，不得假完成

EPIC-07 路线图依赖 EPIC-06，但开工时必须再次检查主仓库是否已经存在 EPIC-06 的正式实现、文档、迁移、契约和测试。

如果 EPIC-06 尚未完成：

- EPIC-07 **可以立即实施**：工程骨架、本地数据库、认证、Android 同步、手工记账、账户、分类、最近账单、同步状态、通知采集框架、快捷入口、日志与 CI。
- EPIC-07 **不得擅自定版**：新的云端候选账单实体、正式账单对账协议、预算同步实体、订阅同步实体、服务器解析规则下发协议。
- 需要跨仓修改协议时，先在 `LifeTrace` 主仓库形成契约，再在本仓库消费。

不得为了“先把页面做出来”而在 Android 仓库自创一个与服务端不兼容的永久协议。

---

# 3. 本阶段范围边界

## 3.1 必须实现

### App 基础

- `com.lifetrace.finance`；
- Kotlin + Android 原生工程；
- 独立登录；
- 独立本地数据库；
- 独立 Android 同步客户端；
- 独立版本和发布流程；
- Finance Scope；
- 深色/浅色主题；
- 基础无障碍和大字体适配。

### 核心页面

- 快速支出；
- 快速收入；
- 转账；
- 最近账单；
- 账单详情；
- 待确认箱；
- 账户；
- 分类；
- 预算；
- 订阅；
- 报表；
- 同步状态与冲突；
- 设置与权限诊断。

### Android 原生能力

- `NotificationListenerService`；
- `WorkManager`；
- `Quick Settings Tile`；
- `App Shortcuts`；
- Home Screen Widget；
- `Share Receiver`；
- Android Keystore 凭据保护；
- 可选 `AccessibilityService`，但默认不得启用。

## 3.2 本 EPIC 不做

- 不把 Rust `lifetrace-sync-client` 通过 JNI/UniFFI 嵌入 Android；
- 不在 Android 中直接访问 PostgreSQL；
- 不允许模型或脚本绕过 Domain Service 直接写账单表；
- 不复制 LifeTrace Desktop 的完整 UI；
- 不实现 Notes、English、Habits 领域；
- 不在 Finance App 中实现完整 AI 管家；
- 不自行重新设计主仓库认证协议；
- 不自行新增未登记的同步 entityType；
- 不把通知原文、Token、密码上传到日志或同步 payload；
- 不以 AccessibilityService 作为首版自动记账的必需条件；
- 不因为云端故障而禁止本地记账。

---

# 4. 技术栈与硬性架构决策

除非在 `precondition-report.md` 中给出明确理由，否则 Agent 按以下技术栈实施。

## 4.1 Android 技术栈

```text
Language              Kotlin
UI                    Jetpack Compose + Material 3
Navigation            Navigation Compose
Async                  Kotlin Coroutines + Flow
Database               Room / SQLite
DI                     Hilt
Network                OkHttp + Retrofit
Serialization          kotlinx.serialization
Background             WorkManager
Preferences            DataStore
Secrets                Android Keystore-backed SecureTokenStore
Widget                 AndroidX Glance 或等价官方 AppWidget 方案
Testing                JUnit + Turbine + Room tests + Compose UI tests
Static Analysis        Android Lint + ktlint/Spotless + Detekt（择一统一）
Build                   Gradle Kotlin DSL + Version Catalog
```

## 4.2 Android 版本策略

- `minSdk` 建议从 API 26 起；
- `compileSdk` / `targetSdk` 使用实施时项目确认的最新稳定 SDK；
- 精确 SDK、AGP、Kotlin、Compose BOM 版本必须固定到版本目录并提交；
- 禁止 `+` 动态依赖；
- Release 构建启用 R8/资源压缩前必须通过回归测试。

## 4.3 不共享运行时，共享协议

Android 不复用 Rust runtime，但必须复用以下语义：

```text
EntityType
Schema Version
JSON 字段名
Money
Time / LocalDate
ChangeId
BaseServerVersion
ServerVersion
Cursor
Tombstone
Conflict
Snapshot
Auth Token 生命周期
App Grant / Scope
```

即：

```text
共享 Contract
不共享 Runtime
```

---

# 5. 建议仓库结构

Agent 首轮工程化后，将仓库整理为：

```text
LifeTrace-finance/
├── app/
├── build-logic/
├── core/
│   ├── common/
│   ├── model/
│   ├── database/
│   ├── network/
│   ├── auth/
│   ├── sync/
│   └── ui/
├── feature/
│   ├── quick-entry/
│   ├── transactions/
│   ├── inbox/
│   ├── accounts/
│   ├── categories/
│   ├── budgets/
│   ├── subscriptions/
│   ├── reports/
│   └── settings/
├── platform/
│   ├── notifications/
│   ├── shortcuts/
│   ├── tile/
│   ├── widget/
│   └── share/
├── contract-snapshots/
├── docs/
│   ├── LifeTrace_EPIC07_Agent_Implementation_Plan.md
│   └── epic-07/
│       ├── precondition-report.md
│       ├── architecture.md
│       ├── database-schema.md
│       ├── sync-state-machine.md
│       ├── notification-parser.md
│       ├── privacy-model.md
│       ├── test-matrix.md
│       └── completion-report.md
├── tools/
├── .github/workflows/
├── gradle/libs.versions.toml
├── settings.gradle.kts
└── README.md
```

模块化的目标是隔离职责，不是为了追求模块数量。若 Gradle 构建复杂度明显高于收益，可以合并相邻 feature，但以下边界不得打破：

```text
Database
Network/Auth
Sync
Platform Notification
UI Feature
```

---

# 6. 开始实施前的强制审计

Agent 的第一个正式开发动作不是写 UI，而是生成：

```text
docs/epic-07/precondition-report.md
```

## 6.1 必须读取的上游文件

至少读取：

```text
LifeTrace/docs/LifeTrace_Complete_Roadmap_v2.md
LifeTrace/docs/epic-04/LifeTrace_EPIC04_Agent_Implementation_Plan.md
LifeTrace/docs/epic-04/completion-report.md
LifeTrace/docs/epic-05/LifeTrace_EPIC05_Agent_Implementation_Plan.md
LifeTrace/docs/epic-05/completion-report.md

LifeTrace/crates/lifetrace-contracts/src/domain/finance.rs
LifeTrace/crates/lifetrace-contracts/src/domain/enums.rs
LifeTrace/crates/lifetrace-contracts/src/registry.rs
LifeTrace/crates/lifetrace-contracts/src/sync/v1/**
LifeTrace/crates/lifetrace-contracts/src/auth/v1/**
LifeTrace/services/lifetrace-cloud/src/**
```

若主仓已经存在 EPIC-06 文档/实现，也必须全部读取。

## 6.2 审计报告必须回答

```text
1. 当前 Cloud Base URL / API version 如何配置？
2. Native 登录、refresh、logout、me、device API 的真实路径是什么？
3. Finance Android 的 appId 与真实 scope 是什么？
4. Push / Pull / Snapshot / Capability 的真实路径和 DTO 是什么？
5. 服务端 batch size / page size / 413 / 429 语义是什么？
6. Cursor expired 如何返回？
7. Conflict payload 的真实结构是什么？
8. finance.* 当前允许同步哪些 entityType？
9. Budget / Subscription 是否已有正式 contract？
10. EPIC-06 是否已有候选交易/通知证据/对账正式协议？
11. 当前 schemaVersion 是多少？
12. Android 需要兼容哪些后端部署环境？
13. 是否已有可复用的 JSON fixture / OpenAPI / JSON Schema？
14. 上游是否已经定义 UUID/UUIDv7 要求？
15. 时间格式、localDate、时区边界如何定义？
```

## 6.3 Gate 分类

审计结果把能力分为：

```text
READY
可立即实施并接真实 Cloud

LOCAL_READY
可先完整实现本地行为，Cloud contract 尚待补全

BLOCKED_BY_EPIC06
不得做正式云端定版
```

未经 Gate 分类不得大规模编码。

---

# 7. 上游 Contract 固定与防漂移

由于 Android App 和 Cloud 位于不同仓库，必须显式解决协议漂移。

## 7.1 Upstream Lock

新增：

```text
contract-snapshots/upstream.lock
```

至少记录：

```text
repository = zhouxingxing1279/LifeTrace
commit = <audited-main-commit>
contract_schema_version = <version>
updated_at = <UTC>
```

## 7.2 Contract Snapshot

从主仓库生成或提取：

- Auth OpenAPI / Schema；
- Sync DTO Schema；
- Finance Domain Schema；
- Entity Registry snapshot；
- Golden JSON fixtures。

若主仓已有生成产物，直接消费；若没有，则建立最小导出脚本，但不能手工复制后失去来源记录。

## 7.3 Kotlin DTO 规则

Kotlin 网络 DTO 必须保持 wire 兼容：

- `camelCase`；
- 未知 string enum 不导致整个 batch 解析失败；
- 未识别字段允许向前兼容；
- 金额 `Long`；
- 时间字符串严格解析；
- `localDate` 独立存储，禁止从 UTC 字符串截前 10 位推导；
- UUID/EntityId 不使用 Int 自增替代；
- `serverVersion` 可空；
- Tombstone 与删除状态不等价于物理 delete。

## 7.4 CI Contract Gate

CI 必须包含：

```text
Kotlin DTO <-> Golden JSON round trip
Finance transaction fixture
Account fixture
Category fixture
Evidence fixture
Push request fixture
Push response fixture
Pull page fixture
Conflict fixture
Snapshot fixture
Auth login/refresh fixture
```

协议测试失败时不得通过“放宽成 JsonObject everywhere”掩盖问题。

---

# 8. 本地 Profile、身份和数据归属

Android 需要沿用 EPIC-05 已验证的区分：

```text
LocalProfileId
CloudUserId
AppId
DeviceId
```

禁止用同一个 `userId: String` 混用。

## 8.1 Local Profile

建议 Room 表：

```text
local_profiles
- id
- profile_type: local | cloud
- cloud_user_id nullable
- display_name
- created_at
- updated_at
```

本地业务表 Owner 始终是 `local_profile_id`。

## 8.2 首次使用

推荐流程：

```text
首次打开
→ 创建稳定 Local Profile
→ 可以本地记账
→ 用户选择登录
→ 登录成功后绑定 Cloud User
→ 执行 Snapshot / Pull / Push 合并门禁
```

登录不得把本地已有账单直接清空或覆盖。

## 8.3 绑定规则

如果本地 Profile 已有数据而云端也已有数据：

1. 先持久化绑定状态；
2. 获取云端 Snapshot；
3. 不删除本地未同步记录；
4. 以同步版本语义进行合并；
5. 冲突进入 `sync_conflicts`；
6. 绑定失败保持本地数据可用。

---

# 9. Room 数据库设计

数据库 schema 必须在实现前记录到：

```text
docs/epic-07/database-schema.md
```

## 9.1 必须存在的核心表

```text
local_profiles
active_profile
finance_accounts
finance_categories
finance_transactions
finance_transaction_evidence
sync_outbox
sync_state
sync_metadata
sync_conflicts
snapshot_staging
```

Android 特有的 Device Local 表：

```text
notification_events
notification_parser_state
transaction_drafts
quick_entry_preferences
local_diagnostics
```

若预算/订阅尚无正式云端 Contract，则其持久化表必须明确标记为 `device_local` 或 feature gated，不得伪装成已同步实体。

## 9.2 Finance Transaction Entity

必须至少映射：

```text
id
local_profile_id
transaction_type
amount_cents
currency
account_id
to_account_id
category_id
counterparty
merchant
item
note
occurred_at
local_date
status
source_type
external_transaction_id
created_at
updated_at
deleted_at
local_version
server_version
modified_by_device
```

金额字段：

```text
Long amountCents
```

禁止：

```text
Double amount
Float price
REAL amount
```

作为核心真实金额。

## 9.3 索引

至少评估：

```text
(local_profile_id, local_date DESC)
(local_profile_id, occurred_at DESC)
(local_profile_id, account_id, occurred_at DESC)
(local_profile_id, category_id, local_date DESC)
(local_profile_id, status, occurred_at DESC)
(local_profile_id, external_transaction_id)
```

外部交易号唯一约束必须与 source/channel 语义一致，不能对 nullable 字段粗暴建立全局唯一导致不同来源冲突。

## 9.4 Room Schema Export

开启 Room schema export，并将 schema JSON 纳入 Git。

每次 migration 必须有：

- migration test；
- 旧版本数据库 fixture；
- 记录数校验；
- 金额总和校验。

---

# 10. Domain Service：所有写入的唯一入口

UI、Notification Service、Tile、Widget、Share Receiver、Sync Worker 都不得直接拼 SQL 或直接修改 DAO。

统一入口例如：

```text
CreateExpenseUseCase
CreateIncomeUseCase
CreateTransferUseCase
UpdateTransactionUseCase
DeleteTransactionUseCase
ConfirmCandidateUseCase
IgnoreCandidateUseCase
ChangeCategoryUseCase
CreateAccountUseCase
UpdateAccountUseCase
CreateCategoryUseCase
```

## 10.1 本地业务写入 + Outbox 同事务

所有需要同步的业务写入：

```text
RoomDatabase.withTransaction {
    writeBusinessEntity()
    writeOutboxChange()
}
```

任何以下状态都属于 bug：

```text
账单已写入，但 Outbox 没写
Outbox 已写，但账单回滚
应用崩溃后账单永远不会同步
```

## 10.2 Remote Apply 不产生 Outbox

Pull / Snapshot Apply 使用内部专用 Repository API：

```text
RemoteApplyRepository
```

不得调用普通本地写入 UseCase，否则会形成同步回环。

## 10.3 删除

用户删除交易时：

- 本地显示立即消失；
- 实体保留 tombstone / deletedAt；
- Outbox 写 delete operation；
- 远端确认后按同步保留策略清理；
- 不能直接物理删除导致旧设备复活。

---

# 11. Android Auth 与安全凭据

## 11.1 App 身份

认证请求使用正式 App ID：

```text
lifetrace-finance-android
```

不得使用：

```text
lifetrace-desktop
lifetrace-web
```

冒充其他客户端。

## 11.2 Token 生命周期

```text
Access Token
→ 仅进程内存

Refresh Token
→ Android Keystore 加密保护

Password
→ 仅登录请求瞬时存在
→ 不落 SQLite / DataStore / Log
```

## 11.3 Refresh Single Flight

多个请求同时遇到 401 时，只允许一个 Refresh 进行：

```text
Mutex / single-flight refresh
```

其他请求等待结果，禁止并发 refresh 导致旋转 token family 被误判重放。

## 11.4 Logout

退出必须：

- 尝试服务端 logout；
- 删除本地 Refresh Token；
- 清除内存 Access Token；
- 取消该账号同步 Work；
- 保留本地业务数据，除非用户显式选择“删除本机数据”；
- UI 进入 local/offline 状态。

## 11.5 日志脱敏

严禁记录：

```text
password
access token
refresh token
完整 Authorization header
完整通知原文
完整银行卡号
```

---

# 12. Android 同步核心

实现一个独立 Kotlin Sync Engine，行为必须对齐 EPIC-05。

建议结构：

```text
core/sync/
├── SyncCoordinator
├── PushEngine
├── PullEngine
├── SnapshotEngine
├── ConflictStore
├── SyncScheduler
├── SyncTransport
├── SyncRepository
└── SyncDiagnostics
```

## 12.1 Push

Push 必须支持：

- 批量；
- changeId 幂等；
- baseServerVersion；
- 部分成功；
- 单条 reject；
- conflict 持久化；
- 网络错误重试；
- 401 refresh；
- 429 `Retry-After`；
- 413 自动缩小 batch；
- 指数退避 + jitter；
- App 重启后继续。

不得在失败后删除 Outbox。

## 12.2 Pull

```text
cursor
→ GET page
→ Room transaction apply page
→ 成功后更新 cursor
```

硬规则：

```text
page apply 失败
=> cursor 不推进
```

Pull apply 不产生 Outbox。

## 12.3 Snapshot

Snapshot 用于：

- 新设备；
- 本地数据重建；
- Cursor 过期；
- 显式修复。

Snapshot 必须：

- 可分页；
- 可恢复；
- 有 staging；
- 校验完成后再切换；
- 不覆盖未上传的本地改动；
- 完成后切换到增量 Cursor。

## 12.4 Conflict

`sync_conflicts` 至少保存：

```text
entityType
entityId
localPayload
remotePayload
baseServerVersion
remoteServerVersion
createdAt
resolutionState
```

UI 至少提供：

```text
保留本地
采用云端
稍后处理
```

具体 Resolve API 与版本语义以上游协议为准。

## 12.5 WorkManager 调度

使用 Unique Work，避免重复 Worker 风暴。

触发：

```text
App 启动
本地写入后 debounce
网络恢复
定时兜底
用户手动同步
Token refresh 成功后
```

建议：

```text
OneTimeWorkRequest 负责事件驱动同步
PeriodicWorkRequest 负责兜底
```

不要依赖高频轮询。

## 12.6 同步状态

UI 明确显示：

```text
未登录
离线
等待同步
同步中
已同步
部分失败
需要登录
存在冲突
需要 Snapshot 修复
```

不得只显示一个“云端不可用”。

---

# 13. 手工记账与 3 秒路径

EPIC-07 的核心体验不是“功能很多”，而是 **高频记账尽可能短**。

## 13.1 快速支出

默认页面结构：

```text
金额输入（自动聚焦）
常用分类 Chips
默认账户
备注/商户（折叠）
保存
```

保存后：

```text
Room commit 完成
→ 立即返回成功
→ 后台 Sync
```

不得等待云端响应后才显示成功。

## 13.2 快速收入

复用同一套金额输入组件和 Domain Service，仅 transactionType 不同。

## 13.3 转账

必须选择：

```text
fromAccount
→ toAccount
→ amount
```

转账不得计入普通支出/收入统计。

禁止通过“创建一笔支出 + 一笔收入”临时模拟而破坏主仓 `transfer` 语义。

## 13.4 3 秒定义

验收不能只凭主观判断。

至少满足：

- 从 Quick Settings Tile / Shortcut 打开后直接到金额输入；
- 无启动页阻塞；
- 金额键盘立即可用；
- 常用账户默认选中；
- 最近/常用分类一屏可点；
- 保存不等待网络；
- 输入金额后完成普通支出不超过 2 个额外交互动作；
- 使用 Macrobenchmark 记录 cold/warm start；
- 用人工脚本记录 20 次普通记账的中位完成时间，目标约 3 秒。

如果启动性能达不到，优先优化启动链路，不通过动画掩盖延迟。

---

# 14. 最近账单与账单详情

## 14.1 最近账单

支持：

- 今天 / 本周 / 本月；
- 支出 / 收入 / 转账；
- 账户；
- 分类；
- 候选/暂记/确认状态；
- 本地搜索商户、备注、项目。

列表必须从 Room Flow 驱动，云端同步只是更新数据库。

## 14.2 账单详情

至少显示：

```text
金额
类型
账户
分类
商户/对方
时间
本地自然日
来源
状态
备注
证据摘要
同步状态
```

支持：

- 编辑；
- 删除；
- 改分类；
- 确认候选；
- 忽略候选；
- 查看同步冲突。

---

# 15. NotificationListenerService：自动账单捕获

通知捕获属于高风险能力，必须单独设计，不得把“监听到了文字”直接等价为“真实交易”。

## 15.1 权限体验

设置页显示：

```text
通知读取权限：已开启 / 未开启
支持来源：微信 / 支付宝 / 银行 / 云闪付
最近捕获时间
最近解析结果
解析失败数
清空通知缓存
```

引导用户进入系统 Notification Access 设置。

## 15.2 最小采集

默认仅读取 Android Notification extras 中记账所需字段：

```text
packageName
postTime
title
text
bigText
subText
notification key/hash
```

不得默认持久化完整 extras bundle。

## 15.3 Allowlist

解析器只处理明确 allowlist 的 package。

规则：

```text
未知包
→ 忽略

已知包但不匹配支付模式
→ 忽略或仅记录 parser diagnostic

明确交易通知
→ 产生 Candidate
```

## 15.4 Parser Pipeline

```text
Notification
→ Normalize
→ Source Detector
→ Rule Match
→ Amount Extractor
→ Merchant/Counterparty Extractor
→ Account/Channel Hint
→ Confidence Score
→ Candidate Transaction
→ Dedup
→ Persist
```

解析器必须是纯 Kotlin 可测试函数，不把正则散落在 Service 中。

## 15.5 Parser 版本

每个解析结果记录：

```text
parserId
parserVersion
sourcePackage
confidence
```

EPIC-06 若提供服务端规则签名下发，则再接入正式协议。

在此之前不得自行发明不可兼容的远程规则格式。

## 15.6 候选状态

低/中置信度：

```text
status = candidate
```

符合 EPIC-06 正式定义的高置信度暂记才可：

```text
status = provisional
```

最终确认：

```text
status = confirmed
```

用户忽略：

```text
status = ignored
```

阈值必须配置且有测试，不得散落 magic number。

## 15.7 通知证据

可同步的 `finance.transaction_evidence` 只保存最小、脱敏、结构化证据。

完整通知原文默认 Device Local，设置短保留期，例如 7 天或更短，并允许立即清空。

## 15.8 去重

正式策略以 EPIC-06 为准。

本地捕获阶段至少用：

```text
sourcePackage
notificationKey/hash
amount
normalized merchant
short time window
externalTransactionId if available
```

避免同一条通知重复产生多笔候选账单。

---

# 16. 待确认箱

待确认箱聚合：

```text
candidate
provisional
parser failed but recoverable
reconciliation ambiguous
```

每个卡片优先展示：

```text
金额
商户/对方
来源
时间
建议分类
置信度等级
```

一键操作：

```text
确认
改分类并确认
忽略
合并
撤销
```

“模糊匹配正式账单”的合并操作必须等待 EPIC-06 正式匹配协议，不允许 Android 自己修改外部交易号破坏后续对账。

---

# 17. Quick Settings Tile

Tile 点击路径：

```text
点击 Tile
→ 启动透明/快速 Activity 或 Deep Link
→ 直接进入 Quick Expense
→ 金额自动聚焦
```

长按进入设置/主页。

Tile 不直接在后台无 UI 创建金额未知的账单。

---

# 18. App Shortcuts

至少提供动态/静态快捷方式：

```text
记支出
记收入
转账
待确认箱
```

所有入口最终调用同一 Navigation + Domain Service，不允许复制业务逻辑。

---

# 19. Home Screen Widget

首版 Widget 目标：**快速入口，不做复杂财务看板**。

建议：

```text
+ 支出
+ 收入
待确认数量
今日支出（可选）
```

Widget 数据从 Room/轻量快照读取，不直接在 Widget Provider 内发网络请求。

---

# 20. Share Receiver

支持用户从其他 App：

```text
分享文本
分享支付截图
分享账单文件（仅在 EPIC-06 支持的格式范围）
```

首版流程：

```text
ACTION_SEND
→ 判断 MIME
→ 复制到 App 私有临时目录
→ 创建 Draft / Import Intent
→ 显示预览
→ 用户确认
```

不得把外部 URI 永久当作稳定文件路径。

截图 OCR/视觉识别若未来加入，属于明确的解析器能力，必须保留来源与置信度，不得覆盖 Notification 证据。

---

# 21. AccessibilityService 策略

EPIC 路线图写的是“可选 AccessibilityService”。

因此：

- 第一版不得把它设为必要权限；
- 默认 manifest / release flavor 不启用；
- 只有在 NotificationListener 无法覆盖明确场景、用户显式同意、隐私和分发政策审查通过后才进入实验；
- Accessibility 捕获的数据仍要进入同一 Candidate Pipeline；
- 绝不用于自动点击支付、操作第三方 App 或执行资金行为。

---

# 22. 账户与分类

## 22.1 账户

支持：

```text
现金
银行卡
微信
支付宝
投资
其他
```

字段遵守 `finance.account` Contract。

账户余额展示需要区分：

```text
openingBalanceCents at balanceAt
+
之后交易计算值
```

若主仓已有更精确余额语义，以主仓为准。

## 22.2 分类

支持：

- 收入分类；
- 支出分类；
- 父子分类；
- system / custom；
- archived；
- 常用分类排序（可 Device Local）。

不得删除仍被交易引用的分类而破坏历史账单；优先 archive。

---

# 23. 预算、订阅与报表的 Contract Gate

当前已审计的基础 Finance Registry 只有：

```text
finance.account
finance.category
finance.transaction
finance.transaction_evidence
```

因此预算和订阅必须先确认上游是否已经新增正式实体。

## 23.1 预算

如果主仓已定义 `finance.budget`：

- 严格按 Contract 实现 Room + Sync + UI。

如果未定义：

- 可以先实现“基于交易的预算预览/本地草稿”；
- 不得自行向 Sync Push `finance.budget`；
- 在 `precondition-report.md` 记录主仓契约缺口；
- 需要跨端预算时先修改上游 Contract。

## 23.2 订阅

同理。

订阅可以先作为“从交易推导的周期性商户建议”只读视图，但永久订阅规则若需要跨端保存，必须有正式 entity contract。

## 23.3 报表

报表默认是派生数据，不需要新同步实体。

从本地 Room 交易计算：

```text
月支出
月收入
净现金流
分类占比
账户趋势
日/周/月趋势
Top merchants
```

所有金额聚合使用整数分；图表层最后再格式化为十进制字符串。

---

# 24. 同步状态、冲突和诊断页面

设置中必须有一个真正可排障的同步页。

至少显示：

```text
Cloud 登录状态
当前 Local Profile
Device ID 后四位/短 ID
最后一次成功 Push
最后一次成功 Pull
当前 Cursor 摘要
Outbox 数量
Conflict 数量
最近错误 code
下次 retry 时间
WorkManager 状态
网络状态
```

操作：

```text
立即同步
重新登录
查看冲突
重试失败项
重新 Snapshot（危险操作需确认）
导出脱敏诊断日志
```

不得显示 Token。

---

# 25. 日志与可观测性

EPIC-07 从第一天就加入日志系统，避免再次出现“异常在请求发出前发生，但 UI 只提示无法连接云端”的问题。

## 25.1 统一事件模型

建议：

```text
DiagnosticEvent
- timestamp
- level
- component
- eventCode
- message
- correlationId
- entityType nullable
- entityId nullable/hashed
- throwableClass nullable
- metadata redacted
```

组件：

```text
AUTH
NETWORK
SYNC_PUSH
SYNC_PULL
SYNC_SNAPSHOT
DATABASE
NOTIFICATION_CAPTURE
NOTIFICATION_PARSE
QUICK_ENTRY
WORK_MANAGER
UI
```

## 25.2 关键阶段必须打点

例如一次 Push：

```text
SYNC_PUSH_ENQUEUED
SYNC_PUSH_START
SYNC_PUSH_BATCH_BUILT
HTTP_REQUEST_START
HTTP_RESPONSE
SYNC_PUSH_APPLY_RESULT
SYNC_PUSH_SUCCESS / FAILED
```

这样可以区分：

```text
调用前失败
DNS/TLS 失败
HTTP 401
服务端 500
DTO 解析失败
Room apply 失败
```

## 25.3 日志保存

- 使用环形缓冲；
- 限制大小；
- 默认本地；
- 用户主动导出；
- 导出前二次脱敏；
- Release 日志不包含敏感 payload。

---

# 26. UI 风格要求

Finance App 是独立软件，不是“把 Desktop 页面缩窄”。

## 26.1 信息层级

避免：

- 大量 10~12sp 辅助文字；
- 每张卡片都写说明；
- 一屏塞满小号统计；
- 按钮文字过长；
- 所有功能同权重。

优先：

```text
金额
主操作
状态
必要上下文
```

## 26.2 字号

默认正文和核心账单信息不得为了“看起来精致”使用过小字号。

必须测试：

- 系统字体放大；
- 深色模式；
- 小屏；
- 长商户名称；
- 大金额；
- 中文/英文混排。

## 26.3 右键不适用 Android

Desktop 的右键交互不迁移到 Android。Android 使用：

- 长按；
- Swipe；
- Bottom Sheet；
- Overflow Menu。

---

# 27. 隐私与安全

## 27.1 通知数据

默认不上传完整通知原文。

同步证据只发送业务必要字段。

## 27.2 截图保护

登录、Token 管理等敏感页面可评估 `FLAG_SECURE`；普通账单页面是否禁止截图由产品设置控制，避免过度限制。

## 27.3 Network Security

Release：

- 禁止明文 HTTP；
- 禁止信任用户 CA 的调试配置进入 release；
- staging/dev 与 prod Base URL 分离；
- 不在 APK 中硬编码私钥/长期 secret。

## 27.4 Backup

Android Auto Backup 必须审查：

- Refresh Token 不进入 backup；
- Keystore key 不依赖无法恢复的备份语义；
- 数据库备份策略与云端 Snapshot 恢复策略一致。

---

# 28. 测试策略

所有验收项必须映射到：

```text
docs/epic-07/test-matrix.md
```

## 28.1 Unit Tests

必须覆盖：

- 金额解析；
- localDate；
- TransactionType / Status；
- Domain validation；
- Outbox creation；
- Notification normalization；
- 微信/支付宝/银行 parser fixture；
- Confidence；
- Dedup；
- Retry backoff；
- Token single-flight refresh；
- Conflict mapping。

## 28.2 Room Tests

覆盖：

```text
业务写入 + Outbox 原子性
Remote apply 不写 Outbox
Migration
Tombstone
Profile 隔离
Cursor 更新原子性
Snapshot staging
```

## 28.3 Sync Contract Tests

使用上游 fixture 模拟：

```text
Push success
Push partial failure
Duplicate changeId
Conflict
401 + refresh
429
413
Network timeout
Pull multiple pages
Pull apply failure
Cursor expired
Snapshot resume
Tombstone
Unknown enum value
Unknown JSON field
```

## 28.4 Instrumentation / Compose Tests

至少覆盖：

- 快速支出；
- 快速收入；
- 转账；
- 待确认箱；
- 权限引导；
- 离线记账；
- 重启后账单仍在；
- 登录/退出；
- 同步状态；
- 大字体。

## 28.5 Notification Service 测试

不要依赖真实支付行为。

使用固定 fixture：

```text
微信成功支付样本
支付宝支付样本
银行卡消费样本
非支付聊天通知
重复通知
退款通知
模糊文本
缺失金额
异常 Unicode
```

不得在测试中构造“看起来能过”的规则却没有负样本。

## 28.6 性能测试

使用 Macrobenchmark / Baseline Profile（若适合）：

- cold start；
- warm start；
- quick entry screen；
- recent transaction list；
- 1 万笔交易查询；
- 5 万笔交易聚合。

---

# 29. CI/CD

新增 GitHub Actions。

## 29.1 PR / Push Gate

至少：

```text
./gradlew spotlessCheck 或 ktlintCheck
./gradlew detekt
./gradlew lint
./gradlew testDebugUnitTest
./gradlew assembleDebug
Room schema verification
Contract fixture tests
```

关键 instrumentation 可在 Emulator workflow 中运行。

## 29.2 Release Gate

Release 必须：

- 正式签名；
- versionCode 单调递增；
- versionName 可追踪；
- 生成 APK；
- 生成 AAB（即使首轮只侧载也保留）；
- 生成 SHA256；
- 保留 mapping 文件；
- Release Notes；
- 不把 keystore/base64 secret 提交仓库。

## 29.3 Build Flavors

建议：

```text
dev
staging
prod
```

Base URL 和日志级别通过 BuildConfig 注入。

`prod` 禁止 cleartext 和 debug endpoint。

---

# 30. 分阶段执行顺序

以下顺序是给 Agent 的正式执行顺序，不允许从“做页面”开始跳过基础门禁。

## Phase 0：前置审计

交付：

```text
docs/epic-07/precondition-report.md
contract-snapshots/upstream.lock
```

完成条件：

- EPIC-04/05 真实接口确认；
- EPIC-06 状态确认；
- entity registry 确认；
- Budget/Subscription contract gap 确认。

## Phase 1：工程骨架

交付：

- Android Gradle 工程；
- 模块边界；
- Compose 主壳；
- Hilt；
- version catalog；
- CI；
- dev/staging/prod；
- 基础日志。

门禁：

```text
clean checkout
→ ./gradlew test
→ ./gradlew lint
→ ./gradlew assembleDebug
```

全部成功。

## Phase 2：Contract + Domain Model

交付：

- Auth DTO；
- Sync DTO；
- Finance DTO；
- Money / Time / ID value objects；
- Golden fixture tests。

禁止开始 Cloud integration 前没有 fixture test。

## Phase 3：Room + Local Domain

交付：

- schema；
- migrations；
- repositories；
- Domain services；
- outbox atomic write；
- recent transaction queries。

门禁：完全断网环境可完成手工支出/收入/转账。

## Phase 4：Auth + Device

交付：

- Login；
- Token refresh；
- Keystore token store；
- logout；
- App grant/scope validation；
- device registration/identity；
- local profile binding。

门禁：进程重启后可通过 refresh 恢复登录，但数据库中查不到 refresh token 明文。

## Phase 5：Android Sync Core

交付：

- Push；
- Pull；
- Snapshot；
- Conflict；
- Tombstone；
- WorkManager scheduler；
- Sync status UI；
- diagnostics。

门禁：

```text
Android 离线新增交易
→ 恢复网络
→ 自动 Push
→ Windows/Web 可收到
```

以及反向 Pull。

## Phase 6：核心 Finance UI

交付：

- Quick Expense；
- Quick Income；
- Transfer；
- Recent；
- Detail；
- Accounts；
- Categories。

门禁：普通支出约 3 秒路径达标。

## Phase 7：Notification Capture

交付：

- Notification permission；
- service；
- allowlist；
- parser pipeline；
- candidate/provisional；
- dedup；
- local evidence；
- parser test fixtures；
- diagnostics。

门禁：普通聊天/营销通知不得大量误入账。

## Phase 8：待确认与 EPIC-06 对接

若 EPIC-06 contract ready：

- 对账；
- 合并；
- evidence；
- 模糊匹配确认；
- 正式账单结果回流。

若未 ready：

- 只完成本地待确认体验；
- 写明 blocker；
- 不伪造服务端行为。

## Phase 9：Android 快捷能力

交付：

- Tile；
- Shortcuts；
- Widget；
- Share Receiver。

所有入口复用 Domain Service。

## Phase 10：预算 / 订阅 / 报表

- 先 Gate Contract；
- 报表可直接完成；
- 预算/订阅按上游 contract 状态实施或 feature gate。

## Phase 11：安全、性能、发布

交付：

- privacy review；
- token review；
- backup review；
- benchmark；
- release workflow；
- test matrix；
- completion report。

---

# 31. 建议的提交序列

Agent 应使用小而可回滚的提交，不允许一个 5 万行“大提交”。

建议：

```text
chore: bootstrap Android project and CI
chore: pin LifeTrace upstream contract snapshot
feat: add finance domain and Room schema
feat: add local-first transaction services and outbox
feat: add native auth and secure token storage
feat: add Android sync push and pull
feat: add snapshot conflict and tombstone handling
feat: add quick expense income and transfer flows
feat: add accounts categories and recent transactions
feat: add notification capture pipeline
feat: add candidate inbox and confirmation flow
feat: add quick settings shortcuts and widget
feat: add share receiver
feat: add finance reports
feat: add budgets and subscriptions contract integration
chore: harden privacy diagnostics and release pipeline
docs: add EPIC-07 completion evidence
```

预算/订阅提交仅在 Contract ready 时执行。

---

# 32. Agent 每阶段强制工作方式

每个 Phase 都遵守：

```text
1. 先读取现有代码和契约
2. 写/更新设计文档
3. 写失败测试或 fixture
4. 实现最小闭环
5. 跑局部测试
6. 跑完整 Gate
7. 检查 git diff
8. 提交
9. 更新 test-matrix / progress
```

禁止：

- 看到编译错误就删除测试；
- 为通过测试改成 `Any` / `JsonObject`；
- 用 TODO 假装完成；
- UI mock 数据长期留在生产路径；
- 网络失败时静默吞异常；
- 解析通知失败后直接忽略且不留 diagnostic；
- 为快速完成把 token 放 SharedPreferences 明文；
- 把 `amountCents` 改成 Double；
- 给所有冲突做 last-write-wins；
- 关闭 lint/Detekt 来通过 CI。

---

# 33. 最终验收矩阵

## A. 快速记账

- [ ] Quick Tile 可直接进入支出金额输入
- [ ] Shortcut 可直接进入支出/收入/转账
- [ ] 普通支出约 3 秒完成
- [ ] 保存不等待网络
- [ ] App 重启后数据仍存在

## B. 离线

- [ ] 飞行模式可新增支出
- [ ] 飞行模式可新增收入
- [ ] 飞行模式可转账
- [ ] 飞行模式可编辑/删除
- [ ] 网络恢复自动同步

## C. 同步

- [ ] Outbox 与业务写入原子
- [ ] 重复 Push 不重复入账
- [ ] Pull 分页正确
- [ ] Pull Apply 失败 Cursor 不推进
- [ ] 429 正确退避
- [ ] 413 可拆 batch
- [ ] 401 只触发 single-flight refresh
- [ ] Tombstone 不被旧数据复活
- [ ] Snapshot 可恢复
- [ ] Conflict 不静默覆盖

## D. 权限和认证

- [ ] App ID 为 `lifetrace-finance-android`
- [ ] Finance App 无法同步 Notes entity
- [ ] Access Token 不落盘
- [ ] Refresh Token 受 Keystore 保护
- [ ] Logout 清理 token
- [ ] 撤销 App Grant 后同步失败并进入重新登录状态

## E. 通知捕获

- [ ] 未授权时不崩溃
- [ ] 授权状态可诊断
- [ ] 支持来源按 allowlist 解析
- [ ] 非支付通知不会入候选箱
- [ ] 重复通知不重复入账
- [ ] 低置信度进入 candidate
- [ ] 用户可确认/忽略
- [ ] 完整通知原文不默认同步

## F. 多端

- [ ] Android 新增交易可在 Windows/Web 出现
- [ ] Windows/Web 修改可 Pull 到 Android
- [ ] 正式账单确认状态可回手机
- [ ] 用户分类和备注不被对账静默覆盖

## G. UI

- [ ] 核心字号可读
- [ ] 支持 dark mode
- [ ] 支持系统大字体
- [ ] 小屏无关键按钮遮挡
- [ ] 金额长数字不溢出
- [ ] 同步失败有明确状态

## H. 可观测性

- [ ] 请求前异常可以定位
- [ ] HTTP 与 DTO 解析错误可区分
- [ ] WorkManager 最近运行可查看
- [ ] 可导出脱敏诊断日志
- [ ] 日志不含 Token/密码

## I. Release

- [ ] CI 全绿
- [ ] Debug APK 可安装
- [ ] Release APK/AAB 可签名构建
- [ ] versionCode 正确递增
- [ ] SHA256 生成
- [ ] signing secret 不在仓库
- [ ] completion report 完成

---

# 34. 最终 Definition of Done

只有同时满足以下条件，Agent 才能声明 EPIC-07 完成：

```text
[ ] Android App 工程可从 clean checkout 构建
[ ] 真实包名 com.lifetrace.finance
[ ] 真实 appId lifetrace-finance-android
[ ] 本地 Room 完整可用
[ ] 本地写入和 Outbox 原子
[ ] Auth/Refresh/Logout 接真实 Cloud
[ ] Android Push/Pull/Snapshot 接真实 Cloud
[ ] WorkManager 自动同步有效
[ ] 离线记账有效
[ ] 3 秒快速记账路径达到目标
[ ] NotificationListenerService 有真实解析 fixture 和负样本
[ ] 候选交易可确认/忽略
[ ] 多端同步往返通过
[ ] Conflict/Tombstone 通过测试
[ ] Tile/Shortcut/Widget/Share Receiver 可用
[ ] 预算/订阅已按正式 Contract 实现，或在上游 Contract 未完成时被明确标记为 blocker，不能虚假声称完成
[ ] 报表使用真实本地数据
[ ] 日志和诊断系统可排查请求前/请求中/请求后错误
[ ] Refresh Token 不明文落盘
[ ] CI、Lint、Unit Test、Build Gate 全通过
[ ] Release 构建可重复
[ ] docs/epic-07/test-matrix.md 完成
[ ] docs/epic-07/completion-report.md 完成
[ ] 所有未完成项都有明确 blocker 和后续 Issue
```

如果 EPIC-06 尚未完成导致正式账单对账、预算或订阅协议无法定版，Agent 必须把 EPIC-07 状态写成：

```text
PARTIALLY COMPLETE — BLOCKED BY EPIC-06 CONTRACT
```

禁止为了把 Epic 标绿而在 Android 仓库私自制造一套服务端不存在的协议。

---

# 35. Agent 开工指令

执行 EPIC-07 时，Agent 应从以下动作开始：

```text
1. 拉取 LifeTrace-finance main
2. 读取本执行方案
3. 拉取/读取 LifeTrace 主仓库 main
4. 记录主仓库 commit SHA
5. 审计 EPIC-04 / 05 / 06
6. 审计 auth / sync / finance contract
7. 创建 docs/epic-07/precondition-report.md
8. 创建 contract-snapshots/upstream.lock
9. 只有完成审计后才 bootstrap Android 工程
10. Phase 1 起逐阶段实施并持续更新 test-matrix
```

遇到上游契约缺口时：

```text
先记录缺口
→ 在 LifeTrace 主仓补 Contract / Cloud
→ 生成新 snapshot
→ 更新 upstream.lock
→ 再实现 Android
```

不要反过来让 Cloud 去适配一个 Android 端临时猜出来的数据结构。

---

# 36. 核心结论

EPIC-07 的正确实施方式不是“新建一个 Compose 项目，然后把记账页面画出来”，而是建立一个真正可以长期独立使用的 Android 财务客户端：

```text
Android 原生输入能力
        +
Room 本地优先数据层
        +
统一 Finance Domain Service
        +
Kotlin Sync Engine
        +
EPIC-04 Auth
        +
EPIC-05 Sync Contract
        +
EPIC-06 对账语义
        +
Notification Capture
        +
快捷入口
        +
可诊断日志
        +
独立 CI / Release
```

其中最重要的三条工程红线是：

1. **任何记账操作都不能依赖网络成功才能保存。**
2. **Android 端不能自行发明与主仓库不一致的同步实体和协议。**
3. **通知解析、同步、认证任一环节失败，都必须能够被诊断，而不是只给用户一个模糊错误。**
