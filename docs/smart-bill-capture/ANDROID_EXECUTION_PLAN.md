# LifeTrace Finance Android 智能截图记账执行方案

本文件是 `LifeTrace/docs/smart-bill-capture/EXECUTION_PLAN.md` 的 Android 落地分册。

## 目标

在现有 Android 财务客户端中新增 BeeCount 风格的智能截图记账能力，但不复制 BeeCount 源码：

- 复用现有 `NotificationCaptureService`、`FinanceRepository`、候选账单、证据、Outbox 与 Sync Engine；
- 新增图片分享入口的真实识别流程；
- 新增可选的 MediaStore 截图监听；
- 图片上传到 LifeTrace Cloud 的统一 Vision API；
- 默认模型由 Cloud 配置为 `glm-4v-flash`；
- API Key 不进入 APK；
- 原图默认不进入 Room、Outbox 或 Cloud 持久化存储。

## Android 流程

```text
系统分享图片 / 新截图
        |
        v
SmartCaptureCoordinator
        |
        +-- hash + dedup
        +-- MIME/size precheck
        |
        v
LifeTraceApi.captureFinanceImage
        |
        v
FinanceCaptureResponse
        |
        v
本地 category/account hint matcher
        |
        v
FinanceRepository.captureVisionCandidates
        |
        +-- finance_transactions
        +-- finance_transaction_evidence
        +-- smart_capture_events
        +-- sync_outbox
        |
        v
待确认箱
```

## 数据库

Room 从 v1 升级到 v2，新增 `smart_capture_events`：

- `id`
- `local_profile_id`
- `image_hash`
- `capture_source`
- `provider`
- `model`
- `status`
- `captured_at`
- `transaction_ids_json`
- `error_code`

要求提供 `MIGRATION_1_2`，不得使用 destructive migration 升级用户现有数据库。

## 平台能力

### Share Receiver

当前 `ShareReceiverActivity` 对图片只缓存临时文件并生成占位文本；本次改为把缓存文件路径传给 `MainActivity`，由 ViewModel/Coordinator 发起识别，识别结束后删除临时文件。

### Screenshot Monitor

- `ContentObserver` 监听 `MediaStore.Images`。
- 仅处理最近新增图片。
- 文件名/路径命中 `screenshot/截屏/截图/screen_shot/screen shot`。
- 使用图片 SHA-256 + 本地事件表做去重。
- 用户显式开启；关闭后立即注销 observer。
- Android 13+ 无 `READ_MEDIA_IMAGES` 时不启用，保留 Share Receiver 路径。
- 第一版不做永久前台服务，不承诺进程被系统杀死后继续监听。

## 网络与安全

- 复用现有 Cloud Auth token。
- `multipart/form-data` 上传 PNG/JPEG/WebP。
- 不在日志记录图片内容、Base64、Provider 原始响应或 API Key。
- 网络失败不创建空交易；保留明确错误状态供 UI 展示。

## UI

- 设置页增加“智能截图记账”区域：功能开关、图片权限、Cloud Vision 状态。
- 分享图片进入 App 后显示识别状态。
- 成功后进入待确认箱，并标记来源“截图识别”。
- 多笔识别可一次生成多条候选。

## 测试

```bash
gradle :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

新增测试至少覆盖：

- screenshot path detector；
- SHA-256 dedup；
- Cloud response 单笔/多笔/空结果；
- candidate/evidence/event 同事务写入；
- Room v1 -> v2 migration；
- ShareReceiver 图片路径透传；
- 权限未授予时的安全降级。

## 完成条件

实现完成后仅提交到 `feature/smart-bill-capture` 并创建 PR；CI 全绿后仍不自动合并 `main`。
