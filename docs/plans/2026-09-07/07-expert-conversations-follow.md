# 07 · 专家会话查询与关注持久化

状态：待审阅/未执行（执行期 Amendment A1 后范围 11 个文件）。前置：06子计划通过独立验证。 范围：11个文件（A1 拓宽，见变更清单）；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

按专家显示完整往来摘要，保留只有发件的专家；关注可刷新保留，待处理和待专家回复分开。

不得改变：现有邮箱、任务记录跳转和未匹配来信入口；发送状态、专家状态写入规则不动。

范围外：不建聊天消息表、不复制邮件数据、不改ES mapping、不做多用户权限体系。

## 关键不变量

### Invariant I-1：会话权威来源
- Rule：来信只取linked inbound_mail_processing；发件只取OUTBOUND mail_record。先DB聚合专家再分页；事件时间取receivedAt或COALESCE(sentAt,createdAt)，稳定id打破平局。
- Applies to：新summary/timeline SQL
- Violation consequence：重复计数或同专家跨页
- 来源：K-mailbox-inbound-source-authority

### Invariant I-2：等待与待处理
- Rule：waitingReply=全历史账号范围receivedCount=0且sentCount>0；SENT计发，FAILED单列failedCount；pendingCount完全复用现有processing待处理谓词，不取seen/人工关注。
- Applies to：所有filter/count/sidebar DTO
- Violation consequence：筛选后伪造未回复、失败发件当已发
- 来源：K-group-before-pagination

### Invariant I-3：关注所有者
- Rule：expert_follow主键(username,contactId)，username只取Session AUTH_USERNAME；PUT是置true、DELETE是置false，均幂等，不使用toggle。
- Applies to：关注新表、API和summary join
- Violation consequence：串用户或重复点击翻转错位
- 来源：original

### Invariant I-4：详情加载与既有入口
- Rule：专家列表只取summary，右侧只加载当前专家；timeline默认50、最多100，游标按(time,source,id)稳定；账号范围可过滤但完整往来不隐式受默认7天/方向限制。
- Applies to：summary/timeline/legacy routes
- Violation consequence：打开列表即取全部邮件、历史来信消失
- 来源：original

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E3/E5（权威来源/分页/Session身份）。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

## 实现方案

1. [I-3] V121建立expert_follow(username VARCHAR(64),expert_contact_id BIGINT,created_at DATETIME)复合主键及contact FK；仅当前admin使用，不新增用户表。新增ExpertFollowService同文件包含必要DTO与参数化JDBC写方法；从现有Session读取身份，非法/缺登录拒绝，body不接受username。
2. [I-1/I-2/I-4] 新建MailboxConversationRepository/Service/Controller，保持MailboxController现有API和大MailRecordRepository不变。共用一份归一化UNION SQL作为summary/count/timeline基础；详细口径见master。所有搜索/排序参数白名单和绑定；姓名/邮箱搜索取expert_contact真实字段，资料fixture姓名错误标签不可据此改姓名。
3. [I-2] summary的总收/成功发/失败发/待处理在账号范围全历史计算；方向/日期/主题/标签条件使用EXISTS限定专家membership，不重算是否从未回复。标签查询只join对应来源标签，不因多标签产生重复行。默认最近往来倒序，关注筛选不强制置顶。latestInbound含真实processing.id供workbench，不能从mailRecord推算。
4. [I-4] timeline返回按来源ID的幂等消息键、body/cleanedBody、处理态/发送态、附件数量和前三名称、技术信息；50条先取最新再正序呈现，olderCursor加载更早。禁止每个列表专家加载完整正文/所有材料。GET详情复用06精确来源，不调用IMAP。
5. [I-1/I-4] 未匹配来信保留原专用入口，不伪造“未知专家”放专家列表；taskExecutionId钻取继续旧table及其原日期语义。迁移目标121；JDBC MySQL集成测试覆盖多标签、混合记录、同timestamp稳定分页及EXPLAIN，不凭内存mock证明GROUP BY正确。
6. [I-1/I-4] MailboxService.resolveAttachments改委托06的resolveMessageAttachments；停用不限定账号/专家/方向的findFirstByMessageId回退。扩展MailboxServiceTest覆盖精确bridge、唯一旧关系和跨账号歧义。关注幂等与Session身份的测试并入MailboxConversationControllerTest，调用真实ExpertFollowService配测试数据库/受控JDBC，不能只mock返回成功。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

执行期修订（Amendment A1，2026-09-08 HUMAN 批准）：child 06 新增的 document/ExpertMaterialController 与既有 campaign ExpertContactManagementController:244 的 `GET /api/expert-contacts/{id}/materials` 映射完全同路径，Spring 启动即 Ambiguous mapping，生产无法启动；audit.md E 表已声明「统一组件改用新材料 API」。本清单追加退役旧 feed 路由的授权文件（不新建文件、不移动旧 URL 之外的端点）；路由回归测试并入已授权 MailboxConversationControllerTest.kt（新增第二个顶层 @WebMvcTest 类同挂两个控制器，证明无歧义映射）。

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V121__create_expert_follow.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertFollowService.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | 新增 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` | 新增 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 修改（含 A1 双控制器映射回归测试类） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改 |
| 9 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt` | 修改 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt` | 修改 |
| 11 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | 修改（A1：退役 `@GetMapping("/{contactId}/materials")` listMaterials 旧 feed 映射；保留 PUT updateMaterialStatus、detail 与其余端点；仅删除该映射专用的死代码，不搬迁、不改 payload） |

## 验收标准

- I-1：同信processing+INBOUNDrecord只计1；50/100行多页无重复/缺失；聚合分页在SQL发生。
- I-2：无回信2SENT→waiting=true；仅FAILED→waiting=false；历史来信在7天之外仍waiting=false；待处理谓词与旧查询一致。
- I-3：并发PUT10次1行，DELETE重复成功；客户端username被拒绝/忽略且不能改变owner，匿名401。
- I-4：summary不带全量body；仅当前专家timeline请求；taskExecutionId和未匹配入口原测试仍通过。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：四类专家、分页和筛选
- 前置条件：用测试数据建立A=2封SENT无来信，B=1封FAILED，C=1封10天前来信+1封今日发件，D=1封待处理来信，并批量建立60位各1发件专家。
- 操作步骤：1. 打开全部/待专家回复/待处理；2. 日期筛选最近7天；3. 关注A刷新、取消关注再刷新；4. 逐页查看。
- 预期结果：A显示收0·发2且待专家回复；B显示发送失败且不在待回复；C不因7天过滤变未回复；D仅在待处理计1；关注刷新保留/取消消失；同专家不跨页重复。
- 覆盖：I-1/I-2/I-3/I-4；本项可观察需求与列明的回归/交互。

### A-2：原入口回归
- 前置条件：测试任务记录有taskExecutionId，另有1封未匹配来信及同名/同messageId跨账号数据。
- 操作步骤：1. 从任务记录进入邮件；2. 打开待匹配来信；3. 切账号筛选查看会话。
- 预期结果：任务明细仍原批次记录；未匹配可绑定/处理；会话不显示未匹配虚构专家，不串账号文件。
- 覆盖：I-1/I-4；本项可观察需求与列明的回归/交互。
