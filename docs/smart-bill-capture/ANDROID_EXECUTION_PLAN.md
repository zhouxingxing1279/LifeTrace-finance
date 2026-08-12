# LifeTrace Finance Android 智能截图记账执行方案

## 1. 目标与边界

本功能尽量保持 BeeCount 当前截图记账架构的职责拆分，但使用 LifeTrace 原生 Kotlin/Compose 技术栈重新实现，不复制 BeeCount 源码。

唯一有意调整的系统边界：**账单图片识别全部由 Android 客户端直接调用 Vision API 完成**。LifeTrace Cloud、Desktop、Browser 不接触原始截图，也不承担 Vision Provider 代理。

默认 AI 配置与 BeeCount 保持一致：

- Provider ID：`zhipu_glm`
- Base URL：`https://open.bigmodel.cn/api/paas/v4`
- Vision Model：`glm-4v-flash`
- API Key：用户自行配置，使用 Android Keystore 加密，仅保存在手机

同时保留 Base URL / Vision Model 可配置能力，以兼容 OpenAI-style Vision Provider。

## 2. BeeCount 模块映射

```text
BeeCount                              LifeTrace Finance Android
--------------------------------------------------------------------------
ScreenshotObserver.kt             -> platform/ScreenshotObserver.kt
ScreenshotMonitorService          -> platform/ScreenshotMonitorService.kt
ImageShareHandlerService          -> platform/ShareReceiverActivity.kt
AutoBillingConfig                 -> automation/AutoBillingConfig.kt
AutoBillingService                -> automation/AutoBillingService.kt
AiBookkeeper                      -> ai/AiBookkeeper.kt
DefaultAiExtractionEngine         -> ai/DefaultAiExtractionEngine.kt
AIProviderFactory.vision          -> ai/AiProviderFactory.kt
AIServiceProviderConfig           -> ai/AiModels.kt + AiSettingsStore.kt
PromptBuilder.billGuardForImage   -> ai/PromptBuilder.kt
JsonResponseParser                -> ai/JsonResponseParser.kt
BillCreationService               -> automation/BillCreationService.kt
processed screenshot memory       -> automation/ProcessedImageStore.kt
```

LifeTrace 额外增加 `PendingShareStore`，仅用于“用户已经分享截图但尚未配置 Vision Key”时在 App 私有存储中暂存临时文件路径。该路径不会通过 exported Activity 的 Intent extra 传递。

## 3. 完整调用链

```text
微信 / 支付宝 / 银行截图
        |
        +------------------------------+
        |                              |
系统分享图片                    MediaStore 新截图
        |                              |
ShareReceiverActivity          ScreenshotObserver
        |                              |
        +--------------+---------------+
                       |
                       v
              AutoBillingService
                       |
          +------------+------------+
          |                         |
    文件就绪等待                 SHA-256 去重
          |                         |
          +------------+------------+
                       |
                       v
                  AiBookkeeper
                       |
                       v
           DefaultAiExtractionEngine
                       |
              PromptBuilder bill guard
                       |
                       v
              AiProviderFactory.vision
                       |
          Android -> Vision API 直连
                       |
                       v
                JsonResponseParser
                       |
                     BillInfo[]
                       |
                       v
               BillCreationService
          +------------+-------------+
          |                          |
   account/category 匹配       通知 candidate 对账
          |                          |
          +------------+-------------+
                       |
                       v
 finance.transaction(candidate/provisional)
                       |
                       v
                 existing Outbox
                       |
                       v
          LifeTrace Cloud Sync
                       |
             Desktop / Browser 查看
```

## 4. 图片入口

### 4.1 系统分享

`ShareReceiverActivity` 继续作为可靠兜底入口：

1. 接收 `image/*`；
2. 把共享 URI 临时缓存到 app cache；
3. 若 Vision 尚未配置，把临时路径写入 App 私有 `PendingShareStore`，再进入 `AiSettingsActivity`；
4. 用户保存 API Key 后从 `PendingShareStore` 一次性 consume 并自动继续识别原截图；
5. 已配置时直接交给 `AutoBillingService`；
6. 处理完成后删除临时图片；
7. 打开待确认箱查看识别结果。

`PendingShareStore` 只保存一个待处理路径；新的待处理截图会删除被覆盖的旧临时文件，显式 clear 也会删除缓存文件，避免长期遗留。

分享入口不需要 `READ_MEDIA_IMAGES`，因此即使用户拒绝媒体读取权限仍可使用。

### 4.2 自动截图监听

`ScreenshotMonitorService` 使用 `ContentObserver + MediaStore.Images`：

- 用户显式开启后注册；关闭立即注销；
- `MainActivity` 启动时只恢复之前已启用且权限仍有效的监听；
- Android 13+ 使用 `READ_MEDIA_IMAGES`；Android 12 及以下使用 `READ_EXTERNAL_STORAGE`；
- 第一版不使用永久前台服务，进程被系统回收后不承诺持续监听；
- 系统分享始终作为可靠 fallback。

`ScreenshotObserver` 的筛选规则与 BeeCount 对齐：

- `screenshot`
- `截屏`
- `截图`
- `screen_shot`
- `screen shot`

同时只接受最近约 30 秒新增媒体，并使用约 500 ms observer debounce。查询 MediaStore 时按 `DATE_ADDED DESC` 排序并读取第一条，不在 `sortOrder` 中拼接非标准 `LIMIT`。

