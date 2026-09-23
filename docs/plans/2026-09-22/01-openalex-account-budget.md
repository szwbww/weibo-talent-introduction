# 子计划01：OpenAlex账号免费预算

日期：2026-09-22。状态：详细计划已完成，待用户确认，未实施。
父计划：[总体设计](openalex-daily-budget-design.md)。依赖：无；部署后原发现、学术补全即可使用，后两计划尚未上线不影响本计划交付。

## 需求描述

- R-1：采集和学术补全共同遵守账号真实每日免费预算；重启、多实例、请求失败不会重新获得一份额度。实际账号本次核验为10000 credits/日，已消耗部分从官方余额扣除。
- R-2：准确区分请求成本、429冷却、日额度等待；为新专家补全保留预算，空闲保留额可供采集使用。
- 保持：研发查询及专家资格/邮箱/去重规则；现有学术补全三层局部更新；API Key只在服务端使用；其他数据源和公开全文下载继续使用自身限流规则。
- 范围外：购买额度、充值、主动接入OpenAlex收费Content下载、提高到100请求/秒、论文队列、前端重构。

## 关键不变量

### I-1：操作成本与消费用途分离
- Rule：新增Operation分类LIST=1、SEARCH=10、SINGLETON=0、CONTENT=100、RATE_LIMIT=0；RequestKind仍表示DISCOVERY/HISTORY_ENRICHMENT/NEW_ENRICHMENT。每次HTTP重试重新预占，不能把搜索当普通列表。禁止依赖URL任意substring误判；按构造请求的操作明确传入，再校验目标主机及路径。Content本期拒绝新增调用。
- Applies to：OpenAlexDataSource全部getJson调用、全文候选URL、策略预占/结算。
- Violation consequence：漏扣、重复扣或将普通全文误计为OpenAlex消费。
- 来源：当前beforeRequest固定预占1的审计；K-discovery-budget-backpressure。

### I-2：账号周期唯一预算与凭证隔离
- Rule：预算以稳定的非秘密accountScope标识账号，不能以Key明文/Key变化建立新额度；同账号全部本系统实例使用同一scope。额度周期由官方reset标识，credits用非负整数。有效免费上限=min(官方日免费额度,配置保护上限)，预付余额永不加入；保护默认10000，旧dailyBudgetUsd=0仅表示采用免费保护默认值，绝不表示无限。换Key不清空已用预算；确认换账号须使用不同scope。
- Applies to：预算表、启动、日切、配置、日志和诊断快照。
- Violation consequence：多实例/换Key绕过上限、付费消耗、凭证泄露。
- 来源：原始需求及官方/rate-limit实测。

### I-3：预占先于外部请求，乱序响应保守处理
- Rule：单事务锁账号行，先预占后放行；每个permit唯一且只结算一次。UNKNOWN不能立即退还。响应真实成本小于估算可仅退还该permit有证据的差额；大于估算立即追扣并阻止后续超预算请求。响应余额只能收紧本周期provider ceiling，较旧响应不能抬高余额。有效可发额同时扣除所有尚未结算预占；宁可保守少发，不重复承诺额度。
- Applies to：reserve/settle/markUnknown/reconcile、多线程重试、异常退出恢复。
- Violation consequence：最后额度被并发透支，失败重试免费化。
- 来源：当前仅内存锁审计。

### I-4：同步与冷却不能变成长时间阻塞
- Rule：启动、官方reset到期、预算不足、异常恢复，以及每300秒同步官方余额；同账号只允许一个校准者。尚未获得可信余额或数据库不可用时，付费计量请求延期，不能退回本地10000。429记录Retry-After/退避到共享notBefore；返回Deferred(reason,retryAt)，不在scheduler或HTTP线程睡到次日。已耗尽额度不阻止允许的0-cost操作，但仍遵守429冷却及账号限速。日切必须先确认官方新reset周期；旧周期未完成permit只结算旧行。
- Applies to：请求策略、OpenAlexDataSource、日切及错误处理。
- Violation consequence：阻塞其他定时任务、假恢复、旧响应污染新一天。
- 来源：K-circuit-breaker-terminal-status的异常可见原则。

