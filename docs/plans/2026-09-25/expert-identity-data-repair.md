# 专家身份存量隔离与修复（阶段二）

状态：用户调整为先数据后代码。2026-09-25已完成53条确认错误数据恢复及独立验证；原计划的身份闸门/隔离及扩展核验尚未实施，不能标记整份计划完成。见[恢复结果](../../audits/2026-09-25-expert-identity/restoration-result.md)。

## 需求描述

已证实错绑记录和待核验风险记录停止使用未经核验的身份发信/补全；按证据逐条修复既有档案与联系人，保留邮件历史。

必须保持：ES真实_id、联系人id及其既有orcid_id关联键；收信归属、邮件正文/时间/发送状态；运营状态、账号绑定、退订抑制；无错误证据的正常姓名与别名。

范围外：自动补发/道歉邮件、历史邮件正文“改正确”、整库覆盖、删除专家、重置CONTACTED重新外联、重构专家ID系统、UI。

## 关键不变量

### I-1：证据分级
- CONFIRMED_WRONG才允许按审核清单更名；NEEDS_REVIEW只隔离。邮箱不像姓名、同名多邮箱、收不到回复都不是更名证据。
- 每个修复记录必须保存旧值、新值、来源URL/节点与邮箱映射、证据版本、操作批次。

### I-2：稳定关联键不改
- ES真实_id、expert_contact.id/orcid_id、mail_record.expert_contact_id保持不变；按邮箱+真实docID+旧值一起核对，不仅按姓名更新。
- 历史“看起来像ORCID”的docID可能属于被误绑的作者。不能通过修改主键断开邮件历史，也不能继续拿该主键当可靠ORCID补全。

### I-3：不再用已否定身份补全
- 使用已有tags字段，新增标签值IDENTITY_REVIEW_REQUIRED和IDENTITY_ORCID_UNTRUSTED，不新增共享字段。
- REVIEW_REQUIRED：enrichProfiles对该档案返回既有NoId，不发作者查询、不更新学术指标。
- ORCID_UNTRUSTED：trustedOrcid不得回退profile.orcidId；已核实externalIds.openAlexAuthorId仍可用于补全。无可靠ID则NoId。
- 只有核验完成才能移除REVIEW_REQUIRED；业务ID本来就是错ORCID时，ORCID_UNTRUSTED保留。
- 适用：人工历史补全、定向补全、自动worker共用enrichProfiles核心。

### I-4：隔离必须真正阻断发送
- 采用既有email_suppression（MANUAL、带独立审计批次reason），而不是EMAIL_INVALID/退订伪标记。已有抑制条目不得覆盖。
- 新增抑制覆盖自动、批量及普通SMTP出口；人工allowSuppressedRecipient显式绕过属于已有能力，操作期间禁止对隔离名单使用。
- 解除时只能删除本批次新建且id/email/source/reason仍一致的抑制条目；不得移除用户退订/历史禁发。

### I-5：按字段备份与并发校验
- 默认dry-run；apply需明确清单与校验哈希；逐条compare-and-set/ES seq_no+primary_term；冲突停该条。
- 只修复已存在索引层，缺失层不创建。MySQL事务更新姓名快照，跨ES/DB进度逐层记账，未全部验证前保留隔离。
- 回滚同样校验修复后的当前值，避免覆盖后续人工修改。

### I-6：错误可能超过姓名
- 逐条核验机构/国家、externalIds.orcid/openAlexAuthorId与学术指标的来源。只有证实属于错误身份的字段才清除或更正；不能凭正确姓名伪造ORCID/学术指标。
- 邮件历史永不重写；相同邮箱收信记录保持关联；姓名修复不恢复NOT_CONTACTED。

## 现状审计

