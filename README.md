# LifeTrace Finance

LifeTrace 的独立 Android 财务客户端（EPIC-07）。包名 `com.lifetrace.finance`，云端 App ID `lifetrace-finance-android`。

## 能力

- 本地优先支出/收入/转账，离线保存；
- Room 业务写入 + Sync Outbox 同事务；
- LifeTrace Auth v1 + Android Keystore Refresh Token；
- Push/Pull/Snapshot/Conflict/Tombstone 自动同步；
- NotificationListenerService 候选账单捕获与去重；
- 待确认箱、账户/分类、报表；
- Quick Settings Tile、App Shortcuts、Home Widget、Share Receiver；
- 脱敏诊断日志。

## 构建

推荐 Android Studio 或 JDK 17 + Android SDK 35 + Gradle 8.9：

```bash
gradle :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

首次运行默认 Cloud URL 是不可路由占位地址，请在“设置”中填写实际 LifeTrace Cloud HTTPS 地址。Debug 构建允许本地 HTTP 联调，Release 禁止明文 HTTP。

完整实施/验收资料见 `docs/epic-07/`。
