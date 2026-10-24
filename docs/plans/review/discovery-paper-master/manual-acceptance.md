# Manual Acceptance — discovery-paper-master

## Epoch 2 — 2026-10-24T14:20:14+08:00

- Reviewed code boundary: c6e9709f349f32c5feb1d067b47c697b0dff05b6..1ebe3534be212c26eb1e490d747ae4a10daa702f
- Machine report epoch: 2
- Status: PENDING
- Governing master: docs/plans/2026-10-23/discovery-paper-master.md; sha256 fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3; recorded commit 3c5a68ad9917fdee22e0548b5afc57a6df04d580; A1–A16.
- 以下26项仅导出原主/子计划A-n。所有人工结果、报告人、证据、时间待人工提供；机器与浏览器夹具结果不能替代人工验收。
- 03原294/page34与04原23/person-label真实效果证据仍不完整；缺失原文或标签的项目保持PENDING，不由机器PASS豁免。

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---|---|---|---|---|---|---|
| master/A-1 | YES | 一次完整发现到管理 | 全文尝试可追溯；共享/保护按01跳过；取消保护不删文件；分析保留，历史资格不自动赋当前；Apollo详情请求0；旧准入及邮件状态无额外变化。 | PENDING | PENDING | PENDING | PENDING |
| master/A-2 | YES | 明确效果与样式 | 79保留、目标52补关联、0新邮箱，任何差异明确记录；没有根据论文猜当前资格；1180px/80vh保留，CSS契约无漂移；旧审核/邮件功能不变。 | PENDING | PENDING | PENDING | PENDING |
| master/A-3 | YES | 持久化、限容、配置快照 | 保存值保留，A不改、B详情0；同步当前页不消费/不推进，队列PENDING+60s且attempts不增加；恢复后可继续；分析仍存在；没有重现系统500次Apollo日限额。 | PENDING | PENDING | PENDING | PENDING |
| 01/A-1 | YES | 配置重启与容量 | 设置仍为 25/15 GiB；磁盘卡数据有 measuredAt；第 4 步返回 ARCHIVE_STORAGE_BLOCKED，SAVED 增量 0。DB 暂停后的 GET 为 503 而非默认配置。 | PENDING | PENDING | PENDING | PENDING |
| 01/A-2 | YES | 保护、共享及批量取消 | 步骤 1 可删 1 文件/2048 bytes，保护 B、共享文件跳过；步骤 3 changed=1 且所有原文仍可读；步骤 5 删除 2 文件/3072 bytes，元数据和分析保留。 | PENDING | PENDING | PENDING | PENDING |
| 01/A-3 | YES | 下载与失败恢复 | 401；PDF 可看，HTML 显示文字不运行；206 且 100 bytes；重启后文件已删除且元数据可查；旧样本 hash 与回放结果不变。 | PENDING | PENDING | PENDING | PENDING |
| 02/A-1 | YES | 全来源与失败分类 | 4 次成功存储、3 个新文件、1 次复用；404=DOWNLOAD_FAILED、超限=TOO_LARGE、无地址无正文；没有将原文 4 记为专家 4；旧 samples 关闭不影响上述结果。 | PENDING | PENDING | PENDING | PENDING |
| 02/A-2 | YES | 空间不足与恢复 | 阻断期间新正文 HTTP=0；同步未推进本页，队列 attempts 不增加且延期 60 秒；恢复后正文入库并按原业务流程消费，不能跳过两篇中的任一篇。 | PENDING | PENDING | PENDING | PENDING |
| 02/A-3 | YES | 关闭、缓存、取消 | 步骤 1 不新增全文请求或伪造原文；步骤 3 已存原文仍可读、已准入专家仍在；新库不改旧采样文件 hash。 | PENDING | PENDING | PENDING | PENDING |
| 03/A-1 | YES | 真实收益复核 | 79/79保留，新增52，邮箱新增0，无错误归属；原有79差异为空。文件缺失必须显示不完整，不报已验证。 | PENDING | PENDING | PENDING | PENDING |
| 03/A-2 | YES | 反例与旧行为 | 五个反例均没有新增独占归属；旧有效归属不减少；缓存/first版本仍为原值，新归档pdfLayout版本20261023、identityExtraction仍20261004；专家和准入表没有写入。 | PENDING | PENDING | PENDING | PENDING |
| 04/A-1 | YES | 职称/学位、年份和主体 | 三种原词和分类分开，年份2023、PUBLICATION_TIME；致谢不赋资格；原文定位一致；专家和准入状态完全不变，没有“符合准入3人”计数。 | PENDING | PENDING | PENDING | PENDING |
| 04/A-2 | YES | 对照、缺失与删除 | 完整者显示真实前后关联数；缺基线者BASELINE_MISSING、delta=null；缺文件者PARTIAL、delta=null；扫描图NOT_FOUND而非NOT_MET；第二次命中缓存；删除后分析可读、重分析410。 | PENDING | PENDING | PENDING | PENDING |
| 04/A-3 | YES | 真实资格审计回归 | 任何结构化事实均有本人原句；其余49不能被自动认定满足/不满足；未覆盖的已知原句列为未实现覆盖，不作虚假成功；生产写入和外部请求均0。 | PENDING | PENDING | PENDING | PENDING |
| 05/A-1 | YES | 真实页面与窄屏 | 顺序采集运行/专家审核/审核记录/论文原文；桌面modal宽1180px、最大高80vh；主色#1e40af，checkbox14px、表头#f8f9fb且不悬浮遮正文；窄屏卡片/工具栏可操作，表格仅自身横滚；发信和原三页签布局/功能不变。 | PENDING | PENDING | PENDING | PENDING |
| 05/A-2 | YES | 批量取消保护和安全删除 | 步骤1 changed1、选择0、原文保留；步骤3取消没有删除；步骤4删物理文件2个/逻辑3072bytes；分析记录不变；磁盘卡measuredAt更新且不把逻辑字节当立即释放磁盘量。 | PENDING | PENDING | PENDING | PENDING |
| 05/A-3 | YES | 预览、资格证据和故障 | 三个分类与2023年份分开，显示固定历史提示；无基线者delta不是0；旧响应不串任务；保存失败弹窗保留输入；删除409刷新预检查且不自动重删；已准入专家数量不变。 | PENDING | PENDING | PENDING | PENDING |
| 05/A-4 | YES | 持久化与下载路径 | 配置25/15GiB与保护状态保留；下载200、HTML只显示文字；没有重复事件/请求；原审核操作与历史记录仍可用。 | PENDING | PENDING | PENDING | PENDING |
| 06a/A-1 | YES | 保存、独立开关、重启 | 初始source=DEPLOYMENT_DEFAULT、detail=false；首次保存revision1，重启仍true/false；最后false/true且effectiveDetails=false；没有请求Apollo。 | PENDING | PENDING | PENDING | PENDING |
| 06a/A-2 | YES | 冲突与故障 | B409；DB故障503，不回退默认；没有Key；任务快照和所有准入状态不变。 | PENDING | PENDING | PENDING | PENDING |
| 06b/A-1 | YES | 独立保存和重启 | 首次detail=false；保存重启后true/false保留；步骤4存false/true，effectiveSearch=false/effectiveDetails=false，未自动将detail抹成false；没有Apollo请求。 | PENDING | PENDING | PENDING | PENDING |
| 06b/A-2 | YES | 新旧任务配置边界 | A/L继续原详情路径；B/C/D搜索允许、详情请求0，必要详情缺失为待核验；summary明确A/L与B/C/D开关不同；配置不改人工已审核状态。 | PENDING | PENDING | PENDING | PENDING |
| 06b/A-3 | YES | 配置失败、日志及现有门禁 | 后保存409、数据库不可用503，不静默付费；统计3/2/1/pending1；原能准入者结果不变，歧义与人工拒绝未放开；不产生500日上限。 | PENDING | PENDING | PENDING | PENDING |
| 07/A-1 | YES | 保存与生效范围 | 步骤2提示“有未保存修改”，没有新任务；步骤3回到true/false；刷新后false/true保持且明确搜索关闭时不调用；A仍显示本次付费开启，不被表单当前值覆盖。 | PENDING | PENDING | PENDING | PENDING |
| 07/A-2 | YES | 失败、重复保存与请求路径 | B先409，503时草稿保留；双击1个PUT；两种启动请求都不携带apolloAssist；保存值重开保持；无Apollo测试调用/Key泄露。 | PENDING | PENDING | PENDING | PENDING |
| 07/A-3 | YES | 分渠道日志与整体样式回归 | 显示3/2/1和登记尚未确认1，保留原准入数并显示非账单说明；新卡片在≤640px一列，否则两列，checkbox14px、主色#1e40af、焦点#3b82f6；原文/审核功能及其他任务样式不变。 | PENDING | PENDING | PENDING | PENDING |

