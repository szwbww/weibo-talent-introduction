# 计划 02：邮件监控新增「服务商」与「地区」维度

> 序列计划 2/2。地区维度依赖计划 1（`01-contact-country-foundation.md`）落库的 `expert_contact.country`。
> 服务商维度无 DB 依赖，可独立先行。

## 需求描述

- 可观察结果：「邮件监控」页今日全链路概览下新增两个分布面板：
  1. **按收件方服务商分布**：发送量、回复率、硬退率/软退数，按服务商桶（gmail / outlook / yahoo / edu / tencent / netease / other）。
  2. **按专家地区分布**：发送量、回复率、晋级数，按 9 大区。
  并在概览卡区新增地区/服务商相关汇总卡。
- 不可改变：
  - 现有概览卡、6 个子 tab（首发邮件/收信分类/发信回复/未匹配邮件/退信名单/漏斗晋级）、发件账号限额监控面板的数据与展示。
  - 现有 `MailMonitoringService.summary/...` 既有方法签名与返回不变（只新增方法，不改旧方法）。
  - 服务商桶口径必须复用 `ProviderResolver`，不得新写一套归一逻辑。
  - 地区折算必须复用 `CountryContinentMapping.toRegion`，不得新写映射。
- 范围外（明确推迟）：
  - 首发邮件明细表新增「地区」列、独立「地区分布」子 tab（预览里展示过，本计划不做，避免改 `listIntroductions`/`IntroductionRow` 增大互动面；后续可单立小计划）。
  - 按服务商×地区交叉透视。
  - 时段（小时级）分布、趋势对比。
  - 把地区/服务商做成可联动的列表筛选（与 K-agg-filter-source-of-truth 的 ES 列表筛选是两套口径，不在此合并）。

## 关键不变量

### Invariant I-1：服务商桶单一来源 = ProviderResolver
- Rule: 监控中"服务商"的归一只能由 `ProviderResolver.resolve(email)` 决定，返回值域固定为 `{gmail, outlook, yahoo, edu, tencent, netease, other}`。SQL 层只负责按**原始域名**分组，桶折叠在服务层用 `ProviderResolver` 完成。
- Applies to: `MailMonitoringService` 新增的服务商聚合方法。
- Violation consequence: 若在 SQL 里硬编码域名集合，会与发送流程（ManualInitialOutreachService:269 `providerResolver.resolve`）口径漂移，退信归因错位。
- 来源: original（依据既有 `ProviderResolver`）

### Invariant I-2：地区折算单一来源 = CountryContinentMapping.toRegion
- Rule: 监控中"地区"由 `CountryContinentMapping.toRegion(expert_contact.country)` 折算；`country` 为 NULL/未知 → `REGION_OTHER`。SQL 只按 `country` 原值分组，折叠在服务层。展示顺序用 `CountryContinentMapping.allRegions()`。
- Applies to: `MailMonitoringService` 新增的地区聚合方法。
- Violation consequence: 与 `ExpertSearchService.aggregateRegions`（:471 同样用 `toRegion`）口径不一致，前端两处地区数对不上。
- 来源: K-agg-filter-source-of-truth（口径同源原则）

### Invariant I-3：聚合口径与既有 summary 对齐
- Rule: 新增聚合的"发送量"=`direction='OUTBOUND' AND mail_type='INTRODUCTION'`，时间用 `sent_at ∈ [from,to)`；"回复"=`direction='INBOUND'`，时间用 `received_at`。与 `MailRecordRepository.countOutboundByMailTypeBetween` / `countInboundBetween` 既有口径一致，确保各维度合计 ≈ 概览卡总数。
- Applies to: 所有新增聚合查询。
- Violation consequence: 维度分布合计与概览总数对不上，用户质疑数据正确性。
- 来源: original

### Invariant I-4：退信按 failed_recipient 域名归桶，类型来自 bounce_type
- Rule: 服务商退信统计从 `bounce_record` 取数，按 `failed_recipient` 的 `@` 后域名经 `ProviderResolver` 归桶；硬/软退区分用 `bounce_type`（既有值域，不新造）。`failed_recipient` 为 NULL 的行归 `other` 或排除（择一并在验收固定）。
- Applies to: `BounceRecordRepository` 新增聚合 + 服务层折叠。
- Violation consequence: 退信率错配服务商，预警失真。
- 来源: original（依据 `BounceRecord.failedRecipient`/`bounceType`，V43 引入 failed_recipient）

## 现状审计

### MySQL `mail_record`（聚合主表）
- Repo：`MailRecordRepository`（Spring Data JDBC + `@Query` 原生 SQL）。既有聚合范式见 `aggregateSenderAccountStats`（:180-196，`GROUP BY sender_account_code` + `SUM(CASE WHEN ...)`）。
- 与本计划相关字段：`direction`、`mail_type`、`sent_at`、`received_at`、`expert_contact_id`、`send_status`。**无收件邮箱/国家**——需 `JOIN expert_contact ec ON mr.expert_contact_id = ec.id` 取 `ec.expert_email`（服务商）与 `ec.country`（地区，计划 1 产出）。`listMailbox`（:217）已示范该 JOIN 与 utf8mb4 collate 处理，可参照。
- Write paths：与本计划无关（只读聚合，不写 mail_record）。
- Read paths（新增）：本计划新增 `aggregateIntroByCountry`、`aggregateInboundByCountry`、`aggregateIntroByDomain`、`aggregateInboundByDomain`（或合并为带维度参数的查询）。

