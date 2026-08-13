#!/usr/bin/env dart
/// BeeCount 国际化翻译状态检查工具
///
/// 功能：
/// 1. 检查各语言翻译文件的完整性和状态
/// 2. 检查各语言文件中多余的 key
/// 3. 检测未使用的翻译 key
/// 4. 提供清理选项
///
/// 使用方法：
/// dart scripts/i18n/check_status.dart

import 'dart:io';
import 'dart:convert';

void main() async {
  print('');
  print('=' * 70);
  print('  BeeCount 国际化翻译状态检查');
  print('=' * 70);
  print('');

  // ========== 第一部分：检查翻译完整性 ==========
  await checkTranslationCompleteness();

  print('');
  print('─' * 70);
  print('');

  // ========== 第二部分：检查各语言多余的 keys ==========
  final extraKeysMap = await checkExtraKeys();

  if (extraKeysMap.isNotEmpty) {
    print('');
    print('─' * 70);
    print('');

    // 询问是否清理多余的 keys
    print('⚠️  是否要清理这些多余的 keys？(y/N): ');
    final confirm = stdin.readLineSync()?.toLowerCase();

    if (confirm == 'y' || confirm == 'yes') {
      await cleanExtraKeys(extraKeysMap);
      print('');
      print('─' * 70);
      print('');
    } else {
      print('❌ 已取消清理多余 keys 的操作');
      print('');
      print('─' * 70);
      print('');
    }
  }

  // ========== 第三部分：检查未使用的 keys ==========
  final unusedKeys = await checkUnusedKeys();

  if (unusedKeys.isNotEmpty) {
    print('');
    print('─' * 70);
    print('');

    // 询问是否清理未使用的 keys
    print('⚠️  是否要清理这些未使用的 keys？(y/N): ');
    final confirm = stdin.readLineSync()?.toLowerCase();

    if (confirm == 'y' || confirm == 'yes') {
      await cleanUnusedKeys(unusedKeys);
    } else {
      print('❌ 已取消清理未使用 keys 的操作');
    }
  }

  print('');
  print('=' * 70);
  print('');
}

/// 检查翻译完整性
Future<void> checkTranslationCompleteness() async {
  final l10nDir = Directory('lib/l10n');
  final languages = ['zh', 'en', 'zh_TW'];

  print('📊 第一步：检查翻译文件完整性');
  print('');

  // 存储每个语言的键信息
  final Map<String, int> keyCount = {};
  final Map<String, Set<String>> allKeys = {};

  // 读取所有语言文件
  for (final lang in languages) {
    final file = File('${l10nDir.path}/app_$lang.arb');

    if (!file.existsSync()) {
      print('⚠️  文件不存在: app_$lang.arb');
      keyCount[lang] = 0;
      allKeys[lang] = {};
      continue;
    }

    try {
      final content = await file.readAsString();
      final Map<String, dynamic> data = json.decode(content);
      final keys = data.keys.where((key) => !key.startsWith('@')).toSet();

      keyCount[lang] = keys.length;
      allKeys[lang] = keys;
    } catch (e) {
      print('❌ 解析失败: app_$lang.arb - $e');
      keyCount[lang] = 0;
      allKeys[lang] = {};
    }
  }

  // 以中文为基准
  final zhKeys = allKeys['zh'] ?? {};
  final zhCount = zhKeys.length;

  // 打印统计表格
  print('语言代码 | 文件名称        | 键数量   | 完成度   | 状态');
  print('-' * 70);

  final languageNames = {
    'zh': '简体中文',
    'en': 'English',
    'zh_TW': '繁體中文',
  };

  for (final lang in languages) {
    final count = keyCount[lang] ?? 0;
    final name = languageNames[lang] ?? lang;
    final percentage =
        zhCount > 0 ? (count / zhCount * 100).toStringAsFixed(1) : '0.0';

    String status;
    if (count == 0) {
      status = '❌ 缺失';
    } else if (count >= zhCount) {
      status = '✅ 完整';
    } else if (count >= zhCount * 0.9) {
      status = '⚠️  接近完成';
    } else {
      status = '🔴 不完整';
    }

    final langCode = lang.padRight(8);
    final fileName = 'app_$lang.arb'.padRight(15);
    final countStr = count.toString().padLeft(7);
    final percentStr = '$percentage%'.padLeft(8);

    print('$langCode | $fileName | $countStr | $percentStr | $status');
  }

  print('-' * 70);
  print('');

  // 详细差异分析
  print('📋 详细分析:');
  print('');

  bool hasIssues = false;

  for (final lang in languages) {
    if (lang == 'zh') continue; // 跳过基准语言

    final langKeys = allKeys[lang] ?? {};
    final missing = zhKeys.difference(langKeys);

    if (missing.isEmpty) {
      print('✅ $lang (${languageNames[lang]}): 完全匹配中文版本');
    } else {
      hasIssues = true;
      print('🔴 $lang (${languageNames[lang]}): 缺少 ${missing.length} 个键');
      if (missing.length <= 10) {
        for (final key in missing.take(10)) {
          print('   - $key');
        }
      }
    }
    print('');
  }

  // 检查空值
  print('🔍 检查空值翻译:');
  print('');

  bool hasEmptyValues = false;
  for (final lang in languages) {
    final file = File('${l10nDir.path}/app_$lang.arb');
    if (!file.existsSync()) continue;

    final content = await file.readAsString();
    final data = json.decode(content) as Map<String, dynamic>;
    final empty = <String>[];

    for (final entry in data.entries) {
      if (!entry.key.startsWith('@')) {
        final value = entry.value?.toString() ?? '';
        if (value.trim().isEmpty) {
          empty.add(entry.key);
        }
      }
    }

    if (empty.isNotEmpty) {
      hasEmptyValues = true;
      print('⚠️  ${languageNames[lang]} 有 ${empty.length} 个空值翻译');
      for (final key in empty.take(5)) {
        print('   - $key');
      }
      if (empty.length > 5) {
        print('   ... 还有 ${empty.length - 5} 个');
      }
      print('');
    }
  }

  if (!hasEmptyValues) {
    print('✅ 所有翻译都有值');
    print('');
  }

  // 总结
  print('📈 总结:');
  print('  基准语言: 简体中文 (zh) - $zhCount 个键');

  final complete =
      languages.where((l) => (keyCount[l] ?? 0) >= zhCount).length;
  final incomplete = languages.length - complete;

  print('  完整翻译: $complete/${languages.length} 个语言');
  print('  待完善: $incomplete 个语言');

  if (!hasIssues && !hasEmptyValues) {
    print('');
    print('🎉 所有翻译文件状态良好！');
  }
}

