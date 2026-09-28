# 02：机构来源证据落库

## 需求描述

新发现档案显示的机构必须有可核对的来源证明；无证据保持空缺状态供后续发送门禁识别。三层晋升/读取不丢证明。不得改变已有主键、身份校验结构、旧非发现档案、历史邮件。范围外：本步骤尚不启用发信拦截，也不回填历史。

## 关键不变量

### Invariant I-1: 单个证据字段
- Rule: 三层 ES 新增且仅新增 `institutionEvidence` keyword；值为 `JATS:<64位小写SHA256>`、`OPENALEX:<...>` 或 `ORCID:<...>`。签发只依据 01 的内部 `institutionSource`（ORCID 用 01b 的唯一机构），不根据 `dataSource` 或旧 `institution` 补签。统一函数以 NUL 分隔固定顺序计算 `来源种类、已存 identityVerification.source/evidenceHash、规范邮箱、姓名、来源作者 ID、已存 externalIds 中论文 ID 或 ORCID ID、机构、国家、机构类型` 的 SHA256；验签只读 ES 已存字段并重算，不依赖本次线上审计备份。机构为空或任一必需来源/身份 ID 缺失时不写此键，不写 `false`/`UNVERIFIED`。该字段是签发时来源绑定及字段未变的校验值，不证明现任职，也不通过哈希本身重新证明外部来源为真。
- Applies to: `ExpertDiscoveryService.toIndexMap/buildOrcidProfile`、三层 mapping。
- Violation consequence: 空字段被误判通过，或机构改值后保留过期证明。
- 来源: original；`DiscoveryIdentity.kt:46-60`。

### Invariant I-2: 证明与显示值原子同步
- Rule: 仅在 `DiscoveryIdentity.allowed`、`identityVerification` 中非空的 ORCID/OpenAlex 作者 ID 与 `externalIds` 对应 ID 无冲突、真实 ORCID 主键（非 `EMAIL-*`）与其 ORCID 值无冲突，且 01/01b 的同一作者单机构有结构证据时写 `institutionEvidence`；发送验签重做相同 ID 检查。JATS 必须有 `externalIds.pmcId`；OPENALEX 必须有同作者 `externalIds.openAlexAuthorId` 及 `doi`/`pmcId` 至少一项；ORCID 必须有 `externalIds.orcid` 与主键一致。机构、国家、类型任何一个变更都必须重算或清除证明。晋升 `_source` 原样透传；学术指标补全不得改机构/证据。
- Applies to: 发现 RAW、候选/申请晋升、学术补全。
- Violation consequence: 历史证据挂到另一个机构上。
- 来源: K-promotion-source-passthrough、K-enrichment-write-three-layers。

### Invariant I-3: 读取全路径
- Rule: `ExpertProfile` 同时读取 `institutionEvidence` 与已存在的 `filterResult`，所有发送使用的 `ExpertSearchService` 投影不能丢；旧文档无这两个字段时分别为 null，不默认合格。`ExpertIndexWriterService.discoveryProfile` 只供分类/资格复评，不参与发送门禁，其机构值仍从原 `_source` 读取。
- Applies to: 搜索、重试、复评/晋升。
- Violation consequence: 第 03 步无法对旧重试联系人执行同口径门禁。
- 来源: original；`ExpertSearchService.kt:477-507,588-593`、`ExpertIndexWriterService.kt:713-756`。

## 现状审计

### RAW/CANDIDATE/APPLICATION ES
- Schema/mapping: 仓库的 `src/main/resources/es/orcid_info_raw.json`、`orcid_info_candidate.json`、`orcid_info_application.json` 均根 `dynamic:false`；`institutionEvidence` 目前不存在；已有 `filterResult` keyword、`identityVerification` object 与机构/国家/类型字段。线上存量索引不保证根级 dynamic 与仓库一致；`ExpertIndexService.updateMappingIfNeeded` 在应用启动时向三层既有索引 `PUT _mapping`，失败只记日志。本计划只修改仓库配置，部署验收必须逐层读取实测 mapping，不能以启动成功代替字段存在。（来源: K-es-dynamic-false、K-es-mapping-single-declaration-source）
- Write paths: `ExpertDiscoveryService.toIndexMap` 建 RAW，`promoteDiscoveredToCandidate` 复制；`buildOrcidProfile` 提供 ORCID；`ExpertIndexWriterService.promoteToCandidate/promoteToApplication` 全 `_source` 透传，`reconcileDiscoveryCandidate` 只写资格；`ExpertDiscoveryService.updateExpertAcademicFields` partial update 学术字段，不得清证据；SBIR 导入属独立路径，未提供所需作者—机构证明则不写本字段。（来源: K-promotion-source-passthrough、K-enrichment-write-three-layers）
- Read paths: `ExpertSearchService.toExpertProfile/sourceFields` 供发送；`ExpertIndexWriterService` map→profile 供复评/晋升但不作发送门禁；`ManualInitialOutreachService` 与 `InitialOutreachService` 在 03 才消费本字段。`filterResult` 原来只在 ES，不在 `ExpertProfile`。
- Interaction points: ES 新字段→搜索模型→DB 重试判定；RAW 新字段→候选/申请晋升；资格更新→`filterResult` 模型。未触及 MySQL schema。
- 线上反例：论文来源档案有 1,256 条非 EMAIL 主键与 `externalIds.orcid` 冲突；`DiscoveryIdentity.allowed` 当前只核姓名/邮箱/evidenceHash（`DiscoveryIdentity.kt:46-60`），未核该 ID 一致性。即使来源论文可查，也不能把该论文机构签给冲突主键。

