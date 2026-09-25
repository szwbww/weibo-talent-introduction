# 01 — 跟踪存储、全局开关与查询/像素接口

状态：待实施。前置：当前工作树最高迁移 V140；执行前复核。后续 02→03→04；本阶段尚不插入外发图片，可独立部署，开关默认关闭。

## 需求描述
- O-1：登录后能读取/保存全局开关、查询成功外发邮件的跟踪状态、统计与详情；未配置公网地址不能开启。
- O-2：具有效随机 token 的公网图片请求记录首次/最近信号；关闭时停止新增信号，历史可查询。
- 不改变：N-1 旧发送/回复/退订/收信行为；N-2 既有监控统计、任务配置 keys、历史邮件；N-3 管理接口登录保护。
- 范围外：发信插图（03）、页面（04）、IP/地区/设备采集、机器人识别、逐次事件表、点击追踪、外部 SDK、历史补追踪、自动清理任务。

## 关键不变量
### Invariant I-1: 关闭是默认值与事实值
- Rule: 只使用 batch_send_setting 的 mailOpenTracking.enabled；缺失/非法值视为 false；无内存权威缓存；保存 true 前验证 HTTPS base URL，保存 false 永远允许。配置读取/预留出错时由 03 放弃追踪，不阻断原发信。
- Applies to: settings GET/PUT、reserve、recordSignal
- Violation consequence: 开关不一致或污染旧任务配置。
- 来源: original；AutoReplySettingService:12–30 默认关闭先例
### Invariant I-2: 随机身份与精确关联
- Rule: 新表 token 是 SecureRandom 32 字节的无 padding Base64URL（43 字符，ascii_bin 唯一）；URL 不含邮箱/专家ID；mail_record 仅新增 open_tracking_id，可空且唯一 FK。关联只按 ID，不按 Message-ID/邮箱/主题。
- Applies to: DDL、reserve、readPage、summary、detail
- Violation consequence: 串信、重复计数、URL 泄露邮箱。
- 来源: K-mail-record-save-sites
### Invariant I-3: 预留独立提交
- Rule: 新增 repository.reserve 用跨 bean 的 REQUIRES_NEW 事务，先提交跟踪行再由 03 发送。该表不 FK 指向调用者尚未提交的 contact/mail_record；预留异常必须由服务/SMTP 捕获于原事务外。
- Applies to: reserve、SMTP 调用的后续契约
- Violation consequence: 快速加载漏记、外层回滚丢 token，或把业务事务标为 rollback-only。
- 来源: K-smtp-idempotency-reservation-before-delivery（本功能不替代既有发信幂等）
### Invariant I-4: 信号是有限聚合
- Rule: 只存 created_at/first_open_at/last_open_at；首次取最早值、最近取最晚值；每次合法 GET 用原子 UPDATE 并在该 SQL 检查全局开关。HEAD、非法 token、未知 token、关闭、存储失败不新增信号；响应仍为同一图片。
- Applies to: public GET/HEAD、recordSignal
- Violation consequence: 并发丢更新、把 HEAD 当阅读、关闭仍累计。
- 来源: original
### Invariant I-5: 统计严格依赖成功记录
- Rule: 列表基础集=mail_record.direction=OUTBOUND AND send_status=SENT AND sent_at 非空；关联 t 后 NOT_TRACKED=t.id null，NO_SIGNAL=t.id 非空且 first null，OPENED=first 非空。统计 trackedSent 只数成功且有关联行，opened 数 first 非空行；同一邮件最多贡献1；分母0返回 rate=null，前端显示—。
- Applies to: readPage/summary/detail
- Violation consequence: 失败、孤儿预留、回复/历史未跟踪邮件污染分母。
- 来源: original
### Invariant I-6: 管理与公开响应分离
- Rule: 管理路径 /api/mail-open-tracking/** 仍被 AuthInterceptor 保护；公开路径仅返回 GIF。管理 DTO 不返回 token、图片URL或邮件正文；读取详情按 mailRecordId，只允许基础集中的记录。
- Applies to: controller 所有路由
- Violation consequence: 预览触发信号、匿名读业务信息。
- 来源: original
### Invariant I-7: 时间与查询边界
- Rule: 沿 TimeZoneConfig 和 MonitoringDateRangeResolver 的上海时间；日期按发送日，闭区间日期转换为 [from 00:00,to+1 00:00)。同一次列表请求的记录/计数/统计在同一只读事务读取；字段严格显式映射，所有 SQL 参数绑定。
- Applies to: readPage、snapshot response
- Violation consequence: 跨日错数、页与指标口径分裂、SQL 注入。
- 来源: original

## 现状审计
权威证据：[mail-open-tracking-audit.md](mail-open-tracking-audit.md) §2–4、§7；逐点写入/读取清单在同目录 evidence。不可省略这些已有写入点的兼容检查。
- mail_record schema/写方/读方：audit §2；本阶段只增加 nullable 字段，现有构造默认 null。
- batch_send_setting：audit §3；新增 key 只在管理员首次保存时 UPSERT，不迁移 seed，避免改变既有 seed 行数。
- 新 mail_open_tracking：之前不存在，反查回执 `no-tracking-implementation.txt`。写方只有新增 repository.reserve/recordSignal；读方 reserve 返回 id/token、管理 readPage/detail。公开请求不创建行。
- expert_contact：audit §4，查询仅取当前名称，收件人用新表 recipient 发送快照；没有快照的未跟踪邮件显示“未保存收件快照”，不能拿当前联系邮箱冒充历史地址。
- IP-1/2/4/认证为本阶段验收交互点；目前公网未验证，不把域名可达当已有事实。

## 实现方案

### T1 — 建表及单一共享字段（I-2/5）
文件：`src/main/resources/db/migration/V141__create_mail_open_tracking.sql`、`mail/domain/MailRecord.kt`（Kotlin 路径前缀见变更清单）。新建 migration，不改旧 migration。

```sql
CREATE TABLE mail_open_tracking (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    token CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    first_open_at DATETIME(6) NULL,
    last_open_at DATETIME(6) NULL,
    UNIQUE KEY uq_mail_open_tracking_token (token)
) ENGINE=InnoDB;
ALTER TABLE mail_record
    ADD COLUMN open_tracking_id BIGINT NULL,
    ADD UNIQUE KEY uq_mail_record_open_tracking (open_tracking_id),
    ADD CONSTRAINT fk_mail_record_open_tracking
        FOREIGN KEY (open_tracking_id) REFERENCES mail_open_tracking(id);
```

- `MailRecord` 尾部加 `val openTrackingId: Long? = null`；不重写正文、历史行、其他状态。
- 不为每次打开建行，不保存 open_count、IP、User-Agent。每次实际 eligible 发送尝试最多预留一行。失败/崩溃遗留的无关联行不进入统计；MVP 不加清理调度。
- 执行时版本号若被占用，先按真实部署顺序修订此文档及清单，禁止复用 V141 覆盖已有迁移。(K-flyway-version-follows-deploy-order)

### T2 — repository/service（I-1～7）
文件：新增 `mail/repository/MailOpenTrackingRepository.kt`、`mail/service/MailOpenTrackingService.kt`。
- 沿 BatchEmailVerificationRepository 的 JdbcTemplate/GeneratedKeyHolder/RowMapper 写法；小型结果 DTO 放在所属文件，无需另建通用框架。
- repository 的 reserve(token,recipient,now) 必须由 service 调 bean 方法触发 REQUIRES_NEW；不在同类自调用绕过代理。recipient 使用实际 ComposedMail.to，长于255或不是单一地址则跳过跟踪，不截断/猜测拆分，不阻断原发信。
- reserve 内再次检查开关；false 返回 null，不插表。生成 token 碰撞仅对唯一键冲突重试，最多3次；其他错误不重试 SMTP。
- `setEnabled` 用单条参数化 `INSERT ... ON DUPLICATE KEY UPDATE setting_value=?,updated_at=?`，只写本 key，不先读再插造成竞争。
- recordSignal 使用单条条件更新：

```sql
UPDATE mail_open_tracking t
SET first_open_at = CASE WHEN first_open_at IS NULL OR first_open_at > ? THEN ? ELSE first_open_at END,
    last_open_at = CASE WHEN last_open_at IS NULL OR last_open_at < ? THEN ? ELSE last_open_at END
WHERE t.token = ?
  AND EXISTS (SELECT 1 FROM batch_send_setting s
              WHERE s.setting_key = 'mailOpenTracking.enabled' AND s.setting_value = 'true');
```

- 此 SQL 接受“预留已提交但 mail_record 尚未落库”的有效 token；保存早到信号。未确认成功的行不会被列表/统计读到。不能为等 SMTP 结果做循环等待。
- 开关关闭语义：成功保存之后新开始处理的请求不计；已在执行的 SQL 按数据库事务顺序完成，不声称撤销已完成信号。再次开启后，过去带 token 邮件可继续记录；未插 token 的历史邮件无法补追踪。
- 预留在“本次发送准备阶段”读取开关；已准备且正在发送的 MIME 不因后续关开关被撤回。关闭后像素记录仍被 SQL 阻止。
- `service` 通过 Spring `@Value("\${talent-introduction.mail-open-tracking.base-url:}")` 读部署地址；只允许绝对 HTTPS URI，有 host，无 userinfo/query/fragment；允许部署 context path；拼接固定 `/t/mail-open/{token}.gif`。不取 request Host/Forwarded，不主动探测 URL，不写死域名。
- URL 空/不合法：GET settings 返回 configured=false；PUT true=400，false可保存。域名证书/路由可达性留给真实部署验收，不能返回“连接成功”。

### T3 — 路由与数据契约（I-1/4/5/6/7）
文件：新增 `mail/controller/MailOpenTrackingController.kt`；`application.yml` 加：

```yaml
  mail-open-tracking:
    base-url: ${MAIL_OPEN_TRACKING_BASE_URL:}
```

位于既有 `talent-introduction` 节点；不新增 application 启动扫描或第二份开关配置。

| 路由 | 请求 | 响应/限制 |
|---|---|---|
| GET `/api/mail-open-tracking/settings` | 无 | `{enabled,configured,baseUrl}`；baseUrl不含token |
| PUT 同路径 | `{enabled:boolean}` | 持久化后同结构；缺失/非bool=400，不默认为false |
| GET `/api/mail-open-tracking/records` | from,to必填；senderAccountCode,status,keyword,pageSize,pageOffset可选 | 下面的 snapshot；pageSize默认20、限制1..100（超范围400）；offset默认0且>=0；status只接受ALL/OPENED/NO_SIGNAL/NOT_TRACKED；from<=to；keyword最多200字符 |
| GET `/api/mail-open-tracking/records/{mailRecordId}` | 正整数 | 同一列表行的详情，非成功外发/不存在=404；不泄露token |
| GET `/t/mail-open/{token}.gif` | token | 200 image/gif、固定1×1透明GIF、Cache-Control:no-store,no-cache,must-revalidate,max-age=0；无Set-Cookie/ETag/重定向 |
| HEAD 同图片路径 | token | 同图片响应头，无正文、无统计副作用；必须显式区分 method，不能被Spring隐式GET委托记一次打开 |

单个 controller 可以用各 method 的完整路径映射，公开/登录边界由既有 interceptor 路径控制；不要为管理接口添加认证豁免。

records response：
```text
{
  records:[{mailRecordId,expertContactId,expertName,recipient|null,
            senderAccountCode,mailType,subject,sentAt,
            trackingStatus,firstOpenAt|null,lastOpenAt|null}],
  totalCount,
  summary:{trackedSent,opened,openSignalRate|null}
}
```

- 不返回完整正文、不返回 token、不返回跟踪 URL；详情只比列表补 `messageId`，无逐次事件历史。
- 基础 SQL：mail_record m LEFT JOIN mail_open_tracking t ON t.id=m.open_tracking_id LEFT JOIN expert_contact ec ON ec.id=m.expert_contact_id；要求 OUTBOUND/SENT/sent_at。从 t.recipient 取快照，t不存在为null。expertName 仅作当前名称显示。
- records/count 使用相同时间、账号、状态、keyword。keyword 用参数化 LOCATE 匹配 ec.expert_name/t.recipient/m.subject；不能拼SQL，不让 `%` 变成未说明的通配符。
- summary 只跟随日期和账号，不随状态/keyword/分页变化；分子分母来自同基础集，`trackedSent=COUNT(t.id)`，`opened=SUM(first_open_at IS NOT NULL)`，rate=opened/trackedSent（0..1）。不要按专家去重，重复非回复邮件分别计数。
- 排序 `m.sent_at DESC,m.id DESC`；数据库分页，不全表拉到内存；显式 REPEATABLE_READ 只读事务统一 snapshot，不假设外部数据库默认隔离级别。继承项目已有 direction/type/sent、sender/sent 索引，先 EXPLAIN 实测，再决定是否另起有证据的索引调整，不能顺带加索引群。
- token 格式不合43字符直接返回同GIF；合法未知token同GIF；数据库错误仅记录不含完整token/邮箱的诊断，公开响应不暴露异常。POST/PUT 图片路由不支持。

### T4 — 验证（I-1～7）
文件：下面四个测试文件。FlywayMigrationIntegrationTest只更新“迁移到最新版本”的140断言，保留验证历史阶段的旧target；新增140→141升级与历史行null用例。真实 MySQL IT 验证DDL、代理REQUIRES_NEW、并发原子更新；MockMvc验证路由/认证；单元测试验证输入/DTO/URL与计数口径。禁止用H2证明MySQL语法或仅mock证明事务。

## 变更文件清单

共10个文件，两个子系统：跟踪数据/配置；HTTP读取和像素。路径外不得顺手改动。

| # | 文件 |
|---|---|
| 1 | `src/main/resources/db/migration/V141__create_mail_open_tracking.sql` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepository.kt` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailOpenTrackingService.kt` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailOpenTrackingController.kt` |
| 6 | `src/main/resources/application.yml` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepositoryIT.kt` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailOpenTrackingServiceTest.kt` |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailOpenTrackingControllerTest.kt` |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` |

## 验收标准
- I-1：无key/非法值关闭；并发PUT同key不重复行；配置缺失PUT true=400，PUT false成功；旧key逐值不变。
- I-2：历史mail_record新列全null；允许多行null；同tracking id不允许关联第二邮件；不存在id拒绝；token大小写敏感；GET DTO无token/url；迁移新库和140升级均通过。
- I-3：MySQL中外层事务先创建contact并保持未提交，reserve经Spring代理插入；另一连接可见t行；外层回滚t仍在；预留失败不把外层事务标回滚。不能在没有代理的手工new对象上验证。
- I-4：并发100次请求只改一行聚合；逆序时间first=min、last=max；关闭/HEAD/未知/非法不更新；GET提前到达在事务提交后的统计中可见。
- I-5：fixture含2条tracked SENT（1opened）、1条FAILED、1条INBOUND、1条untracked SENT、1条孤儿t；summary=2/1/0.5，基础列表3条；status筛选为OPENED时列表1但summary仍2/1/0.5；0分母=null。
- I-6：开启真实auth的MockMvc验证匿名管理401（AuthInterceptor:22/29）、登录可读写；匿名pixel200且GIF可解码；HEAD不落事件；不更改AuthWebConfig。
- I-7：上海午夜边界、同sent_at稳定id排序、空结果、超范围分页、unknown status、反向日期、SQL特殊字符都有断言；snapshot调用一次只读事务。查询计划记录实测，不虚构性能数字。
- 运行：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest -DskipNodeTests=true`。
- MySQL：使用仓库Testcontainers先例与现有 `mysqlIt`/`migrationIt` 开关；`mvn test -DmysqlIt=true -DmigrationIt=true -Dtest=MailOpenTrackingRepositoryIT,FlywayMigrationIntegrationTest -DskipNodeTests=true`（同JDK11）。Docker不可用须记NOT_RUN，不能当PASS。

## 人工验收清单
### A-1: 配置与权限
- 前置条件: 测试实例启用现有登录保护；不开生产开关。
- 操作步骤: 1. 未登录访问settings。2. 登录后读取。3. 未配base URL时开启。4. 保存关闭。
- 预期结果: 依次401、enabled=false/configured=false、400、enabled=false；原全局自动回复开关值不变。
- 覆盖: O-1、N-2/N-3、I-1/I-6、IP-4
### A-2: 信号与关闭
- 前置条件: 在独立测试库通过SQL准备同一天的2条跟踪SENT外发（其中1条first_open_at有值）、1条未跟踪SENT外发、1条FAILED、1条INBOUND和1条未关联跟踪行；均关联测试联系人。保存其中未打开跟踪行的token，不使用真实专家邮件。
- 操作步骤: 1. GET有效图片。2. 读详情。3. 关闭开关后再次GET。4. HEAD同地址。
- 预期结果: 有效图片200；首次/最近字段有值；关闭后及HEAD不再变化；原记录仍能查询。
- 覆盖: O-2、I-2/I-4/I-6、IP-2/IP-4
### A-3: 列表统计口径
- 前置条件: 用SQL在独立测试库准备同一天/同账号的2条跟踪SENT外发（其中1条first_open_at有值）、1条未跟踪SENT外发；另准备1条FAILED、1条INBOUND和1条未关联跟踪行，全部使用测试联系人。
- 操作步骤: 1. 查全部。2. 筛OPENED。3. 切换无数据日期。
- 预期结果: 全部3条、trackedSent=2/opened=1/rate=.5；筛后1条且统计不变；空日期0/0/null。
- 覆盖: O-1、I-5/I-7、IP-1
### A-4: 已有功能回归
- 前置条件: 保存旧自动回复开关及邮件监控总览值；测试收件箱与退订测试token可用。
- 操作步骤: 1. 部署01且保持新开关关闭。2. 查看旧监控、邮箱配置、历史正文。3. 对自己邮箱执行一封现有测试邮件与测试退订。
- 预期结果: 旧配置与历史正文未改；旧总览口径不变；邮件正常收取，退订入口仍生效；新功能不主动发送。
- 覆盖: N-1/N-2/N-3、I-1、IP-3
### A-5: 公网部署条件
- 前置条件: 测试环境配置真实HTTPS base URL，包含实际context path；由操作者使用可外网访问网络。
- 操作步骤: 1. 匿名访问有效像素地址。2. 比较应用访问日志与详情时间。3. 访问管理API。
- 预期结果: 图片200无登录跳转，后台出现信号；管理API仍要求登录；若失败保留开关关闭并记录DNS/证书/代理具体失败。
- 覆盖: O-2、I-6、部署未知项
