# LifeTrace Finance Android 智能截图记账执行方案

## 目标

把智能记账能力完全放在 Android 本地：

- 复用现有 `NotificationCaptureService`、`FinanceRepository`、候选账单、证据、Outbox 与 Sync Engine；
- 系统分享图片与可选截图监听都进入同一个本地识别流水线；
- 使用 ML Kit Text Recognition v2 中文模型做设备端 OCR；
- 使用本地 `LocalBillParser` 把 OCR 文本转换成结构化候选账单；
- 原始截图、OCR 全文不上传 Cloud；
- Cloud、Desktop、Browser 只接收最终同步的结构化财务数据。

## 流程

```text
系统分享图片 / 新截图
        |
        v
SmartCaptureCoordinator
        |
        +-- screenshot guard
        +-- SHA-256 dedup
        |
        v
ML Kit Chinese OCR (on-device)
        |
        v
LocalBillParser
        |
        v
category/account hint matcher
        |
        v
FinanceRepository.captureLocalOcrCandidates
        |
        +-- finance_transactions
        +-- finance_transaction_evidence
        +-- smart_capture_events
        +-- sync_outbox
        |
        v
待确认箱 -> Sync
```

## Room v2

新增 `smart_capture_events`：

- `id`
- `local_profile_id`
- `image_hash`
- `capture_source`
- `engine`
- `status`
- `captured_at`
- `transaction_ids_json`
- `error_code`

提供显式 `MIGRATION_1_2`，保留所有 v1 数据。

## Share Receiver

现有图片分享逻辑只创建占位文本；本次改为：

1. 将共享图片缓存到 app cache；
2. 把路径传给主界面；
3. `SmartCaptureCoordinator` 读取并 OCR；
4. 解析成功后写候选账单；
5. 无论成功失败均删除临时图片。

## Screenshot Monitor

- `ContentObserver` 监听 `MediaStore.Images`。
- 仅处理最近新增媒体。
- 路径/名称命中 `screenshot`、`截屏`、`截图`、`screen_shot`、`screen shot`。
- 图片 SHA-256 + `smart_capture_events` 唯一约束避免重复入账。
- 用户显式打开开关后注册 observer；关闭后注销。
- Android 13+ 使用 `READ_MEDIA_IMAGES`；Android 12 及以下按系统版本使用媒体读取权限。
- 第一版不做永久前台服务，系统分享路径作为可靠兜底。

## LocalBillParser

第一版重点覆盖微信、支付宝和常见银行卡支付截图 OCR 文本：

- 先判断是否存在支付成功/交易详情/退款/收款等财务证据；
- 金额解析为整数分，禁止浮点入库；
- 支持 `expense` / `income` / `refund` / `transfer` / `fee`；
- 商户、商品、支付方式、时间只在 OCR 文本有明确证据时填写；
- 分类使用现有 `CategoryClassifier`；
- 无法判断字段留空；
- 低置信度进入 `candidate`，由用户确认。

## 隐私

- 图片不离开设备；
- Room 不保存原图；
- 不把 OCR 全文写入 diagnostic log；
- evidence 只保存本地 engine、图片 hash 派生 source id、confidence 等最小元数据；
- Cloud 只看到正常 `finance.transaction` / `finance.transaction_evidence`。

## 后续可选增强

在支持设备端生成式 AI 的机型上，可增加 Gemini Nano / ML Kit Prompt API 作为 OCR 后语义消歧层，但不能取代硬校验与确定性解析器，也不能让不支持该能力的设备失去基本记账功能。

## 测试

```bash
gradle :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

新增测试覆盖：

- 微信/支付宝 OCR fixture；
- 非账单 fixture；
- amount/type/merchant/account hint 解析；
- screenshot path detector；
- hash dedup；
- candidate/evidence/event 同事务写入；
- Room v1 -> v2 migration；
- ShareReceiver 图片路径透传；
- 权限未授予时安全降级。

## 完成条件

实现完成后仅提交到 `feature/smart-bill-capture` 并创建 PR；CI 全绿后仍不自动合并 `main`。