/// 检查各语言多余的 keys
Future<Map<String, Set<String>>> checkExtraKeys() async {
  print('🔍 第二步：检查各语言多余的 keys');
  print('');

  final l10nDir = Directory('lib/l10n');

  // 读取中文文件作为基准
  final zhFile = File('${l10nDir.path}/app_zh.arb');
  if (!zhFile.existsSync()) {
    print('❌ 找不到 app_zh.arb 文件');
    return {};
  }

  final zhContent = await zhFile.readAsString();
  final zhData = json.decode(zhContent) as Map<String, dynamic>;
  final zhKeys = zhData.keys.where((key) => !key.startsWith('@')).toSet();

  print('📊 基准文件 (app_zh.arb): ${zhKeys.length} 个键');
  print('');

  // 支持的语言列表 (排除中文)
  final languages = ['en', 'zh_TW'];

  // 收集每个语言的多余键
  final Map<String, Set<String>> extraKeysMap = {};

  for (final lang in languages) {
    final file = File('${l10nDir.path}/app_$lang.arb');
    if (!file.existsSync()) {
      print('⚠️  跳过不存在的文件: app_$lang.arb');
      continue;
    }

    final content = await file.readAsString();
    final data = json.decode(content) as Map<String, dynamic>;
    final keys = data.keys.where((key) => !key.startsWith('@')).toSet();

    // 找出多余的键
    final extraKeys = keys.difference(zhKeys);

    if (extraKeys.isNotEmpty) {
      extraKeysMap[lang] = extraKeys;
    }
  }

  if (extraKeysMap.isEmpty) {
    print('✅ 没有发现多余的键！');
    return {};
  }

  // 显示所有多余的键
  print('═══════════════════════════════════════════════════════════════');
  print('📋 发现以下语言有多余的键：');
  print('');

  final languageNames = {
    'en': 'English',
    'zh_TW': '繁體中文',
  };

  for (final entry in extraKeysMap.entries) {
    final lang = entry.key;
    final keys = entry.value;

    print('🔴 $lang (${languageNames[lang]}): ${keys.length} 个多余的键');
    print('─'.padRight(60, '─'));

    // 按字母排序显示
    final sortedKeys = keys.toList()..sort();
    for (var i = 0; i < sortedKeys.length && i < 10; i++) {
      print('  ${(i + 1).toString().padLeft(3)}. ${sortedKeys[i]}');
    }
    if (sortedKeys.length > 10) {
      print('  ... 还有 ${sortedKeys.length - 10} 个');
    }
    print('');
  }

  print('═══════════════════════════════════════════════════════════════');

  return extraKeysMap;
}

