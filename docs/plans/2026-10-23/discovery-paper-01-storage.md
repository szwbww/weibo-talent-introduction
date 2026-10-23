# 01 — 论文原文库、容量与管理 API

状态：待执行。前置：总计划的基线检查。后续：02 接入下载，04 增加分析，05 接入页面。

## 需求描述

提供可持久化、可预览、可保护及批量删除的论文原文库；准确展示原文库实际占用和所在文件系统剩余空间。该切片提供 API，默认不开启新任务留存；不要求等前端才能测试或部署。

必须保持：既有抽样目录、抽样清理和回放格式不变；专家、准入、邮件、队列状态不因保护或删除原文改变。范围外：对象存储、分布式文件系统、自动按天删除、全文索引、自动清空旧样本。

## 关键不变量

### Invariant I-1: 文件与业务记录分离
- Rule: 新目录 `/opt/talent/data/discovery-papers`，不能位于 Web 根目录或旧 samples 目录内。文件 SHA256 去重；一篇论文可有多个下载尝试，一个文件可被多任务引用。删除原文保留论文元数据、下载尝试、提取结果和分析记录。
- Applies to: 新建、复用、删除、恢复扫描、所有查询和下载。
- Violation consequence: 重复占磁盘、跨任务误删、删除后无法复盘。
- 来源: original；K-extraction-samples-are-not-consumption。

### Invariant I-2: 状态可恢复且不撒谎
- Rule: 原文落盘并校验后才返回 SAVED；文件缺失不能继续返回可预览。保护标记是 record 级持久化布尔值，默认 false；protected=true 的记录及其文件不能被本批删除。取消保护仅改变该标记和 revision，不能调用删除。
- Applies to: 写入、读取、批量保护/取消保护、删除和重启恢复。
- Violation consequence: 假成功、无法恢复、取消保护意外删文件。
- 来源: original。

### Invariant I-3: 删除必须重检共享关系
- Rule: 删除单位是所选 record 的全部可删除原文；若某 blob 仍被未选 record 引用，或任一引用受保护，该 blob 的此次删除跳过。多文件 record 可部分删除，状态由剩余文件推导。预检查与提交均在同一把变更锁下重算引用，不能使用客户端传入的大小/路径/保护状态。提交后必须返回逐条结果。
- Applies to: 删除预检查、提交、并发下载复用、保护写入。
- Violation consequence: 一个任务删除另一个任务的证据。
- 来源: original。

### Invariant I-4: 容量与持久配置
- Rule: 设置为 singleton id=1，enabled、maxBytes、minFreeBytes、revision 原子保存；新安装无行时有效值为 false、21474836480、16106127360、0。它们是本计划选定默认值，不是线上现状。DB 读失败返回 503，不按无行处理。保留上限不含旧抽样目录；可用空间用新目录所在 FileStore.usableSpace。原文库占用含 blob 和临时文件；DB 中元数据体积不伪称文件占用。
- Applies to: GET/PUT settings、stats、写文件前后检查。
- Violation consequence: 刷新丢配置、磁盘保护失效、显示错误。
- 来源: original；K-discovery-budget-backpressure。

### Invariant I-5: 原文访问边界
- Rule: 所有 API 在 `/api/expert-discovery/papers` 下，复用登录拦截；只接受正整数 record/attempt ID，不接受路径或任意 URL。服务端计算固定 hash 路径、拒绝 symlink。PDF 仅 PDF magic 验证后 inline，XML/HTML/TEXT 按 text/plain 返回，不能执行原文脚本。未登录 401、无记录 404、已删除 410。
- Applies to: 查询、预览、下载、批处理。
- Violation consequence: 原文公开暴露或任意文件读取。
- 来源: original；K-download-context-path-host-injection。

## 现状审计

### 旧抽样目录（不改写）
- schema：`DiscoverySampleModels.kt` 的 schemaVersion=1、stage=EXTRACTION、仅 OPENALEX 合法；属性见 `DiscoverySampleProperties.kt:9`。
- 写：`DiscoverySampleCapture.capture/save` → `DiscoverySampleStore.commit:148`；startupCleanup、scheduledCleanup、commit 内 retained 会删除旧数据；commit 还按 bucket/条数/字节淘汰。
- 读：Store.scan、`src/test/.../DiscoverySampleReplay.kt` 的 list/replay/compare；运维分析脚本只读。无业务管理 API。
- 交互：新库必须使用独立根目录；旧清理器不能扫描/删除新库。（来源: K-extraction-samples-are-not-consumption）

