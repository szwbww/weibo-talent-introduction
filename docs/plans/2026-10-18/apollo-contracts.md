# Apollo P1 公共数据与接口契约

本文件是01～07的规范性附录；所有新增定义都是**拟实现契约**，不是声称当前代码已有。基线/路径证据见[审计](apollo-code-audit.md)。

## C-1：配置与上线范围

新增 `DiscoveryVerificationProperties`，prefix=`talent-introduction.discovery-verification`；在同文件定义 `@Configuration @EnableConfigurationProperties(DiscoveryVerificationProperties::class)`，不改全局配置注册。

|属性|默认|语义|
|---|---|---|
|enabled|false|允许新run登记；关闭不撤销已有ENFORCE gate|
|worker-enabled|false|允许领取/对外请求；关闭保留任务|
|mode|AUDIT|仅允许AUDIT/ENFORCE；run创建时冻结|
|apollo-default|true|仅enabled且服务ready时用于新启动默认；不会自行开feature|
|apollo-api-key|空|仅环境变量/既有服务器密钥配置注入，toString/日志脱敏|
|requests-per-minute|10|本功能自限，不是声称账户官方限额|
|requests-per-day|500|UTC自然日数据库累计尝试数，发送前原子占位；多节点共享|
|max-requests-per-item|12|所有宽搜/变体/窄搜/重试合计上限；预算到顶停为SEARCH_INCOMPLETE|
|max-pages-per-query|5|100条/页，本应用保护值；未穷尽不判NO_MATCH|
|max-transient-attempts|3|超时/5xx退避30s/120s/600s，第三次失败后ERROR|
|lease-seconds|120|每30秒续租；过期可重领，旧token不能完成|
|poll-ms|5000|worker空轮询上限频率；无阻塞长sleep|
|orcid-public-base-url|https://pub.orcid.org/v3.0|只读公开记录；项目现有OrcidProperties没有token字段，不假设已有认证|
|orcid-access-token|空|新增可选只读token环境配置；无token尝试公共读取，401/403按接口错误留痕，不声称已有访问能力|
|http-connect/read-timeout|3s/15s|单请求超时；响应上限2MiB|

enabled=false：保持旧发现行为，UI显示“未启用资格核验”；enabled=true但缺必要配置：capabilities.ready=false，Apollo选项不可选并显示原因；若请求显式apolloAssist=true则503拒绝启动，不静默关闭。apolloAssist=false仍执行新资格门槛及可用证据核验，不调用Apollo。`mode=AUDIT`结果只展示“影子核验建议”，自动准入计数为0，另列suggested计数；不得以AUDIT结果升级真实准入。默认参数是本计划设计值，执行前须校验实际账户限额适配，不能把500当供应商保证。

## C-2：新表完整DDL

V158截至基线未占用；实施时如已被占，先修订计划，不覆盖迁移。现有共享表新增字段0；下面三表是同一新核验子系统。时间UTC DATETIME(3)，前端按现有北京时间格式。无外键，避免task_execution独立清理牵连。保留账本/准入证据不自动清理；attempt响应仅存限定字段，禁止原始页面或PDF全文。