/// 清理各语言多余的 keys
Future<void> cleanExtraKeys(Map<String, Set<String>> extraKeysMap) async {
  print('');
  print('🔄 开始清理多余的 keys...');
  print('');

  final l10nDir = Directory('lib/l10n');
  int totalDeleted = 0;

  for (final entry in extraKeysMap.entries) {
    final lang = entry.key;
    final extraKeys = entry.value;
    final file = File('${l10nDir.path}/app_$lang.arb');

    final content = await file.readAsString();
    final data = json.decode(content) as Map<String, dynamic>;

    // 删除多余的键及其元数据
    for (final key in extraKeys) {
      data.remove(key);
      data.remove('@$key');
      totalDeleted++;
    }

    // 写回文件
    final encoder = JsonEncoder.withIndent('  ');
    final formatted = encoder.convert(data);
    await file.writeAsString('$formatted\n');

    print('  ✅ app_$lang.arb: 删除 ${extraKeys.length} 个键');
  }

  print('');
  print('✅ 清理多余 keys 完成！共删除 $totalDeleted 个键');
}

/// 检查未使用的 keys
Future<List<String>> checkUnusedKeys() async {
  print('🔍 第三步：检查未使用的翻译 key');
  print('');

  // 读取中文 arb 文件获取所有 keys
  final arbFile = File('lib/l10n/app_zh.arb');
  if (!arbFile.existsSync()) {
    print('❌ 找不到 lib/l10n/app_zh.arb 文件');
    return [];
  }

  final arbContent = await arbFile.readAsString();
  final arbData = json.decode(arbContent) as Map<String, dynamic>;

  // 获取所有非元数据的 keys
  final allKeys =
      arbData.keys.where((key) => !key.startsWith('@')).toList();

  print('📊 总共有 ${allKeys.length} 个翻译 keys');

  // 搜索 Dart 文件中的使用情况
  final libDir = Directory('lib');
  final dartFiles = <File>[];

  await for (final entity in libDir.list(recursive: true)) {
    if (entity is File && entity.path.endsWith('.dart')) {
      // 过滤掉 lib/l10n/ 目录下的生成文件
      if (!entity.path.contains('lib/l10n/')) {
        dartFiles.add(entity);
      }
    }
  }

  print('📁 扫描 ${dartFiles.length} 个 Dart 文件...');
  print('');

  final unusedKeys = <String>[];
  final usedKeys = <String>{};

  for (final key in allKeys) {
    bool isUsed = false;

    for (final file in dartFiles) {
      final content = await file.readAsString();

      // 检查各种可能的使用方式
      if (content.contains('l10n.$key') ||
          content.contains('l10n!.$key') ||
          (content.contains('AppLocalizations.of(') &&
              content.contains(').$key'))) {
        isUsed = true;
        usedKeys.add(key);
        break;
      }

      // 使用正则表达式匹配更复杂的模式
      final pattern = RegExp(r'[.\s]\??!?' + key + r'\b');
      if (pattern.hasMatch(content)) {
        isUsed = true;
        usedKeys.add(key);
        break;
      }
    }

    if (!isUsed) {
      unusedKeys.add(key);
    }
  }

  // 输出结果
  print('✅ 使用中的 keys: ${usedKeys.length}');
  print('❌ 未使用的 keys: ${unusedKeys.length}');
  print('');

  if (unusedKeys.isNotEmpty) {
    print('📝 未使用的 keys 列表：');
    print('=' * 60);
    for (var i = 0; i < unusedKeys.length && i < 20; i++) {
      final key = unusedKeys[i];
      final value = arbData[key];
      print('  • $key: "$value"');
    }
    if (unusedKeys.length > 20) {
      print('  ... 还有 ${unusedKeys.length - 20} 个');
    }
    print('=' * 60);
  } else {
    print('🎉 太好了！没有发现未使用的 keys！');
  }

  return unusedKeys;
}

/// 清理未使用的 keys
Future<void> cleanUnusedKeys(List<String> unusedKeys) async {
  print('');
  print('🔄 开始清理未使用的 keys...');
  print('');

  // 获取所有语言的 arb 文件
  final l10nDir = Directory('lib/l10n');
  final arbFiles = await l10nDir
      .list()
      .where((entity) => entity is File && entity.path.endsWith('.arb'))
      .cast<File>()
      .toList();

  for (final file in arbFiles) {
    final fileName = file.path.split('/').last;
    final content = await file.readAsString();
    final data = json.decode(content) as Map<String, dynamic>;

    // 删除未使用的 keys 及其元数据
    for (final key in unusedKeys) {
      data.remove(key);
      data.remove('@$key'); // 删除元数据
    }

    // 写回文件（格式化 JSON）
    final encoder = JsonEncoder.withIndent('  ');
    final formatted = encoder.convert(data);
    await file.writeAsString('$formatted\n');

    print('  ✓ $fileName');
  }

  print('');
  print('✅ 清理未使用 keys 完成！共删除 ${unusedKeys.length} 个键');
  print('💡 请运行 flutter gen-l10n 重新生成本地化代码');
}
