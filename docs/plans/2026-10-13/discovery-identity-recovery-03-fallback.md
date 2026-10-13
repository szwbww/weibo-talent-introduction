# 03：有邮箱、无可靠身份时的有限来源回退

状态：方案，未实施；须先由 02 真实样本确认收益。对应 R3，为策略调整，不承诺现有遗漏规模。

## 需求描述

首个公开来源只找到邮箱而没有任何可靠作者关系时，预算内多尝试一个既有公开来源，提高可验证关系召回。

保持：已有可靠结果、完全没有邮箱的既有停止语义、身份标准、预算/限流/暂停、黑名单与发送规则。范围外：全面解析器重写、追加第三方搜索、绕验证码、仅按邮箱顺序分配作者、自动重跑旧队列。

## 关键不变量

### Invariant I-1：精确触发，不降低证据要求
- Rule：只有 foundEmailCount>0 且具有完整姓名和 validEvidence 的结果数为 0 才触发。已有至少一条可靠绑定按原路径返回；零邮箱按既有策略。CORE 可试已有 downloadUrl；OpenAlex 可试一个未尝试的既有公开候选 URL，无此候选则保持未知；不额外调用 Unpaywall 来满足本策略。
- Applies to：两来源 extractAuthorEmails。
- Violation consequence：额外抓取无限扩大，或将邮箱存在当作身份成立。
- 来源：源码 CoreDataSource:159、OpenAlexDataSource:105。

### Invariant I-2：预算与合并语义
- Rule：OpenAlex 整篇原 90 秒/最多 3 个 URL 不变，XML 与备用共用截止时间；CORE 从入口到后备共享同一截止时间并受 FulltextRequestGate 约束。最多一次“已获得内容后的后备请求”。若请求超时，已获取内容与未绑定邮箱仍保留，不改报未取到全文；HTTP 数累加。邮箱规范化去重；若来源之间出现明确不同作者关系则不签该邮箱证据。
- Applies to：下载调用、EmailExtractionOutcome 合并。
- Violation consequence：预算越界或统计误导。
- 来源：现有有界全文策略。

### Invariant I-3：只影响新鲜提取
- Rule：保持持久 proof VERSION；本轮仅改变回退，不接受旧缓存缺证据结果为可靠。EXTRACTION_VERSION 不必因增加后备来源而冒充重抽；新鲜抽取沿现有 stamping，旧缓存仍按现有兼容判断。需要重跑者由 02 新请求完成。
- Applies to：提取输出与队列消费。
- Violation consequence：未重取源却把旧缓存升级为新证明。
- 来源：当前队列契约。

## 现状审计

CORE 纯文本 emails 非空即返回 resolver 结果；OpenAlex XML 或 URL 的 resolvedFulltextObtained 为 true 即返回。两者是既有节省请求的设计，并非解析异常。当前 resolver/PDF 已有脚注、归一化与具名链接支持，本份不再重复开发这些功能。

本份不直接读写业务存储。输出通过 extractQueuedItem 写 V133 extraction_json；直接消费/consumeQueuedItem 再走 identityRejection→验证→去重→RAW→准入。写入仍全部在原 service/repository，不新增字段。主要交互 X1：后备提取→唯一绑定→消费；X2：超时→内容计数→任务报告；X3：新鲜输出/旧缓存→版本门禁。

## 实现方案

1. I-1/I-2：两来源增加共享“是否只有未绑定邮箱线索”的内部 helper，放 EmailExtractionOutcome.kt；只决定是否增加一次已有公开后备，不能给无证据 AuthorEmail 补证。
2. I-2：CORE 显式传递剩余 deadline；OpenAlex 保留已取结果供合并，不把后备失败覆盖为全文获取失败。报告 methodUsed 说明最终使用的方法，HTTP 数为两次之和。
3. I-1/I-3：以 02 找到的真实样本作为收益门槛；若现有资源没有这种真例，只保留提案，不把合成通过宣称为线上恢复。已有 parser 的 false-positive 负例全部保留。

## 变更文件清单

相对仓库根，共 6 个，无共享存储新增字段。

|文件|用途|
|---|---|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSource.kt|有限 PDF 后备|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt|有内容无身份时的有限后备|
|src/main/kotlin/com/weibo/talentintroduction/discovery/domain/EmailExtractionOutcome.kt|内部判定与合并 helper，不新增持久字段|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/CoreDataSourceTest.kt|文本线索到 PDF、预算|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt|XML/HTML线索到备用、统计|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt|提取结果到原去重/补证门禁|

## 验收标准

- I-1：未绑定邮箱触发一次；已有绑定、无邮箱不触发；唯一后备关系才成功；全部歧义仍未知。
- I-2：deadline 到点新增请求 0、最多 3 URL；后备失败时 fulltextObtained=true 且邮箱线索不丢；请求数准确；同邮箱结果只一条。
- I-3：旧缓存不改版本不回灌；新提取结果按原门禁消费；原无效邮箱、人工决定和不发送约束不变。
- JDK 11 执行本份三项测试及既有 PdfEmailExtractorTest、SourceAuthorEmailResolverTest、DiscoveryPipelineServiceTest；后三者仅运行。

## 人工验收清单

### A-1：真实召回
- 前置条件：02 保存的首来源无绑定、既有后备来源有明确关系的真实原文；外部 HTTP 替身返回原始字节。
- 操作步骤：按旧/新策略分别回放，查看来源、邮箱、姓名、证据、请求数；消费新结果。
- 预期结果：后备多 1 次、来源证据能核对；进入原去重/补证门禁，实际发送 0；未绑定者不进入 RAW。
- 覆盖：I-1/I-3，X1。

### A-2：无益情况与预算
- 前置条件：有绑定、无邮箱、全部歧义、截止时间已过、后备超时五组。
- 操作步骤：回放并查看请求日志与 outcome。
- 预期结果：前两组不增加请求；歧义不绑定；截止后请求 0；后备超时仍保留已获内容和邮箱；不突破原上限。
- 覆盖：I-1/I-2及保持项，X2。

### A-3：缓存和业务回归
- 前置条件：旧版/当前版缓存各一条，另有无效邮箱、重复记录、人工 HOLD。
- 操作步骤：走队列消费，读取专家写入及准入结果。
- 预期结果：不自动修改旧缓存；不兼容版本仍拒绝；无效邮箱不新增、重复不新增、HOLD不解除、邮件0。
- 覆盖：I-3及保持项，X3。
