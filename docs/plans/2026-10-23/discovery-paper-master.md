# 深度发现：论文原文、解析修复、证据展示与 Apollo 保存配置

状态：**计划完成，待执行**。创建日期：2026-10-23。代码基线：`c6e9709f349f32c5feb1d067b47c697b0dff05b6`，实际研究包含当前工作树已有改动；各被引用文件SHA256见代码证据。本轮没有实施产品代码、改线上配置、重启或发布。

## 需求描述

1. 每次深度发现实际取得的完整论文正文可持久留存、按任务查看和下载；容量受控、失败可见。保留原有失败抽样功能。
2. 修复已有真实原文实验证明可改善的邮箱—姓名关联；以同一批原文前后对照验收，不把猜测写成收益。
3. 在当前线上深度发现弹窗增加“论文原文”：原文预览、解析对照、职称/学位历史原句、保护、**批量取消保护**、批量删除和磁盘容量。
4. Apollo免费搜索、付费详情分开保存到后端；新任务采用保存值，运行中任务保留快照；执行日志区分两类尝试。

必须保持：现有身份/准入/SELF/教授/人工保护/国家补全规则与已晋升结果；现有HTTP超时、下载大小、提供商429与重试边界；专家审核、审核记录、邮件任务；旧失败样本库与离线回放。删除论文不删除专家或分析记录。

范围外：执行此前“无明确不合格证据放行”的准入实验方案；自动迁移/补抓历史缺失原文；OCR/LLM；推断师生关系、当前职称、年龄或国籍；新增第三方数据服务；对象存储；自动历史批量准入；购买额度；API Key管理页面。新原文库从功能启用后开始，旧抽样原样保留。

## 关键不变量

### Invariant I-1: 事实、提案、效果不能混写
- Rule: 294样本/52补关联/79保留是已核对的实验汇总；20GiB/15GiB是本计划选定默认配置；任何上线收益必须同原文/同配置双版本复验。没有原文不能宣称问题已修复。
- Applies to: 全部子计划、执行报告、验收和发布说明。
- Violation consequence: 形成无法兑现的准确率/准入承诺。
- 来源: K-plan-quantified-claims-need-grep-receipts。

### Invariant I-2: 保存、关联、资格、准入四件事分开
- Rule: 下载留存≠身份消费≠资格符合≠候选晋升；原文历史教授/PhD事实仅展示，不写当前资格。原文删除或分析没有业务晋升副作用。
- Applies to: 捕获、分析、API、前端、日志。
- Violation consequence: 把论文上的人物描述当现任资格，或虚增准入统计。
- 来源: K-extraction-samples-are-not-consumption；K-paper-affiliation-is-not-employment；K-admission-event-and-projection-counts。

### Invariant I-3: 样式与文件边界必须明确
- Rule: 仅05/07改前端，逐字落实它们的S-n CSS/DOM；不允许执行代理临时补class或inline style。每子计划≤10文件，超出先修订计划；共享文件按01→02→03→04→05→06a→06b→07顺序，不并行覆盖。
- Applies to: 所有子计划执行和复验。
- Violation consequence: 漂移成另一套UI、共享文件互相覆盖、复验无法收敛。
- 来源: K-master-plan-shared-file-sequential-gates；K-frontend-cache-key-triad。

### Invariant I-4: 保存配置与运行快照分离
- Rule: 原文库启用/容量为即时存储保护；Apollo两开关只作用新任务。旧任务的Apollo行为、旧缓存、旧first解析不被配置保存/重新分析覆盖。免费模式不调用收费详情。
- Applies to: settings、registration、worker、capture、UI。
- Violation consequence: 任务中途改变权限、免费意外扣费、旧基线丢失。
- 来源: K-discovery-reentry-keeps-run-accounting；K-task-launch-config-registration。

## 现状审计

### 实际代码与线上证据

| 已核实事实 | 证据 | 规划含义 |
|---|---|---|
| 当前是限量抽样，并非全原文库 | DiscoverySampleProperties、Store.commit，capture仅OPENALEX | 新建独立归档，不简单调大旧配额 |
| PDF/HTML、EuropePMC XML、PMC_OA XML、CORE内嵌文本的真实获取路径不同 | 02路径表；代码快照 | 每条路径明确接入，不能仅改PdfEmailExtractor |
| 实验补回52组，未多发现邮箱 | 服务器gain-summary.json：294样本、79保留、34+18补回、0新增邮箱 | 03只实现两条已验证版面规则 |
| 论文可有本人历史资格，也大量缺证据 | qualification-summary.json：23篇432页，1人教授且博士，2人博士在读，49缺证据 | 04仅展示明确本人事实，不改资格门禁 |
| 当前Apollo是部署默认+启动checkbox | Registration.resolveOptions、app.js:10110/10172 | 后端保存值+运行快照+前端保存动作同时修 |
| 搜索与详情是两种API | ApolloPeopleSearchClient:123/150；官方文档 | 分开控制，不把账号套餐或总请求数当收费次数 |
| 当前免费放宽策略只在实验脚本中 | scripts/discovery_admission_probe/README.md | 不未经新批准把实验变生产准入 |