### master/A-1 — 一次完整发现到管理

- 原计划：[discovery-paper-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-master.md)；SHA-256 fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3
- 前置条件：01～07已部署隔离验收实例，使用02多来源假服务+01共享/保护夹具，原文开启20/15GiB，Apollo保存true/false；记录既有专家/准入/邮件基线。
- 操作步骤：1. 启动发现。2. 从执行记录查看本次原文。3. 解析并定位本人原句。4. 批量取消保护、预检查并删除可删原文。5. 刷新页面检查统计/专家/邮件。
- 预期结果：全文尝试可追溯；共享/保护按01跳过；取消保护不删文件；分析保留，历史资格不自动赋当前；Apollo详情请求0；旧准入及邮件状态无额外变化。
- 覆盖：I-2/I-3/I-4；需求1/3/4；跨下载→存储→UI→分析→删除。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### master/A-2 — 明确效果与样式

- 原计划：[discovery-paper-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-master.md)；SHA-256 fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3
- 前置条件：03固定294样本、04固定23篇资格审计集可访问；1440×1080与643×900浏览器。
- 操作步骤：1. 执行03前后回放并阅读汇总。2. 核对资格原句。3. 对照05/07契约查看所有空/错/禁用/窄屏状态。4. 打开原审核页和普通邮件任务。
- 预期结果：79保留、目标52补关联、0新邮箱，任何差异明确记录；没有根据论文猜当前资格；1180px/80vh保留，CSS契约无漂移；旧审核/邮件功能不变。
- 覆盖：I-1/I-2/I-3；需求2/3和must-NOT-change。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### master/A-3 — 持久化、限容、配置快照