### 新库（当前不存在）
- Flyway 最新为 V158；新增 V159，不能修改 V158。执行前若编号被占用，先修订本计划及清单再改号。
- 新表全部写路径限定本切片 Repository/Service；02 只能调用 service，04 只能经 service 写分析 JSON。读路径：controller、02 capture、04 analysis、05 UI。没有 ES mapping 变化。
- SQL 与文件系统无跨资源事务；必须明确中间状态和恢复行为，不能声称 `@Transactional` 会撤销文件写入。

### 访问控制与存量设置范式
- `AuthWebConfig.kt:23` 拦截 `/api/**`，仅排除登录/me；不增白名单。
- `DiscoveryScheduleSettingRepository.kt` 是 singleton + JDBC + UTC DATETIME(3) 范例；本计划不修改该表或调度设置。
- 证据全文：[代码快照](discovery-paper-code-receipts.md)。

## 实现方案

### T-1 新 schema 与协议（I-1～I-4）

`V159__create_discovery_paper_archive.sql` 创建以下 4 张表；引擎 InnoDB、utf8mb4，ID 和 hash 采用二进制比较，时间 UTC DATETIME(3)。不用 JSON 类型依赖，不存 API key。

| 表 | 必备字段、约束 |
|---|---|
| discovery_paper_archive_setting | id INT PK 固定 1；enabled TINYINT(1)；max_bytes BIGINT；min_free_bytes BIGINT；revision BIGINT；updated_at DATETIME(3)。首次 PUT 建行；范围在 Kotlin 读写两侧验证 |
| discovery_paper_archive_record | id BIGINT AUTO_INCREMENT PK；execution_id BIGINT；source_name VARCHAR(32)；paper_key VARCHAR(160)；metadata_json MEDIUMTEXT；outcome_json MEDIUMTEXT NULL；analysis_json MEDIUMTEXT NULL；protected TINYINT(1) DEFAULT 0；revision BIGINT DEFAULT 0；created_at/updated_at DATETIME(3)。UNIQUE(execution_id,source_name,paper_key)，INDEX(execution_id,id)。metadata 包含 title/year/doi/作者/来源；JSON 有 schemaVersion=1 |
| discovery_paper_archive_blob | sha256 CHAR(64) PK；size_bytes BIGINT；media_type VARCHAR(64)；state VARCHAR(16)；created_at DATETIME(3)。state 只允许 AVAILABLE/DELETING/MISSING；相同 hash 不重复存正文 |
| discovery_paper_archive_attempt | id BIGINT AUTO_INCREMENT PK；record_id BIGINT；attempt_key CHAR(64) UNIQUE；kind VARCHAR(24)；source_url TEXT NULL（脱敏）；status VARCHAR(24)；blob_sha256 CHAR(64) NULL；reused TINYINT(1) NOT NULL DEFAULT 0；http_status INT NULL；reason_code VARCHAR(64) NULL；completed_bytes BIGINT DEFAULT 0；created_at/updated_at DATETIME(3)。INDEX(record_id,id)、INDEX(blob_sha256,status)。status=STARTED/SAVED/DOWNLOAD_FAILED/TOO_LARGE/SAVE_ERROR/DELETED |

模型、DTO、枚举放 `DiscoveryPaperArchiveModels.kt`；不向 ExpertProfile、VerificationItem、队列表添加字段。paper_key 复用 `ExpertDiscoveryService.envelopeForPaper(...).itemKey`（02 在调用方传入）；attempt_key=SHA256(recordId + 本次抽取 UUID + 来源尝试序号)，同一次保存重试幂等，重新发起 HTTP 的尝试另记一条。

