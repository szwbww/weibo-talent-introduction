# 02 — 全文下载接入与容量阻断

状态：待执行。依赖 01。不开启付费请求、不改变解析规则。

## 需求描述

启用原文留存后，每次深度发现实际取得的完整 PDF/XML/HTML/CORE 全文文本进入原文库；下载失败、无地址、超限和保存失败可分别查询。磁盘不足阻断新全文下载，不能一边丢原文一边报告已全量保存。

必须保持：来源调用顺序、下载超时/大小限制、原有采样、专家门禁、已成功准入结果不变；取消任务不回滚已保存文件和专家。范围外：自动重抓历史未保留原文、保存中断的半份 PDF、留存普通元数据 HTTP 包、ORCID JSON 伪装论文、修改第三方下载成功率。

## 关键不变量

### Invariant I-1: 覆盖真实获取路径
- Rule: 仅真实深度发现 executionId>0 的 PAPER 抽取进入归档 scope；scope 在执行抽取的线程内建立，并在 finally 恢复。OPENALEX/EUROPE_PMC/PMC_OA/CROSSREF/CORE/ARXIV 共用；ORCID RECORD 不归档为论文。每次完整响应原字节都保存，不以最终是否找到邮箱、是否被消费或是否准入决定保存。
- Applies to: SYNC、QUEUE、并行 extraction、PDF fallback、PMC XML、CORE metadata.fullText。
- Violation consequence: 新库仍只抽样、线程串任务、失败样本被漏掉。
- 来源: K-extraction-samples-are-not-consumption。

### Invariant I-2: 下载、缓存和消费不混算
- Rule: 归档成功≠消费成功；采集统计和原文库统计分别查询。缓存 extractionJson 没有再次发起下载，不能补造 SAVED、不能为填留档重复请求。CORE 内嵌文本标 EMBEDDED_TEXT、HTTP 增量为 0，不标“原始 PDF”。未完整下载只记失败/超限，不能提供伪原文预览。
- Applies to: HTTP hooks、队列缓存复用、metadata fallback、outcome 保存。
- Violation consequence: HTTP、原文数、专家数互相冒充。
- 来源: K-extraction-samples-are-not-consumption；K-admission-event-and-projection-counts。

### Invariant I-3: 存储失败不得推进消费
- Rule: 归档开启时 beginRecord/beginAttempt/saveBody 的容量、权限、DB/IO 失败为基础设施失败。SYNC 当前页不得消费/推进 checkpoint；QUEUE 将该 job CAS 退回 PENDING、nextAttemptAt+60s、不递增 attempts、不消耗已存 extractionJson。继续采用原 lease/generation 防止旧 worker 回写。已下载成功但落盘失败记 SAVE_ERROR。
- Applies to: 同步 parallelExtractOutcomes、extractOutcome、extractQueuedItem、Pipeline.processJob 异常边界。
- Violation consequence: 丢失原文且游标越过，或把磁盘满误记成“不合格专家”。
- 来源: K-page-commit-must-cover-consumer-failures；K-discovery-budget-backpressure。

### Invariant I-4: 抽样和业务结果保持
- Rule: 新 Capture 不依赖旧 sample permit/quota/context.valid()，也不替换旧 capture；旧抽样开关关闭时新库仍能全量记录。归档关闭时不增加 SQL/文件写入、不改变原网络/解析结果。保存原始 outcome 与 parser version，不从后来的重解析倒写原始结果。
- Applies to: 捕获嵌套、元数据写入、关闭分支、取消。
- Violation consequence: 仍受旧 20/10 限量、对照基线被覆盖。
- 来源: K-source-recall-needs-original-layout。

## 现状审计

### 正文获取路径（均已逐字核对）
| 路径 | 当前代码 | 本次接入点 |
|---|---|---|
| PDF、PDF 地址返回 HTML/其他完整响应 | PdfEmailExtractor.download:235–298；fullBody 在完整读完后、判断格式前 | begin/headers/body/end；所有 fallback 地址均记录 |
| Europe PMC XML（含 OpenAlex 优先 PMC） | EuropePmcDataSource.fetchFullTextXml:107–123 | 重试每次各记 attempt；完整 bytes 保存 |
| PMC_OA XML | PmcOaDataSource.fetchFullTextXml:71–80，直接 RestTemplate.getForObject | 加同样 hooks，不因没有旧 sample hook 而漏存 |
| CORE 内嵌全文 | CoreDataSource.extractAuthorEmails:159–176，paper.fullText | 新 scope 在调用 source 前保存非空全文 UTF-8 bytes；正文来源 EMBEDDED_TEXT |
| CROSSREF / ARXIV / CORE PDF | 各 DataSource.extractAuthorEmails 调 PdfEmailExtractor | 复用 PDF 接入，不在每个来源再复制写文件逻辑 |
| ORCID | ExpertDiscoveryService.extractQueuedItem RECORD 分支 | 无论文，保持原样 |

