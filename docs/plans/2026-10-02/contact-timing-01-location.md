# 01：人工所在地持久化与国家时区目录

依赖：[总计划 G-0](contact-timing-00-master.md)。状态：待执行。范围：9 个文件，所在地后台 1 个子系统。

## 需求描述

O-1：已登录用户可给联系人保存国家和可选时区，刷新后读回同一设置。
O-2：返回完整的本次目录及每国明确的默认时区；不选具体时区等于持续使用该国默认。

必须保持 N-1：既有 expert_contact.country、专家状态、ES 画像、邮件和国家统计不变。
范围外：回复习惯计算、前端界面、批量设置、自动国家推断、审计日志系统、跨联系人共享设置。

## 关键不变量

### Invariant I-1: 人工配置的身份与存在性
- Rule: `expert_contact_id` 为新表主键并引用真实 contact；每个 contact 最多一行。无行表示未配置，不以空 country 或“未知国家”占位。配置属于 contact，不属于登录用户或 ORCID。
- Applies to: 新表迁移、service.get/save、控制器所有带 contactId 的入口。
- Violation consequence: 配置串专家，或未配置被静默猜成某国。
- 来源: original。

### Invariant I-2: 国家代码有目录权威
- Rule: `country_code` 仅接受当前目录中的大写 ISO 二字码；trim 后转大写再校验。未知代码 400，不能写入。目录只从随包 JSON 加载，不在线查位置、不解析现有 country 文本。
- Applies to: 目录加载、PUT 校验、响应展示。
- Violation consequence: 国家和时区关系无法验证。
- 来源: original。

### Invariant I-3: null 与显式时区不同
- Rule: `zone_id IS NULL` 表示使用目录 defaultZoneId；显式值必须属于该国 zones。有效时区 = zoneId ?: defaultZoneId。空白 zoneId 归一为 null；不将默认值展开后回写 zone_id。换国家必须同时校验/清空旧显式时区。
- Applies to: SQL upsert、GET/PUT 配置 DTO、目录验证。
- Violation consequence: 默认设置变成永久锁死的具体时区，或跨国时区残留。
- 来源: original。

### Invariant I-4: 写入闭包与身份
- Rule: 唯一业务写入口是新控制器 PUT→新服务的参数化 upsert；要求会话 username 非空。不存在 contact 返回 404；请求非法返回 400；未登录 PUT 返回 401。校验失败不写任何表。
- Applies to: 控制器、service.save、迁移。
- Violation consequence: 越过既有登录边界或部分写入。
- 来源: original；复用 AuthSessionKeys、AuthInterceptor 和 GlobalExceptionHandler 的既有协议。

### Invariant I-5: 既有国家字段不参与人工配置
- Rule: 不改 expert_contact.country，不增加其实体字段，不改 ES、不触发回填、发信、排期或晋级。新表没有后台同步写入口。
- Applies to: 新服务与迁移；所有既有 writer 保持原样。
- Violation consequence: 人工配置污染统计或被旧写链覆盖。
- 来源: original；K-line-number-guard-breaks-on-any-insertion。

## 现状审计

### expert_contact（本功能只校验 id）

- Schema：V1:83–101 有 BIGINT 主键及 campaign/orcid 唯一约束；V48 增加可空 country VARCHAR(128) 与国家索引。
- 与 country 相关的业务写路径：InitialOutreachService:82–89、ManualInitialOutreachService:899–903 从专家画像新建；ContactCountryBackfillService:69–75 调 ExpertContactRepository.updateCountryById:67–68；既有实体 save(copy) 保留字段。
- 完整表级引用、SQL/仓库调用与迁移命中均在 `grep-receipts.md#contact-store`。涵盖首次建联、人工建联、ConversationStateService、ExpertOperatorStatusService、层级操作、联系人管理、自动晋级、自动/人工回复的实体写入；本计划不接入任何一条旧写链。
- 读路径：ExpertContactRepository 国家账号分布（126–132），MailRecordRepository 国家统计（366–393），联系人/会话读取、索引同步和业务发送。新 reader 只 SELECT id 存在性。
- IP-1：画像/回填写原 country → 旧统计继续读取原 country；人工设置新表 → 新 GET 读取新表。两条链必须互不覆盖。

### expert_contact_location（新增 store）

当前 `src/main/scripts/tools` 检索无命中，证据包含 exit_code=1。新增表不扩展现有共享表，因此没有对共享 store 同时增加多个业务字段。

唯一新增业务写路径为 `ExpertContactLocationController.save → ExpertContactLocationService.save`；唯一新增配置读路径为同控制器 GET→service.get，02 在其后复用。数据库 FK 删除是第二个、非业务入口的删除路径：联系人真正删除时级联清理本表。仓库检索未发现现有 contact 删除服务；本计划不新增联系人删除功能。