record.state 不落第二份状态，优先级为：存在 SAVE_ERROR→SAVE_ERROR（另报已存数）；任一 SAVED+AVAILABLE→STORED；存在STARTED→PROCESSING；全部已删除且曾有文件→DELETED；其余→NOT_STORED。没有下载地址只有 record，无伪造 attempt。重复下载同字节可有多个 attempt，但文件数/字节按 blob 去重。SAVED时原子冻结reused=true/false；newFiles（本任务首次落盘尝试）按reused=false计，reusedAttempts按true计；删除后历史成功尝试仍保留完成字节及reused，当前在库文件数另按AVAILABLE去重。kind允许PDF_URL/PMC_XML/PMC_OA_XML/EMBEDDED_TEXT；record元数据去fullText后上限512KiB，outcome_json和analysis_json分别上限4MiB，超过返回明确METADATA_TOO_LARGE，不静默截断证据。

### T-2 存储与恢复（I-1～I-4）

- `DiscoveryPaperArchiveProperties.kt` 固定directory及部署总许可allowed（默认true）；同文件用 `@EnableConfigurationProperties` 注册，沿用SampleProperties的套路。有效开启为allowed&&DB.enabled，DB无行默认false。容量和用户启用状态来自DB。未开启不因/opt目录尚未创建而阻止应用启动；首次启用先检查/创建根目录，失败不报有效开启。GET stats在根不存在时从最近既有父目录取FileStore，标directoryAvailable=false；无法读取则unknown/503，不能使整个应用无法启动。
- `DiscoveryPaperArchiveService` 提供 beginRecord/beginAttempt/saveBody/finishAttempt/finishExtraction；不存在采样概率、bucket quota、每篇最多两份等抽样限制。传入 bytes 原样写入，hash 基于原始字节。
- 根目录 0700、文件 0600；`.tmp/<uuid>` 写完并 fsync，再原子 rename 到 `blobs/<hash前2位>/<hash>.bin`。新目录与旧目录不能互为祖先；相对路径、symlink、路径逃逸立即失败。
- 文件创建、引用登记、保护和删除共用一个 service 内重入锁及根目录文件锁；锁不可用返回可重试错误，不按成功跳过。本版是单应用实例，不引入分布式锁框架。
- 写入顺序：STARTED 持久化→临时文件→校验/rename→事务内登记 blob 与 SAVED。数据库提交失败不能报告 SAVED；孤立文件下次恢复核对后删除，旧已有 blob 不得误删。
- 删除顺序：锁内重新预检查→事务标 blob=DELETING→unlink→事务标全部所选关联 attempt=DELETED。unlink 失败保留 DELETING，返回 DELETE_IO_ERROR。启动及每 5 分钟仅恢复 DELETING/超过 1 小时孤立临时文件/无引用 blob；不按年龄清理 AVAILABLE。扫描发现文件缺失标 MISSING，读取返回 SOURCE_MISSING，不能删除分析数据。保护写遇该record任一blob处于DELETING时返回409 DELETE_IN_PROGRESS，不能在删除提交后再报保护成功。
- DELETING恢复只依据已持久化的blob及其引用状态，重复执行幂等；新capture遇DELETING不能新增引用或复用，先完成恢复/返回可重试阻断。扫描无引用blob与写入finalize共用变更锁，临时文件有活跃writer登记，不能把尚在提交的文件当垃圾删除。MISSING正文返回410 SOURCE_MISSING；不要返回空PDF。
- 容量 gate：新写入后不得使 archive usedBytes>maxBytes 或 FileStore.usableSpace<minFreeBytes；复用已存在 blob 不增加 bytes，仍需证明文件存在/hash/长度正确。空间不足抛固定码 ARCHIVE_STORAGE_BLOCKED；02 处理任务 backpressure。预检查后空间被外部进程抢占导致写失败，记 SAVE_ERROR，不静默丢弃。
- stats 从同一个 FileStore 读取 totalSpace/unallocatedSpace/usableSpace；used=total-unallocated，reserved=max(0,unallocated-usable)。archive usedBytes 为实际存在的 blob+temp 字节；计数/磁盘数据最多缓存 30 秒，响应含 measuredAt；失败返回 unknown/null，不能返回 0。新建/删除成功使缓存失效。全目录校验只在恢复/主动校验，不在每次查询重新 hash 全库。

### T-3 API（I-1～I-5）

