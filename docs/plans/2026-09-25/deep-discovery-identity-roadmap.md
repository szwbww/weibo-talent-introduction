# 深度发现身份防错路线

2026-09-25。状态：代码审计及方案完成，应用代码尚未修改、未部署。本文为分阶段设计路线，不是跨模块一次执行清单；第一阶段执行范围见 [解析修复](expert-identity-parser-repair.md)。其余阶段需在实施前按以下契约分别形成不超过10文件的子计划。

## 目标与原则

只有原始来源能明确证明“这个邮箱属于这个作者”，才创建可用于联系的专家身份。邮箱能收信、姓名相似、同篇论文、同一个通讯脚注，都不能单独证明归属。真实同名、同人多邮箱必须允许；重名仅是排查信号，不是删除或合并条件。

证据不足的新线索保存在论文抽取/审计结果，不自动新建专家档案、不自动晋升或加入首发名单。本文不建议重新恢复已删除的未知档案到RAW。

## 已查实的代码风险

| 路径 | 当前行为 | 后果 |
|---|---|---|
| `JatsXmlEmailParser.parseXrefCorrespondence` | 引用同一说明的每位作者都拿到说明内全部邮箱 | 共享通讯段错绑 |
| `JatsXmlEmailParser.mergeResults` | 姓、名、机构、ORCID分别取第一个非空值 | 冲突消失，可能拼出混合身份 |
| `PdfEmailExtractor.verifiedAuthorFor`，Core复用 | 邮箱姓名片段唯一匹配，或唯一作者+唯一邮箱 | 比旧首字母规则严格，但仍是推断，不能升级为来源证明 |
| `DiscoveryPipelineService`约1005行 | `extractionJson`非空就跳过重新抽取 | 新解析器上线后仍消费旧错误结果 |
| `ExpertDiscoveryService.consumeOutcomeInternal` | 邮箱有效后入库，资格满足即晋升；没有邮箱归属证据门禁 | 无名/歧义结果仍可能成为专家 |
| `existsInRawIndexByEmail`及写入 | 只查询RAW、只判断存在，查询与写入分开 | 不辨身份冲突；并发去重无原子保证 |
| `trustedOrcid`约2600行 | 从非EMAIL业务键取ORCID | 旧错绑业务键继续污染学术补全 |
| `promoteDiscoveredToCandidate`、`promoteRawToCandidateWithEmail`、`ExpertRevalidationService`、`ExpertIndexWriterService` | 多个晋升入口 | 单改发现入口不能保护全部路径 |

来源读取：JATS直接消费者为EuropePmcDataSource、PmcOaDataSource；OpenAlex通过PMC分支间接使用。Europe PMC搜索字段与ORCID公开记录也构造AuthorEmail，必须逐源核实归属，不按来源名称一律放行。

持久化事实：三层ES mapping均`dynamic:false`；`externalIds`是`enabled:false`对象，无法作为可查询状态字段使用。`AuthorEmail`与`EmailExtractionOutcome`没有身份验证/解析版本契约。论文队列有`extraction_json`及租约/generation CAS，已有`payload_version`属于输入元数据，不能冒充解析器版本。

知识依据：K-author-identity-needs-email-evidence、K-historical-identity-orcid-fallback、K-expert-classification-one-object-three-layers、K-initial-outreach-four-gate-paths。

## 分阶段实施

### 1. 修复确定性的解析错误

修改JATS归属与合并；原文显式姓名/唯一缩写必须精确划定邮箱片段；共享脚注不再广播给所有作者。同名不同作者节点不得合并；冲突不选第一条。先完成独立测试可部署，仍保持发送暂停，不能据此宣布全链路修复。详见子计划。

### 2. 建立来源证据契约并封住入库入口

拆成“证据与三层存取”和“发现准入与各来源适配”两个小计划。建议一个顶层结构化事实`identityVerification`：状态、规则版本、来源标识、证据定位/摘要哈希、绑定的邮箱和作者标识；以有界摘要存储，全文保留在审计材料。状态建议`VERIFIED / UNRESOLVED / CONFLICT`，缺失是历史未验证，绝不默认VERIFIED。保存状态不等于提供人工放行入口。