IP-2：PUT 写入 → GET/02 推荐读取；IP-3：未登录/非法国家时区 → 必须在写入之前失败。

### 国家时区文件

现有 meeting-timezones-zh.properties 有 IANA id、中文标签和别名，没有可直接使用的 ISO country→zones/default 映射；MeetingConfirmationService 的 defaultZoneId 是会议默认 Shanghai，不能拿来作为每国默认。新增静态 JSON 采用证据目录中已经生成的 `country-timezones.proposed.json`，逐字复制。

目录含 247 个国家/地区、418 条时区关系。国家关系来自保存的 IANA zone.tab；显示名来自 JDK Locale 和仓库中文时区目录；没中文名的直接展示 IANA id。已预览的 28 国保留预览默认，其余默认固定为此次 zone.tab 首行。此规则是产品选择，不声称首都是已核实事实。用户可覆盖。

IP-4：目录更新 → null 设置使用新默认，显式设置继续使用已选合法 zone；本轮锁定目录版本，后续更新另案审计。运行时必须先满足总计划 G-0。

### 可复用实现与保护边界

ExpertRepliedDismissalService:12–51 已有“窄服务 + NamedParameterJdbcTemplate + contact 存在性 + MySQL upsert”范式；本功能沿用，不增加通用 repository 框架。AuthWebConfig:23–27 保护 `/api/**`，PUT 按 MailboxConversationController 的会话身份防御。GlobalExceptionHandler:18–28 提供 400/404。

不修改 ExpertContactRepository、MailRecordRepository、MailboxService；它们被 OperatorStatusWriteSeamGuardTest 的精确行号白名单覆盖。（来源: K-line-number-guard-breaks-on-any-insertion）

## 实现方案

### T-1：迁移与模型（I-1/I-2/I-3/I-5）

文件：`V146__create_expert_contact_location.sql`、`ExpertContactLocationModels.kt`。
执行前重新检查最大迁移号（2026-10-02 基线 HEAD：V145 已被 `V145__add_batch_email_verification_allowed_states.sql` 占用）；本计划清单按该复核指令改用下一个未用号 V146（fast-p ledger A1），不能覆盖已有迁移。

```sql
CREATE TABLE expert_contact_location (
    expert_contact_id BIGINT NOT NULL,
    country_code CHAR(2) NOT NULL,
    zone_id VARCHAR(64) NULL,
    PRIMARY KEY (expert_contact_id),
    CONSTRAINT fk_expert_contact_location_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='人工配置的联系人所在地；zone_id为空表示使用国家默认时区';
```

不回填原 country，不加 updated_at、模型版本、得分、用户偏好 JSON。创建请求 DTO `SaveContactLocationRequest(countryCode:String, zoneId:String?)`。

响应 `ContactLocationView` 字段冻结：`contactId:Long, configured:Boolean, countryCode:String?, countryLabel:String?, zoneId:String?, effectiveZoneId:String?, zoneLabel:String?, usingDefaultZone:Boolean`。未配置后三个 zone 字段均 null，usingDefaultZone=false；已配置 usingDefaultZone=(zoneId==null)。

### T-2：目录（I-2/I-3）

文件：`contact-country-timezones.json`、`ExpertContactLocationCatalog.kt`、`ExpertContactLocationModels.kt`。

- 将证据提案文件逐字复制为资源；读取字段 sourceVersion/sourceUrl/defaultPolicy/countries。
- countries 元素为 `{code,labelZh,defaultZoneId,zones:[{id,labelZh}]}`。
- 加载时验证 code 唯一、zones 非空且不重复、default 属于 zones、每个 id 可 `ZoneId.of`。加载失败必须给出确定的配置错误，不回退 UTC 或随便删除不认识的 zone。
- 排序使用已固定资源顺序，默认项在前端单列展示；时区不按当前 UTC 偏移去重（偏移相同不等于规则相同）。
- 不引入运行时联网、额外第三方库或国家自动识别器。

### T-3：配置服务和接口（I-1–I-5）

文件：`ExpertContactLocationService.kt`、`ExpertContactLocationController.kt`、`ExpertContactLocationModels.kt`。

服务直接用 NamedParameterJdbcTemplate，命名参数查询 contact 存在性；保存前完成所有目录校验，然后单条 `INSERT ... ON DUPLICATE KEY UPDATE country_code=VALUES(country_code), zone_id=VALUES(zone_id)`。同一 contact 同时保存采用数据库最后完成写入，返回重新读取的配置，不增加乐观锁。GET 无配置返回 200/configured=false，不创建占位行。

接口按总计划三个配置路由。GET countries 返回固定目录（可以原模型序列化）；GET/PUT 配置按上述 DTO。PUT 显式检查 Session username；业务代码不从请求体接收 username。写入后的新 reader 为 GET，02 读取同一服务；旧 reader 无须改动。

