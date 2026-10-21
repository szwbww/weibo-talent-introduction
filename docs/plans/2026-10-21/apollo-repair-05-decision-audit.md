# 05 · 候选逐项判定留档与对照导出

状态：可交付执行，尚未实施。修订：r2（2026-10-21）。基线：`962fcfe924ad7562a4ea917d174180df82ac3b5c`。依赖：04 完成并复验通过。

## 需求描述

可观察结果：未匹配记录能追到各候选在哪个条件失败；对照报告区分搜索问题、HTTP问题、规则拒绝和提交保护。

必须保留：准入判定只有一套实现，diagnostics不参与资格；已有论文样本目录/抽样上限/7天保留；旧记录不伪造诊断。

不做：复制所有person完整响应到前端；新建日志平台；为历史已丢失错误正文补造值。

## 关键不变量

### Invariant I-1: 诊断沿真实分支产生
- Rule：规则返回可选trace，与同次decision同时计算；不得QueryService另跑不同规则伪装当时trace。诊断中的NOT_EVALUATED与FAILED分开。
- Applies to：Rules.evaluate、Worker落库、Query读详情
- Violation consequence：日志解释与实际准入不一致。
- 来源：original

### Invariant I-2: 有界且可重现
- Rule：沿03的diagnostics根扩展decision，不再新增根字段；固定schemaVersion/ruleVersion/evaluatedAt、输入摘要、各步骤枚举、候选ID与证据引用。最多200候选，完整总数和truncated显式，规则候选全集不裁剪。
- Applies to：Rules trace、Worker保存、Replay导出
- Violation consequence：体积膨胀，或裁剪改变唯一性判断。
- 来源：K-verification-cached-evidence-replay

### Invariant I-3: 新旧证据不能混称
- Rule：持久trace标记RECORDED；对旧输入重新评估标记RECOMPUTED并标执行版本/时间；没有原错误正文输出NOT_RECORDED。纯规则建议、Gate结论和Commit保护各列。
- Applies to：QueryService / ReplayTool
- Violation consequence：以重算冒充原始运行事实、夸大修复效果。
- 来源：K-discovery-admission-read-write-parity

## 现状审计

当前 item 详情仅返回最终 matchedCandidate、reasonCodes、missingFields 与 attempt摘要，未匹配时候缺各候选分支解释。现有ReplayTool.evaluateInput约254–281行会重跑ORCID Parser，局部变量gate仍只是Rules.evaluate，并未调用AdmissionGate；该字段不能当真实Gate验收证明。成功详情和 search.candidates 已保留，故无须第二份原始候选库。

本步只有 evidence_json.diagnostics 的内部扩展；Rules返回纯内存trace，Worker为唯一运行时持久化者；Gate仍只使用原业务字段，Query仅返回已记诊断；ReplayTool仅在输出受限服务器报告中写重算诊断。

共享表结构、全量读写路径见 [证据审计 E-6/E-7](apollo-repair-retention-evidence.md)，作为本计划规范性组成部分，执行/复验不可跳过。本子计划只修改下表白名单；其余路径为回归边界。

## 实现方案

1. **同次判定诊断（I-1/I-2）**：扩展 VerificationDiagnostics，固定候选步骤：nameMatch（MATCH/NO_MATCH/NOT_EVALUATED）、orcid（MATCH/CONFLICT/NOT_AVAILABLE/NOT_EVALUATED）、currentInstitution/historicalInstitution（MATCH/DIFFERENT/UNKNOWN/NOT_EVALUATED）、professor/specialty（MATCH/NO_MATCH/NOT_EVALUATED）、chosenReason、rejectedReason。保存规则version、aliasversion与Normalization输入引用；原姓名/机构/title仍引用existing候选对象，不重复存。不为解释新增一套姓名分类算法，只记录原分支是否匹配；原APOLLO_EMAIL_GIVEN_NAME_MATCH原因继续保留即可。仍按现有短路顺序判定，未执行分支为NOT_EVALUATED；必须先对业务候选全集完成原有判定，再有界取trace；命中候选优先入trace。
2. **唯一实现（I-1）**：Rules在原分支填trace，允许旧 evaluate 调用继续返回兼容decision；将trace作为VerificationDecision的可选附加字段（默认NULL），Gate只比较原outcome/qualification/route/personId。Worker.finish把trace写diagnostics.decision，与原outcome同一事务；不得新增第二套matching evaluator。
3. **详情与导出（I-2/I-3）**：QueryService显示RECORDED或NULL；ReplayTool新增只读对照报告列：itemId、inputHash、old/newRuleVersion、old/newOutcome、matchedPersonId、reasonDelta、searchCoverage、lastHttpStatus/resultCode、gateResult、protectionStatus、traceOrigin。新增显式 `--mode frozen-rules`，默认直接使用冻结payload.evidence/search/institutionNormalization，不重跑Parser、不重新查询外部。旧侧使用冻结ruleVersion/aliasVersion；新侧用显式目标版本2生成内存副本，记录唯一输入差异，固定evaluatedAt。原parser-only对照模式保留但不得混入规则收益。现有所谓gate输出是纯规则复算，报告标RULE_REEVALUATION，真实gateResult另列；无法实际调用AdmissionGate并取得所需上下文时标NOT_EVALUATED，不猜测，更不能把Rules.evaluate重命名为Gate通过。保持现有冻结文件不可修改，生成新报告并记录hash；目录0700/文件0600，服务器内使用，不提交人物数据到Git。将Normalizer和Rules/Parser相关class纳入版本摘要，防只换机构规则而class manifest没变化。
4. **测试（I-1/I-2/I-3）**：RulesTest验证教授/当前/历史/专业、无候选、冲突、搜索截断每条路径；WorkerTest和QueryServiceTest验证保存、读取、未知字段兼容；ReplayToolTest验证固定时间同输入可复现、旧trace不会补造、保护差异独立。