- 原计划：[discovery-paper-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-master.md)；SHA-256 fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3
- 前置条件：06运行中任务A快照true/true；保存配置true/false；测试FileStore可注入低空间。
- 操作步骤：1. 重启隔离实例检查保存值。2. 新启动B并看A/B执行记录。3. 注入空间不足分别测试SYNC/QUEUE。4. 恢复空间。5. 删除原文后再查看分析。
- 预期结果：保存值保留，A不改、B详情0；同步当前页不消费/不推进，队列PENDING+60s且attempts不增加；恢复后可继续；分析仍存在；没有重现系统500次Apollo日限额。
- 覆盖：I-2/I-4；需求1/4；存储与调度/配置与Worker的交互。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 01/A-1 — 配置重启与容量

- 原计划：[discovery-paper-01-storage.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-01-storage.md)；SHA-256 6e1d78f48e71e9a518ce6cb7ea8de1cbd6ae2af76a0d837351aacd693d9ee0d3
- 前置条件：隔离验收实例用本计划测试夹具生成目录和空归档表；GET settings 返回 enabled=false、maxBytes=21474836480、minFreeBytes=16106127360。
- 操作步骤：1. PUT revision=0、enabled=true、maxBytes=26843545600、minFreeBytes=16106127360。2. 重启验收实例。3. 再 GET settings/stats。4. 在测试 FileStore 夹具将 usableSpace 设为 16106127359，调用 saveBody 夹具。
- 预期结果：设置仍为 25/15 GiB；磁盘卡数据有 measuredAt；第 4 步返回 ARCHIVE_STORAGE_BLOCKED，SAVED 增量 0。DB 暂停后的 GET 为 503 而非默认配置。
- 覆盖：I-2/I-4；容量、持久配置。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 01/A-2 — 保护、共享及批量取消

