# 01：修复 PDF 邮箱归属

状态：待审阅，未实施。基线、原始证据及边界见 [总计划](discovery-repair-00-master.md)。1 个子系统；7 个实施文件；无新增存储字段。

## 需求描述

阻止共享通讯标记、同一行多个通讯标记、相邻作者联系段落将邮箱交叉归属，进而传播错误机构和学术身份。

保持：明确的一人多邮箱；HTML/纯文本已有明确归属；未知邮箱保留线索；原有邮箱验证、资格与去重；现有已存身份凭证有效性。范围外：猜测所有姓名变体、修改元数据拆名公共规则、自动清理线上档案、自动重跑旧缓存、修改原文下载策略。

## 关键不变量

### Invariant I-1：唯一性必须包含原文作者
- Rule：同一标记在原文作者区出现多个拥有者，或者存在不能对应元数据的拥有者时，该共享标记不得证明某一个人的邮箱；不能仅对成功匹配的元数据作者计数。
- Applies to：PdfAuthorContactLayout.collect/markerOwners 的标记归属。
- Violation consequence：元数据少一个中间名或连字符差异就把共享邮箱全部给首位作者。
- 来源：原始 Crea PDF 重放；K-author-identity-needs-email-evidence。

### Invariant I-2：每个联系片段独立归属
- Rule：同一行不同标记的邮箱分段处理；新作者联系语句即使尚未匹配到元数据，也必须终止上一段。一个无冲突的作者片段可有多个邮箱。
- Applies to：marker contact、paragraph contact；SourceAuthorEmailResolver.resolvePdf 消费结果。
- Violation consequence：上一作者继承下一作者邮箱、ORCID/OpenAlex ID 和机构。
- 来源：原始 Debie/Kumar PDF 重放。

### Invariant I-3：歧义只保留邮箱线索
- Rule：归属无法确定时不生成 Contact 身份证明；现有 resolver 可以保留 UNKNOWN_OWNER 邮箱，但不给其补上姓名、作者 ID、机构和 identityEvidence。旧版本抽取结果必须拒绝消费；已入库凭证版本保持不变。
- Applies to：collect 输出→resolvePdf→extractQueuedItem→consumeQueuedExtraction。
- Violation consequence：旧缓存重新导入已经修掉的错误，或无关的已入库专家批量失效。
- 来源：K-identity-cache-version-and-admission。

## 现状审计

### PDF 内存联系块与归属对象
- 代码：`PdfAuthorContactLayout.kt:55-95,140-179`。标记集合仅 `*†‡`；同一行取首标记后遍历所有邮箱；rosterOwners 只统计匹配到元数据全名的作者；段落边界只识别 `is with`，缩写仅“given 首字母 + 完整 family”。
- 写：collect 的标记循环、联系段落循环、endContactBlocks；resolver 的 results 生成 ExtractedEmail，复制被选作者身份与机构；PdfEmailExtractor 把解析结果写入提取输出。
- 读：SourceAuthorEmailResolver.resolvePdf 合并来源冲突；ExpertDiscoveryService 消费，只有带有效身份的结果进入后续作者身份补全。HTML resolve 和纯文本 textClaims 为其他归属来源，保持冲突拒绝，不把多邮箱直接视为错误。
- 交互 P-1：PDF 源片段→归属对象→作者凭证→机构/学术 ID。原文与元数据都必须进入测试，不能伪造一个已经选好 owner 的 Contact 来证明解析修复。

