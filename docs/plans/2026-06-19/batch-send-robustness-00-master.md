# 批量发送健壮性改造：SMTP 连接复用 + 错误分级 + 流式快照 + 每日计数重置 + 退信监控 + 速率动态调节 + 事务修正（主计划）

> 本主计划是「批量发送健壮性改造」的总纲。针对现有批量发送流程在**稳定性、可伸缩性、邮件投递质量**方面的 7 项改进点，按 create-p 规范拆分为可独立部署、独立验收的子计划。本文件承载**共享的现状审计与关键不变量**，各子计划按编号引用。

## 需求描述

可观察结果（运维/技术视角）：
1. **SMTP 连接复用**：批量发送不再每封邮件新建 SMTP 连接，按账号缓存 `JavaMailSenderImpl` 实例，减少连接建立/销毁开销。
2. **SMTP 错误分级处理**：区分 4xx（暂时性）和 5xx（永久性）错误，永久性错误标记收件人不再重试，暂时性错误触发账号级限流/暂停。
3. **流式快照**：`buildSnapshot()` 不再一次性加载全部候选到内存，改为按轮分页拉取，降低大候选池场景下的内存压力。
4. **每日计数自动重置**：`todaySentCount` 在每天零点自动重置，同时重置因限额耗尽而暂停的账号 `autoSendPaused` 状态。
5. **退信监控**：通过 IMAP 收集退信（Bounce），统计退信率，退信率过高时自动暂停对应发送账号以保护域名信誉。
6. **发送速率动态调节**：从统一 `perMailIntervalMs` 改为按账号维护独立间隔，收到 421/452 限流时自动拉长该账号间隔。
7. **事务粒度修正**：修复 `InitialOutreachService.sendInitialBatch()` 整方法 `@Transactional` 导致的"SMTP 超时回滚但邮件已发出"不一致风险。

不可改变（必须保留）：
- 主计划 `2026-06-18-scheduled-batch-send-00-master.md` 定义的全部不变量（I-1 ~ I-9）保持不变。
- 防重语义（I-7）：ES `operatorStatus=CONTACTED`、`mail_send_attempt` UNIQUE upsert、`hasSentIntroduction` 双检。
- 状态机通过 `ConversationStateService.transition(...)` 流转。
- 模拟账号 `SIMULATOR_NOOP` 永不参与真实发送。
- 账号「可发送」判定口径（I-3）不变。

不做（明确推迟）：
- SPF/DKIM/DMARC 配置与验证（DNS 层面改造）。
- 邮件内容个性化（当前是固定模板，暂不改）。
- 多活动并存 / 分布式部署。
- Self-check 探针改为外部验证地址（当前 SMTP 自发探针保留）。

## 现状审计

### SMTP 发送层
- `SmtpMailDeliveryService.send()`：每次调用 `new JavaMailSenderImpl()`，建立 SMTP 连接、认证、发信、关闭。批量场景下重复建立连接，性能差且容易触发邮件服务商连接频率限制。
- `DefaultSelfCheckProbeSender.sendProbe()`：同样的问题，每次探针新建连接。
- `send()` 返回 `DeliveredMail(status="SENT")`，SMTP 抛异常时上层 catch 统一记 FAILED，不区分错误类型。

### 快照构建
- `ManualInitialOutreachService.buildSnapshot()`：调用 `expertSearchService.scrollExpertsFiltered()` 全量滚动加载候选到 `List<Pair<ExpertContact?, ExpertProfile>>`。如果候选池有 5 万条，整个 List 驻留内存。

### 每日计数
- `todaySentCount` 存储在 `mail_sender_account.today_sent_count`，仅有手动 API `resetTodaySentCount(accountCode)` 可重置。无自动按天重置机制。
- 跨午夜运行的批次：计数不清零 → 账号被判满（假阳性）；手动清零后 → 当日已发量遗忘（超限风险）。