真实汇总已于本次计划研究再次SSH读取，凭据未读取/输出，人员明细未下载：

- `/root/discovery-layout-gain-czy_fjjk/gain-summary.json`（asOf 2026-10-23T09:28:53.890693Z）
- `/root/discovery-layout-gain-czy_fjjk/gain-validation.json`
- `/root/discovery-all-retained-audit-sepxa4l0/summary.json`
- `/root/paper-qualification-audit-4yo_4f8l/qualification-summary.json`

原文可能受旧清理器继续淘汰。**执行03前必须核实/冻结294原文和labels，缺失则明确真实复验未完成。** 不因目录名还在就假定正文仍存在。固定副本只放服务器私有目录0700/0600，不进Git；hash/数量/汇总可入验收报告。

### 完整写读审计索引

| store/资源 | 当前/新增写者 | 读者 | 子计划 |
|---|---|---|---|
| 旧samples目录/schema1 | Capture.save；Store.commit/retained/startupCleanup/scheduledCleanup | Store.scan；测试CLI DiscoverySampleReplay；私有审计脚本 | 01/02/03，保持旧语义 |
| 新archive四表+独立文件目录 | 01唯一Repository/Service；02调用service；04 analysis CAS | 01 API、04分析、05UI | 01/02/04/05 |
| discovery_source_cursor | 现有ExpertDiscoveryService checkpoint路径 | 同步加载、队列起点导入 | 02仅异常交互，不改schema |
| discovery_paper_job | 现有queue Repository claim/saveExtraction/returnToPending/complete/cleanup | Pipeline worker、queue mapper | 02只调用已有CAS，不新增列 |
| discovery_verification_run.settings_json | 现有Repository.createRun；06 Registration传入新apolloAccess字段 | Worker、QueryService、旧run兼容解释 | 06/07，仅1个新增顶层JSON字段 |
| discovery_verification_attempt | 现有reserve/complete/恢复写者 | 现有报表；06新增只读渠道聚合；离线probe | 06不改账目写口 |
| 新discovery_apollo_setting | 06新Repository | SettingsService、Registration、QueryService、07UI | 06/07 |
| DOM/静态资源 | index/app/styles及新papers模块 | 浏览器、JS测试 | 05/07；全文CSS已给出 |
| RAW/CANDIDATE/APPLICATION及准入/联系人/邮件 | 本计划不新增写者、不加字段、不重建mapping | 既有发现流程原样使用 | 回归验证，不进行数据修复 |

详细字段/全部接缝与grep receipts：[代码证据](discovery-paper-code-receipts.md)。各子计划包含自己的store审计与A-n跨路径验收；表中新store没有存量隐式写者。

### 前端来源与边界

