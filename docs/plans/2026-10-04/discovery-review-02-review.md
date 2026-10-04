# 02：持久审核与当前页操作

依赖：01。本阶段新增真实审核 API；发送切换由主计划发布门控制。

## 需求描述

深度发现可查询完整审核原因，单个、所选、当前页确认后持久保存通过/暂缓/不通过，支持历史与撤销。保持专家事实、已有联系人状态与既有发件账号绑定；无真实SMTP。

范围外：所有页后台枚举（03）、批量发信开关切换（05/D1）、补齐专家事实。

## 关键不变量

### Invariant I-1：当前结论与历史各司其职
- Rule：两张相关 MySQL 表：`expert_discovery_admission` 每个真实docId一行当前结论/版本；`expert_discovery_review_item` 每个快照目标一行，保存决策及应用结果。前者支持事务行锁/版本CAS，后者同时承担固定名单和历史；不再另建任务表、事件总线或ES字段。
- Applies to：迁移、repository、review service。
- Violation consequence：并发覆盖、历史丢失或重复存储漂移。
- 来源：原始；现有 TaskExecution 已承接任务头。

### Invariant I-2：批准对同身份有效
- Rule：身份键为真实docId+规范化邮箱+givenNames/familyNames，精确绑定；研究方向、机构、国家、指标、分类后续变化不使人工批准失效。身份改变显式显示“身份已变化，原审核不适用”，自动通过或重新审核后准入。有效人工批准优先于自动问题；HOLD/REJECTED 不被自动覆盖；REVOKE重新运行自动校验，未通过回NEEDS_REVIEW。
- Applies to：提交、查询、撤销、04重验。
- Violation consequence：批准套到另一个人或批准后被旧问题反复拦截。
- 来源：用户要求；DiscoveryIdentity原有绑定语义。

### Invariant I-3：确认名单与操作者可信
- Rule：页面/所选请求提交真实docIds+所见版本，最多1000人；服务端创建STAGED快照并返回batchKey/hash。confirm只接受这个固定快照；actor从登录session读取，不接受客户端伪造用户名。拒绝必须填备注1–1000字符，其余可选。所有写API复用登录保护；操作不发送邮件。
- Applies to：controller、prepare/confirm/revoke。
- Violation consequence：越界批审、冒名授权。
- 来源：用户确认要求；AuthSessionKeys。

### Invariant I-4：成功必须持久且可恢复
- Rule：item状态STAGED→READY→APPLYING→APPLIED；可出现STALE/FAILED/CANCELLED。数据库内更新当前结论与标记APPLIED在同一事务；提交前比较admission revision和身份。重复confirm返回原结果。02只持久化审核，不做ES晋升；04接投影，UI分别显示“审核已保存/候选同步待完成”，不能把两者混成一次虚假成功。
- Applies to：事务、并发、重试、历史接口。
- Violation consequence：点了通过但无记录、旧请求覆盖新审核。
- 来源：原始；X2/X5。

## 现状审计

当前没有审核表；三层mapping dynamic:false。现有 ExpertIndexResponse:374不提供凭证，不能复用其缺失值推断原因。新review查询使用 Writer.readDiscoveryDocument(level,docId):695获取真实source和seq/term；discoveryProfile:715缺凭证字段，必须补装载。这不会新增ES映射。

已有 AuthInterceptor `/api/**`、AuthSessionKeys.USERNAME；任务与审核数据登录内访问。当前ExpertSearchService分页及scroll有自己的sourceFields白名单；本子计划不更改它，避免漏出字段影响所有消费者。完整读写清单见公共审计E5；新增两表读写仅本子计划service/repository，04才接自动结果。

交互：ES读→解释→快照（X2）；MySQL事务→列表/历史（X5）；姓名/邮箱变更→旧批准不迁移（X3）。

## 实现方案

1. **I-1/I-2/I-4**：新迁移 `V148__create_expert_discovery_review.sql`（当前最高V147；执行前若被其他工作占用，先修订文件名，禁止覆盖迁移）。
   - admission：`expert_doc_id VARCHAR(128) COLLATE utf8mb4_bin PK`，identity_hash CHAR(64)，decision VARCHAR(24)，revision BIGINT，decision_item_id nullable BIGINT，policy_version VARCHAR(32)，checked_at DATETIME(3)，updated_at。decision仅AUTO_PASSED/NEEDS_REVIEW/MANUAL_APPROVED/LEGACY_APPROVED/HOLD/REJECTED；无第二个eligible布尔。
   - review_item：id BIGINT PK，batch_key VARCHAR(64)，expert_doc_id同上，source_level VARCHAR(16)，identity_hash、snapshot_hash CHAR(64)，expected_revision BIGINT，action VARCHAR(16)（APPROVE/HOLD/REJECT/REVOKE），state VARCHAR(16)，snapshot_json LONGTEXT，reason_snapshot_json TEXT，actor VARCHAR(100)，note VARCHAR(1000)，previous_item_id nullable BIGINT，execution_id nullable BIGINT，error_code VARCHAR(64)，created_at/confirmed_at/applied_at DATETIME(3)。UNIQUE(batch_key,expert_doc_id)，索引(docId,id)、(batch_key,state,id)。不为task_execution设外键，避免任务清理删除历史。
   - snapshot包含规范筛选条件、所见事实及原因、ES seq/term、准备时间；姓名/邮箱只在鉴权接口返回。复用当前MySQL版本支持的DDL，不依赖CHECK在5.7真实执行。