### 退信
- 当前完全无退信收集/统计/告警机制。`ImapMailReceiveService` 只处理业务回复（专家回信），不识别退信。

### 速率控制
- `perMailIntervalMs` 是全局配置值（`batch_send_setting`），所有账号统一间隔。不同邮件服务商容忍度差异大。

### 事务
- `InitialOutreachService.sendInitialBatch()`：整方法 `@Transactional`。循环内调用 `mailDeliveryService.send()`（SMTP 网络 IO），如果某封超时导致异常，前面成功的 `expertContactRepository.save()` 和 `mailRecordRepository.save()` 全部回滚，但邮件已发出。
- `ManualInitialOutreachService.runScheduledBatch()` 无 `@Transactional`，通过 `txHelper.recordSuccess()` 拆分事务，已正确处理。

### 数据库
- 最大迁移版本号：V28。

## 关键不变量（全特性共享）

### Invariant R-1：连接池语义安全
- 规则：缓存的 `JavaMailSenderImpl` 实例必须是**线程安全**的（`JavaMailSenderImpl` 本身是线程安全的）。缓存 key 为 `accountCode`，账号 SMTP 配置变更时必须清除对应缓存条目。账号禁用/删除时清除。
- 适用于：子计划 01（SMTP 连接复用）。
- 违反后果：用旧配置发信 / 连接泄漏。

### Invariant R-2：错误分级不改变已有的成功路径
- 规则：SMTP 返回 250（无异常）的处理逻辑完全不变。只在异常路径增加分类。错误分类结果通过 `DeliveredMail` 返回值传递，不改变方法签名的兼容性。
- 适用于：子计划 02（错误分级）。
- 违反后果：回归已有功能。

### Invariant R-3：流式快照与防重等价
- 规则：分页拉取的快照必须保持与原全量快照相同的防重语义：同一 `orcidId` 在整个运行过程中至多处理一次。`seenOrcids` 集合跨页保持。
- 适用于：子计划 03（流式快照）。
- 违反后果：重复发信。

### Invariant R-4：每日重置的原子性
- 规则：重置 `todaySentCount` 必须基于 `last_sent_at` 日期判断（而非固定时间点触发），避免时区问题和调度延迟导致误重置。重置必须在事务内完成。重置同时解除因 `DAILY_LIMIT_EXHAUSTED` 暂停的账号。
- 适用于：子计划 04（每日计数重置）。
- 违反后果：计数错乱 / 超限发送。

### Invariant R-5：退信判定准确性
- 规则：退信识别必须基于标准 RFC 3464 DSN 格式或常见退信主题/发件人模式。不可将正常业务回复误判为退信。退信记录必须关联到原始发送的 `expertContact`。
- 适用于：子计划 05（退信监控）。
- 违反后果：误暂停账号 / 漏统计退信。

### Invariant R-6：速率调节不突破 I-6 定量约束
- 规则：动态调节只改变间隔（变慢），不会减少间隔突破配置下限。总量约束（`dailyCap`、`dailySendLimit`）不受影响。
- 适用于：子计划 06（速率动态调节）。
- 违反后果：超量发送。

## 子计划拆分

| 编号 | 名称 | 核心改动 | 可独立部署 |
|------|------|----------|-----------|
| 01 | SMTP 连接复用 | `SmtpMailDeliveryService` 引入按账号缓存 | ✅ |
| 02 | SMTP 错误分级 | `DeliveredMail` 增加错误类型，编排器按类型处理 | ✅ |
| 03 | 流式快照 | `buildSnapshot()` 改为延迟分页拉取 | ✅ |
| 04 | 每日计数自动重置 | 定时任务 + `last_sent_at` 日期判断 | ✅ |
| 05 | 退信监控 | IMAP 退信收集 + 统计 + 自动暂停 | ✅ |
| 06 | 速率动态调节 + 事务修正 | 按账号间隔 + 421 限流检测 + InitialOutreachService 事务修正 | ✅ |