### ES RAW/CANDIDATE/APPLICATION
- 2026-09-25实测：RAW/CANDIDATE未声明dynamic（默认true），APPLICATION为dynamic:false；三层均已有tags keyword、externalIds object enabled:false。禁止假定仓库mapping已完全等于线上。（来源：K-es-dynamic-false）
- 身份原始写入：ExpertDiscoveryService.buildOrcidProfile/buildProfile→toIndexMap；晋级promoteDiscoveredToCandidate、tryAutoPromote及ExpertIndexPromotionService复制已有文档；各导入脚本也可能写整文档。
- 学术补全唯一共享写入updateExpertAcademicFields(:2501)对已有三层局部更新指标，不修姓名；enrichProfiles(:2612)共享读入口；trustedOrcid(:2600)直接信任非EMAIL前缀的profile.orcidId。
- 已证实datta_madamwar@yahoo.com绑定Avani Bharatkumar Patel且存在补全指标；rizgar.ramadhan@dpu.edu.krd的真实docID也呈其他作者ORCID形状。不能只改显示姓名。
- ExpertSearchService映射姓名/tags；ExpertProfile.displayName拼接姓名；MailVariableService解析姓名与姓氏；详情与邮件模板使用这些值。
- 分类依赖机构和指标：被否定的学术字段清除后，应走既有分类服务重新计算，不能保留旧分类作为可信依据；本阶段脚本先隔离，待核实身份后通过既有补全流程重算，不自行猜分类。

### expert_contact/mail_record
- expert_contact：id主键，orcid_id为业务关联键，expert_name可空，expert_email，运营与会话字段。
- 姓名写入：InitialOutreachService新建联系人；ManualInitialOutreachService两处创建/重用流程；AutoMailReplyService陌生入站新建；此次脚本将新增有条件更新。
- 姓名读取：MailVariableService、MailboxService、MailboxConversationRepository、邮件/会议/LLM工作台展示；只修ES会留下MySQL错误快照。
- mail_record按expert_contact_id关联。此次只读比对称呼与发送计数，修复脚本无写权限/无更新语句针对该表。
- batch_email_verification和任务目标中expert_name为历史快照，不追写历史审计；新批次重新读取已修档案。

### email_suppression
- 已有唯一email约束；source支持MANUAL，reason可记录批次；EmailSuppressionService.suppress幂等不覆盖旧记录。
- 写入：人工管理API、退订/退信相关服务；本次脚本只能插入新的MANUAL隔离条目。
- 读取：InitialOutreachService、ManualInitialOutreachService多个发信/取目标入口、SmtpMailDeliveryService共同检查。（来源：K-dual-outreach-paths）

### discovery_paper_job / expert_academic_enrichment_job
- 论文队列metadata_json/extraction_json储存抽取结果；已完成不追写，未消费旧结果应以原文重新抽取或隔离后续入库产物。
- 补全任务UNIQUE(expert_doc_id)，worker租约/人工补全共享核心；运行中旧profile仍可能回写。apply前停止相关采集/补全任务并确认在途结束；无法确认则不清洗。
- 不直接改运行中lease/任意置SUCCEEDED，不通过更名“重建”任务。

## 实现方案

1. 修改ExpertDiscoveryService的trustedOrcid与enrichProfiles，落实I-3。精确标签命中；保持未隔离专家原行为。新标签通过ExpertProfile.tags已有字段读取，无mapping新增。
2. ExpertDiscoveryServiceTest增加：隔离档案不发网络查询；错误ORCID主键不回退；经过核实的author ID允许补全；未隔离档案回归；已有层局部写入仍不建缺失层。（I-2/I-3/I-5）
3. 新建scripts/repair_expert_identity.py与对应测试。四个显式模式：audit、quarantine、apply-reviewed、rollback；默认audit/dry-run；输入阶段审计清单和审核映射，不内置“邮箱字符串推姓名”。凭据来自用户授权的标准配置，禁止日志输出。（I-1至I-6）
4. quarantine：对审核选定范围使用现有抑制表，备份旧条目与三层tags，追加REVIEW_REQUIRED；对明确错误ORCID主键追加ORCID_UNTRUSTED。保留所有原标签/状态。（I-2至I-5）
5. apply-reviewed：备份各层身份字段与contact快照；校验证据、旧值、版本；按existing层CAS patch；MySQL按id/email/旧姓名条件更新。错误作者ID/污染指标按逐条字段清单处理，未知真实值不猜。所有层及邮件预览核验通过才解隔离。（I-1/I-2/I-5/I-6）
6. 清洗顺序：先53条已证实记录（包括15个实际错称呼邮箱），再126个已联系风险邮箱的剩余项，再5304风险清单；还需继续覆盖单条错绑，Rizgar已证明重复规则存在漏检。普通多邮箱不批量删除。（I-1）
7. 上线与清洗期间暂停相关在途补全/采集，核实worker无旧任务执行；对旧extraction_json不直接重消费。重新解析流程需要按既有任务状态机单独出执行清单，禁止直接批量重置队列表。（I-5/I-6）