### execution 与存储交互
- SYNC：`ExpertDiscoveryService:938` 为所有来源构造 sampleContext；`:1613` 旧 capture **仅 OPENALEX**；`:1686` CompletableFuture worker 内调用 extractOutcome。
- QUEUE：`DiscoveryPipelineService:1031` 注入 execution/job；`ExpertDiscoveryService:2105` 旧 capture 同样只支持 OPENALEX。原文 scope 必须在实际 worker 建立，不能依赖父线程 ThreadLocal。
- 01 新表/文件唯一读写 service；本切片增加 capture 调用，无直接 SQL。01 查询 API立即读到记录。
- 旧 `discovery_source_cursor` schema V32，写/读都经 ExpertDiscoveryService.loadSourceCheckpoint/saveSourceCheckpoint 和队列起点读取；不修改 schema。不能将存储异常压成 PaperExtraction.extractionError 后进入 consumeOutcome。
- `discovery_paper_job` schema V133:118：metadata_json、extraction_json、status、lease_token、generation；写口 `DiscoveryPaperQueueRepository.saveExtraction/returnToPending/completeJob/cleanup`，读口 mapper/find/claim 与 Pipeline.processJob；新路径只使用 returnToPending。缓存非空时不会进入 extractQueuedItem，按原路径消费。
- 既有 Capture/Store 读写见 01；完全保留。

## 实现方案

### T-1 新 Capture（I-1/I-2/I-4）

新 `discovery/archive/DiscoveryPaperArchiveCapture.kt`：组件实例 + 静态 ThreadLocal scope hooks；显式 context(executionId,source,paperKey,entry,batchNumber,queueJobId)。不能调用旧 context.valid() 判断所有来源。record 在任何正文 HTTP 前建立；关闭则直接 delegate。一个 scope 内尝试序号递增；每次重新抽取有独立 UUID，saveBody 重试仍使用同 attemptKey。

scope 保存 recordId、attempt handles、firstFatal；不累积完整 body 列表。每份完整 bytes 同步交给 01 service 后释放局部引用，避免论文全部堆在内存。内嵌 CORE 文本标 EMBEDDED_TEXT，先保存再调用 source。旧抽样继续独立工作。

`finishExtraction` 将 outcome_json 写成 `{schemaVersion:1,first:{capturedAt,parserVersion,attemptIds,outcome},latest:{...}}`：parserVersion始终是对象`{identityExtraction:DiscoveryIdentity.EXTRACTION_VERSION,pdfLayout:0}`；0只表示03版面扩展之前的实现标签，03再改为其RULE_VERSION，不能把parserVersion先存整数再不升schema改对象。attemptIds是产生本次outcome的scope内全部尝试ID；first 只设一次；latest 随真实抽取更新；analysis_json 是 04 的独立空间。source.paper.fullText 不重复嵌入 metadata_json，保存在正文文件；URL用既有 scrubUrl 语义去凭据/query/fragment。enabled在每篇scope开始时读取，关闭不丢弃该篇已经取得的正文；容量阈值每次写入检查。需要等待中的论文，在下一次重试按新enabled重新决定是否归档。

即使下层 broad catch 吞掉 hook 异常，scope.firstFatal 仍在 delegate 结束时重新抛 `PaperArchiveBlockedException(code)`；finally 结束 STARTED 尝试和恢复 ThreadLocal。不能通过“诊断失败不影响业务”的旧 sample 语义掩盖此异常。没有完整 body 的下载异常记 DOWNLOAD_FAILED/TOO_LARGE；它们不属于保存基础设施失败。

### T-2 HTTP hooks 与调用入口（I-1～I-4）

修改 `PdfEmailExtractor.kt`、`EuropePmcDataSource.kt`、`PmcOaDataSource.kt`：新增 archive begin/headers/fullBody/end，保留旧 sample hooks。archive preflight 在每次请求发出前；没有 archive scope 时 no-op。headers 不以解析后的字符串正文冒称原始 bytes。

修改 `ExpertDiscoveryService.kt`：
现有构造器末尾新增可选archiveCapture默认null，沿用当前sampleCapture可选注入模式以保留旧隔离测试调用；生产Spring必须注册该组件。FlowIT要真实验证bean存在与开启后的必经路径，不能靠null跳过功能跑绿。
1. extractOutcome 与 queue PAPER 分支包一层 archive.capture，原旧 sample.capture 保留在内；用 envelopeForPaper 的现有 itemKey，不另创 DOI 去重算法。
2. extractOutcome catch 对 archive 异常直接 rethrow；parallelExtractOutcomes 必须等待本页已启动 futures 全部结束，再抛去包装后的首个 archive 异常。不能首个 join 抛异常后让其他下载继续在后台写。
3. 同步页在 extractions 未全部成功返回前没有 consume；存储阻断离开该页，沿用现有 task 失败报告，不新造暂停状态或推进 checkpoint。异常消息固定中文“论文原文留存受阻，本页尚未消费”，附稳定 code。
4. extractQueuedItem 对 archive 异常 rethrow，不能变成通用 retryable extraction failure。scope 异常优先于被下载层转换的“无邮箱”。

