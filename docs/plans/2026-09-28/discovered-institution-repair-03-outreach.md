# 03：无机构证据的新发现首发拦截

## 需求描述

新发现专家仅在身份、机构、候选资格均可证实且符合任务地区时进入首发；预估、ES 新目标、MySQL NEW 重试和旧首发给同一结论。不得改变非新发现专家的类型白名单及发送语义、材料催办、邮件模板正文。范围外：历史机构重提取和任务配置 UI。

## 关键不变量

### Invariant I-1: 新发现首发判定
- Rule: `DiscoveryIdentity.isDiscovery(profile)` 或档案标签含 `待确认` 时，发送前必须 `DiscoveryIdentity.allowed(profile)`、`institution` 非空、`country` 能由 `CountryContinentMapping` 映射、`institutionEvidence` 经 02 的统一验签通过（含来源 ID 一致性）、`filterResult == PASSED`；否则不建联系人、不占发件名额、不发邮件。`待确认` 即使缺身份对象也因无机构证据被阻断。其他非发现档案不加这组门禁，也不改全局 `DiscoveryIdentity.isDiscovery` 的既有语义。
- Applies to: `RecipientScope.matchesExpert`、`ManualInitialOutreachService` ES 目标/重试、`InitialOutreachService`。
- Violation consequence: 历史脏档案绕过筛选继续发送。
- 来源: original；`DiscoveryIdentity.kt:46-60`、`ManualInitialOutreachService.kt:1730-1770`。

### Invariant I-2: 地区不能把缺值当 Other
- Rule: 新发现/待确认按已证实机构所在地 `country` 判地区；空国家不属于 Other，未映射/无法证实的国家即使任务未指定地区也不能进入首发；不得用 `nationality` 或论文国家推断本人国籍。ES 预筛和内存重试的最终判定相同。
- Applies to: `RecipientScope`、`ExpertSearchService.regionFilter` 后的最终筛选、`CandidateEligibilityService`、预估。
- Violation consequence: UNKNOWN/脏国家通过 Other；或者重试与 ES 结果不同。
- 来源: K-region-constant-not-display-label、K-batch-send-filter-retry-parity；`BatchExecutionModels.kt:131` 与 `ExpertSearchService.kt:145-165`。

### Invariant I-3: 预估—执行共用最终筛选
- Rule: 预估与实际发送必须对 ES 候选页及 NEW 重试联系人应用同一个 I-1/I-2 最终谓词；ES 查询可做粗筛，但不能把粗筛数量当可发送数量。旧首发即使先取到无证据者，也要继续分页找足指定数量，不能创建联系人后才跳过。
- Applies to: `ManualInitialOutreachService.countEsTargets/fetchEsPage/filterKnownProfiles/buildRetryableTargets`、`InitialOutreachService.sendInitialBatch`。
- Violation consequence: 预估虚高、批次人数不足或漏网错发。
- 来源: K-batch-send-filter-retry-parity。

## 现状审计

### ES 候选与 MySQL `expert_contact`
- Schema/mapping: 02 后 ES 三层有 `institutionEvidence` keyword，原有 `filterResult` keyword、`identityVerification` object；`expert_contact` 在 `V1__create_business_tables.sql:79` 创建，`V48__add_country_to_expert_contact.sql` 增可空国家。联系人没有机构列。
- Write paths: 发现与 `ExpertIndexWriterService.reconcileDiscoveryCandidate` 写 ES 资格；`InitialOutreachService:82`、`ManualInitialOutreachService:847` 建联系人并复制 country；`ContactCountryBackfillService:69-75` 仅补空国家；`ExpertContactRepository.updateCountryById` 可更新国家。发送完成写 mail_record，不在本计划改变。（来源: K-batch-send-filter-retry-parity）
- Read paths: `ManualInitialOutreachService.buildEsFiltersForLevel` 现在不含 `filterResult`/身份/机构证据；`countEsTargets` 无邮件验证时直接 ES count，有邮件验证时 scroll；`buildRetryableTargets` 用 `RecipientScope.matchesExpert`；`InitialOutreachService` 只检查类型/邮箱；`MailVariableService` 在通过后读取机构/国家；`SenderAccountAssignmentService` 读 country。
- Interaction points: ES→预估→发送；DB NEW 联系人→搜索专家→重试发送；country→地区查询/账号分配；`CandidateEligibilityService` 当前 `nationality ?: country` 把机构所在地作国籍。03 修改两个发送路径，材料催办不变。

