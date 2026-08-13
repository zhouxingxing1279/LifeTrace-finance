import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('removed promotional entries stay absent from client surfaces', () {
    final minePage = File('lib/pages/main/mine_page.dart').readAsStringSync();
    final accountsPage =
        File('lib/pages/account/accounts_page.dart').readAsStringSync();
    final aboutPage =
        File('lib/pages/settings/about_page.dart').readAsStringSync();
    final productPromos =
        File('lib/services/marketing/product_promos.dart').readAsStringSync();

    expect(minePage, isNot(contains('mineShareApp')));
    expect(minePage, isNot(contains('mineCopyPromoText')));
    expect(accountsPage, isNot(contains('beeAssetsPromo')));
    expect(aboutPage, isNot(contains('beeAssetsPromo')));
    expect(productPromos, isNot(contains('beeAssetsPromo')));

    for (final asset in <String>[
      'assets/images/beeassets_logo.png',
      'assets/images/beeassets_logo.svg',
      'assets/images/beeassets_dashboard.png',
      'assets/images/beeassets_dashboard_en.png',
      'assets/images/beeassets_holdings.png',
      'assets/images/beeassets_holdings_en.png',
    ]) {
      expect(File(asset).existsSync(), isFalse, reason: asset);
    }
  });

  test('business poster sharing and the remaining product stay available', () {
    final homePage = File('lib/pages/main/home_page.dart').readAsStringSync();
    final annualReport =
        File('lib/pages/report/annual_report_page.dart').readAsStringSync();
    final productPromos =
        File('lib/services/marketing/product_promos.dart').readAsStringSync();

    expect(homePage, contains('SharePosterService.showPosterCarouselPreview'));
    expect(annualReport, contains('SharePosterService.sharePoster'));
    expect(productPromos, contains('beeDnsPromo'));
  });
}