- 原计划：[discovery-paper-01-storage.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-01-storage.md)；SHA-256 6e1d78f48e71e9a518ce6cb7ea8de1cbd6ae2af76a0d837351aacd693d9ee0d3
- 前置条件：ServiceTest 同一 fixture 可通过测试启动器建 A/B 共享 1024 字节、C 独占 2048 字节，B protected=true；fixture 只在 test classpath，无生产造数路由。
- 操作步骤：1. 预检查删除 A/B/C。2. 取消。3. POST protection 将 B=false。4. GET 三条验证原文还在。5. 再预检查并确认删除 A/B/C。
- 预期结果：步骤 1 可删 1 文件/2048 bytes，保护 B、共享文件跳过；步骤 3 changed=1 且所有原文仍可读；步骤 5 删除 2 文件/3072 bytes，元数据和分析保留。
- 覆盖：I-1/I-2/I-3；旧专家、邮件和队列计数前后相同。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 01/A-3 — 下载与失败恢复

- 原计划：[discovery-paper-01-storage.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-01-storage.md)；SHA-256 6e1d78f48e71e9a518ce6cb7ea8de1cbd6ae2af76a0d837351aacd693d9ee0d3
- 前置条件：验收测试夹具一份 PDF、一份含 `<script>alert(1)</script>` 的 HTML；一份旧 samples 控制样本。
- 操作步骤：1. 未登录访问 content。2. 登录后预览 PDF、HTML。3. 请求 PDF bytes=0-99。4. 使用 failpoint 夹具中断 delete 的 unlink 后事务，重启。5. 重放旧 samples 控制样本。
- 预期结果：401；PDF 可看，HTML 显示文字不运行；206 且 100 bytes；重启后文件已删除且元数据可查；旧样本 hash 与回放结果不变。
- 覆盖：I-1/I-2/I-5；访问/删除/旧抽样交互。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 02/A-1 — 全来源与失败分类

- 原计划：[discovery-paper-02-capture.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-02-capture.md)；SHA-256 53381edc616c15ae87db31c1abacea4a4652702a60432dec0aa713fb57af11c4
- 前置条件：隔离实例加载 FlowIT 的 6 来源假服务：PDF 两次相同字节、XML 一次、CORE 文本一次、404 一次、超限一次、无地址一次。启用归档，关闭旧 samples。
- 操作步骤：1. 启动一轮发现。2. GET papers/stats 与逐条详情。3. 下载已有原文校验 fixture SHA256。
- 预期结果：4 次成功存储、3 个新文件、1 次复用；404=DOWNLOAD_FAILED、超限=TOO_LARGE、无地址无正文；没有将原文 4 记为专家 4；旧 samples 关闭不影响上述结果。
- 覆盖：I-1/I-2/I-4；来源→归档→查询。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 02/A-2 — 空间不足与恢复

- 原计划：[discovery-paper-02-capture.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-02-capture.md)；SHA-256 53381edc616c15ae87db31c1abacea4a4652702a60432dec0aa713fb57af11c4
- 前置条件：FlowIT 的两篇同页同步任务以及一个待抽取 queue job；假 FileStore 可用空间低于阈值。
- 操作步骤：1. 分别执行同步/队列。2. 查任务和 papers。3. 释放空间并恢复队列；同步重新启动同条件。4. 查看 checkpoint/请求次数。
- 预期结果：阻断期间新正文 HTTP=0；同步未推进本页，队列 attempts 不增加且延期 60 秒；恢复后正文入库并按原业务流程消费，不能跳过两篇中的任一篇。
- 覆盖：I-3；存储→同步游标/队列状态。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 02/A-3 — 关闭、缓存、取消

