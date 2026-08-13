// BeeCount 家族产品推广信息的中央注册表。
//
// 所有用 `ProductPromoCard` / `ProductPromoCompact` / `ProductPromoLauncher`
// 的页面都从这里取数据,避免 logoAsset / appStoreId / 邮箱地址 / 域名等关键
// 字段在多处复制。新增产品 / 改 App ID / 调整域名,只改这一处。
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../widgets/biz/product_promo_card.dart';

/// 蜜蜂域名 BeeDNS — DNS 管理工具。
ProductPromo beeDnsPromo(BuildContext context) {
  final l10n = AppLocalizations.of(context);
  return ProductPromo(
    logoAsset: 'assets/images/beedns_logo.png',
    title: l10n.aboutBeeDNS,
    subtitle: l10n.aboutBeeDNSSubtitle,
    introBody: l10n.aboutBeeDNSIntro,
    // 琥珀橙
    brandColor: const Color(0xFFF59E0B),
    appStoreId: '6757992815',
    // googlePlayUrl: 'https://play.google.com/store/apps/details?id=com.tntlikely.beedns',
    websiteUrl: 'https://dns.beejz.com',
    contactEmail: 'sunxiaoyes@outlook.com',
    // BeeDNS 截图暂不展示;有合适的产品截图后填这里:
    // screenshotAssets: const ['assets/images/beedns_xxx.png'],
  );
}

/// 标准介绍弹窗文案构造器(每个产品共用一套通用文案,产品自己的介绍文案
/// 在 [ProductPromo.introBody] 里)。
ProductPromoTexts buildPromoTexts(BuildContext context, String productName) {
  final l10n = AppLocalizations.of(context);
  return ProductPromoTexts(
    betaDialogTitle: l10n.productPromoAndroidTitle,
    betaDialogMessage: l10n.productPromoAndroidMessage,
    emailLabel: l10n.productPromoEmailLabel,
    copiedToast: l10n.productPromoCopiedToast,
    mailUnavailableToast: l10n.productPromoMailUnavailable,
    emailButton: l10n.productPromoEmailButton,
    websiteButton: l10n.productPromoWebsiteButton,
    openStoreButton: l10n.productPromoOpenStore,
    testFlightButton: l10n.productPromoTestFlight,
    emailSubject: l10n.productPromoEmailSubject(productName),
    emailBody: l10n.productPromoEmailBody(productName),
  );
}