| 方法与路径（统一前缀 `/api/expert-discovery/papers`） | 契约 |
|---|---|
| GET /settings；PUT /settings | PUT `{revision,enabled,maxBytes,minFreeBytes}`；maxBytes/minFreeBytes 为 1～1024 GiB 的整 GiB；CAS 冲突 409。允许降低到当前占用以下，保存成功但 gate=BLOCKED，不自动删文件 |
| GET /stats?executionId= | 全局磁盘/原文 bytes + 可选任务聚合。papers=record 数，attempts=尝试数，savedAttempts、newFiles、reusedAttempts、failedAttempts 分列；没有失败当无邮箱的合计 |
| GET ?executionId=&q=&status=&protected=&hasGain=&page=0&size=20 | size 20/50/100，q≤200，ID 合法；分页按 id DESC，total 精确。SQL 参数化、LIKE 转义。返回 record、下载状态、作者关联统计与分析是否存在；未分析不显示 0 新增 |
| GET /{id} | metadata、所有 attempts、冻结 outcome、analysis、保护状态/revision；JSON 不含本机路径、URL 查询参数或凭据 |
| GET /{id}/attempts/{attemptId}/content?download=false | 验证 attempt 属于 record；PDF 支持单 Range（206/416）；非 PDF 返回 text/plain UTF-8；download=true 强制 attachment。Cache-Control private,no-store、nosniff；服务端安全文件名 |
| POST /protection | `{items:[{id,revision}],protected:true/false}`，最多 200 条，逐行事务/CAS；响应 changed/unchanged/conflicts，不以失败项成功计数；没有全库隐式操作 |
| POST /delete-preview | `{ids:[...]}` 最多 200，返回 deletableFiles/bytes、protectedRecords、sharedFiles、notStored、snapshotHash 和逐项理由 |
| POST /delete | `{ids:[...],snapshotHash}`，重算变化则 409 + 新预检查；返回 deletedFiles/bytes、skipped、failed、remainingFiles。bytes 是删掉的文件逻辑字节，不能宣称等于文件系统立即释放的块数 |

接口DTO字段名必须固定（不能前后端各自命名）：

- settings：`revision,enabled,maxBytes,minFreeBytes,effectiveEnabled,gate,reasonCode,updatedAt`。gate=READY/BLOCKED/DISABLED/UNAVAILABLE。
- stats：`executionId,storage,counts`。storage=`{directoryAvailable,archiveUsedBytes,blobBytes,tempBytes,fileCount,protectedPaperCount,diskTotalBytes,diskUsedBytes,diskAvailableBytes,diskReservedBytes,maxBytes,minFreeBytes,enabled,gate,reasonCode,measuredAt}`；空间读取失败对应数字null。counts=`{papers,attempts,savedAttempts,newFiles,reusedAttempts,failedAttempts,notStoredPapers}`；savedAttempts是历史成功次数（含后来DELETED/MISSING），newFiles/reusedAttempts与它相加相等；fileCount是当前物理在库数，不拿它与成功次数相加。
- list：`{items,total,page,size}`，每row=`{id,revision,executionId,sourceName,paperKey,title,doi,pubYear,state,protected,storedBytes,attemptCount,readableAttemptIds,firstCapturedAt,updatedAt,extractionSummary,analysisSummary}`；storedBytes按该record的可读hash去重（不是全库贡献），extractionSummary=`{emails,resolved,unresolved}`，无outcome为null；analysisSummary=04完整基线delta/事实人数/coverage，无analysis为null。
- detail：`{record,paper,attempts,outcome,analysis}`，record同row；attempt包含id/kind/status/sourceUrl/bodySha256/mediaType/bytes/reused/httpStatus/reasonCode/readable；outcome及analysis为版本化结构；没有绝对文件路径。
- protection：`{changed,unchanged,conflicts:[{id,reasonCode}],items:[{id,protected,revision}]}`。
- delete-preview：`{ids,snapshotHash,deletableFiles,deletableBytes,protectedRecords:[id],sharedFiles:[hash],busyFiles:[hash],notStored:[id],items:[{id,reasonCodes}]}`。hash是排序后的ID/revision/attempt状态/blob状态/全部活跃引用+保护的规范JSON的SHA256，提交重新计算。
- delete：`{deletedFiles,deletedBytes,skipped:[{id,reasonCode}],failed:[{id,reasonCode}],remainingFiles}`。失败的DB/API响应使用HTTP状态和固定code/message，不返回堆栈/文件路径。