### MySQL `expert_contact`
- 计划 1 新增 `country` 列（VARCHAR，可空）+ `idx_expert_contact_country`。本计划 JOIN 读取 `country`、`expert_email`。
- 依赖校验：执行本计划前，计划 1 必须已合并且回填完成（否则地区面板大量 Other）。**Provider 面板不依赖 country**，可在计划 1 未完成时先验证。

### MySQL `bounce_record`
- `BounceRecord`：`failedRecipient: String?`、`bounceType: String`、`receivedAt`、`senderAccountCode`。
- Repo `BounceRecordRepository` 既有 `countHardBouncesSince`/`countSoftBouncesSince`（被 `getBounceStats` 用）。新增按域名分组聚合。

### 晋级 `expert_application_promotion`
- `ExpertApplicationPromotionRepository`（`promotionRepository`）。`ExpertApplicationPromotion` 有 `expertContactId`、`promotionStatus`、`createdAt`。地区晋级数需 `JOIN expert_contact` 取 country。既有 `countByStatusAndCreatedAtBetween('SUCCESS', from, to)`（summary 用）。新增按 country 分组。

### 监控服务/控制器/响应/前端
- `MailMonitoringService`（:31）：聚合编排层，注入了 `mailRecordRepository`、`bounceRecordRepository`、`promotionRepository`、`expertContactRepository`。新增方法在此编排 + 折叠桶/大区。
- `MailMonitoringController`（`/api/mail-monitoring`）：新增 GET 端点。
- `MailMonitoringResponses.kt`：新增 DTO。
- 前端 `static/index.html`（:194-228 监控面板区）+ `static/app.js`（监控渲染逻辑、`monitoringRefreshBtn`、`monitoringCards`）。
- Interaction points：
  - `mail_record` 写（首发/回复流程，计划外）↔ 本计划聚合读：只读，无写冲突。
  - `expert_contact.country` 写（计划 1）↔ 本计划地区聚合读：跨计划依赖，已在"依赖校验"声明。

## 实现方案

### 阶段 A：后端聚合查询（obey I-3、I-4）

- 任务 A1：`MailRecordRepository` 新增（参照 `aggregateSenderAccountStats` 范式，JOIN 参照 `listMailbox` 的 collate 处理）：
  - `aggregateIntroSentByCountry(from,to): List<CountryCount>` — `OUTBOUND/INTRODUCTION`，`GROUP BY ec.country`（NULL 归一组）。
  - `aggregateInboundByCountry(from,to): List<CountryCount>` — `INBOUND`，`COUNT(DISTINCT expert_contact_id)`，`GROUP BY ec.country`。
  - `aggregateIntroSentByDomain(from,to): List<DomainCount>` — `OUTBOUND/INTRODUCTION`，`GROUP BY SUBSTRING_INDEX(ec.expert_email,'@',-1)`。
  - `aggregateInboundByDomain(from,to): List<DomainCount>` — `INBOUND`，`COUNT(DISTINCT expert_contact_id)`，`GROUP BY` 域名。
  - DTO `CountryCount(country: String?, count: Long)`、`DomainCount(domain: String?, count: Long)` 定义在 repository 文件内（与既有 `SenderAccountDailyStats` 同处）。
- 任务 A2：`BounceRecordRepository` 新增 `aggregateBouncesByDomain(since): List<DomainBounceCount>`（`GROUP BY` failed_recipient 域名 + `SUM(CASE WHEN bounce_type='HARD'...)`）。obey I-4。
- 任务 A3：`ExpertApplicationPromotionRepository` 新增 `aggregateSuccessByCountry(from,to): List<CountryCount>`（JOIN expert_contact，`promotion_status='SUCCESS'`，`GROUP BY ec.country`）。
  - 说明：promotionRepository 的实现位置需执行时确认（接口在 expert 模块）；若其为纯 CrudRepository + 既有 `@Query`，按相同范式加方法。

### 阶段 B：服务层折叠（obey I-1、I-2）

- 任务 B1：`MailMonitoringService` 新增 `providerDistribution(date): List<ProviderStatRow>`：
  - 调 A1 域名聚合 + A2 退信聚合；用 `ProviderResolver.resolve("x@$domain")` 把每个域名折进桶，累加 sent/replied/hardBounce/softBounce；回复率=replied/sent。注入 `ProviderResolver`（新增构造参数）。obey I-1。
- 任务 B2：`MailMonitoringService` 新增 `regionDistribution(date): List<RegionStatRow>`：
  - 调 A1 国家聚合 + A3 晋级聚合；用 `CountryContinentMapping.toRegion(country)` 折进大区，按 `allRegions()` 顺序输出；回复率=replied/sent。obey I-2。
  - 复用既有 `MonitoringDateRangeResolver.resolveDay(date)` 取 [from,to)。