- 原计划：[discovery-paper-02-capture.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-02-capture.md)；SHA-256 53381edc616c15ae87db31c1abacea4a4652702a60432dec0aa713fb57af11c4
- 前置条件：已有 extractionJson 的 job、一个已存原文记录及一位已准入专家；保存关闭归档。
- 操作步骤：1. 消费缓存 job。2. 开启归档并运行假来源。3. 首份原文保存后取消任务。
- 预期结果：步骤 1 不新增全文请求或伪造原文；步骤 3 已存原文仍可读、已准入专家仍在；新库不改旧采样文件 hash。
- 覆盖：I-2/I-4；缓存与取消回归。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 03/A-1 — 真实收益复核

- 原计划：[discovery-paper-03-parser.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-03-parser.md)；SHA-256 c46019e05d5315854277057d2550541093b5f4649a7961c2f4a30f9ccf999abe
- 前置条件：通过 RegressionTest 的私有样本入口固定原 294 样本；执行代理生成 hash 清单及前后对照汇总，所有原文仍在服务器。
- 操作步骤：1. 分别运行基线及新 parser。2. 执行 verify_layout_gain.py。3. 用原文查看器逐条检查新增52组对应页码/完整姓名/邮箱块；打开原有79组的差异清单。
- 预期结果：79/79保留，新增52，邮箱新增0，无错误归属；原有79差异为空。文件缺失必须显示不完整，不报已验证。
- 覆盖：I-1/I-2/I-3；真实原文→新规则→汇总。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 03/A-2 — 反例与旧行为

- 原计划：[discovery-paper-03-parser.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-03-parser.md)；SHA-256 c46019e05d5315854277057d2550541093b5f4649a7961c2f4a30f9ccf999abe
- 前置条件：合成 PDF fixtures 分别为跨栏、两作者同名、一块两个邮箱、下一作者介入、旧共享标记。
- 操作步骤：1. 使用同一回放入口分析。2. 比较部署前后解析结果。3. 检查带旧版本的队列缓存与原留档 first。
- 预期结果：五个反例均没有新增独占归属；旧有效归属不减少；缓存/first版本仍为原值，新归档pdfLayout版本20261023、identityExtraction仍20261004；专家和准入表没有写入。
- 覆盖：I-1～I-4；must-NOT-change 全部回归。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 04/A-1 — 职称/学位、年份和主体

- 原计划：[discovery-paper-04-analysis.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-04-analysis.md)；SHA-256 d506cc357820388ec3ef7a094314c28e58196db9800c84df52f0757459ecbf49
- 前置条件：隔离测试夹具含三位不同作者：甲“Assistant Professor”，乙“received his PhD”，丙“PhD student”；均在明确本人简介且paperYear=2023；另含对他人教授的致谢。
- 操作步骤：1. 对原文POST analysis。2. GET详情。3. 打开原句所在页/段落。4. 查这三人现有专家/准入状态。
- 预期结果：三种原词和分类分开，年份2023、PUBLICATION_TIME；致谢不赋资格；原文定位一致；专家和准入状态完全不变，没有“符合准入3人”计数。
- 覆盖：I-1/I-2/I-3；原文→分析→查看。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 04/A-2 — 对照、缺失与删除

- 原计划：[discovery-paper-04-analysis.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-04-analysis.md)；SHA-256 d506cc357820388ec3ef7a094314c28e58196db9800c84df52f0757459ecbf49
- 前置条件：一记录有完整旧first和正文，一记录缺旧基线，一记录2份正文中缺1份，一记录只有扫描图。
- 操作步骤：1. 分别分析四条。2. 对第一条再次分析。3. 删除第一条原文再GET详情/POST analysis。
- 预期结果：完整者显示真实前后关联数；缺基线者BASELINE_MISSING、delta=null；缺文件者PARTIAL、delta=null；扫描图NOT_FOUND而非NOT_MET；第二次命中缓存；删除后分析可读、重分析410。
- 覆盖：I-2/I-3/I-4；删除与分析交互。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 04/A-3 — 真实资格审计回归

