# 09a：ORCID 查询编码与旧游标隔离

状态：计划追加，尚未实施。2026-09-27；create-p；归属 [09](discovery-repair-09-subject-scope.md)。

## 需求描述

恢复既有 ORCID 六类关键词检索的真实语义，修正查询从首个正确分片开始续跑。前置：08 完成。

保持：关键词 AND/引用规则、每种子一分片、邮箱身份验证、10000 检索窗口、其他来源游标、暂停/预算/去重。范围外：通用 HTTP 重构、修改所有来源编码、清空历史游标或队列、改变 ORCID 内部按 ID 查找的查询语言。

## 关键不变量

### Invariant I-1：只编码一次
- Rule：保留现有 URLEncoder UTF-8 参数编码，最终构造 `java.net.URI`，调用 RestTemplate 的 URI 重载。只修 ORCID 此请求，不修改全局 URI handler。最终解码一次 q 应严格等于 queryShards 产出的原字符串。
- Applies to：searchOrcidPage、searchOrcidRecords 及两者所有调用方。
- Violation consequence：研发主题变成近全库检索，或破坏其他来源 URL。
- 来源：DD-23、实际 URI 复现。

### Invariant I-2：不同实际查询不可复用旧 offset
- Rule：DiscoveryCheckpointCodec 新增共用 sourceCanonicalCriteria(sourceName, criteria)，在原 canonicalCriteria 结果上仅对 ORCID 追加固定 `;orcidQueryEncoding=uri-v1`；sourceKey 与 ExpertDiscoveryService.queueQueryHash 都从该函数取 hash 输入。其他来源 key 字节不变，通用 canonicalCriteria 与 v2 envelope 不变。旧 ORCID key 保留但不自动继承；新 key 首次 cursor=null，即 topic=0、offset=0。不得重置其他来源或按 papers_processed_total 推算位置。
- Applies to：同步 load/persist、queueQueryHash、queueLegacySeedCursor。
- Violation consequence：用错误大集合的 offset 跳过正确小集合，修完仍漏人。
- 来源：K-master-plan-shared-file-sequential-gates。

### Invariant I-3：旧排队内容不被假装已修复
- Rule：不删除旧 stream/job，不自动迁移旧 payload、不改总流水线 hash/epoch。旧 ORCID stream 与新 sourceKey 隔离；存量排队条目的专业资格最终由 09d 控制。人工显式 cursor 保留既有语义，不自动将其当数据库旧游标清空。
- Applies to：同步发现、队列源 key、旧 job 消费。
- Violation consequence：篡改历史或把请求修复误当成存量清理。
- 来源：原始需求。

## 现状审计

以 [09 共享审计](discovery-repair-09-scope-audit.md)为本节组成部分，包含实际 mapping、DB 约束、全部读写入口、X9-1～X9-6 交互及源文件检索回执。行号为 2026-09-27 审计基线；执行按方法名重新定位，基于前置子计划完成后的代码，不覆盖 01～08 改动。

直接证据：OrcidDataSource.kt:96-101，默认 RestTemplateConfig.kt:62；生产 class 相同。正确 engineering 查询 num-found=52804，重复编码=29787977，仅证明核查时查询语义异常，不估算专家恢复量。

## 实现方案

1. **请求修复（I-1）**：只改 OrcidDataSource.kt；OrcidDataSourceTest.kt 使用真实 RestTemplate + MockRestServiceServer 捕获最终 URI，禁止仅 mock getForObject(String)。断言 keyword:"engineering"、keyword:"computer science"、中文、字面 +/%/引号经一次 URL 解码还原；分页 start/rows 独立且不进入 q。按 ORCID 查找调用保留原 query 文本，并校验仍使用 URI 重载。
2. **查询 key（I-2/I-3）**：改 DiscoveryCheckpointCodec.kt 与对应测试，并将 ExpertDiscoveryService.kt 的 queueQueryHash 改读上述共同规范化函数。冻结旧基线非 ORCID key 样本；ORCID 同条件新旧 key 不同、长度≤50、临时 cursor 不影响 key。ExpertDiscoveryServiceTest.kt 以旧 key=ACTIVE|3|9000 开始，验证同步不读入旧 offset，队列不继承旧 key；新 key 第二次可正常续跑，不扫描或清理旧行。
3. **结果产物**：测试生成 `target/discovery-plan-acceptance/09a.json`，记录实际 request URI、decode 后 q、新旧 key、下一次 cursor。全部计数来自实际调用。

## 变更文件清单

共 6 个文件；清单外改动须先修订本计划。

| 文件 | 类别 |
| --- | --- |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodec.kt` | 生产 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 生产 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSourceTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodecTest.kt` | 测试 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 测试 |

## 验收标准

- I-1：最终 URI 不含由预编码造成的 `%253A/%2522`；一次解码等于原 q；无外网请求。
- I-2：ORCID 新 key 从首片开始，新 key 重跑复用自身 offset；其他五源 key 与基线逐字相同。
- I-3：无 SQL DELETE/批量 UPDATE、无旧 stream 删除；旧 job 的身份检查仍成立。
- 回归：OrcidDataSourceTest、DiscoveryCheckpointCodecTest、ExpertDiscoveryServiceTest。01 的失败页重放用例仍过。

## 人工验收清单

### A-1：实际查询与首轮续跑
- 前置条件：隔离测试生成 09a.json，包含 engineering、computer science、旧 ORCID cursor 与一个 OpenAlex cursor。
- 操作步骤：1. 查看 actualUri 和 decodedQuery；2. 查看两次 ORCID cursor；3. 对照 OpenAlex old/new key。
- 预期结果：decodedQuery 分别是 keyword:"engineering"、keyword:"computer science"；旧 ORCID 首次 offset=0，第二次为模拟返回 next offset；OpenAlex key 相同；旧行删除数=0。
- 覆盖：需求、I-1～I-3、X9-1。

人工验收时再从本节导出同名 `-acceptance.md`；本次不生成通过记录。测试与离线验收输出不得访问生产或发送邮件。