修改 `DiscoveryPipelineService.kt`：仅在 extractQueuedItem 调用边界捕获该类型，调用 returnToPending(job,原 lease/generation,now+60s,code,now)，加入 waitReasons，返回；不修改 repository SQL。重试前检查归档 gate，持续空间不足不重复发全文 HTTP。旧 extractionJson 非空仍走原流程，不清空它补下载。取消与 lease 丢失仍以现有 CAS 为准。

### T-3 接入验证（I-1～I-4）

新增合成来源/临时目录/假 FileStore/假 HTTP 的集成夹具；测试路径包含 6 来源、CORE 内嵌+PDF fallback、一次 XML 重试、无 URL、同文件跨任务、多个失败地址后成功、原文成功而消费未开始即取消。不得借此向真实外部接口发测试请求。

## 变更文件清单

8 文件；子系统 2（原文采集、发现调度）；既有共享 store 新字段 0。

| 文件 | 动作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCapture.kt | 新增 scope/hook/异常 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | SYNC/QUEUE 包装及异常边界 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt | PDF/HTML 正文 hook |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt | XML hook |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/PmcOaDataSource.kt | XML hook |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt | 存储阻断不消耗重试 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt | scope、字节、关闭回归 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveFlowIT.kt | 多来源/游标/队列/取消 |

## 验收标准

- I-1：两个并行任务使用不同 recordId/executionId，完整响应 hash 与服务器实际保存 bytes 相同；6 来源均覆盖；无旧 sample permit 仍归档。
- I-2：10 次完整下载其中 3 次相同内容→savedAttempts=10、唯一新增 blob=8；CORE 内嵌 HTTP=0；缓存消费新增下载=0/原文=0。
- I-3：SYNC 并行页第 2 篇写失败，页内消费=0、checkpoint 不变，所有 futures 已结束；QUEUE 同错误 attempts 不变、PENDING+60s；恢复容量后成功，旧 lease 不可提交。
- I-4：关闭时源调用/outcome 等值、SQL 写 0；旧采样测试通过；原始 first 不被再次抽取/分析覆盖；取消不删除已存文件或已准入专家。
- JDK 11 新增两套测试和既有 DiscoverySampleCaptureTest、PdfEmailExtractorTest、ExpertDiscoveryServiceTest、DiscoveryPipelineServiceTest；测试名字不存在时先核对实际文件并修订命令，不伪称已运行。

## 人工验收清单

### A-1: 全来源与失败分类
- 前置条件：隔离实例加载 FlowIT 的 6 来源假服务：PDF 两次相同字节、XML 一次、CORE 文本一次、404 一次、超限一次、无地址一次。启用归档，关闭旧 samples。
- 操作步骤：1. 启动一轮发现。2. GET papers/stats 与逐条详情。3. 下载已有原文校验 fixture SHA256。
- 预期结果：4 次成功存储、3 个新文件、1 次复用；404=DOWNLOAD_FAILED、超限=TOO_LARGE、无地址无正文；没有将原文 4 记为专家 4；旧 samples 关闭不影响上述结果。
- 覆盖：I-1/I-2/I-4；来源→归档→查询。

### A-2: 空间不足与恢复
- 前置条件：FlowIT 的两篇同页同步任务以及一个待抽取 queue job；假 FileStore 可用空间低于阈值。
- 操作步骤：1. 分别执行同步/队列。2. 查任务和 papers。3. 释放空间并恢复队列；同步重新启动同条件。4. 查看 checkpoint/请求次数。
- 预期结果：阻断期间新正文 HTTP=0；同步未推进本页，队列 attempts 不增加且延期 60 秒；恢复后正文入库并按原业务流程消费，不能跳过两篇中的任一篇。
- 覆盖：I-3；存储→同步游标/队列状态。

### A-3: 关闭、缓存、取消
- 前置条件：已有 extractionJson 的 job、一个已存原文记录及一位已准入专家；保存关闭归档。
- 操作步骤：1. 消费缓存 job。2. 开启归档并运行假来源。3. 首份原文保存后取消任务。
- 预期结果：步骤 1 不新增全文请求或伪造原文；步骤 3 已存原文仍可读、已准入专家仍在；新库不改旧采样文件 hash。
- 覆盖：I-2/I-4；缓存与取消回归。