- 原计划：[discovery-paper-04-analysis.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-04-analysis.md)；SHA-256 d506cc357820388ec3ef7a094314c28e58196db9800c84df52f0757459ecbf49
- 前置条件：服务器私有23篇/432页固定hash与人工labels，需提前验证尚有原文；仅在隔离验收实例运行。
- 操作步骤：1. 批量调用纯函数验收入口。2. 比对1位教授且博士、2位当时博士在读与其余49人。3. 查看所有新增事实的原句。
- 预期结果：任何结构化事实均有本人原句；其余49不能被自动认定满足/不满足；未覆盖的已知原句列为未实现覆盖，不作虚假成功；生产写入和外部请求均0。
- 覆盖：I-1～I-4；真实证据边界。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 05/A-1 — 真实页面与窄屏

- 原计划：[discovery-paper-05-frontend.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-05-frontend.md)；SHA-256 4c8de3b3406ca4d1129713fe1b7ea6ae79067112ceac411fa5a3a42de3995f00
- 前置条件：01的A/B共享+保护、C独占夹具及一条无正文记录，登录隔离实例；窗口1440×1080。
- 操作步骤：1. 专家列表→深度发现。2. 查看四页签并键盘切换。3. 进论文原文。4. 改643×900，横向滚动表格。5. 打开普通批量发信任务对照。
- 预期结果：顺序采集运行/专家审核/审核记录/论文原文；桌面modal宽1180px、最大高80vh；主色#1e40af，checkbox14px、表头#f8f9fb且不悬浮遮正文；窄屏卡片/工具栏可操作，表格仅自身横滚；发信和原三页签布局/功能不变。
- 覆盖：I-1/I-3，S-1～S-4。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 05/A-2 — 批量取消保护和安全删除

- 原计划：[discovery-paper-05-frontend.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-05-frontend.md)；SHA-256 4c8de3b3406ca4d1129713fe1b7ea6ae79067112ceac411fa5a3a42de3995f00
- 前置条件：A/B共享1024bytes、B保护，C独占2048bytes；使用01测试夹具构造。
- 操作步骤：1. 筛选已保护，选B，点批量取消保护。2. 清筛选，查看B原文。3. 选择A/B/C，删除预检查后取消。4. 再次预检查并确认。5. 查看解析和磁盘卡。
- 预期结果：步骤1 changed1、选择0、原文保留；步骤3取消没有删除；步骤4删物理文件2个/逻辑3072bytes；分析记录不变；磁盘卡measuredAt更新且不把逻辑字节当立即释放磁盘量。
- 覆盖：I-1/I-2/I-4，S-2/S-3/S-4。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 05/A-3 — 预览、资格证据和故障

- 原计划：[discovery-paper-05-frontend.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-05-frontend.md)；SHA-256 4c8de3b3406ca4d1129713fe1b7ea6ae79067112ceac411fa5a3a42de3995f00
- 前置条件：04的甲Assistant Professor/乙已取得PhD/丙PhD student夹具；另一条BASELINE_MISSING；测试服务器可返回409/503与延迟响应。
- 操作步骤：1. 查看记录，确认没有自动分析。2. 点击解析对照，分别打开两类证据页签和定位。3. 切任务期间延迟旧列表。4. 保存设置时返回503。5. 用另一会话先保护文件，再在旧会话确认删除。
- 预期结果：三个分类与2023年份分开，显示固定历史提示；无基线者delta不是0；旧响应不串任务；保存失败弹窗保留输入；删除409刷新预检查且不自动重删；已准入专家数量不变。
- 覆盖：I-1～I-4，S-1～S-4；API→UI全部写读路径。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 05/A-4 — 持久化与下载路径