### 对照模式契约（I-1/I-3）

只在现有evaluate命令追加`--mode frozen-rules --target-rule-version 2 --target-alias-version 2`；manifest、manifest-sha256、old-classpath、fixed-classpath沿原参数。缺mode保持旧CLI兼容行为；指定frozen-rules必须显式给两个target参数，不认识的版本报错，禁止回落当前默认。目标版本只改内存副本，frozen before/manifest不改；固定时间来自manifest.evaluatedAt，明确输出oldStoredOutcome、oldRecomputedOutcome、newRecomputedOutcome三个列。已有旧class hash与本次固定输入不匹配时中止对照，不把当前类重复跑两次冒充前后版本。

原输出persistentGateOutcome不得继续被新报告解释为真Gate；新报告gateResult默认NOT_EVALUATED，实际AdmissionGate由04的Gate测试验证。Commit是否保护以持久原因和实际提交回执区分：历史原因标RECORDED_PROTECTION，不冒充对当前RAW的新检查。生产补跑结果再用inspect读真实状态，离线evaluate写入admission数必须0。

## 变更文件清单

| 序号 | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/VerificationDiagnostics.kt` | 候选判定trace |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt` | 沿真实分支产trace |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | 同结论持久化 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | trace来源及读取 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt` | 只读对照报告/class摘要 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt` | 分支/数量边界 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | 原子保存 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` | 已记录/缺失语义 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayToolTest.kt` | 对照可复现/版本 |

共 9 个文件；1 个业务子系统；不修改前端、数据库 schema 或 ES mapping。新增测试数据放入列出的测试文件，不另建 fixture 文件。

## 验收标准

- I-1：所有匹配/拒绝路径的trace与实际执行分支一致；未执行步骤为NOT_EVALUATED；去掉diagnostics，准入结果相同。
- I-2：201个候选保留完整业务候选，trace≤200且total=201/truncated=true；唯一性照201人判断。导出无key，文件权限0600。
- I-3：旧项trace=NULL或重算报告RECOMPUTED，绝不RECORDED；264条历史正文缺失仍NOT_RECORDED；纯规则差异不等于新增admission。
- 集成：HTTP成功候选→Rules→Worker/SQL→详情→冻结→固定时间离线对照；新旧样本目录与保留策略无变更。

### 执行命令与通过条件

以下命令在仓库根目录执行；本次只写计划，未运行这些测试。新增用例写入白名单测试文件，复用已有MockRestServiceServer/Mockito/Testcontainers；不新增Mock服务、测试后台或运行时调试接口。

```sh
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationReplayToolTest test
```

通过条件：指定测试实际执行，失败0、错误0、必要用例跳过0；检查本次target/surefire-reports时间戳，旧报告不算通过。数据库IT明确检查Docker可用，不得用环境不可用跳过。测试结果与该步代码diff一起交复验；人工清单另行完成。

## 人工验收清单

### A-1: 解释未匹配
- 前置条件：测试环境准备姓名一致但机构/历史/职称/专业均不支持的候选，以及两个同名支持候选。
- 操作步骤：1. 运行核验；2. 请求item详情diagnostics.decision。
- 预期结果：前者原因IDENTITY_NOT_MATCHED且有各步骤结果；后者IDENTITY_AMBIGUOUS；均无自动准入。
- 覆盖：I-1/I-2

### A-2: 旧记录与有界输出
- 前置条件：旧item没有trace；测试item包含201候选；服务器已有论文sample包。
- 操作步骤：1. 查两项详情；2. 运行只读对照导出；3. 检查文件权限与sample hash。
- 预期结果：旧trace=NULL，重算标RECOMPUTED；201候选trace≤200且显式截断；文件0600；论文包hash不变。
- 覆盖：I-2/I-3；保留既有留存

### A-3: 规则与提交分开
- 前置条件：对照集中含RND_OUT_OF_SCOPE、IDENTITY_CHANGED、人工拒绝项。
- 操作步骤：1. 生成旧新对照；2. 比较规则列与protection列；3. 查看实际admission数量。
- 预期结果：规则建议变化单列；保护未核实标NOT_EVALUATED，已核实阻断单列；离线导出本身新增admission=0。
- 覆盖：I-3；留档→重算→提交保护

