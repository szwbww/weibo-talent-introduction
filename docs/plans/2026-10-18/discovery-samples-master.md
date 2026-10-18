# 深度发现失败样本留存与离线回放 · 总计划

2026-10-18；状态：待实施。本次只编写计划，未修改业务代码、未部署、未声称失败率已有改善。
代码基线：`3efc192faf043badc1ef27bef124a71d3120c4c5`；工作区已有其他任务变更，不得覆盖。
执行规范：[数据与采集契约](discovery-samples-contracts.md)；[代码审计与证据](discovery-samples-code-audit.md)。

## 需求描述

O-1：下一批 OPENALEX 发现可留下限量的失败输入与成功对照，能按执行 ID 找到论文元数据、当次原始正文、抽取结果及下载失败类别。
O-2：用同一输入离线比较两个代码版本，报告新增/丢失/改变的作者邮箱绑定；有人工真值时才能报告准确性改善。

必须保持：N-1 原抽取规则、网络重试/超时/大小限制与七个平台行为；N-2 原任务处理数、RAW 收录、准入、Apollo 核验、游标和暂停/恢复语义；N-3 生产队列缓存复用和身份缓存版本；N-4 诊断存储故障不改变业务返回/异常，磁盘与额外留存内存均有界。

范围外：本轮不修 PDF 解析/403/身份召回，不加 Apollo 调用，不改日志页面、不新增 CSS/API/数据库表/ES 字段，不做通用爬虫、全量抓包、任务回放入库、历史批次反推、自动优化或成功率承诺。首版仅 OPENALEX；其内部走 EuropePMC 的 XML 仍算 OPENALEX 样本。其他来源直接调用同一个解析器不能被采集。

真实基线：任务 **26127**，2026-10-15，2,200 篇论文，1,519 条作者邮箱记录，1,350 条身份未解析，123 档案 RAW 收录、0 晋升。已核对聚合日志，未找回这一批逐篇原始输入；另有 2026-09-29 的 6 组历史 PDF+OpenAlex 元数据，不能冒充本批样本。详见证据 summary.json。

## 关键不变量

### Invariant I-1: 采样不是准入账本
- Rule：样本固定 stage=EXTRACTION；一份样本对应一次被保留的论文抽取观察，不表示该论文已消费、RAW 已写或已准入。缺姓名的记录数与论文样本数分开。所有配置、字段、状态按 contracts 定义。
- Applies to：01存储、02采集、03报告。
- Violation consequence：暂停后的预抽取和多邮箱论文导致虚假收录/准入计数。
- 来源：K-progress-log-batchonly-two-readers；本次代码审计。

### Invariant I-2: 保存发生过的输入
- Rule：正文必须来自当次业务已完整读取的字节；不得补发请求。元数据保留原始作者顺序和内容；缺正文明确标记。真实执行 ID 显式传到实际 worker，不能取全局最近任务；不得伪造 OpenAlex work ID。schemaVersion=1 与 identityRuleVersion 各自独立。
- Applies to：01模型、02两个入口和下载钩子、03输入校验。
- Violation consequence：串执行、重抓伪装原样本、错误绑定无法复现。
- 来源：K-source-recall-needs-original-layout；K-task-activity-execution-identity。

### Invariant I-3: 诊断旁路与资源限制
- Rule：默认关闭；只捕获诊断自己的错误，不吞业务异常。无新的网络请求、业务持久化字段或后台下载线程。原始业务结果保持相同；额外文件 IO 耗时需要实测，不能承诺零开销。容量、留存和跳过原因按 contracts 执行。
- Applies to：01/02所有新写路径与清理。
- Violation consequence：磁盘满或锁竞争阻塞业务、诊断变成生产故障。
- 来源：K-page-commit-must-cover-consumer-failures。

### Invariant I-4: 回放结论有边界
- Rule：只离线解析，不访问网络/DB/ES/Apollo。比较双方输入 SHA 与解析配置相同；无人工标注不计算准确率。403/TLS/超时等可统计已观察类别，不能通过离线正文回放证明网络问题已改善。采样非随机，不外推整批收益。
- Applies to：03工具及结果解释。
- Violation consequence：新增邮箱误算正确绑定，制造无证据的优化效果。
- 来源：K-author-identity-needs-email-evidence；K-source-recall-needs-original-layout。

### Invariant I-5: Apollo 共享入口有顺序
- Rule：与 Apollo 计划独立工作树开发；本计划02合入共享服务前，先完成 Apollo05 的合并与回归，再按最新签名接入。采样仅包围抽取，不包围登记/消费。保留 Apollo reserve→RAW→activate 的异常传播与检查点约束。
- Applies to：02合并、最终集成测试。
- Violation consequence：吞登记失败、旧准入绕过、互相覆盖实现。
- 来源：apollo-05-integration.md；K-page-commit-must-cover-consumer-failures。

## 现状审计