## 变更文件清单

| 文件 | 动作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | 标签隔离及ORCID回退闸门 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | 三类身份与回归测试 |
| scripts/repair_expert_identity.py | 审计、隔离、逐条修复、回滚 |
| scripts/test_repair_expert_identity.py | CAS、三层/DB部分失败、抑制保留、dry-run测试 |

## 验收标准

- I-1：NEEDS_REVIEW更名被拒；只有公开归属证据完整的映射可apply。
- I-2：修复前后所有文档/联系人/邮件关联键集合相同；错ORCID主键不再用于查作者。
- I-3：REVIEW_REQUIRED无补全网络请求；清洗后只有已验证身份可补全。
- I-4：两条外联路径与SMTP隔离生效；原退订条目未被删除；人工显式绕过不算闸门失败但操作流程不得使用。
- I-5：dry-run零写入；版本冲突拒写；中途故障保留隔离并可断点重试；回滚不覆盖后续编辑；APPLICATION缺失不新建。
- I-6：Shakir的ES与联系人显示Shakir M. Abas；原mail_record 7162正文仍为历史错误称呼；未重发；状态仍CONTACTED/INTRO_SENT。
- 复核指标与机构来源，无法证明正确则隔离，不以“更名完成”宣称整档案已正确。

## 人工验收清单

### A-1：确认错误修复
- 前置：先在脱敏测试副本使用Shakir原始记录与邮件7162关联。
- 操作：dry-run→检查差异→apply-reviewed→查看原始/候选/联系人详情和下一封邮件预览。
- 预期：姓名Shakir M. Abas；称呼不再Alzakholi；历史正文不变；联系人/邮件ID、账号绑定与运营状态不变。
- 覆盖：I-1/I-2/I-5/I-6；ES→联系人→邮件预览。

### A-2：风险隔离及解除
- 前置：一个待核验邮箱、一个已有退订邮箱、一个普通正确专家。
- 操作：quarantine后走自动与手工批量发送预览/测试SMTP；完成证据审核再解除此次新增隔离。
- 预期：风险邮箱禁止实际外发；正确专家行为不变；已退订邮箱始终禁发；只移除本批次新增条目。
- 覆盖：I-1/I-4；正常别名、退订和发送回归。

### A-3：错误ORCID与补全
- 前置：错误ORCID形状主键的Datta样本，保留业务ID，追加隔离标签。
- 操作：人工和worker各尝试补全；核实正确author ID后再试；无可靠ID时再试。
- 预期：隔离期间零作者查询；有正确author ID只查询该ID；无ID不回退错误业务主键。收信仍归原联系人。
- 覆盖：I-2/I-3/I-6；脚本→补全→三层。

### A-4：失败与恢复
- 前置：测试副本模拟ES一层失败、DB并发改名、APPLICATION不存在。
- 操作：执行修复、重试和回滚。
- 预期：部分失败保持隔离；并发冲突不覆盖；已有成功层可幂等重试；APPLICATION仍不存在；历史邮件内容/状态计数不变。
- 覆盖：I-5；跨存储写入及回滚。