2. **I-1/I-4**：repository用JdbcTemplate参数绑定与事务；初始化admission行 INSERT IGNORE，再SELECT FOR UPDATE；只比较预计revision并递增。失败回滚，不伪装APPLIED。不要以“最新自增id”替代并发控制。
3. **I-2/I-3**：`GET /api/discovery/review/experts`，参数level（默认RAW）、tag（默认discovered）、from、size、q、issue、decision；所有页范围与其一致。列表返回真实事实、原因、自动/人工结果、当前版本、已审核人/时间；详情额外返回存储证据。暂无可靠发现批次直接关联字段，不新增“按批次”伪筛选；来源/发现时间仅按确实存储值展示，未知就未知。issue/decision/q为服务端过滤，不只过滤当前页。02先在ReviewService内实现完整source读取与分批准入查询，03抽出同一实现为ScanService，不写第二套条件。基础ES可筛项前推，需跨MySQL判定的项逐500批处理后分页/精确计数；不能先截20条再筛选。重查询提供取消/超时，超时时返回明确错误而非不完整总数，不新增资料缓存。
4. **I-3/I-4**：`POST /api/discovery/review/batches/prepare`，scope=IDS，action、docIds、expectedRevisions、note；`POST /batches/{batchKey}/confirm`仅batchHash；`GET /batches/{batchKey}`明细计数；`GET /history?docId=...`；`POST /items/{id}/revoke`创建新REVOKE动作，只撤销仍是当前有效的决策，否则409。GET无写副作用； prepare/confirm 同源请求校验，actor取session。
5. **I-2/I-4**：批处理逐项事务、结果分类；无邮箱/格式非法不伪造地址，可人工批准资格，但状态说明“无可投递地址”；不能展示“发送成功”。调用方以后实际地址构造失败必须返回明确执行错误，不能悄悄过滤。
6. **I-1–I-4**：测试真实事务回滚、同doc并发两个不同审核、幂等确认、备注校验、未登录401、客户端actor无效。模型/DTO放同一新domain文件，repository集中两表访问，避免为每个接口拆一套类。

## 变更文件清单

共9文件，审核持久化/API两个相关子系统；既有共享存储新增字段0。

| 文件 | 操作 |
|---|---|
| src/main/resources/db/migration/V148__create_expert_discovery_review.sql | 新增两张相关表 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryReview.kt | 新增实体/DTO/枚举 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryReviewRepository.kt | 新增参数化SQL |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt | 新增查询/准备/确认/撤销 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewController.kt | 新增登录态API |
| src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt | 补完整profile读取；不改ES写语义 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt | 新增 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryReviewRepositoryIT.kt | 新增MySQL事务集成 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt | 新增 |

## 验收标准

- I-1：迁移空库/已有库均可执行；两张表约束有效；没有ES字段新增、没有审计只留浏览器内存。
- I-2：人工批准缺机构者后更新研究字段仍批准；更换邮箱显示旧授权不适用；HOLD不被自动改写；REVOKE恢复自动结果。
- I-3：未登录不读写；actor来自session；名单/hash篡改拒绝；1001项拒绝；reject空备注400。
- I-4：100条含3条版本冲突→97APPLIED+3STALE；重复确认不新增有效决策；注入事务失败当前结论不改变。
- X2/X3/X5：读取真ES源、写DB、刷新/重启后审核历史一致；无SMTP调用。

## 人工验收清单

### A-1：整页与所选
- 前置条件：隔离环境页大小20，准备20名待审核样本。
- 操作步骤：1. 用接口prepare当前20名，查看返回清单。2. 改列表页但不改快照。3. confirm。4. 刷新和重启后再查询。
- 预期结果：恰好原20名MANUAL_APPROVED，actor为当前登录人；新增/另一页人不改变；记录仍在。
- 覆盖：I-1/I-3/I-4、X2/X5。

### A-2：拒绝、暂缓、撤销与并发
- 前置条件：2个登录会话，同一名待审核专家。
- 操作步骤：1. A准备批准，B先拒绝并填“需复核”。2. A确认。3. B撤销当前拒绝。4. 更新机构研究字段后再查看。
- 预期结果：A得到STALE不覆盖B；拒绝备注持久；撤销后按自动结果显示AUTO_PASSED或NEEDS_REVIEW；原始事实未被审核修改。
- 覆盖：I-2/I-4、必须保持项、X3。

### A-3：权限与失败
- 前置条件：未登录窗口、可注入数据库失败的验收环境。
- 操作步骤：1. 未登录调用confirm。2. 登录后制造提交失败。3. 查看历史与专家事实。
- 预期结果：步骤1为401；步骤2不显示APPLIED；无联系人状态/账号绑定变化，无邮件投递。
- 覆盖：I-1/I-3/I-4、全部must-not-change。