## 实现方案

1. 三层仓库 mapping 文件添加同名同类型的单一 keyword 字段；不在本计划执行线上 `PUT _mapping` 或任何存量数据回填。（I-1、I-3）
2. `ExpertProfile` 加可空 `institutionEvidence`、`filterResult`；`ExpertSearchService` 的 `_source` 投影与转换显式读取。无值是 null。复评由 `ExpertIndexWriterService` 对原 `_source` 处理，不经此发送投影。（I-3）
3. 在 `DiscoveryIdentity` 加唯一 token 生成/验证函数，签发和验签都先检查已存身份字段与 `externalIds`/主键无冲突；`ExpertDiscoveryService` 只为 01 内部来源字段已确认的单一机构和 01b 唯一 ORCID 机构调用签发。`buildProfile` 在原始 `AuthorEmail` 尚在场时签发，`toIndexMap` 写入；不得扫描历史文档按字段外观补签。论文与 ORCID 只使用 `externalIds`/`identityVerification` 中实际持久化的 ID；SBIR/CORE 无满足结构化证明则不签发。晋升已有 `_source` 全量透传，不为其另写第二套复制逻辑。（I-1、I-2）

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `src/main/resources/es/orcid_info_raw.json` | mapping |
| `src/main/resources/es/orcid_info_candidate.json` | mapping |
| `src/main/resources/es/orcid_info_application.json` | mapping |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` | 可空证据与已有资格投影 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` | token 唯一生成/验证函数 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 证据签发/写入 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 读取投影 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 签发/拒签 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 缺失/存在读取 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 若搜索文件移行，仅改排除清单行号 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt` | A1：mapping 新增字段致 RAW 顶层属性计数变化时，仅按语义更新计数期望（36→37）与紧邻注释，断言与语义不变 |

## 验收标准

- I-1：三层仓库 mapping 有同类型 keyword；部署后逐层 `GET _mapping` 均见 `institutionEvidence:keyword`；单机构强证据出现规范 token；空/多机构/身份未证实无键；对只有旧机构文本、没有内部 `institutionSource` 的样本不补签。
- I-2：统一函数重算发现变机构/国家/类型或出现 `orcidId`/`externalIds.orcid`/身份凭证冲突后旧 token 失效；学术补全不触碰该键；RAW→候选→申请复制相同 token，复用现有晋升测试覆盖透传。（来源: K-promotion-source-passthrough）
- I-3：发送模型读到 `institutionEvidence` 与 `filterResult`，旧文档均为 null；晋升 `_source` 透传。`OperatorStatusWriteSeamGuardTest` 若行号因本次修改偏移，只按语义更新定位，不放宽断言。

## 人工验收清单

### A-1: 有证据档案跨三层
- 前置条件: 测试环境准备一名来源可追溯且身份验证为 `VERIFIED` 的单机构新发现专家。
- 操作步骤: 运行发现、晋升候选及测试申请，再分别查询三层 `_source`。
- 预期结果: 三层 `institutionEvidence` 完全相同且为 `JATS:`/`OPENALEX:`/`ORCID:` 加 64 位十六进制；显示机构/国家/类型一致。
- 覆盖: I-1、I-2、I-3，RAW→候选→申请。

### A-2: 缺证据与旧文档
- 前置条件: 一名多机构歧义新发现专家和一名没有该新字段的旧非发现专家。
- 操作步骤: 运行发现后查询前者，读取后者详情并执行原有复评。
- 预期结果: 前者无 `institutionEvidence`；后者读取不报错、原业务字段和复评行为不变。
- 覆盖: I-1、I-3、不得改变旧档案。

### A-3: 学术补全回归
- 前置条件: 单机构且已有证据的专家，OpenAlex 作者 `last_known_institutions.type=company`，论文机构类型为 education。
- 操作步骤: 运行作者指标补全，查询三层档案。
- 预期结果: `institutionType=education` 且证据 token 原值不变；hIndex 等学术指标仍按原规则补全。
- 覆盖: I-2、学术指标不变。