```sql
CREATE TABLE discovery_verification_run (
  execution_id BIGINT NOT NULL,
  mode VARCHAR(16) NOT NULL,
  policy_version VARCHAR(32) NOT NULL,
  apollo_requested TINYINT(1) NOT NULL,
  settings_json TEXT NOT NULL,
  input_closed TINYINT(1) NOT NULL DEFAULT 0,
  revision BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE discovery_verification_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_execution_id BIGINT NOT NULL,
  expert_doc_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  identity_hash CHAR(64) NOT NULL,
  policy_version VARCHAR(32) NOT NULL,
  source_name VARCHAR(32) NOT NULL,
  source_item_key VARCHAR(128) NOT NULL,
  batch_number INT NOT NULL,
  snapshot_json MEDIUMTEXT NOT NULL,
  fact_fingerprint CHAR(64) NOT NULL,
  state VARCHAR(24) NOT NULL DEFAULT 'RESERVED',
  outcome VARCHAR(32) NULL,
  qualification VARCHAR(24) NOT NULL DEFAULT 'NOT_EVALUATED',
  route VARCHAR(16) NOT NULL DEFAULT 'NONE',
  apollo_entered_at DATETIME(3) NULL,
  search_completed_at DATETIME(3) NULL,
  candidate_found_at DATETIME(3) NULL,
  person_matched_at DATETIME(3) NULL,
  apollo_person_id VARCHAR(128) NULL,
  evidence_json MEDIUMTEXT NULL,
  reasons_json TEXT NULL,
  request_count INT NOT NULL DEFAULT 0,
  transient_attempts INT NOT NULL DEFAULT 0,
  generation BIGINT NOT NULL DEFAULT 0,
  lease_token VARCHAR(64) NULL,
  lease_until DATETIME(3) NULL,
  next_attempt_at DATETIME(3) NULL,
  admission_revision BIGINT NULL,
  projection_state VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
  projection_error VARCHAR(128) NULL,
  activated_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dvi_identity (expert_doc_id, identity_hash, policy_version),
  KEY idx_dvi_run_batch (owner_execution_id, batch_number, id),
  KEY idx_dvi_claim (state, next_attempt_at, lease_until, id),
  KEY idx_dvi_projection (projection_state, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE discovery_verification_attempt (
  id BIGINT NOT NULL AUTO_INCREMENT,
  item_id BIGINT NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  provider VARCHAR(16) NOT NULL,
  query_kind VARCHAR(16) NOT NULL,
  page_number INT NOT NULL,
  attempt_number INT NOT NULL,
  requested_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  http_status INT NULL,
  result_code VARCHAR(64) NOT NULL,
  request_json TEXT NOT NULL,
  response_json MEDIUMTEXT NULL,
  response_sha256 CHAR(64) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dva_request (request_key),
  KEY idx_dva_item (item_id, id),
  KEY idx_dva_budget (provider, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`settings_json`只存非秘密配置；`policy_version`= `APV_20261018_V1_AUDIT` 或 `APV_20261018_V1_ENFORCE`，均≤32。`snapshot_json`冻结docId、姓名/邮箱、身份凭证、原机构、原始来源/链接、ORCID/OpenAlex标识、已有准入revision及构造时间；不得用它回写专家整文档。`fact_fingerprint`是规范JSON SHA256，字段白名单为identityHash、externalIds中的ORCID/OpenAlex、institution、employment、degree、age、原RAW已有可验证凭证摘要、当前硬阻断的配置快照。新获取的Apollo/ORCID响应摘要属于evidence_json，不混入RAW输入fingerprint，避免新证据产生后反过来使自身输入哈希失效。变动重查，不把“姓名哈希相同”当“资格证据仍适用”。

表内state/route/qualification/outcome值域由Kotlin显式校验，MySQL5.7不能依赖CHECK执行。新增字段读写责任：登记服务写run/RESERVED及snapshot；worker写租约/查询/证据/结果；提交服务写admission_revision/projection；查询服务只读。所有计数从item/attempt推导，不新增总数缓存列。

## C-3：状态机、恢复、并发

- state=`RESERVED → QUEUED → RUNNING → RETRY_WAIT/COMPLETED/ERROR`。RESERVED没有activated_at，排除准入分母；RAW失败留RESERVED供来源重试，同run输入关闭且来源重试已终结者标ERROR但仍activated_at=null，只入“收录登记异常”，不得偷偷算123的第124条。
- outcome终态=`DIRECT_CLEAR/APOLLO_CLEAR/APOLLO_UNCLEAR/PENDING/PROTECTED`；state=ERROR时outcome必须null。待处理outcome也null。qualification=`NOT_EVALUATED/MET/UNKNOWN/NOT_MET/CONFLICT`，route=`NONE/DIRECT/APOLLO`。
- projection_state=`NOT_REQUIRED/PENDING/PROJECTED/ALREADY_PRESENT/APPLICATION_PRESENT/ERROR`。只有真实AUTO_PASSED提交完成才有后三类成功值。既有candidate/application存在只代表可用投影或保护，不算本次新建数。
- identityHash不同永不复用；同hash事实变化不直接复用准入证据。worker保存冲突原文后PENDING，reason=`FACT_CHANGED`，需重新核验。新规则版本只对新登记输入生效，旧凭证版本按原规则读，不全表失效。
- 固定锁序 `run → admission（如需）→ item → attempt`。一个事务最多一个run；批量查询不用写锁；外部HTTP和ES在事务外。领取分配UUID token并generation+1，完成/续租WHERE必须含id/token/generation/state。事务失败不改计数。
- 查询请求先原子占budget并落attempt `DISPATCH_RESERVED`。多实例使用数据库命名锁 `GET_LOCK('discovery_verification_apollo_budget',1)`，**同一Jdbc连接**执行预算查验与attempt插入后`RELEASE_LOCK`，再出网；拿不到锁延后不算错误。崩溃留下的已占位请求按已使用计预算，不谎称供应商未收到。每次实际重发单独attempt，不能实现HTTP外部副作用恰好一次；只保证结果/准入幂等。分页进度作为evidence_json内独立progress对象保存：stage=LOCAL_EVIDENCE/APOLLO_SEARCH/DECIDE/PROJECT，queryIndex/page/complete/candidateIds/finishedQueryKeys；数据schemaVersion=1，禁止和已确认字段证据混用。Apollo排队指标只有在LOCAL_EVIDENCE完成且将进入APOLLO_SEARCH时才设置entered_at。
- 429解析Retry-After（秒或HTTP日期），否则60秒；RETRY_WAIT，不算无候选，不消耗transient_attempts，但实际request_count和日预算增加。401/403→ERROR CONFIG_AUTH，停止该worker后续领取，capabilities显示需处理配置；不自动升级接口。400/422→ERROR REQUEST_INVALID；5xx/超时按3次策略。成功HTTP但缺people/total_entries或类型错误→ERROR RESPONSE_INVALID，不能按空结果。
- 仅RUNNING租约过期可重领；终态不得被旧回包覆盖。完成核验后同一MySQL事务存evidence/outcome并调用共享准入服务；commit失败回滚。提交成功后候选同步失败只重做投影，不重调Apollo、不再次增加自动准入。
- 准入落库后人工再HOLD/REJECT属于新审核动作：保留“本次自动准入”历史事件，逐条显示当前decision和revision。projection重试提交前后均检查当前decision/identity，若已被人工改写则停止并标`SUPERSEDED_BY_REVIEW`；不得继续投影或把人工决定回写为AUTO。当前可发送仍由原持久准入selector控制。
- 采集关闭只设run.input_closed；核验active=sum(QUEUED/RUNNING/RETRY_WAIT)；input_closed且active=0才显示核验已结束。run的采集SUCCESS不写成核验SUCCESS。历史TaskExecution被保留策略清掉后账本仍可按executionId只读；不在缺失task行时创建新run。

## C-4：证据与规则输入

`VerificationEvidence`：schemaVersion=1；identityHash、factFingerprint、provider、sourceUrl、fetchedAt、sourceUpdatedAt可空、responseSha256、field、value、jsonPointer/原始短摘录、assertionSource（组织/本人/未知）、temporalScope（CURRENT/HISTORICAL/UNKNOWN）、conflicts[]。没有来源的value不能转MET。

姓名规范化仅NFKC、大小写/空白/标点规范化、已知中间名有/无；其他姓名变体必须来自当前绑定ORCID.other-names，不能自行补全姓。Apollo宽搜先于机构窄搜；不加教授/年龄/国家过滤来预先丢候选。所有成功页按Apollo ID并集去重，5页/查询到顶仍有下一页则SEARCH_INCOMPLETE。

自动本人匹配需同时：①原source身份凭证有效并绑定当前邮箱、全名；②有独立当前任职证据（绑定ORCID、机构来源条目，保留来源）把同一全名/ORCID连到该机构；③Apollo响应全名严格匹配，或q_person_name使用该已证全名且first_name/遮蔽姓可兼容并无相反姓名信息；④宽搜穷尽后，仅一个候选与独立机构/身份事实一致，无ID/机构冲突。③遮蔽姓只是兼容检查，**没有①②④时必须待核验**，不能凭唯一命中、同校或邮箱域名放行。ORCID没有绑定、只有历史署名机构、多个匹配均PENDING。若来源不能证明ORCID记录属于本人，不调用随机同名ORCID做补证。

资格MET：组织提供且当前有效的教授类任职，或已完成博士教育证明+可靠日期年龄矩阵。ORCID end-date非空的education本身不能证明毕业，须明确完成/授予语义；没有degree完成证明保持UNKNOWN。ORCID自述任职可作线索，不能单独升为“已明确”。Apollo title可作为核验线索但不把遮蔽候选职称当本人的已明确资格。任职证据过时、历史条目/退休称号、同名另一人的学位进入UNKNOWN/CONFLICT。首版无法解析的官网内容只展示待补证，不临时引入LLM或猜测规则。

新门禁只替代纳管档案的学历/年龄判断。身份来源、独立邮箱格式/验证、明确研发范围排除、明确国籍限制和其他配置硬阻断继续遵守现有policy。Apollo身份匹配不修复JATS/ORCID源身份凭证，不写 `identityVerification/degree/age/institution/emailVerifiedLevel`。

## C-5：报表API

均在现有认证的`/api/**`下；GET只读，不隐式触发外部请求/重核验。不增加公开预览数据接口。

|方法与路径|请求|响应/边界|
|---|---|---|
|GET /api/expert-discovery/verification/capabilities|无|enabled,workerEnabled,mode,ready,apolloDefault,reasonCodes；无密钥|
|GET /api/expert-discovery/verification/summary|executionIds=逗号分隔，1～50个正ID|每ID coverage,counts,lifecycle,snapshotVersion,asOf,mode,collectionStatus|
|GET /api/expert-discovery/verification/executions/{id}/batches|page默认0,size默认20，1～100|totalBatches，items，runCounts，snapshotVersion；按batch_number稳定排序|
|GET /api/expert-discovery/verification/executions/{id}/records|route=ALL/DIRECT/APOLLO，result=ALL/CLEAR/UNCLEAR/PENDING/ERROR/QUEUED/PROTECTED，q≤100字符，page,size,batchNumber可选正整数|total,items,counts,snapshotVersion；服务端过滤后分页，id稳定排序|
|GET /api/expert-discovery/verification/items/{itemId}|正ID|原始身份/机构线索、每字段证据、完整reasons、attempt摘要、route/qualification/outcome、当前admission/revision、projection；禁止原始密钥响应|

非法枚举/参数400，不存在item404；旧执行不存在新账本返回coverage=NOT_RECORDED计数null；有账本0条返回RECORDED与0。所有响应一个REPEATABLE_READ只读事务内聚合，各写事务同步run.revision+1；同请求总计/分页数据一致。两个独立请求snapshotVersion不同，前端整块采用新快照，不把旧页与新汇总拼成精确对账。

`counts`固定键：`profiles,autoAdmitted,directAdmitted,apolloAdmitted,clear,unclear,pending,errors,queued,protected,projectionPending,projectionErrors,apolloEntered,apolloSearched,apolloCandidates,apolloMatched,apolloHttpAttempts`。queued含QUEUED/RUNNING/RETRY_WAIT；errors含activated后的ERROR；RESERVED异常另列registrationErrors。projection指标不参与profiles加和。reason计数从同筛选的档案去重；完整reason对象 `{code,label,count}`，未知code显示原码，不用“其他”吞掉。

records固定列：`itemId,docId,name,email,institution,source,batchNumber,route,resultLabel,missingFields,reasonCodes,updatedAt,currentAdmissionDecision,projectionState`。q参数转义SQL LIKE的 `%/_/\\`，参数绑定；输出HTML统一escapeHtml，链接仅http(s)。`result=CLEAR`取DIRECT_CLEAR/APOLLO_CLEAR；UNCLEAR只APOLLO_UNCLEAR；PENDING不是UNCLEAR。

若候选同步异常，汇总行附“已准入，候选同步待处理 X”，不会把X混在Apollo接口错误。若人工后续改变结论，记录仍保留本次结果，同时当前审核状态显示HOLD/REJECTED，用户不会把历史准入当当前可发送。

## C-6：固定验收样本

F123是**报表测试夹具**，不可写进生产业务或默认SQL数据迁移。其40/40/43人为批次用于复现已批准预览，不用来验证05的每100档案分配算法；实际分批另用101条→100/1验收。测试helper在隔离测试库插入run +123个合成docId，不带真实个人资料；test HTTP禁止真实网络。batch1/2/3分别40/40/43，固定时间顺序，邮件域名example.invalid。

|批次|DIRECT_CLEAR|APOLLO_CLEAR|APOLLO_UNCLEAR|PENDING身份歧义|PENDING无候选|ERROR|QUEUED|合计|
|---|---:|---:|---:|---:|---:|---:|---:|---:|
|1|6|8|6|8|5|2|5|40|
|2|5|7|6|9|6|1|6|40|
|3|4|6|7|9|8|2|7|43|
|总计|15|21|19|26|19|5|18|123|

期望：auto55，direct15，apollo40，clear36，unclear19，pending45，errors5，queued18。entered108；searched85（40准入+26歧义+19无候选）；candidates66；matched40。ERROR和QUEUED的searchCompleted均null。projectionPending0；追加投影故障fixture时auto不变，projectionErrors独立增加1。

F-RULE：当前教授+可靠组织来源→DIRECT_CLEAR；绑定Apollo本人+年龄未知→APOLLO_UNCLEAR；完整同人+已获博士+39岁→CLEAR；恰好40无副教授资格→NOT_MET待核验；40有可靠当前副教授→MET；博士在读/历史Professor/姓名碰撞/机构矛盾/遮蔽姓单独命中→不得CLEAR；查询未完→SEARCH_INCOMPLETE；403→ERROR；完全搜索0候选→PENDING APOLLO_NO_MATCH；protected7类不写准入。

## C-7：测试/上线约束

Kotlin命令使用JDK11：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=<该片测试类>`；MySQL并发测试使用隔离Docker MySQL5.7/8.0，不连接线上或既有共享业务库，不复制会DELETE共享表的旧IT清理套路。新IT通过Testcontainers动态数据源，无Docker为“未执行”，不是PASS。

前端 `node --test src/test/js/discoveryVerification.test.js src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js`；`node --check src/main/resources/static/app.js`；完成后 `node --test src/test/js/*.test.js` 和相关既有后端回归。机测通过再做浏览器1366/1920/390宽，真实DOM而非只有stub。本计划阶段不运行不存在的测试，也不标开发完成。
