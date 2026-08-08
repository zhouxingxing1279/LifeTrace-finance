# 隐私与安全模型

- Access Token：仅进程内存。
- Refresh Token：Android Keystore AES/GCM；SharedPreferences 只保存密文和 IV。
- Password：仅登录请求瞬时存在。
- Release Cloud URL 强制 HTTPS；Debug 可为本地联调启用 HTTP。
- 不记录 Authorization、密码、Token 或完整通知原文。
- 诊断日志为本地环形上限（最近 1000 条），导出内容经过脱敏。
- `android:allowBackup=false`，避免凭据/数据库通过系统备份产生不明确恢复语义；完整业务恢复依赖 Cloud Snapshot。
- AccessibilityService 未加入 manifest，也不是首版依赖。