已展示[线上隔离预览](https://qingfei.szwbww.com/previews/discovery-papers-20261023.html)来自实际业务HTML/CSS；本地快照、截图与交互说明在`docs/mockups/discovery-papers-preview/README.md`。该静态预览不是已上线功能，内含虚构列表、内存删除/保护和磁盘旧快照；不得直接拿它覆盖正式业务页面。

05保留真实1180px/80vh弹窗/四页签结构，换成真实API；PDF使用原始文件预览，不复制演示“论文页”。07追加Apollo持久配置。CSS全文、所有hover/active/disabled/focus、窄屏、checkbox修正、table header防重叠均在05/07；旧全局class不修改。

### 知识使用与排除

已读取并更新22项命中记录（包含自审补核的缓存兼容版本知识）。身份归属、原文版面、缓存兼容版本、抽样非消费、原文非任职、页提交完整性、缓存回放、任务重入/预算、准入读写一致、提供商错误证据作为各I-n依据；下载contextPath、任务配置注册、cache key、嵌套表头、checkbox、全局p、真实DOM测试落到S-n和验收。调度cron/取消知识用于不改变原调度的回归；未引入新cron策略。审核/准入read-write parity只作为“不改准入写口”的边界，本次不扩展其规则。

已发现旧K-provider-error-evidence-and-channel-recovery把“非2xx正文全丢失”当现状，实际已修；本次写回纠正，不能沿用旧知识制造重复修复。K-source-recall中的“未留原文”旧事实也已注明后来增加限量抽样。缓存版本知识hit_count从9到10，按技能提升一行到CLAUDE。所用条目没有5条同一主题重复，未作跨主题强行合并；没有需要新增的角色配置文件。

## 实现方案

### 顺序、隔离与出口（I-1～I-4）

| 顺序 | 子计划 | 文件上限 | 可独立验证/部署的结果 |
|---|---|---:|---|
| 01 | [存储与管理API](discovery-paper-01-storage.md) | 9 | 独立目录/持久化/鉴权/保护删除/容量API；默认关闭，不接入旧业务 |
| 02 | [全下载接入](discovery-paper-02-capture.md) | 10 | 六论文来源真实正文留存，容量阻断不丢游标，可通过API使用；含已批准的存储接缝修订 |
| 03 | [版面解析修复](discovery-paper-03-parser.md) | 7 | 两条解析规则独立生效，固定原文回放报告；同步新采集版本回归 |
| 04 | [解析对照与历史事实](discovery-paper-04-analysis.md) | 9 | 只读分析现有正文、分析JSON和查看API |
| 05 | [论文原文前端](discovery-paper-05-frontend.md) | 6 | 真实页面管理/预览/磁盘/批量取消保护，完整CSS |
| 06a | [Apollo配置持久化](discovery-paper-06a-apollo-persistence.md) | 5 | 两开关独立保存API，默认/冲突/失败语义 |
| 06b | [Apollo运行接入](discovery-paper-06b-apollo-runtime.md) | 10 | 新旧run分离、分渠道日志、现有测试适配 |
| 07 | [Apollo前端配置/日志](discovery-paper-07-apollo-frontend.md) | 5 | 保存状态、收费开关、运行快照与尝试数字，完整CSS |

每份先实施、机器复验通过，再进入下一份；不把机器复验当成人工验收。任务可以一次连续执行，但不能并行修改共享文件或越过失败关卡。本轮只创建计划，未启动执行。

共享文件顺序：ArchiveService/ArchiveRepository 01→02→04，Models 01→04，Controller 01→04；Capture 02→03，PdfEmailExtractor 02→04；index/app/styles 05→07。Flyway V159归档、V160 Apollo；执行前再核对最新migration，冲突必须修订计划清单。不得修改已应用迁移。

### 执行前检查（I-1/I-3）

1. 读取当前AGENTS/CLAUDE与本主计划，记录HEAD和dirty文件；已有本地改动不能reset、覆盖或混进“本功能已完成”。不为此擅自切分支或部署。
2. 重核代码快照涉及接缝；差异只记录实际语义，不用旧行号盲贴。
3. 新依赖只允许现有JDK11/Kotlin/Spring JDBC/PDFBox/浏览器原生能力，无新消息队列、通用规则引擎或存储框架。
4. 测试使用隔离MySQL/临时目录/假HTTP；真实留档回放只读服务器私有固定原文，不启动生产Spring上下文，不调用付费API。
5. 本计划的API、字段、默认值、错误码、CSS、人工期望值是实现契约；发现无法满足时先用代码/样本证明差异并修订相应子计划，不临场放宽身份规则。

### 验证与发布边界（I-1～I-4）

- JDK11：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`；按子计划跑有关测试，最后构建一次WAR并完整执行受影响JS测试。构建失败与测试SKIPPED要明列。
- 03真实294样本复验与04资格事实原句核对是效果门槛，不可由合成测试替代；缺原文时仍可完成代码，但不得报告“真实效果已验证”。
- 发布准备才编写release记录、包hash及迁移回退说明，不在本计划阶段修改发布历史冒充上线。部署/启用是后续用户授权下的执行事项。
- 运维回退：关闭新archive.enabled可以停止新留存，但保留目录/表；Apollo保存值仅影响新run，不能伪称关闭后旧run不再详情调用。代码回退不得删除新增库或已保留文件，需先确保旧二进制不会消费不识别的新解析规则证据。若回退涉及正在运行任务，按既有停止/恢复流程，不能直接删队列。

## 变更文件清单

本主计划是治理入口，不作为合并后的超大实施清单。具体生产代码权限严格以各子计划≤10文件表为准。本轮治理/子计划产物如下（8个子计划）；代码证据快照及实验算法附件在上文链接，不属于生产实现变更清单：

| 文件 | 用途 |
|---|---|
| discovery-paper-master.md | 顺序、范围、全局验收 |
| discovery-paper-01-storage.md | 子计划01 |
| discovery-paper-02-capture.md | 子计划02 |
| discovery-paper-03-parser.md | 子计划03 |
| discovery-paper-04-analysis.md | 子计划04 |
| discovery-paper-05-frontend.md | 子计划05 |
| discovery-paper-06a-apollo-persistence.md | 子计划06a |
| discovery-paper-06b-apollo-runtime.md | 子计划06b |
| discovery-paper-07-apollo-frontend.md | 子计划07 |

## 验收标准

- I-1：每个效果声明可指向真实固定hash/版本/汇总；52仅表示关联，不写成52人准入；推断、待实现规则和已观察数据标清。
- I-2：留存/消费/资格/准入指标有独立来源；删除/分析没有专家或准入写入；历史资格时效提示存在。
- I-3：各子计划文件计数合规、无未列文件；05/07 CSS逐字校验；共享文件按顺序复验；API→UI契约相符。
- I-4：Apollo四组合、旧新run、免费请求0详情、持久配置和原文容量阻断都通过；未把部署配置所有字段都冒称已冻结。
- 全部子计划A-n是权威人工清单；人工开始时才导出各自acceptance.md，不预先制作第二份漂移清单。

## 人工验收清单

### A-1: 一次完整发现到管理
- 前置条件：01～07已部署隔离验收实例，使用02多来源假服务+01共享/保护夹具，原文开启20/15GiB，Apollo保存true/false；记录既有专家/准入/邮件基线。
- 操作步骤：1. 启动发现。2. 从执行记录查看本次原文。3. 解析并定位本人原句。4. 批量取消保护、预检查并删除可删原文。5. 刷新页面检查统计/专家/邮件。
- 预期结果：全文尝试可追溯；共享/保护按01跳过；取消保护不删文件；分析保留，历史资格不自动赋当前；Apollo详情请求0；旧准入及邮件状态无额外变化。
- 覆盖：I-2/I-3/I-4；需求1/3/4；跨下载→存储→UI→分析→删除。

### A-2: 明确效果与样式
- 前置条件：03固定294样本、04固定23篇资格审计集可访问；1440×1080与643×900浏览器。
- 操作步骤：1. 执行03前后回放并阅读汇总。2. 核对资格原句。3. 对照05/07契约查看所有空/错/禁用/窄屏状态。4. 打开原审核页和普通邮件任务。
- 预期结果：79保留、目标52补关联、0新邮箱，任何差异明确记录；没有根据论文猜当前资格；1180px/80vh保留，CSS契约无漂移；旧审核/邮件功能不变。
- 覆盖：I-1/I-2/I-3；需求2/3和must-NOT-change。

### A-3: 持久化、限容、配置快照
- 前置条件：06运行中任务A快照true/true；保存配置true/false；测试FileStore可注入低空间。
- 操作步骤：1. 重启隔离实例检查保存值。2. 新启动B并看A/B执行记录。3. 注入空间不足分别测试SYNC/QUEUE。4. 恢复空间。5. 删除原文后再查看分析。
- 预期结果：保存值保留，A不改、B详情0；同步当前页不消费/不推进，队列PENDING+60s且attempts不增加；恢复后可继续；分析仍存在；没有重现系统500次Apollo日限额。
- 覆盖：I-2/I-4；需求1/4；存储与调度/配置与Worker的交互。


计划自审（2026-10-23）：8个子计划的章节顺序、变更清单、相对链接、I/S引用与新增class覆盖已检查；文件数依次9/8/6/9/6/5/10/5；两份CSS括号与DOM class覆盖检查通过。自审发现并修正了“升级缓存兼容常量会拒绝旧队列”“新增script触发16资源断言”“核验构造器/旧迁移测试遗漏”三处范围问题。以上是文档自审，不是产品测试或上线效果证明。

## 修正记录

- 2026-10-23：用户“批准 继续”批准02暂停报告C-1/C-2的最小修订：02授权由8增至10文件，加入ArchiveService/ArchiveRepository，明确已启用scope的保存完成与原子保留first/更新latest。依据I-3与执行前检查第5项，保持每子计划≤10文件且不改变准入或缓存规则；共享文件顺序同步为01→02→04。批准和提交身份记录在 `../fast/discovery-paper-master/ledger.md` 的 Amendments 表；原始自审计数为历史记录。
- 2026-10-24：用户“批准 继续”批准03暂停报告C-1：03由6增至7文件，增加CaptureTest新采集版本断言同步，保留first冻结与缓存兼容约束。依据I-3及执行前检查第5项；不改变真实样本缺失时不得宣称效果已验证的边界。批准与提交身份见 `../fast/discovery-paper-master/ledger.md` Amendments。
