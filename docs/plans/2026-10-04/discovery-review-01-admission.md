# 01：基础校验解释器

依赖：无。只新增解释能力，不改变当前发送行为。审计依据：[E1/E2](discovery-review-audit.md)。

## 需求描述

将现有自动资格判断展开为能展示、能验证的逐项结果，供深度发现审核使用。保持学术身份验证与历史人工认可含义；不新设 h-index、机构级别或研究方向阈值，不修改线上配置。

范围外：审核落库、发信切换、重新抓取来源、D1历史发送政策。

## 关键不变量

### Invariant I-1：自动判定只搬现有规则
- Rule：发现专家自动判定=CandidateEligibilityService 当前启用规则 + DiscoveryIdentity 身份 + 机构非空 + 国家可映射 + 机构凭证。`filterResult` 为已有资格结果，不把缺少 PASSED 当不可自愈的循环条件；实时重算候选资格后产生新结论。研究方向缺失单独展示资料提示，只有现有分类规则实际失败时才产生资格原因。
- Applies to：新增 policy、自动收录、重验和旧数据初始化调用者。
- Violation consequence：引入未经用户要求的新标准，或永远不能自动通过。
- 来源：E1；用户要求。

### Invariant I-2：人工认可不升级为学术证明
- Rule：有效 LEGACY_APPROVED 是人工准入依据，单独返回；不修改 DiscoveryIdentity.allowed 的 source-verified 语义。新人工批准消费点由02提供；纯 policy 不签发人工批准。
- Applies to：policy、凭证解释。
- Violation consequence：把运营认可用于作者绑定、错误补全。
- 来源：K-batch-send-filter-retry-parity；E2。

### Invariant I-3：解释与布尔判定同源
- Rule：未命中有效历史认可时，自动状态 AUTO_PASSED iff 阻断原因数组为空、NEEDS_REVIEW iff 非空；有效历史认可单独返回LEGACY_APPROVED及资料提示，不混入自动分支。每项返回 code、label、field、observed、expected、sourceLocation；未知信息用 null + UNKNOWN，禁止虚构。
- Applies to：模型、校验解释、API消费者。
- Violation consequence：显示原因与实际判断两套逻辑。
- 来源：原始。

## 现状审计

只读 ES profile、eligibility 设置；本子计划不新增存储写入。ES 的所有来源写入/读取见公共审计 E5；profile 是这些写入的投影。CandidateEligibilityService:21 返回结构化 rejectReasons；DiscoveryIdentity.allowed:45、institutionEvidence:110、validInstitutionEvidence:133 当前只有 Boolean/null。现有 hash 重算可复用，不另写签名算法。

交互点：X1自动发现与X3重验以后共用本解释；01本身不接业务写入。旧数据 `filterRejectReason` 只能作为历史原文展示，不直接覆盖重算结果。ORCID零机构/多机构已在存储时合并为null，展示“当前无唯一机构；无法从已存数据区分未提供或多机构”。

## 实现方案

1. **I-1/I-3**：新增 `DiscoveryAdmissionModels.kt` 定义 `AdmissionReason`、`AutomaticAdmissionResult`（状态、阻断原因、提示、policyVersion、checkedAt、配置快照摘要）。没有 REVIEW_REQUIRED boolean 冗余字段；eligible 从状态派生。
2. **I-1/I-2/I-3**：新增 `DiscoveryAdmissionPolicy.kt`，输入完整 profile 与现有 EligibilityResult；调用现有签名函数。新增 `DiscoveryIdentity.explainIdentity/explainInstitutionEvidence` 返回原因；allowed/validInstitutionEvidence 仍以原函数为权威，用真值表证明解释一致。不要改来源识别/签发算法。
3. **I-3**：原因词表至少包括 IDENTITY_MISSING、IDENTITY_VERSION_UNSUPPORTED、IDENTITY_FIELDS_MISMATCH、IDENTITY_SOURCE_INVALID、INSTITUTION_MISSING、COUNTRY_MISSING、COUNTRY_UNMAPPED、INSTITUTION_EVIDENCE_MISSING、INSTITUTION_EVIDENCE_INVALID、SOURCE_ID_MISSING、SOURCE_ID_CONFLICT，以及现有 candidate rejectReasons 原码。token缺失时仅列当前输入情况，不声称还原历史故障。
4. **I-1/I-2**：有效历史认可返回 LEGACY_APPROVED，并把机构/国家/凭证问题作为已认可的事实提示，后续不再将它强制送回审核；不重新按“5338”数量创建认可记录。只有真实 valid digest 的记录可用。
5. **I-1–I-3**：新增测试以结构化构造样本覆盖分支；命名为合成样本，不冒用真实专家姓名。原 DiscoveryIdentityTest、LegacyDiscoveryApprovalTest 全量回归。

## 变更文件清单

以下路径相对仓库根；完整且唯一的执行白名单，共5文件、1子系统；无新共享存储字段。

| 文件 | 操作 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryAdmissionModels.kt | 新增 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt | 新增 |
| src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt | 新增解释函数，保留已有判断与签发 |
| src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicyTest.kt | 新增 |
| src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt | 解释/布尔一致性 |

## 验收标准

- I-1：逐项打开/关闭现有 candidate 配置；观察原因只随对应设置变化；证明没有新增默认阈值。country空、有研究方向样本只产生相应现有原因。
- I-2：有效legacy=LEGACY_APPROVED，allowed仍false；改邮箱/姓名/摘要后旧legacy不生效；没有自动签发代码。
- I-3：所有阻断组合解释与判定一致；token缺失/非法/不匹配不同码；零/多机构无法确定时不输出肯定归因。
- 运行 `DiscoveryAdmissionPolicyTest,DiscoveryIdentityTest,LegacyDiscoveryApprovalTest`。

## 人工验收清单

### A-1：可解释结果
- 前置条件：在隔离测试环境准备全过、缺机构、ORCID缺国家、有机构无token、token不匹配5份合成profile；通过测试夹具调用解释器并输出JSON，不需修改线上数据。
- 操作步骤：1. 查看5份输出的state/reasons。2. 查看有研究方向但缺机构样本。3. 对照页面上线后同样数据。
- 预期结果：全过为AUTO_PASSED；其余NEEDS_REVIEW；原因分别指向缺项，token缺失不被标为摘要不匹配；研究方向显示原值。
- 覆盖：I-1/I-3、X1/X3。

### A-2：历史认可回归
- 前置条件：测试夹具生成1份有效历史回执，复制并修改邮箱得到第2份。
- 操作步骤：1. 输出两份解释结果。2. 检查学术身份验证输出。
- 预期结果：第1份LEGACY_APPROVED但学术身份未被升级；第2份不继承旧认可；不改任何来源字段。
- 覆盖：I-2、必须保持项。