- 任务 B3（可选汇总卡数据）：在不改 `summary()` 签名的前提下，新增轻量方法或在两个 distribution 返回里附 `topRegion`/`worstBounceProvider` 供前端卡片用（也可前端从 distribution 自行算，避免后端再加方法——优先前端算，减少后端面）。

### 阶段 C：控制器 + DTO

- 任务 C1：`MailMonitoringResponses.kt` 新增 `ProviderStatRow(provider, sentCount, repliedCount, replyRate, hardBounceCount, softBounceCount)`、`RegionStatRow(region, sentCount, repliedCount, replyRate, promotionCount)`。
- 任务 C2：`MailMonitoringController` 新增：
  - `GET /api/mail-monitoring/provider-distribution?date=` → `List<ProviderStatRow>`
  - `GET /api/mail-monitoring/region-distribution?date=` → `List<RegionStatRow>`
  - 参照既有 `senderAccounts(date)` 端点写法。

### 阶段 D：前端（obey 展示口径）

- 任务 D1：`static/index.html` 在监控视图（:194-228）「今日全链路指标概览」与子 tab 面板之间，新增两个 `<section class="panel">`：
  - 「按收件方服务商分布」表（服务商/发送量条形/发送/回复率/硬退率/软退）。
  - 「按专家地区分布」表（地区/发送量条形/发送/回复率/晋级）。
  - 概览卡区追加：覆盖地区数、覆盖服务商数、最高退信服务商（前端从 distribution 计算，sentence case，复用既有 `metric-card` 结构）。
- 任务 D2：`static/app.js` 在监控刷新逻辑（`monitoringRefreshBtn` / 初次加载）中并行 `fetch` 两个新端点，渲染两张表 + 卡片；沿用既有渲染辅助与样式类（`.metric-card`/`.table-wrap`/表头大写）。失败时不阻塞既有面板渲染。

## 变更文件清单

| # | 文件 | 改动 | 不变量 |
|---|------|------|--------|
| 1 | `.../mail/repository/MailRecordRepository.kt` | 4 个聚合 `@Query` + 2 DTO | I-3 |
| 2 | `.../mail/repository/BounceRecordRepository.kt` | 1 个域名退信聚合 + DTO | I-4 |
| 3 | `.../expert/repository/ExpertApplicationPromotionRepository.kt` | 1 个按国家晋级聚合 | I-3 |
| 4 | `.../monitoring/service/MailMonitoringService.kt` | `providerDistribution` / `regionDistribution`，注入 `ProviderResolver` | I-1, I-2 |
| 5 | `.../monitoring/controller/MailMonitoringResponses.kt` | `ProviderStatRow` / `RegionStatRow` | — |
| 6 | `.../monitoring/controller/MailMonitoringController.kt` | 2 个 GET 端点 | I-1, I-2 |
| 7 | `src/main/resources/static/index.html` | 2 个面板 + 概览卡 | — |
| 8 | `src/main/resources/static/app.js` | 拉取 + 渲染 | I-1, I-2 |

文件数：8 ≤ 10 ✓。子系统：2（后端聚合 / 前端展示）✓。新增 DB 字段：0 ✓。

## 验收标准

- I-1：单测/手测 `providerDistribution`：构造收件方为 `a@gmail.com`、`b@hotmail.com`、`c@mit.edu`、`d@qq.com`、`e@unknown.xx` 的发送记录，断言分别落入 gmail / outlook / edu / tencent / other 桶，且与直接调 `ProviderResolver.resolve` 结果一致（无独立硬编码集合）。
- I-2：构造不同 country 的联系人 + 发送记录，断言 `regionDistribution` 大区归并与 `CountryContinentMapping.toRegion` 一致；`country=NULL` 归 `Other`；输出顺序 = `allRegions()`。
- I-3：同一日期下，`Σ regionDistribution.sentCount` 与 `Σ providerDistribution.sentCount` 均 == `summary(date).introductions`（口径自洽）；inbound 同理对齐 `countInboundBetween` 的 distinct 口径（如有差异在用例注释说明 distinct vs 行数）。
- I-4：构造 `bounce_record`（HARD/SOFT，failed_recipient 不同域名 + 一条 NULL），断言按桶归并、硬软分开，NULL 行处理与所选策略一致。
- 回归：既有 `summary` / 6 子 tab / 发件账号限额监控面板返回与渲染不变；`/api/mail-monitoring/*` 既有端点不变。
- 集成：监控页加载，两张新面板与新卡片正确渲染；某个新端点 500 时旧面板仍正常（前端容错）。`mvn test` 通过。

## 依赖与顺序

- 服务商维度（任务 A1 域名聚合、A2、B1、C、D 的服务商部分）**无 DB 依赖**，可在计划 1 完成前先实现验证。
- 地区维度（A1 国家聚合、A3、B2、C、D 的地区部分）依赖计划 1 的 `expert_contact.country` 已落库 + 回填完成。
- 建议执行顺序：计划 1 全量合并并回填 → 计划 2 一次性实现两维度（同批文件，避免重复改 6 个文件）。