- 原计划：[discovery-paper-05-frontend.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-05-frontend.md)；SHA-256 4c8de3b3406ca4d1129713fe1b7ea6ae79067112ceac411fa5a3a42de3995f00
- 前置条件：应用部署在/talent，记录一份PDF与一份HTML；当前设置20/15GiB。
- 操作步骤：1. 保存25/15GiB，保护一份原文。2. 刷新并重启隔离实例。3. 下载两份。4. 关闭重开弹窗，再打开专家审核与审核记录。
- 预期结果：配置25/15GiB与保护状态保留；下载200、HTML只显示文字；没有重复事件/请求；原审核操作与历史记录仍可用。
- 覆盖：I-2/I-3/I-4，S-1/S-3；持久配置/旧页面回归。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 06a/A-1 — 保存、独立开关、重启

- 原计划：[discovery-paper-06a-apollo-persistence.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md)；SHA-256 9802f4f4b4009a8dfbf57eb158efb83e32a32e5badc7f8f27f5fddc4b6aa3fc7
- 前置条件：隔离验收实例应用V160，表空，服务/key配置可用；登录获得会话。
- 操作步骤：1. GET设置。2. PUT revision0/search=true/detail=false。3. 重启实例再GET。4. 按返回revision PUT search=false/detail=true。
- 预期结果：初始source=DEPLOYMENT_DEFAULT、detail=false；首次保存revision1，重启仍true/false；最后false/true且effectiveDetails=false；没有请求Apollo。
- 覆盖：I-1/I-2/I-3；HTTP→DB→重启回读。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 06a/A-2 — 冲突与故障

- 原计划：[discovery-paper-06a-apollo-persistence.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md)；SHA-256 9802f4f4b4009a8dfbf57eb158efb83e32a32e5badc7f8f27f5fddc4b6aa3fc7
- 前置条件：两个会话读取同revision；测试Key值为合成字符串；可临时停止隔离MySQL。
- 操作步骤：1. A/B依次用旧revision保存不同组合。2. 停MySQL再GET/PUT。3. 查响应与日志。4. 恢复数据库并查正在运行任务快照。
- 预期结果：B409；DB故障503，不回退默认；没有Key；任务快照和所有准入状态不变。
- 覆盖：I-2/I-3/I-4；must-NOT-change。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 06b/A-1 — 独立保存和重启

- 原计划：[discovery-paper-06b-apollo-runtime.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md)；SHA-256 41c6fd43b5cd37153a524d251cc9283e15e8d73581a423851b053db91324b170
- 前置条件：隔离实例连接测试MySQL，配置假Apollo服务与测试Key，清空新settings表。
- 操作步骤：1. GET设置。2. PUT search=true/detail=false。3. 重启并GET。4. PUT search=false/detail=true。5. 再GET capabilities。
- 预期结果：首次detail=false；保存重启后true/false保留；步骤4存false/true，effectiveSearch=false/effectiveDetails=false，未自动将detail抹成false；没有Apollo请求。
- 覆盖：I-1；存储→API→重启。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 06b/A-2 — 新旧任务配置边界

- 原计划：[discovery-paper-06b-apollo-runtime.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md)；SHA-256 41c6fd43b5cd37153a524d251cc9283e15e8d73581a423851b053db91324b170
- 前置条件：本计划WorkerTest的SQL fixture建runA新快照true/true和legacy runL；让A停在详情前。随后保存true/false。
- 操作步骤：1. 恢复A、L。2. 分别手动/定时/队列启动B/C/D。3. 查看任务summary和假HTTP请求日志。
- 预期结果：A/L继续原详情路径；B/C/D搜索允许、详情请求0，必要详情缺失为待核验；summary明确A/L与B/C/D开关不同；配置不改人工已审核状态。
- 覆盖：I-2/I-3/I-4；各启动入口→Worker→summary。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 06b/A-3 — 配置失败、日志及现有门禁