后台只提供受认证的 ID 路由，不返回可猜公网静态地址。下载 response 流释放资源；没有服务器网络代理功能。

## 变更文件清单

根目录均为仓库；文件合计 9，子系统 1；既有共享 store 新字段 0。

| 文件 | 动作 |
|---|---|
| src/main/resources/db/migration/V159__create_discovery_paper_archive.sql | 新增 4 张归档专属表 |
| src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryPaperArchiveProperties.kt | 新增配置注册 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveModels.kt | 新增协议/DTO/状态 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt | 新增唯一 JDBC 读写口 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt | 新增文件与事务协调 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveController.kt | 新增管理 API |
| src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepositoryIT.kt | SQL/CAS/聚合 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveServiceTest.kt | 文件失败/恢复/共享删除 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveControllerTest.kt | 鉴权/下载/批处理 |

## 验收标准

- I-1：两个 record 引用同一 1024 字节文件，只增加 1024 字节正文占用；删除不得调用专家/准入仓库。旧 samples 测试通过。
- I-2：在 rename 前、rename 后 SQL 提交前、DELETING 后、unlink 后注入崩溃，重启恢复后的状态与实际文件一致；重复取消保护不删除文件。
- I-3：A/B 共享 blob；仅删 A→共享跳过；选 A+B 且 B 保护→保护/共享跳过；解除 B 后选 A+B→物理删除一次；并发保护/新增引用使预检查失效 409。
- I-4：settings 重启可读；数据库故障 503；容量边界精确到字节；两并发写不突破 cap；磁盘 null≠0。
- I-5：路径攻击、symlink、跨 record attempt、非 PDF HTML 伪装、401、404、410、Range 都有测试。SQL IT 不得用内存 map 代替。
- JDK 11 执行列出的测试及既有 DiscoverySampleStoreTest/DiscoverySampleCaptureTest；不调用真实 Apollo、不改线上库。

## 人工验收清单

### A-1: 配置重启与容量
- 前置条件：隔离验收实例用本计划测试夹具生成目录和空归档表；GET settings 返回 enabled=false、maxBytes=21474836480、minFreeBytes=16106127360。
- 操作步骤：1. PUT revision=0、enabled=true、maxBytes=26843545600、minFreeBytes=16106127360。2. 重启验收实例。3. 再 GET settings/stats。4. 在测试 FileStore 夹具将 usableSpace 设为 16106127359，调用 saveBody 夹具。
- 预期结果：设置仍为 25/15 GiB；磁盘卡数据有 measuredAt；第 4 步返回 ARCHIVE_STORAGE_BLOCKED，SAVED 增量 0。DB 暂停后的 GET 为 503 而非默认配置。
- 覆盖：I-2/I-4；容量、持久配置。

### A-2: 保护、共享及批量取消
- 前置条件：ServiceTest 同一 fixture 可通过测试启动器建 A/B 共享 1024 字节、C 独占 2048 字节，B protected=true；fixture 只在 test classpath，无生产造数路由。
- 操作步骤：1. 预检查删除 A/B/C。2. 取消。3. POST protection 将 B=false。4. GET 三条验证原文还在。5. 再预检查并确认删除 A/B/C。
- 预期结果：步骤 1 可删 1 文件/2048 bytes，保护 B、共享文件跳过；步骤 3 changed=1 且所有原文仍可读；步骤 5 删除 2 文件/3072 bytes，元数据和分析保留。
- 覆盖：I-1/I-2/I-3；旧专家、邮件和队列计数前后相同。

### A-3: 下载与失败恢复
- 前置条件：验收测试夹具一份 PDF、一份含 `<script>alert(1)</script>` 的 HTML；一份旧 samples 控制样本。
- 操作步骤：1. 未登录访问 content。2. 登录后预览 PDF、HTML。3. 请求 PDF bytes=0-99。4. 使用 failpoint 夹具中断 delete 的 unlink 后事务，重启。5. 重放旧 samples 控制样本。
- 预期结果：401；PDF 可看，HTML 显示文字不运行；206 且 100 bytes；重启后文件已删除且元数据可查；旧样本 hash 与回放结果不变。
- 覆盖：I-1/I-2/I-5；访问/删除/旧抽样交互。