具体 schema、全部相关读写检索、源码节选和 SHA 清单见 [audit](discovery-samples-code-audit.md)。新文件仓库目前不存在；不借用 queue extraction_json、任务 details_json 或专家索引存样本。

- IP-1：同步正 executionId→线程池抽取→文件。整页先抽取，之后逐条检查暂停/预算。
- IP-2：队列 executionId/jobId→fresh extraction→文件；缓存消费无下载。队列租约失效或暂停仍可能已有抽取样本。
- IP-3：Pdf/EuropePMC 完整响应字节→采集上下文→原子文件提交→回放读取。
- IP-4：文件提交与清理→列表/回放读取；需识别并发清理和损坏，不能读取半包。
- IP-5：共享发现服务→Apollo 登记/RAW/准入；所有业务写路径保持原状。

## 实现方案

1. [01 存储与限额](discovery-samples-01-store.md)：6文件，独立样本子系统，默认关闭，可单独部署/验证。执行 I-1～I-3。
2. [02 采集接入](discovery-samples-02-capture.md)：8文件，发现入口与全文采集两个子系统，依赖01；共享文件在 Apollo05 后合并。执行 I-1～I-3/I-5。
3. [03 离线回放](discovery-samples-03-replay.md)：4文件，离线诊断工具子系统，依赖01 schema，可先用合成包开发；接入02后验收真实新样本。执行 I-2/I-4。

无需新增 UI，所以本计划不触及样式；Apollo 日志页面仍由原计划07负责。生产开启使用外部配置，路径须预先确认所有者和空间；本计划不授权执行时直接触发大规模线上采集。

## 变更文件清单

总计划仅编排下列三份子计划，不能直接以总计划越过子计划文件上限执行。实施共 **18个不同文件：新增10、修改8**；每片≤10文件、≤2子系统；既有共享存储新增字段 **0**。精确清单在各子计划，不包含本轮计划/证据/知识文档。

|顺序|子计划|实施文件数|与 Apollo 重叠|
|---|---|---:|---|
|1|discovery-samples-01-store.md|6|0|
|2|discovery-samples-02-capture.md|8|2个服务+2个对应测试|
|3|discovery-samples-03-replay.md|4|0|

## 验收标准

- I-1：暂停尾部样本仍 `EXTRACTION`，无准入字段；论文数/作者邮箱数独立。
- I-2：同步/队列串行和并行均匹配实际 execution；正文 hash 与业务收到字节一致；缓存命中零新增下载。
- I-3：关闭、目录不可写、空间不足、锁占用、超限、重启清理均有确定预期；对照网络调用序列及业务输出不变；记录耗时差而不提前宣称改善。
- I-4：同代码回放零差异；错 hash 拒绝；网络不可用仍可解析；无正文明确跳过；人工真值缺失准确率为 null。
- I-5：Apollo05 与02合并后执行双方测试；采样失败不能让 Apollo 登记失败变成功。各片完成机器验证后分别人工验收。

计划结构自检见 [plan-self-review.json](discovery-samples-evidence/plan-self-review.json)：清单路径、文件数量、必需章节、I/O/N/IP验收覆盖、链接及证据SHA已核对。这是计划自检，不是实施测试通过记录。

## 人工验收清单

### A-1: 留存到离线读取
- 前置条件：完成01～03；按02 A-1生成 `/tmp/discovery-sample-acceptance/capture` 合成样本与验收结果。
- 操作步骤：1. 运行03 runbook的 list。2. 对该目录运行 replay。3. 查看两份 JSON。
- 预期结果：样本带正 executionId、source=OPENALEX、stage=EXTRACTION；有正文包的 SHA 校验通过；无正文的403包显示 NO_BODY；报告没有准入/收录人数。
- 覆盖：O-1/O-2、N-2、I-1/I-2/I-4、IP-1/IP-2/IP-3/IP-4。

### A-2: 业务保护与资源边界
- 前置条件：01/02测试提供 acceptanceDir 下的 caps、disabled、unwritable、cache、pause、apollo 六类验收产物，构造方法见子计划A项。
- 操作步骤：1. 按子计划01 A-2、02 A-2/A-3执行。2. 核对业务与请求序列对照报告。3. 查看 sample 事件日志。
- 预期结果：关闭与开关开启时业务结果对照 equal=true；缓存新增下载=0；不可写只产生 IO_ERROR 诊断；Apollo登记失败仍失败；采样上限断言 true。
- 覆盖：N-1/N-2/N-3/N-4、I-3/I-5、IP-2/IP-4/IP-5。

### A-3: 不夸大优化效果
- 前置条件：两个同代码版本、同一份输入包；未提供人工真值。
- 操作步骤：1. 按03 A-1分别生成A/B报告。2. compare。3. 阅读网络失败样本与精度字段。
- 预期结果：输入相同、绑定变化0、accuracy=null；403样本不算“修复”。本轮交付结果是可回放证据链，不填写“成功率提升X%”。
- 覆盖：O-2、N-1、I-4、IP-3/IP-4。