三层mapping、ExpertProfile、ExpertSearchService.sourceFields/toExpertProfile及相关映射构建同时识别此对象。明确发现来源标记，不能仅依赖可能被修改的tag，也不能把其他导入源错误归类成深度发现。

所有AuthorEmail生产者逐一适配：直接作者地址、显式对应文本、确属同一公开ORCID记录的邮箱才允许验证；PDF/Core仅凭邮箱拼写或单作者单邮箱的结果降为线索。姓名/机构/真实外部ID整组传播，不能从邻近作者补齐缺失值。

新发现入库前必须VERIFIED；否则只记录线索与拒绝原因。重复邮箱须读取已有真实身份并比较，冲突保留已有档案、记录冲突，不覆盖；并发下同邮箱不同身份只能有一个准入成功，另一方进入冲突处理。实施前选择并验证按规范化邮箱串行/唯一约束方案，不能宣称ES先查后写已经解决并发；不重命名现有业务ID。

### 3. 阻止后续传播与旧任务回灌

拆成“队列版本”、“补全/晋升保护”、“首发保护”三个子计划。

- 抽取结果带独立规则版本。无版本/旧版本不得消费，按现有租约和generation CAS重新抽取或隔离；替换结果要正确结算字节配额、重试计数，禁止直接SQL清空一列后继续。新版恢复重用新版结果，不重复下载；旧解析在途返回也必须被拒绝。
- 学术补全仅使用与已验证邮箱身份一致的真实ORCID/OpenAlex ID；保留稳定ES/联系人业务关联键，发现来源禁用业务键ORCID回退。补全完成回写前再比对身份版本/乐观锁，避免旧任务覆盖已纠正指标；不同作者指标不得沿用，明确哪些字段失效并定向清理。
- 晋升覆盖发现直接晋升、补邮箱晋升、RAW重新审核、人工/通用层级晋升；身份未确认不能通过其他路径升级。
- 首发覆盖InitialOutreachService、ManualInitialOutreachService的ES查询/计数/分页、RecipientScope重试联系人过滤，以及最终发送前重新检查。已有联系人快照不能绕过。限制为深度发现来源；其他导入来源维持现有策略。材料提醒和回复如何处理须单独界定，不能偷偷扩大为全局禁发。

### 4. 保护此次清理结果

已删除实际是1,923条专家档案，而非1,924条；名单见[最终删除记录](../../audits/2026-09-25-delete-unresolved-experts/README.md)。建立可审计的重新导入拦截记录，以规范化邮箱为主要命中键并保留旧ID/原因/来源，避免更换ORCID业务键绕过。命中后默认不自动重建，有新明确证据也先产生复核结果，解除拦截需要显式操作。

已修复/来源支持档案的既有证据应导入新的验证体系；必须使用最终更正结果，包括3条审计误判纠正和Xingquan Zhu排除记录。不能把一整个风险批次全标VERIFIED，也不能再用旧“待确认”名单盖掉已证实结论。保持历史邮件原样；联系人姓名只在此次确认身份范围内定向同步。

### 5. 回放与恢复

先静态原文回放，再测试环境全链路，最后线上小批量无发信验证；通过后才能考虑恢复发送。上线保持用户当前暂停状态，不自行开启发送。

验收必须同时满足：

1. 已独立核定的错误样本零错绑；明确样本应正确识别，不能用全部拒绝伪装通过。审计脚本输出不能当唯一真值，抽取规则与验收标注独立。
2. 未确认/冲突线索新增专家0、晋升0、首发0；合法同名与一人多邮箱保留。
3. 旧版缓存、旧在途任务、并发重复发现、已删除名单重放均不能绕过。
4. 已修复档案身份与受保护字段无倒退，业务关联键与历史邮件不变；补全不会重建缺失索引层。
5. 提供每批VERIFIED/UNRESOLVED/CONFLICT/拦截数量及样本证据，支持离线审计。后续异常率阈值应根据回放基线设定，不预设任意准确率承诺。

## 范围边界

本轮先落地路线及两文件解析子计划；以上后续阶段不是已完成的代码修复。它们涉及多个共享存储和发送入口，尚需分别补齐所有读写路径、准确文件清单、迁移与回滚验收后执行。无UI改版、无全库合并、无AI自动猜名、无恢复发送动作。