- 原计划：[discovery-paper-06b-apollo-runtime.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md)；SHA-256 41c6fd43b5cd37153a524d251cc9283e15e8d73581a423851b053db91324b170
- 前置条件：上述3搜索/2详情/1其他账目fixture；原直接教授/SELF成功fixture、仅姓名歧义fixture、人工拒绝fixture；两会话读取同revision。
- 操作步骤：1. 两会话相继保存不同值。2. 暂停DB再GET。3. 恢复DB，查看尝试统计并运行资格fixtures。
- 预期结果：后保存409、数据库不可用503，不静默付费；统计3/2/1/pending1；原能准入者结果不变，歧义与人工拒绝未放开；不产生500日上限。
- 覆盖：I-1～I-4；设置/报表与must-NOT-change回归。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 07/A-1 — 保存与生效范围

- 原计划：[discovery-paper-07-apollo-frontend.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-07-apollo-frontend.md)；SHA-256 b1fc292788236a5620340395e393a97216cac516df449a2ad3f1ccfdd703fa69
- 前置条件：隔离实例06已部署，已保存搜索true/详情false；一正在执行任务A快照true/true。
- 操作步骤：1. 打开深度发现，勾选付费但不保存。2. 点开始执行。3. 恢复已保存值。4. 保存搜索false/详情true。5. 刷新页面，查看A日志。
- 预期结果：步骤2提示“有未保存修改”，没有新任务；步骤3回到true/false；刷新后false/true保持且明确搜索关闭时不调用；A仍显示本次付费开启，不被表单当前值覆盖。
- 覆盖：I-1/I-2/I-3，S-1/S-2。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 07/A-2 — 失败、重复保存与请求路径

- 原计划：[discovery-paper-07-apollo-frontend.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-07-apollo-frontend.md)；SHA-256 b1fc292788236a5620340395e393a97216cac516df449a2ad3f1ccfdd703fa69
- 前置条件：两浏览器会话读同revision；测试代理可延迟PUT/返回503；观察Network。
- 操作步骤：1. A保存后B保存。2. B恢复后再次修改，模拟503并双击保存。3. 恢复服务保存成功，分别用默认来源/关键词启动。4. 关闭弹窗再重开。
- 预期结果：B先409，503时草稿保留；双击1个PUT；两种启动请求都不携带apolloAssist；保存值重开保持；无Apollo测试调用/Key泄露。
- 覆盖：I-1/I-2/I-4，S-1/S-3。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

### 07/A-3 — 分渠道日志与整体样式回归

- 原计划：[discovery-paper-07-apollo-frontend.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-07-apollo-frontend.md)；SHA-256 b1fc292788236a5620340395e393a97216cac516df449a2ad3f1ccfdd703fa69
- 前置条件：06的3搜索/2详情/1其他/pending1 fixture；1440×1080及643×900窗口。
- 操作步骤：1. 展开执行记录。2. 看当前配置和任务快照。3. 检查两种窗口宽度，再切论文原文、专家审核、审核记录，打开另一个普通任务。
- 预期结果：显示3/2/1和登记尚未确认1，保留原准入数并显示非账单说明；新卡片在≤640px一列，否则两列，checkbox14px、主色#1e40af、焦点#3b82f6；原文/审核功能及其他任务样式不变。
- 覆盖：I-2/I-3/I-4，S-1～S-3；must-NOT-change回归。
- Human result: PENDING
- Evidence/note: PENDING
- Reporter: PENDING
- Timestamp: PENDING

## Human Sign-off

- Decision: PENDING
- Boundary: 1ebe3534be212c26eb1e490d747ae4a10daa702f
- Accepted master identity: PENDING — fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3 / 3c5a68ad9917fdee22e0548b5afc57a6df04d580 / A1–A16
- Reporter: PENDING
- Timestamp: PENDING
- Note: PENDING
- 仅接受人工逐项PASS/FAIL及证据/说明；全部必选项通过后，须明确签署上述代码边界与主计划身份。后续人工结果追加新记录，不改写本待验快照。
