# BeeCount 客户端上游引入记录

## 目的

`LifeTrace-finance` 现在直接以 BeeCount 的完整 Flutter Android 客户端作为根工程，后续按产品需求逐项删减或替换功能。此前的原生 Kotlin/Compose 客户端已从当前工作树删除，仍可通过 Git 历史恢复。

## 上游来源

- 上游项目：`TNT-Likely/BeeCount`
- 当前镜像仓库：`zhouxingxing1279/BeeCount`
- 引入提交：`70c800ba7c726aecf55f2f0936bb522c0c69aace`
- 提交时间：2026-08-12 20:27:02 +0800
- 上游作者：sunxiao（GitHub: `TNT-Likely`）

## 引入范围

完整保留 Android 构建所需的 Flutter 源码和资源：

- `android/`
- `assets/`
- `lib/`
- `packages/`
- `scripts/`
- `test/`
- `tool/`
- Flutter 工程配置、依赖锁文件和本地化配置
- 原始 README、贡献说明、隐私说明、许可证、商业许可说明和第三方声明

未引入与 Android 客户端构建无关的 `ios/`、演示视频、商店预览图片、上游 CI 配置和上游 Git 历史。上游 Git 提交号已固定在本文件和根目录 `UPSTREAM.md` 中，便于追溯及后续同步。

## 许可证与署名

BeeCount 使用自定义非商业许可。任何修改和分发都必须保留 `LICENSE`、`LICENSE_EN`、作者信息、版权标识和第三方声明。当前项目按个人、非商业和公开源码用途引入；不得把本目录改造成商业产品，除非另行获得原作者的商业授权。

## 后续改造原则

1. 未经明确指定，不删除 BeeCount 功能或版权信息。
2. LifeTrace 后端兼容通过独立适配层实现，不直接破坏 BeeCount 本地数据模型。
3. 金额进入 LifeTrace 接口时转换为整数分。
4. 外部交易号保持唯一，重复导入不得重复入账。
5. AI 识别结果先进入候选账单，用户确认后再生成正式账单。
6. 每一批删减或替换完成后运行 Flutter 静态分析、单元测试和 Android 构建，全部通过后才允许合并到 `main`。

## 已批准删减

### 2026-08-13：推广入口

- 删除“我的”页的“分享应用”入口。
- 删除“我的”页的“复制推广文案”入口。
- 删除 BeeAssets（蜜蜂家当）在资产页头部和“关于 → 更多产品”中的入口。
- 删除 BeeAssets 的推广注册、本地化文案、Logo 和产品截图资源。
- 保留首页、统计、年度报告等业务海报分享能力。
- 保留 BeeDNS 产品卡片和通用产品推广组件。