### I-5：补全保留可借用但有依据
- Rule：本周期免费额度20%为初始保留目标（10000时2000）。预算库只读现有补全任务，PENDING/RUNNING/RETRY_WAIT均为未完成；有待补时保留=min(floor(有效免费上限×保留比例),待补数×补全估算成本)，无样本按每任务10 credits估计；有样本取max(10,最近100次NEW_ENRICHMENT请求成本P95×3)作为估算，在保留目标足够时最低保留一个10-credit请求，所有预留均不得超过实际剩余额度。当前调用链没有贯穿每个专家的成本标识，因此该值是请求样本推算，不宣称实际单专家成本。该估计只用于公平分配，所有实际请求仍按I-1/I-3独立预占。无待补且连续60秒无NEW_ENRICHMENT请求时保留降为0；出现待补立即恢复保留，不撤回已经发出的请求。人工历史补全不能消费新补全保留区。
- Applies to：DISCOVERY/HISTORY_ENRICHMENT预占、补全任务只读统计、快照。
- Violation consequence：采集饿死补全或无任务仍长期闲置额度。
- 来源：总体设计的20%保留要求。

### I-6：新表字段均有生命周期
- Rule：account行唯一account_scope；day行唯一(account_scope,reset_at)；reservation行唯一permit_id并关联周期。reservation状态RESERVED→SETTLED或UNKNOWN；UNKNOWN只可由有依据的结算/周期关闭归档，不能自动当未花费。amount_reserved/amount_actual分别表示本次预占/确认成本；request_kind/operation不可混用；created_at/settled_at用UTC。rate_next_at、cooldown_until、sync_lease_token/until和last_synced_at归账号行，不因实例重启清除。免费上限、确认消耗、未结算预占及provider ceiling分别存储，禁止把snapshot可用值再次当原始余额扣减。
- Applies to：新迁移、JDBC存储、恢复、快照、清理。
- Violation consequence：双扣、丢扣、重启解除429限制、跨日串账。
- 来源：原始设计。

## 现状审计

### OpenAlex调用及内存预算
- `config/OpenAlexRequestPolicy.kt`：唯一内存策略；beforeRequest按1预占，recordResponse接收额度头并更新spent/inFlight/providerRemaining/reset。RequestKind是用途，不是成本。
- `discovery/service/OpenAlexDataSource.kt`：getJson是搜索、作者ID/ORCID/批量ID/最近论文共同入口；下载回调会读取credits响应头，但回调发生在下载之后，不能代替预占。普通PMC/出版社全文不消耗OpenAlex额度。
- `config/RestTemplateConfig.kt`：openAlexRestTemplate已有认证拦截器；policy Bean目前只传properties。保留凭证限定主机，不将Key放入普通PDF URL、日志、前端快照。
- 交互X-1：各消费用途→同一policy→共享预算；既有Deferred语义必须兼容，不更改调用方的专家门禁和补全写入。

### MySQL（新预算表与现有任务）
- 预算表尚不存在；新写路径完整集合：reserve、settle、markUnknown、reconcile、共享限速/冷却、校准租约、过期归档。新读路径：上述事务、policy.snapshot以及后续队列/前端只读消费。
- `V131__create_expert_academic_enrichment_job.sql`：expert_doc_id唯一；status、next_attempt_at、lease_token/lease_until；无本计划新增字段。
- 现有写路径：ExpertAcademicEnrichmentJobService.enqueue及ExpertAcademicEnrichmentJobRepository的入队、领取、租约、重试和终态CAS；读路径：自动worker、人工补全、统计。本计划仅新增按status统计读取，不修改这些写者。
- 交互X-2：任务入队/结束→预算保留变化；不能把next_attempt_at未来的待补误认为没有任务。