## 5. AutoBillingService

职责：

1. 检查 Vision Provider 是否配置；
2. 等待 MediaStore / cache 文件写入完成：最长约 3 秒，每 100 ms 检查一次；
3. 读取 PNG/JPEG/WebP，最大 10 MiB；
4. 计算 SHA-256；
5. 查询 `ProcessedImageStore` 防止同一图片重复调用 AI；
6. 调用 `AiBookkeeper.fromImage()`；
7. 非账单返回空数组时记为已处理，不创建交易；
8. 有账单时调用 `BillCreationService`；
9. 成功后触发现有 `SyncScheduler`；
10. diagnostic log 只记录 provider/model、数量、错误类型，不记录 API Key、图片 Base64 或模型完整原始响应。

`ProcessedImageStore` 使用本地 SharedPreferences 保存最多 200 个图片哈希，不增加 Room schema。

## 6. AI 层

### 6.1 Provider 配置

`AiSettingsStore` 保存非敏感配置：

- `providerId`
- `baseUrl`
- `visionModel`
- `screenshotMonitorEnabled`

`AiSecretStore` 单独保存 API Key：

- AES/GCM
- Key 由 Android Keystore 管理
- 不进入 Room
- 不进入 Outbox
- 不进入 LifeTrace Cloud

### 6.2 AiProviderFactory

`AiProviderFactory.vision()` 使用 OkHttp 直接从 Android 请求：

`{baseUrl}/chat/completions`

请求包含：

- Bearer API Key
- `visionModel`
- 图片 Base64
- `PromptBuilder.billGuardForImage()` 生成的指令

实际调用由 `AutoBillingService` 的 `Dispatchers.IO` 协程域发起，阻塞式 OkHttp 不占用主线程。

第一版只实现本项目需要的 Vision capability，但 Provider 配置结构保留 BeeCount 的可扩展边界。

### 6.3 PromptBuilder

账单守卫要求模型：

- 先判断图片是否包含真实已完成财务交易；
- 聊天、文章、商品详情、桌面、设置页、自拍等返回 `[]`；
- 支持一张截图识别多笔独立交易；
- 原价、优惠、红包、优惠券、小计等不得重复计账；
- 使用最终实付/到账金额；
- 返回严格 JSON 数组；
- 把当前 LifeTrace 账户名和分类名作为候选上下文提供给模型；
- 支持 `expense / income / transfer / refund / fee`；
- transfer 使用 `fromAccount / toAccount`。

### 6.4 JsonResponseParser

容错责任与 BeeCount 相同：

- 支持 Markdown code fence；
- 支持数组或单对象；
- 提取首个平衡 JSON block；
- 清理 trailing comma；
- `amountCents <= 0` 丢弃；
- 未知 transaction type 丢弃；
- currency 非法时回退 CNY；
- confidence 限制在 `[0, 1]`；
- RFC3339 时间转换为 `Instant`；
- 非账单 `[]` 不创建任何交易。

## 7. BillCreationService

AI 只提供人类可读 hint，不直接决定 durable entity ID。

### Account

匹配顺序：

1. 标准化后完全匹配；
2. 名称包含匹配；
3. 卡号尾四位；
4. 微信/支付宝账户类型 fallback。

### Category

匹配顺序：

1. AI 返回分类名完全匹配；
2. 分类名包含匹配；
3. 复用现有 `CategoryClassifier` 关键词规则；
4. 无可靠结果则保留未分类，进入待确认箱。

### Status

- `confidence >= 0.90` -> `provisional`
- 其余 -> `candidate`
- 两种状态都进入现有待确认箱，由用户最终确认

### 通知候选对账

LifeTrace 已有通知捕获，因此截图识别成功后会检查：

- `sourceType == notification`
- candidate/provisional 状态
- 金额相同
- 时间差不超过 5 分钟

匹配后保留信息更完整的截图账单，并把对应通知 candidate 标记为 ignored，避免同一消费重复统计。

若图片明确包含 `externalTransactionId`，已有相同 external ID 的有效交易不会再次创建。

## 8. Desktop / Browser / Cloud

本次不增加：

- Cloud Vision API
- Cloud Provider Key
- Desktop 图片识别
- Browser 图片识别
- 图片上传或图片持久化

Android 识别完成后仍通过现有 `finance.transaction` + Outbox/Sync 进入 Cloud；Desktop/Browser 只负责查看、修改、统计现有财务数据。

## 9. 测试与发布门禁

现有 PR CI 作为正式门禁：

```bash
gradle --no-daemon :core:test
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:lintDebug
gradle --no-daemon :app:assembleDebug
gradle --no-daemon :app:assembleRelease
gradle --no-daemon :app:connectedDebugAndroidTest
```

新增测试覆盖：

- bill guard 包含账户/分类上下文和非账单空数组要求；
- fenced JSON / trailing comma；
- 单对象与多笔数组；
- amount/type/confidence/time 校验；
- transfer `fromAccount/toAccount`；
- 非账单 `[]`；
- BeeCount 风格截图名称/路径检测。

## 10. 合并策略

开发分支：`feature/smart-bill-capture`

PR：`LifeTrace-finance#4`

流程：

1. 完成代码；
2. 更新本执行文档与主仓库总体文档；
3. PR CI 全部通过；
4. 复核最终 diff 不包含 OCR / Cloud Vision；
5. 合并到 `main`。
