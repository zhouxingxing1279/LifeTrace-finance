# Android Sync 状态机

```text
LOCAL WRITE
  → OUTBOX PENDING
  → WorkManager(network connected)
  → Capabilities
  → Snapshot(if required)
  → Push
      accepted/duplicate → ack + serverVersion
      conflict → sync_conflicts + ack
      401 → single-flight refresh → retry once
      413 → shrink batch
      429 → Retry-After
  → Pull(cursor)
      page apply transaction → advance cursor
      apply failure → cursor unchanged
      snapshot required → Snapshot → stop page loop
```

冲突不使用 Last-Write-Wins。用户可“保留本地”（以最新 remote serverVersion 新建 Outbox）或“采用云端”（Remote Apply）。
