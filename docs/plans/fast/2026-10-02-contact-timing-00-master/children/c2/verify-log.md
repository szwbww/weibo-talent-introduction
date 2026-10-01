# Verify Log — c2（contact-timing-02-recommendation）

轻量验证报告逐次追加于此；每次报告以 `## Light Verification: <VERDICT>` 开头。

## Light Verification: LIGHT_PASS_WITH_NOTES

Child: c2 — `docs/plans/fast/2026-10-02-contact-timing-00-master/children/c2/brief.md`（批准计划 `docs/plans/2026-10-02/contact-timing-02-recommendation.md`）
Boundary: `dc5546a6e914c23249a8b7dbadcc977e5df1b7e3`..`ab8e4cb82bace355c06412d260a42b5fbe6cce74`（实现提交 `ab8e4cb`；区间内另一提交 `a1217b3` 仅为 c1 文档记录）
Verifier: C2Verify

### Four Gates

|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git show --stat ab8e4cb` = 8 files / 1755 insertions / 19 deletions，逐条等于 brief 的 8 个授权文件（3 主代码 + 4 测试 + 新增 `ReplyTimeRecommender.kt`），无第九文件；c1 的 `ExpertContactLocationCatalog.kt`、`V146` 迁移、`contact-country-timezones.json` 均不在 diff 中；区间另一端 `a1217b3` 只动 `docs/plans/fast/**`（c1 execution/fix/verify-log + ledger）；工作树仅有 `ledger.md` 改动与未跟踪 `children/c2/`（均非产品/测试文件）|
|Plan and invariants|PASS|I-1：`ExpertContactLocationService.kt:133` 账号集合 `findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)`、`:290-298` 窄投影 SQL（`expert_contact_id` + `sender_account_code IN (:accountCodes)` + 180 天窗口 + `ORDER BY received_at DESC, id DESC` + `LIMIT 1001`，无 body/subject/from_email/process_*）、`:157/227/231` message-id 与 `(owner非空,uidValidity>0,imapUid)` 去重、不按处理状态过滤；I-2 `:147` `received_at` 取出 `LocalDateTime` 后显式 `atZone(Asia/Shanghai).toInstant()` 再投影；I-3 `ReplyTimeRecommender.kt:80-101`（未配置在 `:95-99` 提前返回 null）+ `:47` D=3 阈值 + `WORK_WINDOW_BINS=18`；I-4 `:53/50/75/116/124/62/65` 与 `:290-298` 窗口/截断/日上限 2/半衰期 30 天/平滑 0.2-0.6-0.2/先验 1/18/容差 1e-12，`:145-146` take(1000) 判截断；I-5 `:187-208` 当地今天→已过 now 则 +1 天、`atZone` 缺口顺延/重叠较早 offset、正区间校验、最多顺延 3 天否则抛明确错误；I-6 只读（无 INSERT/UPDATE，只调 `findAllByAccountCodeNot` 一次）。测试证据：`ReplyTimeRecommenderTest` 21 例（印度/纽约夏冬/尼泊尔/Lord Howe 逐字端点、13:00–15:00 平局取早、23:45 跨午夜、日上限=2、30 天=1/2、E/alpha 老化下降、未来排除、DST 缺口/重叠/Troll 压缩、`profile` 逐分量断言）、`ExpertContactLocationServiceTest` 9 例（SQL 列白名单与参数、1001/1000 截断边界、空账号集不发查询、换时区重投影、零写）、`ExpertContactLocationControllerTest` 6 例（401 不触达 service、404、strict JSON 两 mode 形态）、IT 7 例（来源隔离、跨账号 message-id 去重、模拟器排除、绑定/解决/reopen、180 天边界、1001 截断 + EXPLAIN/耗时、只读 + 重投影）|
|Required commands|PASS|①`mvn test -Dtest=ReplyTimeRecommenderTest,ExpertContactLocationServiceTest,ExpertContactLocationControllerTest`（JAVA_HOME=zulu-11.0.32）exit 0 / `BUILD SUCCESS`：`ReplyTimeRecommenderTest` 21/0/0、`ExpertContactLocationServiceTest` 19/0/0、`ExpertContactLocationControllerTest` 13/0/0 = 53/0/0（对照 c1 基线 10/7 + 新增 21）；同命令 `exec:exec(node-test)` 1273 pass / 0 fail，两项 `node --check` 通过；②`mvn test -Pmysql-it -Dtest=ExpertContactLocationServiceIT -Dspring.datasource.url='…/talent_contact_timing_it…'` exit 0 / `BUILD SUCCESS`：`ExpertContactLocationServiceIT` 14/0/0（c1 基线 7，+7）；stdout 记录 `c2 EXPLAIN plan: [{table=inbound_mail_processing, type=ALL, key=null, rows=1001…}]`、`c2 timing read over 1001 rows: 21 ms`。两条命令均在 `ab8e4cb` 上全新执行；运行后隔离库 fixture 自清（`inbound_mail_processing` 0、fixture `mail_record` 0、`expert_contact_location` 0、`expert_contact` 0、`mail_sender_account` 1=模拟器种子），业务库未被触碰（`talent_introduction` V146 计数 0、该表不存在）|
|Downstream interfaces|PASS|响应 DTO 逐字等于 brief 冻结形状：`ExpertContactLocationModels.kt:74-111` 只含 `mode/localStart/localEnd/beijingStart/beijingEnd/sampleCount/replyDayCount/historyDays/historyTruncated/recentSamples[{receivedAtBeijing,receivedAtLocal}]/calculatedAt`，无 confidence/preferredHour/bestHour；`location` 仍为未变的 `ContactLocationView`（models diff 相对 c1 只有追加与注释）；当地时区 id 在 `location.effectiveZoneId`；路由 `ExpertContactLocationController.kt:51-52` 新增只读 `GET /{contactId}/timing`，01 的 `GET /countries`(:41)、`GET /{contactId}`(:44)、`PUT /{contactId}`(:54) 逐字未改；401 沿 `AuthInterceptor`/PUT 会话边界（controller 测试断言匿名 401 且不触达 service），404 经 `NoSuchElementException`→`GlobalExceptionHandler.kt:26-28` `NOT_FOUND`；`ExpertContactLocationService` 构造器新增第三参 `MailSenderAccountRepository`（全仓 3 个构造点均在授权文件内），`get/save` 签名未变|

### AUTO_FIX

- N/A

### RECORD_ONLY

- R-1（证据形态，非门禁违规）：I-1 的「有效物理键去重」分支（owner 非空 + uidValidity>0 + imapUid 相同而 message-id 不同 → 合并）只能在单测里以 mock 行覆盖（`ExpertContactLocationServiceTest` 该例断言 8 行→6 条）；IT 无法构造该场景，因为 V134 唯一键 `uk_inbound_mp_owner_uid (mailbox_owner_code, uid_validity, imap_uid)` 不允许两行同物理身份共存。实现与计划 T-1 一致（源码逐条可读），IT 覆盖的是库中实际可能出现的形态（跨账号同 message-id、owner=NULL/代际=0 不合并），执行报告 Deviations 2 已自行披露。

### Required Action

- COMPLETE_CHILD