### discovery_paper_job 抽取缓存与 ES 三层
- Schema：现有 discovery_paper_job 的 extraction_json 文本中含 identityRuleVersion；不新增列。ES RAW/CANDIDATE/APPLICATION mapping 为 dynamic=false，身份凭证受现有 schema 约束；本计划不增加 ES 字段。
- 缓存写：ExpertDiscoveryService.extractQueuedItem 写当前 EXTRACTION_VERSION；DiscoveryPipelineService 提取分支调用 repository.saveExtraction；仓库的消费完成/失败与清理分支更新任务状态或删除缓存行。
- 缓存读：DiscoveryPipelineService 读取 extractionJson，非空时跳过提取；ExpertDiscoveryService.consumeQueuedExtraction 校验版本，不兼容返回 IDENTITY_EXTRACTION_VERSION_UNSUPPORTED；现有 worker 标记 FAILED。
- 档案写：消费链的 create-only RAW、新人直接入候选、后续重新核验晋升；机构由现有 InstitutionEvidence 边界控制；学术补全由现有三层 update-existing 写入。本计划仅阻止错误来源进入这些现有写入口，不改任何 ES 写法。
- 档案读：去重按邮箱；资格校验读取作者/机构证据；补全读取 author ID/ORCID。完整代码检索见 [identity-paths.txt](discovery-repair-evidence/identity-paths.txt)、[store-paths.txt](discovery-repair-evidence/store-paths.txt)。
- 交互 P-2：版本生产→持久缓存→版本消费。仅增加 EXTRACTION_VERSION（当前 20261002，实施取更大值），不改变 DiscoveryIdentity.VERSION（当前 20260925）。存量非空缓存不自动重新提取，失败原因必须保留可见。

### 真实证据与边界

原始 PDF/作品 JSON 均保存于 [ZIP](discovery-repair-evidence/original-source-evidence.zip)，manifest 记录 URL 与 SHA。重放见 [contact-replay.json](discovery-repair-evidence/contact-replay.json)。

| 原文 | 当前重放 | 修复后最低要求 |
|---|---|---|
| 10.1017/wtc.2021.11 | Crea 被绑定三个邮箱 | ristic、jan.veneman 两邮箱不得绑定 Crea；共享 * 可以全部暂不归属 |
| 10.1109/trustcom50675.2020.00114 | Debie 被绑定两个邮箱 | helge 邮箱不得绑定 Debie；独立唯一 ‡/§ 联系片段可分别归属 |
| 10.1109/tpds.2023.3240567 | Kumar 继承 Singh 邮箱 | ashutosh 不得绑定 Kumar；jitendra 明确片段继续可用 |
| 10.3390/s21186037；10.1109/lra.2020.2974695；Lumma 原文（manifest） | 本次版本未重放出错误绑定 | 保证 Damien≠Nguyen、Anton≠Everett、Martin Pauly≠Lumma 三条负例，不宣称已找到其生产分支 |

原文复现与合成边界测试分开标注。（来源：K-named-fixture-must-use-real-row）

## 实现方案

### T-1：收紧局部联系片段（I-1、I-2）

修改 `PdfAuthorContactLayout.kt`：
1. 仅在现有页面/作者区域边界内，按原文标记出现的作者片段建立拥有者数量；没有匹配上元数据的署名也参与歧义判断。不要把 pre-abstract 区的机构脚注或联系行误计为作者拥有者；无法分清作者段则拒绝该标记。无需扩展全局姓名匹配。
2. 统一作者/联系侧支持的符号集合，至少 `*`、`∗`、`†`、`‡`、`§`；`∗` 与 `*` 做明确符号归一。按标记边界切分同一联系行，再各自抽邮箱；分段不明确则不出证明，不整行分配。
3. 将下一作者 `is with` / `are with` 识别为段落边界，包括 `A. K. Singh` 这样的多首字母格式。边界判断独立于“能否匹配已知作者”。保留现有几何列限制与最多五行。
4. 对确切全文姓名、现有缩写正例保留支持；不为了本案添加一个跨模块“智能拆姓名”服务。新作者无法唯一匹配时允许失去召回，不允许错误继承。
5. 现有 SourceAuthorEmailResolver 保持接口与冲突规则；只有观察到其会绕过新的歧义结果且给出可复现输入时，先修订计划再增加代码文件，不能擅自放宽规则。

### T-2：缓存边界（I-3）

修改 `DiscoveryIdentity.kt` 的 EXTRACTION_VERSION 为大于实施时当前值的新值；保留 VERSION。生产提取与消费者已共用该常量，无需增加数据迁移/重新入队。`DiscoveryPipelineServiceTest.kt` 验证旧非空缓存失败且未入 ES、新结果仍能消费；不把 FAILED 宣称为已重提取修复。

### T-3：原文贯穿回归（I-1～I-3）