## 实现方案

1. 在 `RecipientScope` 建唯一新发现最终谓词（I-1/I-2），供 ES 页、NEW 重试及旧首发调用；证据 token 校验具体来源/hash 格式与 02 同源，不能只看非空。`CandidateEligibilityService` 对新发现只读明确 `nationality`，旧非发现原有 fallback 不变。（I-1、I-2）
2. `ManualInitialOutreachService` ES 页返回后应用该谓词，重试通过 `matchesExpert` 同判；`countBySnapshot` 和执行前估算改用现有 scroll 批量最终筛选，保留取消/邮件验证语义。页 offset 针对 ES 粗筛持续推进，过滤一页不能当数据耗尽；实际发送前再检查。用固定测试快照断言阻断人数与原因。（I-1、I-3）
3. `InitialOutreachService` 在保存 contact 前检查同一谓词；`ExpertSearchService.searchExpertsByTypesWithEmail` 增分页能力（现有默认调用行为保持），旧首发跳过不合格新发现后继续取页直到达到 `size` 或用尽。地区 ES 粗筛允许超集，但最终不能让空/未知国家通过；非新发现的原有 OR 国家/国籍语义保留。（I-1、I-2、I-3）

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 唯一最终谓词、重试地区 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 预估/ES/重试/发前门禁 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 旧首发分页与发前门禁 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 旧查询可分页、必要的 ES 粗筛 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityService.kt` | 新发现不以所在地代国籍 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 预估/新目标/重试/Other |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 旧首发继续分页、不建错联系人 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 分页及 ES 地区粗筛 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityServiceTest.kt` | 国籍语义回归 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 若搜索文件移行，仅改排除清单行号 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailOpenTrackingPersistenceTest.kt` | A2：预估改走 scroll 后，仅补 `scrollExpertsFiltered` stub（沿用同一 expert fixture），断言与语义不变 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | A2：`countEsTargets` 旧签名删除后，反射目标改为新签名 `countEsTargets(RecipientScope, LocalDateTime, () -> Boolean)`，「每个 funnel level 都被查询」断言不变 |

## 验收标准

- I-1：同一档案分别缺身份、缺机构证据、ORCID 主键与 `externalIds.orcid` 冲突、`filterResult=REJECTED` 均在三条首发路径阻断；`待确认` 且无证明也阻断；完整证明才允许；其他非发现样本行为不变。
- I-2：新发现/待确认 country=null 或未映射值在无地区限制及 Other 两种任务中均不进入 ES 最终名单/重试；真实映射国家按地区进入；明确中国 `nationality` 仍受国籍资格规则，机构所在地 China 不再冒充本人国籍。
- I-3：预估和执行对相同快照、固定 ES/DB 数据输出相同收件人集合；第一页全被过滤时继续第二页；重试联系人与 ES 入口结果一致；材料催办测试通过。（来源: K-batch-send-filter-retry-parity）

## 人工验收清单

### A-1: ES 与重试一致
- 前置条件: 测试候选层一名无 `institutionEvidence`、一名三证齐全且 `filterResult=PASSED` 的新发现专家；前者再建 NEW 且未发联系人。
- 操作步骤: 打开 INTRODUCTION 预估，运行一次测试发送，查看联系人和邮件。
- 预期结果: 预估可发人数为 1；只有三证齐全者产生新邮件，无证者无新邮件及新建联系人。
- 覆盖: I-1、I-3、ES→发送/DB→发送。

### A-2: Other 与国籍
- 前置条件: 新发现专家已证机构但国家为空；另一名国家为 Japan；范围仅选 Other，再改为 Asia (Japan & Korea)。
- 操作步骤: 两次查看预估并执行测试发送。
- 预期结果: Other 时二人均不在可发名单；Asia (Japan & Korea) 时仅 Japan 专家进入。机构所在地不显示为“国籍”。
- 覆盖: I-2、ES→地区/重试→地区。

### A-3: 旧首发与非发现回归
- 前置条件: 旧首发查询第一页为无证据新发现，第二页有一名三证齐全新发现；另备一名符合原配置的非新发现专家和材料催办联系人。
- 操作步骤: 执行指定 `size=1` 的旧首发，再执行非发现首发和材料催办测试。
- 预期结果: 旧首发发给第二页专家且无错建联系人；非发现与材料催办各仍按原配置发送。
- 覆盖: I-1、I-3、不得改变非发现/催办行为。
