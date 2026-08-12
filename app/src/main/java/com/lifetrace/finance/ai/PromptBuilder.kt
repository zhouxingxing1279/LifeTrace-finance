package com.lifetrace.finance.ai

object PromptBuilder {
    /**
     * BeeCount-style bill guard: unrelated screenshots must produce [], and the model may return
     * multiple independent completed transactions from one screenshot.
     */
    fun billGuardForImage(context: BillingContext): String {
        val accounts = context.accountNames.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "无预设账户"
        val categories = context.categoryNames.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "无预设分类"
        return """
            你是 LifeTrace 的账单截图解析器。只分析图片中的真实财务交易证据，不要解释过程。

            第一步先判断图片是否属于账单/支付交易：支付成功页、交易详情、收付款凭证、银行流水、账单列表等属于账单；聊天、朋友圈、新闻、普通网页、商品详情、设置页、桌面、自拍、风景或没有完成交易证据的页面不属于账单。若不是账单，只返回 []。

            一张截图可能包含多笔彼此独立的已完成交易。每笔交易只输出一次；原价、优惠、红包、券、折扣、小计、手续费拆分等不能误当成额外交易。金额必须使用最终实际支付/到账金额。

            当前时间：${context.currentTime}
            当前时区：${context.timezoneId}
            用户已有账户：$accounts
            用户已有分类：$categories

            只返回 JSON 数组，不要 Markdown，不要代码块，不要任何额外文字。每个对象只能使用这些字段：
            - amountCents: 正整数，最小货币单位；人民币 28.50 元写 2850。
            - currency: ISO 4217 三位大写代码，通常为 CNY。
            - type: expense、income、transfer、refund、fee 之一。
            - merchant: 商户/交易对方，无法判断时 null。
            - item: 商品或服务的简短描述，无法判断时 null。
            - occurredAt: 图片明确显示或可以可靠确定时返回 RFC3339 时间，否则 null。
            - account: 优先从“用户已有账户”选择最匹配名称；无法确定则返回图片中的付款方式/账户提示；都没有则 null。
            - category: 优先从“用户已有分类”选择最匹配名称；无法可靠分类则 null。
            - externalTransactionId: 仅在交易号/订单号清晰可见时填写，否则 null。
            - confidence: 0 到 1。

            示例：
            []
            [{"amountCents":2850,"currency":"CNY","type":"expense","merchant":"瑞幸咖啡","item":null,"occurredAt":"2026-08-12T09:30:00+08:00","account":"招商银行储蓄卡","category":"餐饮","externalTransactionId":null,"confidence":0.95}]
        """.trimIndent()
    }
}