将证据 ZIP 逐字复制为 `src/test/resources/discovery/ownership-20260929.zip`；新增 `ownership-20260929-expected.json` 仅保存真实禁止关系与允许关系、来源/SHA/说明，不修改原文。测试读取 ZIP 的作品 JSON 并使用生产同样的作者映射，不手工补中间名来消除问题。

修改 `SourceAuthorEmailResolverTest.kt`：真实 PDFBox 解析六份原文；新增合成正反例涵盖未匹配署名的共享标记、同行 ‡/§、下一段 are with、多首字母边界、合法一人两邮箱、纯文本/HTML 原有归属。合成案例明确命名 SYNTHETIC，不冒充线上原文。

修改 `ExpertDiscoveryServiceTest.kt`：让真实解析结果进入现有 consumeQueuedExtraction/create-only 捕获器，断言七条错误关系均不会传递机构或 author ID；共享未知邮箱不触发作者补全。覆盖邮箱去重与现有资格拒绝案例。测试仅替换下载传输/外部数据库，不能直接 stub 好 Contact。

## 变更文件清单

所有 Kotlin 路径均从仓库根开始。

| # | 文件 | 动作 |
|---|---|---|
| 1 | src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt | 标记与段落边界 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt | 仅 EXTRACTION_VERSION |
| 3 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt | 原文与合成解析回归 |
| 4 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | 真实消费链回归 |
| 5 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt | 缓存版本回归 |
| 6 | src/test/resources/discovery/ownership-20260929.zip | 原文证据，逐字复制 |
| 7 | src/test/resources/discovery/ownership-20260929-expected.json | 逐案预期与出处 |

## 验收标准

- I-1：Crea 两个错误邮箱断言为零；未知署名共享标记不可被漏计；单人唯一标记正例不消失。
- I-2：Debie 不再取得 helge；Kumar 不再取得 ashutosh；合法单人双邮箱为2条同一主人；HTML/纯文本旧正例通过。
- I-3：七条错误归属均不向消费输出错误作者 ID/机构；UNKNOWN_OWNER 不调用作者补全；旧缓存返回指定失败原因且 ES 写入0；新缓存被正常消费；凭证 VERSION 未变。
- 校验 ZIP 每项 SHA 与原始 manifest；测试报告清楚区分4条可重放错误关系和3条真实负例。
- Java11：`mvn -Dtest=SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityTest test`。无真实发信、无线上写入。

## 人工验收清单

### A-1：真实邮箱归属
- 前置条件：隔离测试环境，六份归档原文；运行本计划的 SourceAuthorEmailResolverTest 与 ExpertDiscoveryServiceTest，保留逐案解析报告（写入 target，不提交）。
- 操作步骤：1. 打开报告按邮箱搜索 ristic、jan.veneman、helge、ashutosh、damien、aderuiter、m.pauly；2. 对照报告内姓名和作者 ID；3. 查看 Crea 共享 * 判定理由。
- 预期结果：前述七条禁止关系各为0；Crea 共享标记记录为无法唯一归属；jitendra 明确联系片段保留；不出现替代猜测姓名。
- 覆盖：I-1、I-2、I-3；P-1；需求结果。

### A-2：合法归属和准入未变
- 前置条件：同一测试报告包含一人两邮箱 PDF、明确 HTML/纯文本、重复邮箱、资格拒绝案例，输入与期望均显示。
- 操作步骤：1. 打开四类案例；2. 检查双邮箱数量；3. 检查 HTML/纯文本主人；4. 检查去重与资格结果。
- 预期结果：双邮箱为2且主人相同；明确 HTML/纯文本各1条正确主人；同一邮箱不会新建第二份档案；不满足既有资格的案例候选新增0。
- 覆盖：I-2、I-3；保留行为；P-1。

### A-3：缓存与既有凭证
- 前置条件：版本回归报告包含新、旧两份同原文 extraction_json 和已入库 VERSION 凭证。
- 操作步骤：1. 检查旧缓存处理状态与写入数；2. 检查新缓存处理结果；3. 检查旧档案凭证验证结果。
- 预期结果：旧缓存 FAILED，原因 IDENTITY_EXTRACTION_VERSION_UNSUPPORTED，写入0；新缓存按正常身份规则处理；既有凭证仍有效，未声称旧缓存自动重跑。
- 覆盖：I-3；P-2；保留凭证行为。