### 持续保留的存储契约
- ES三层mapping仍dynamic:false、enrichedAt keyword；本计划不写ES、不更改已有updateExpertAcademicFields的LayerUpdateResult。读写仍经原发现与补全服务。（来源：K-enrichment-write-three-layers）
- 2026-09-22官方实测daily_budget=1美元、remaining=0.9773美元、prepaid=0。费率依据：[认证与额度](https://help.openalex.org/api/authentication/)、[成本示例](https://help.openalex.org/access/example-costs/)。实施前重查，不用本次剩余值初始化未来预算。
- 当前Flyway最高V131；若实施前已被其他工作占用V132，先更新本计划具体文件名，不能覆盖已发布migration。

## 实现方案

### T-1：共享账本与原子操作（I-2/I-3/I-4/I-6）
修改文件清单1、2。V132新增`openalex_budget_account`、`openalex_budget_day`、`openalex_budget_reservation`，InnoDB，时间DATETIME(3) UTC，整数BIGINT，无API Key列。

- account：account_scope VARCHAR(64)主键、rate_next_at、cooldown_until、sync_lease_token VARCHAR(64)、sync_lease_until、last_synced_at、last_new_enrichment_at、created_at/updated_at。
- day：id主键、account_scope、reset_at唯一组合、free_limit、confirmed_spent、outstanding_reserved、provider_ceiling、closed_at、created_at/updated_at；额度字段非负约束。
- reservation：permit_id CHAR(36)主键、day_id、request_kind VARCHAR(32)、operation VARCHAR(16)、amount_reserved、amount_actual nullable、status VARCHAR(16)、created_at/settled_at；(day_id,status)索引；状态CHECK按I-6。
- JDBC repository负责DTO与事务边界，提供reserve/settle/unknown/reconcile/snapshot/claimSync。固定锁序account→day→permit；网络调用一律在事务之外。账号级rate_next_at推进200ms，控制默认5/s；等待槽位也返回retryAt，不占数据库锁睡眠。
- 同步时记录请求发起的周期/时间；只接受本周期、非陈旧快照。采用保守余额下界，不因periodic同步减少UNKNOWN预占；最迟官方下一reset关闭旧周期。展示“官方已用/本地预占/保护后可用”三值，保守冻结不伪装成真实消耗。
- 已关闭周期账本和permit保留7天后批量归档/删除，活跃周期及未解决permit不按短TTL释放；清理最多每批1000。

### T-2：策略与所有调用收口（I-1至I-5）
修改文件清单3、4、5、6、7。

- policy引入显式Operation、permitId和Deferred原因`DAILY_BUDGET/RATE_LIMIT/BUDGET_SYNC/BUDGET_STORE_UNAVAILABLE/ENRICHMENT_RESERVE`；现有外部方法保留兼容适配，新增带permit的方法供真实请求使用。生产Bean强制注入JDBC账本；同账号所有本系统实例必须使用同一数据库和scope，不把不同API Key当不同账号。免费保护依赖所有计量消费者纳入此账本；实施前核对无其他服务绕开账本消费同账号，若存在则先接入统一账本或使用独立账号，不能承诺同时存在外部消费时仍绝不触及预付余额。单元测试可注入假存储，禁止生产静默内存降级。
- DataSource逐个标注：filter/cursor列表=LIST、含官方search参数=SEARCH、`/authors/{id}`实体=SINGLETON、`/rate-limit`=RATE_LIMIT。禁止HTTP客户端在policy之外自动重试；每次实际重试回到getJson重新取得permit。超时标UNKNOWN，HTTP错误按真实响应成本或保守预占处理；授权错误展示可诊断原因，不无限重试。
- 对全文候选URL明确拒绝OpenAlex Content主机和指向OpenAlex API的非授权下载路径。本阶段不主动增加100-credit下载；RestTemplateConfig内为OpenAlex API禁用自动跨origin重定向；公共全文执行器对重定向逐跳检查并拒绝Content/API计量目的地址，不仅校验初始URL。若现存路径无法证明未收费，跳过该路径并尝试已有公开链接。认证拦截器只作用于既有允许的API origin，不随外站重定向传Key。
- properties/application.yml新增accountScope（同账号默认`primary`）、freeBudgetCredits=10000、budgetSyncInterval=300s；保留5/s、ratio0.2。旧dailyBudgetUsd>0折算credits后只能收紧保护额，0使用默认免费保护；负数配置启动失败。费用分类常量集中一处，官方费率变动须显式更新，不自动启用付费。
- 对外快照固定字段：accountScope、resetAt、officialLimitCredits、officialRemainingCredits、confirmedSpentCredits、reservedCredits、effectiveRemainingCredits、enrichmentReserveCredits、lastSyncedAt、deferredReason、retryAt；不得包含Key/认证URL。由后两计划直接读取。

### T-3：验证及独立发布（I-1至I-6）
修改文件清单8、9、10。使用可控Clock/HTTP stub、真实MySQL双连接事务测试。测试存储不可达、双实例、乱序响应、Key更换但scope不变、付费余额、日切、429。原发现和补全固定fixture回归。发布只切换共享预算，不启动新队列。

## 变更文件清单

路径均相对仓库根；恰好10个文件，2个子系统：预算存储/请求策略、OpenAlex适配。现有ES/补全任务表新增字段为0。

| # | 文件 | 改动 |
|---|---|---|
| 1 | src/main/resources/db/migration/V132__create_openalex_budget.sql | 新账本三表 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/discovery/repository/OpenAlexBudgetRepository.kt | 新JDBC账本、DTO |
| 3 | src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt | 成本/用途、共享预占及校准 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexProperties.kt | 免费保护配置 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt | 生产账本注入 |
| 6 | src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt | 所有请求分类/结算及下载保护 |
| 7 | src/main/resources/application.yml | 配置及语义说明 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicyTest.kt | 策略场景 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt | 请求/凭证/公开全文回归 |
| 10 | src/test/kotlin/com/weibo/talentintroduction/discovery/repository/OpenAlexBudgetRepositoryIT.kt | MySQL并发与恢复 |

## 验收标准

- I-1：1/10/0成本逐路径断言，模拟CONTENT=100不激活实际下载；重试二次记账两次，出版社请求0笔账本记录。
- I-2：限额10000、官方已用227时有效额最多9773；模拟prepaid100仍不增加有效额；同scope换Key和双实例重启不重置。
- I-3：最后10 credits双连接并发10个LIST+1个SEARCH，批准总成本≤10；重复settle不重复扣；超时预占不释放；先低余额后高余额不增加可用。
- I-4：DB失败和同步失败均不发计量请求；429后的cooldown双实例共享；reset前后旧响应不能写新周期；scheduler路径没有长时间sleep。
- I-5：待补存在时保留生效；最后任务结束60秒后可借用；重新入队恢复保留；新补全可使用保留，历史补全不可。
- I-6：新表唯一约束、状态迁移、UTC时间和锁序在真实MySQL验证；账本聚合等于permit明细，活跃UNKNOWN不被清理。
- Java11：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=OpenAlexRequestPolicyTest,OpenAlexDataSourceTest,OpenAlexBudgetRepositoryIT -DmysqlIt=true test`。IT显式选择，不能以Surefire默认未执行IT算通过；Docker/MySQL不可用记阻塞，不忽略或改成mock。发布前正常package一次，检查失败是否来自既存WIP。

## 人工验收清单

### A-1：已使用额度与付费保护（X-1）
- 前置条件：隔离测试库，Mock HTTP将免费总额设10、已用8、prepaid100；无待补任务且等待60秒；两个应用同accountScope。
- 操作步骤：1. 启动两应用；2. 分别请求两次普通列表；3. 重启其中一个再请求一次；4. 查询账本及Mock HTTP调用日志。
- 预期结果：总共只发出2次计量请求，effectiveRemainingCredits=0；第三次DAILY_BUDGET；prepaid仍100；无Key日志。
- 覆盖：R-1、I-1/I-2/I-3，Key保密保持项。

### A-2：错误、乱序与次日恢复
- 前置条件：隔离环境免费总额10；Mock先挂起一请求并记费，再返回较旧余额；下一reset设2分钟后。
- 操作步骤：1. 触发请求并制造超时；2. 返回旧高余额；3. 暂停数据库再发请求；4. 恢复数据库并跨reset。
- 预期结果：超时记录UNKNOWN且仍占1；旧响应不增加余额；DB停用期间无计量请求；官方确认新周期后余额10，旧请求结算不改变新周期。
- 覆盖：R-1/R-2、I-3/I-4/I-6。

### A-3：新专家补全预留（X-2）
- 前置条件：免费预算10000，测试补全表通过既有入队接口创建200条PENDING，Mock补全有10-credit请求；禁用邮件外发。
- 操作步骤：1. 请求预算快照；2. 消耗采集可用部分；3. 执行新补全及人工历史补全；4. 完成全部待补，等待60秒。
- 预期结果：初始保留2000；采集不能抢占保留；新补全可继续，历史补全等待；无待补60秒后保留0。
- 覆盖：R-2、I-5，补全入队→预算读取。

### A-4：专家及公开全文回归
- 前置条件：隔离环境一名合法专家、一名邮箱无效、一名身份歧义、一名已有APPLICATION专家；用既有测试入口入队；补全Mock返回固定学术字段，邮件外发关闭。
- 操作步骤：1. 按RND_TARGET执行旧发现及自动补全；2. 查看三层专家和任务结果；3. 请求普通出版社PDF并提交Content候选URL。
- 预期结果：合法专家按旧门禁入库；无效邮箱/歧义不错误晋升；既有姓名、邮箱、运营字段不变；学术字段仍partial update；普通PDF0 credits；Content候选被跳过，不向外站附Key；发信0。
- 覆盖：全部保持项、I-1/I-2及X-1回归。