### T-4：验证（I-1–I-5）

文件：下面清单的三项测试。service 单测覆盖目录、null/default、跨国非法时区与旧字段未写；controller 测试覆盖实际 URL、JSON、401/400/404；IT 在隔离 MySQL 数据库验证 migration、upsert、FK 和 reload。使用现有 mysql-it 门禁，但不得照搬已有 IT 的全库清理逻辑到业务数据库。

## 变更文件清单

| # | 路径 | 操作 |
|---:|---|---|
| 1 | src/main/resources/db/migration/V146__create_expert_contact_location.sql | 新增 |
| 2 | src/main/resources/contact-country-timezones.json | 新增，复制证据快照 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationModels.kt | 新增 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationCatalog.kt | 新增 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationService.kt | 新增 |
| 6 | src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationController.kt | 新增 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationServiceTest.kt | 新增 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt | 新增 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactLocationServiceIT.kt | 新增 |

## 验收标准

- I-1：同 id 两次 PUT 仍一行；新 id GET 未配置且 DB 没有新增行；不存在的 id GET/PUT 404；外键拒绝悬空 contact。
- I-2：247 项、418 个关系与证据快照一致；资源 SHA256 对齐；`br` 归一为 BR，`ZZ` 返回 400。
- I-3：BR/null 回读 zoneId=null、effectiveZoneId=America/Sao_Paulo、usingDefaultZone=true；BR/Manaus 为 false；BR/Asia/Tokyo 返回 400 且前值保持。
- I-4：无 Session PUT 401；数据库捕获的调用仅是存在性查询、新表 upsert/read；失败分支无 update。
- I-5：执行前后原 country、operator_status、邮件行数一致；相关旧文件 diff 为空，既有行号守卫通过。
- 集成：隔离库启用 Flyway，验证 GET→PUT→进程重启→GET；IP-1 原 country 回填后新配置不变；IP-2/3 覆盖参数化查询和错误原子性。
- 单测命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertContactLocationServiceTest,ExpertContactLocationControllerTest,OperatorStatusWriteSeamGuardTest`。该路径必须指向满足 G-0 的 JDK 11。
- IT 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Pmysql-it -Dtest=ExpertContactLocationServiceIT -Dspring.datasource.url='jdbc:mysql://127.0.0.1:3306/talent_contact_timing_it?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'`；账号凭据沿本机测试配置，不写入计划。缺独立测试库时如实记未验证，不把 mock 当 MySQL 证明。

## 人工验收清单

### A-1: 默认与显式配置持久化
- 前置条件: 隔离测试环境已经登录；从 GET `/api/mail/mailbox/conversations` 的 items 选一个测试 contactId 记为 C；禁止使用真实专家数据做验收写入。
- 操作步骤: 1. GET `/api/mail/contact-locations/countries`。2. PUT `/api/mail/contact-locations/C`，body 为 `{"countryCode":"BR","zoneId":null}`。3. 重启测试应用，GET 同地址。4. PUT `{"countryCode":"BR","zoneId":"America/Manaus"}`，再 GET。
- 预期结果: 目录中 BR 含 16 个 zone，默认 America/Sao_Paulo；第 3 步 configured=true、zoneId=null、usingDefaultZone=true；第 4 步 effectiveZoneId=America/Manaus、usingDefaultZone=false。
- 覆盖: O-1/O-2；I-1/I-2/I-3；IP-2/IP-4。

### A-2: 非法配置不覆盖
- 前置条件: C 已保存 BR/Manaus；保留正常登录会话和一个无登录会话的客户端。
- 操作步骤: 1. PUT BR/Asia/Tokyo。2. PUT ZZ/null。3. 无登录客户端 PUT BR/null。4. 登录客户端 GET C。5. 对测试库中不存在的 id 执行 GET。
- 预期结果: 顺序返回 400、400、401；C 仍为 BR/Manaus；不存在 id 返回 404。
- 覆盖: I-1/I-2/I-3/I-4；IP-3。

### A-3: 原国家和邮件业务隔离
- 前置条件: C 的原 expert_contact.country 在测试库设为 `旧统计值`；记录邮件数与原状态。已保存新配置 BR/Manaus。
- 操作步骤: 1. 查询原国家统计。2. 只在测试库将原 country 改为 `画像回填值`，模拟现有回填入口的结果。3. GET C 配置；再次查看邮件数、状态和国家统计。
- 预期结果: 新配置仍是 BR/Manaus；原国家统计从 `旧统计值` 变为 `画像回填值`，不变成 BR；邮件数与状态保持记录值。
- 覆盖: N-1；I-5；IP-1。
