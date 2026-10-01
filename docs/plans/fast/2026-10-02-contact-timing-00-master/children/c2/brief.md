# Child Brief — c2（子计划 02：根据回复时间计算北京时间推荐窗口）

本 brief 是 c2 的唯一执行契约入口；计划文件是逐字实现契约，本 brief 只补充 c1 已冻结事实、环境绑定与流程约束。

## 任务与身份

- 工作区（唯一允许改动的位置）：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-contact-timing-00-master`
- 分支：`fast/2026-10-02-contact-timing-00-master`
- child_base_sha：`dc5546a6e914c23249a8b7dbadcc977e5df1b7e3`（c1 已验证终态；证据 commit `a1217b3`）
- 批准计划：`docs/plans/2026-10-02/contact-timing-02-recommendation.md`
- 只读证据（按需查阅，不要修改）：`docs/plans/2026-10-02/contact-timing-evidence/`
- 执行报告（你写，**不要提交**）：`docs/plans/fast/2026-10-02-contact-timing-00-master/children/c2/execution.md`
- 必须使用 `execute-p` 流程；只允许一个本地实现提交，提交信息：`feat(fast-p): implement c2`

## 授权文件（8，见计划「变更文件清单」）

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationModels.kt`（扩展 c1）
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationService.kt`（扩展 c1）
3. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationController.kt`（扩展 c1）
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ReplyTimeRecommender.kt`（新增）
5. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ReplyTimeRecommenderTest.kt`（新增）
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationServiceTest.kt`（扩展 c1）
7. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt`（扩展 c1）
8. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationServiceIT.kt`（扩展 c1）

不得修改 c1 的 `ExpertContactLocationCatalog.kt`、迁移 V146、`contact-country-timezones.json` 或任何计划外文件；不得新增第九个文件。

## c1 已验证事实（c2 的基线，不得破坏）

- `ExpertContactLocationService(jdbcTemplate: NamedParameterJdbcTemplate, catalog: ExpertContactLocationCatalog)`；`fun get(contactId: Long): ContactLocationView`；`fun save(username, contactId, request): ContactLocationView`。
- `ExpertContactLocationCatalog.all()` / `country(code)`；`ContactCountryTimezoneCatalog`（sourceVersion/sourceUrl/defaultPolicy/countries）。
- `ContactLocationView` 字段冻结：`contactId, configured, countryCode?, countryLabel?, zoneId?, effectiveZoneId?, zoneLabel?, usingDefaultZone`。
- 01 的三个配置路由与语义不得改变（计划 02 I-6 / 总计划「01 配置接口与国家默认语义保持」）；timing 是**新增只读** `GET /api/mail/contact-locations/{contactId}/timing`，不修改 PUT 响应。
- c1 验证结论（`children/c1/verify-log.md`，证据 commit `a1217b3`）：四门全 PASS；RECORD_ONLY R-1（HTTP 层未直接断言 configured+国家默认 JSON 形态，可在本 child 顺带覆盖）与 R-2（G-0 延后项）。

## 环境绑定（与 c1 相同）

- 所有计划命令的 `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`（Zulu 11.0.32.1 / tzdb 2026b，418/418 目录 id 可解析）。系统 zulu-11（2021e）不可用。
- G-0 ≥2026c 版本线仍未满足（当前 JDK 11 GA 最新 tzdb=2026b）；照常实现，但**不得宣称时区验收通过**。
- MySQL：容器 `ti-mysql-it` 运行中（127.0.0.1:3306，root/root）；独立库 `talent_contact_timing_it` 已迁移到 V146、当前 0 行。不得触碰 `talent_introduction` 或业务库。
- Node v25.7.0。

## 必需命令（逐字执行，仅 JAVA_HOME 按上文绑定）

1. 单测：
   `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ReplyTimeRecommenderTest,ExpertContactLocationServiceTest,ExpertContactLocationControllerTest`
2. MySQL IT：
   `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Pmysql-it -Dtest=ExpertContactLocationServiceIT -Dspring.datasource.url='jdbc:mysql://127.0.0.1:3306/talent_contact_timing_it?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'`

基线对照：c1 终态时 ServiceTest 10/0/0、ControllerTest 7/0/0（`ReplyTimeRecommenderTest` 尚不存在）；IT 7/0/0。命令 2 的 `-Dspring.datasource.url` 已证明会传播到测试 JVM（c1 与基线 probe）；IT 已自带 `SELECT DATABASE()` 隔离断言。不要运行全量 `mvn test`。

## 必须保持的不变量（计划为准，摘要）

- I-1：样本只来自 `inbound_mail_processing`（指定 contactId），账号集合复用 `MailSenderAccountRepository.findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)` 现有口径；不读 INBOUND `mail_record`、不读正文；按 message_id/物理身份去重；处理状态不作为学习资格门槛。
- I-2：`received_at` 按 Asia/Shanghai 还原 Instant 再投影目标 ZoneId；禁止把 DATETIME 当 UTC/固定偏移。
- I-3：无配置 → `configured=false`、`recommendation=null`；回复日 D<3 → `WORK_HOURS` 完整 08:00–17:00；D≥3 → `REPLY_PATTERN` 连续四桶（允许跨午夜、工作时间外）。
- I-4：180×24h 窗口、排除未来、LIMIT 1001（>1000 截断 + historyTruncated）、日权重上限 2、30 天半衰期、平滑/先验/平局公式固定（见计划 T-2 逐字公式）；同输入同输出；recentSamples ≤8 条且不含正文/主题/邮箱/内部 id。
- I-5：候选起点用专家当地今天，已过 now 则加一天；端点带完整日期与 offset；DST gap 顺延/overlap 取较早 offset；不产生倒置或零长度区间（最多重试 3 天，仍失败返回明确计算错误）。
- I-6：timing GET 只读（不写任何表、不调 IMAP/SMTP/ES/LLM）。

## 接口（计划 T-3 冻结）

`GET /api/mail/contact-locations/{contactId}/timing`（404 与登录边界沿 c1）：

```text
{ location: ContactLocationView,
  recommendation: null | { mode: "WORK_HOURS"|"REPLY_PATTERN", localStart, localEnd, beijingStart, beijingEnd,
    sampleCount, replyDayCount, historyDays:180, historyTruncated, recentSamples:[{receivedAtBeijing, receivedAtLocal}], calculatedAt } }
```

字段语义只此一套（无 confidence/preferredHour/bestHour）。当地时区 id 在 `location.effectiveZoneId`。

## 返回

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要（命令→退出码/计数）、报告路径。不要 push、merge、rebase、amend、reset；不要修改主工作区 `/Users/lukai/IdeaProjects/weibo-talent-introduction`；不要提交 `docs/plans/fast/**`；不要审查 c3。
