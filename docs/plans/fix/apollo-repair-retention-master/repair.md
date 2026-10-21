# Repair Plan: apollo-repair-retention-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-10-21/apollo-repair-retention-master.md`
Verification report: `docs/plans/review/apollo-repair-retention-master/machine-verification.md`，Epoch 1，FAIL / INITIAL；报告证据提交 `26ab342f13f8b22ae0c54f3f248a7e7200b5be12`。
Implementation boundary: `962fcfe924ad7562a4ea917d174180df82ac3b5c..462c52385bfe3b248016829a08a5b6ceb2403bb4`
Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master`
Branch: `fast/apollo-repair-retention-master`
Planning evidence head: `17a80ba9f6178ab8c139366743607960277fe2c7`
Repair artifact: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/fix/apollo-repair-retention-master/repair.md`

上述相对路径均相对于唯一选定 worktree；不得在原仓库或其他 worktree 执行。

## Authority and Evidence

- 主计划 governing/invoked/fast-recorded SHA256 均为 `7b0113d6782083d3c86b910f74197bd39e3875f2018a93e68ef6d0f5554cfbda`，主计划身份 CONSISTENT。
- A1 保持：子06构造调用迁移授权，批准提交 `c337ee2aec8a7fc5f36584afeb0d00464ca5103e`；子06 SHA256 `bc32f14ea0db548fc4b81ea3459777f016da5f317aa1e6831c1365e3ea42e999`。
- A2 已批准并落实：人类明确回复「批准 生成修复  repair」。提交 `17a80ba9f6178ab8c139366743607960277fe2c7` 只修订 `docs/plans/2026-10-21/apollo-repair-04-identity-normalization.md`，白名单9→10，授权既有 IntegrationTest 的指定新run版本预期迁移。子04旧 SHA256 `1b1fb2670794126f2a7842988319d7b9796a244a4bd74c00e23a6cb111896b2b`，现 SHA256 `d343e2ff9164b0f1ba91778f62540c64c47926cf13ee7605bf08967fff71ad48`。A2不修改产品行为。
- 本计划使用既有 FAIL / INITIAL 报告；不重跑复验、不改写原报告。V-2此前 PLAN_AMENDMENT_REQUIRED 的范围阻断仅因A2获批准解除；V-1/V-2尚未修复，机器结果仍FAIL，人工验收仍PENDING。
- 原独立报告源 `/tmp/apollo-retention-aggregate-review.md` SHA256 `5e148ae4ed020b8188ecdd5b0ad4432f162661288e63cdbbb77634cbd03a3975`。临时日志仅辅助追溯；执行和再验不得依赖它们长期存在。
- 本次批准授权A2及生成计划，不构成本计划执行批准。执行前必须取得人类明确调用本文件的 `execute-p` 授权。

## Objective

完整脱敏错误诊断中带引号且含空白的秘密值，使新写入诊断和详情API不残留秘密尾部；迁移已批准新run版本对应的既有综合测试预期，恢复必跑最终回归。

## Findings in Scope

| Finding | Severity | Requirement | Root Cause | Disposition |
|---|---|---|---|---|
| V-1 | P1 | 子01 I-2：message中的key/token/password完整脱敏，秘密不得进入诊断SQL/API | VerificationDiagnostics.kt:93 的值匹配在空白处结束，没有形成完整引号值边界 | REPAIRABLE；原契约范围内实现修复 |
| V-2 / O-04-01 | P2，显式mandatory且阻断PASS | 主计划必跑IntegrationTest；子04 I-1新run版本2、旧run兼容 | IntegrationTest.kt:235仍断言新Registration run的institutionAliasVersion==1 | REPAIRABLE；A2已授权精确测试迁移 |

V-1独立合成复现：message为 `invalid parameter {"password":"alpha TOPSECRET omega"}` 或 `invalid parameter {'token':'alpha TOPSECRET omega'}`；`fromBytes`序列化的保存DTO及`read`投影均残留 `TOPSECRET omega`。无引号 `api_key=single-secret` 控制样本正常。诊断命令 `python3 /tmp/apollo-aggregate-review/run-sanitizer-probe.py` exit0，输出 `sanitizer-probe.log` 的两个泄漏用例均为 `SECRET_RETAINED_STORED=true SECRET_RETAINED_API=true`；仅用合成数据，无真实秘密。该exit0表示复现成功，不表示契约通过。

V-2独立机器证据：Epoch1 C09R exit1，33 tests / 1 failure / 0 errors / 0 skipped，失败栈仅指向 IntegrationTest.kt:235。RegistrationService.kt:64–70的新默认2符合批准契约；此项是测试预期未迁移，不是生产默认错误。IntegrationTest.kt:78/:107的旧run回退1断言正确且已通过，必须保留。

## Findings Excluded

| Finding / Item | Reason |
|---|---|
| F-01原具体反例、F-04-01 | 历史已解决项；保留谱系，不重开或重命名为新任务 |
| O-04-01作为单独任务 | 已映射V-2，不重复计算或创建第三任务 |
| 生产召回收益、历史264/14/203项对照、样本hash、人工验收 | 后续验收事项，不是本计划代码修复或生产操作授权 |
| 其他脱敏/匹配/投影建议、无关编译警告、可选清理 | 无本轮确证强制违反，不扩展范围 |

## Unchanged Contract

- 使用既有唯一 VerificationDiagnostics 根、DTO、公共接口；无新服务、依赖、SQL schema、ES字段、前端或日志系统。
- 非2xx读取上限16385字节、字段白名单与长度上限、非JSON原文不保存保持；正常错误分类、成功响应解析、原hash/FULL/PREFIX、429退避和历史NULL语义保持。
- V-1只修既有敏感键值的脱敏边界。普通诊断信息仍可用，不得通过删除整条message/所有诊断来规避回归。
- V-2只迁移A2明确指定测试的新run机构别名版本预期1→2。生产默认2、旧run规则1、缺payload版本0、已冻结版本、未知版本失败关闭保持。
- SELF、姓名/同名保护、人工及身份变更保护、Gate/Commit、请求账、恢复、游标、投影统计和历史样本保持。
- 不覆写原attempt/sample，不回填/清洗历史SQL，不运行replay apply，不发真实Apollo/ORCID请求，不部署、不操作生产。
- 不删除、弱化、跳过既有测试；不改批准计划、既有review报告、历史证据或无关fixture。末尾handoff是单独列明的唯一证据写入例外。

## Authorized Files

仅以下4个既有产品/测试文件；无需新增产品或测试文件。

| File | Purpose / Bound |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/VerificationDiagnostics.kt` | R-1：完整脱敏带引号秘密值，维持既有有界诊断与读取语义 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ApolloPeopleSearchClientTest.kt` | R-1：复用HTTP fixture，证明双/单引号含空白值完整脱敏，普通分类及单词控制保持 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | R-1：复用既有真实MySQL/HTTP/Query链验证存储/API及请求账 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt` | R-2：仅A2指定测试的新Registration run institutionAliasVersion预期1→2；不改其他断言 |

## Repair Tasks

### R-1: 修正带引号秘密值的完整边界

- Resolves: V-1。
- Root cause: 可选开引号与值体没有完整配对；值体在首个空格结束，后续sanitize无法识别脱离键名的秘密尾部。
- Files: Authorized Files前三项，除此不写。
- Change: 对既有识别的key/token/password键，按完整引号值脱敏；引号内空白不得使秘密尾部被保留。保留无引号值保护、字段边界和可用非敏感诊断。不限定具体正则或解析实现。
- Discriminating regression: 在既有HTTP测试覆盖上述双引号password和单引号token合成反例，断言完整秘密及可识别尾部 `TOPSECRET omega` 不存在，且分类和普通错误文字仍保留；保留无引号单词控制。测试必须能在原实现上暴露V-1，而非仅检查存在REDACTED标记。
- Write/read regression: 复用WorkerTest现有真实MySQL+Mock HTTP+Query场景，检查 `attempt.response_json` 实际保存文本与详情API投影均不含秘密及其尾部。覆盖 `VerificationDiagnostics.read` 对含完整敏感值诊断输入的投影保护；不得为此批量修改旧数据库行。验证一次失败请求只增加一次真实请求账，错误分类、非成功response_sha256及原有断言保持，无额外HTTP。
- Existing verification: 下方E1完整定向组，必要MySQL/HTTP用例实际运行，失败/错误/必要跳过均0。
- Must not change: 全部Unchanged Contract；特别是白名单、有限字段和保留有用诊断。
- Prohibited: 扩大provider分类、加入新服务/依赖、历史数据清洗、读取真实秘密、修改规则/恢复/准入逻辑，或新增无确证需求的大型测试矩阵。

### R-2: 迁移新run机构别名版本断言

- Resolves: V-2 / O-04-01。
- Root cause: 必跑综合测试仍使用新run旧版本预期；Registration按批准的子04版本2运行正确。
- File: 仅 Authorized Files 第四项。
- Change: 仅将既有 `manual keyword scheduled and continuous freeze options and bind actual positive execution IDs` 测试中约:235的 `institutionAliasVersion == 1` 迁移为 `== 2`。
- Discriminating regression: 直接使用该既有测试的四个真实入口新run断言；不另写镜像实现测试。该断言应验证已持久化的settings，而非改用与实现同源的常量让测试失去区分力。
- Existing verification: 下方E2完整四类组，含真实IntegrationTest；保留:78/:107的旧run规则1断言、全部生命周期、owner归属、请求/无HTTP与保护断言。
- Must not change: 生产默认、旧版本兼容、其他测试行为与数据；A2范围不扩展。
- Prohibited: 为迎合旧断言回退生产默认、删除/跳过用例、改写旧run期望，或改动该测试文件中的其他断言。

## Verification Commands

规划阶段不运行以下命令。Executor获明确批准后，在上述唯一worktree执行；Maven必须串行，避免共享target碰撞。容器仅为现有隔离MySQL测试。

```sh
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master
export JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
export JAVA_TOOL_OPTIONS=-Dapi.version=1.44
java -version
docker version
```

`JAVA_TOOL_OPTIONS`必须向未显式带API参数的Worker测试传递1.44；Epoch1已证实默认1.32遭Docker server最低1.40拒绝。权限受限应使用已授权的本地测试升级路径；不能把权限失败或环境失败当作测试通过。

Executor必需命令：

```sh
# E1：V-1 HTTP、真实SQL和详情读取链
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ApolloPeopleSearchClientTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest test
# E2：V-2及主计划最终回归；mysqlIt必须显式启用
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationIntegrationTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationAdmissionGateTest,ApolloMatchingV2Test test
```

E1历史40/0/0/0；新增有效断言后按实际测试计数记录，不硬编码新总数。E2历史33/1/0/0，IntegrationTest本身12/1/0/0；预期全部既有用例仍实际运行、失败/错误/必要跳过0。两组都须exit0；读取本次日志及所选类XML的时间戳，记录真实tests/failures/errors/skipped，不能拿target中陈旧XML补齐。Maven绑定的JS/语法检查也须完成。命令失败则记录真实原因，不通过修改无关文件绕过。

独立review-fast-p aggregate复验在机器PASS前，重新核对整个主计划+A1+A2矩阵、V-1/V-2谱系，并新鲜执行下列10组；不能用executor日志替代。第3组同时满足子02/03相同RepositoryIT要求：

```sh
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ApolloPeopleSearchClientTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationReplayToolTest,DiscoveryVerificationQueryServiceTest test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationRepositoryIT test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloMatchingV2Test test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationReplayToolTest test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryPromotionProgressServiceTest test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationReportingIT test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -DmysqlIt=true -Dapi.version=1.44 -Dtest=DiscoveryVerificationIntegrationTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationAdmissionGateTest,ApolloMatchingV2Test test
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=ExpertDiscoveryServiceTest,TaskProgressControllerTest,TaskExecutionControllerTest test
```

## Completion Criteria

- V-1两个合成反例的HTTP诊断、实际SQL保存及详情投影无秘密/尾部；有用非敏感信息与原分类、请求账、哈希语义保持。
- V-2指定新run断言精确迁移为2，旧run兼容及全部原断言保持；E2真实通过。
- E1/E2新鲜exit0，相关MySQL/HTTP测试无必要跳过；记录实际计数及日志，不硬编码新总数或伪造覆盖。
- 产品/测试改动限于4个Authorized Files；计划/契约未变；没有生产、历史恢复或额外provider调用。
- Executor完成末尾确切本地产品及证据提交、durable handoff，报告READY_FOR_VERIFICATION；不得自授机器PASS或人工ACCEPT。
- 独立聚合复验须另行完成全部矩阵/10组命令；人工验收继续PENDING。排除项不作为修复完成条件。

## Self-Review

- 两项任务对应两个确证根因，合用一份计划；V-1保留P1，V-2保留显式mandatory P2及O-04-01映射。
- A2已消除唯一范围权限缺口；无BLOCKED项伪装为可修，没有新增行为或暗改契约。
- 四文件均获明确授权；R-2受A2更窄限制；新增回归只证明已复现秘密值边界及实际写读链。
- Executor两组自验与独立十组聚合复验职责分清；计数如实记录，不弱化测试。
- 无分轮、平行或自动重试修复计划；本计划仅DRAFT_READY，尚未实施。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

批准A2及生成计划不等于批准执行。明确的人类调用以下确切路径，才批准本计划当前内容；内部代理转交、自动继续或推断路径不构成批准：

```text
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/fix/apollo-repair-retention-master/repair.md
```

## Review-Fast-P Execution Handoff

明确的人类 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/fix/apollo-repair-retention-master/repair.md` 调用授权且仅授权：

1. 本计划Authorized Files及规定的验证命令。开始先核对worktree/branch、批准计划和repair SHA256、干净index/产品边界；允许本计划与匹配review证据提交使HEAD前移，未知产品差异必须先查明，不得一并暂存或覆盖。
2. R-1/R-2和executor必需E1/E2通过后，报告READY_FOR_VERIFICATION前，恰好一个本地产品提交，只暂存4个Authorized Files中实际改动文件。具体提交主题：`fix(discovery): redact quoted secrets and align version regression`。
3. 追加唯一durable handoff `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/review/apollo-repair-retention-master/repair-execution.md`，保留既有epoch。按review-fast-p格式记录：Evidence mode=DURABLE_HANDOFF；准确人类批准来源/原调用；repair路径及SHA256；prior code head、repair code head、handoff提交前HEAD；executor身份（不可见如实UNAVAILABLE）；结果READY_FOR_VERIFICATION；实际变更文件及授权映射；全部executor命令、退出码、实际计数与日志；偏差；产品/index和证据提交后worktree清洁检查。不能把规划者/复验者身份冒充executor，也不能编造未跑命令。
4. 恰好一个docs-only证据提交，仅包含上述repair-execution.md，具体主题：`docs(review-fast-p): record retention repair execution`。不改review ledger、machine-verification、manual-acceptance、原计划或本repair文件；这些由控制器按流程管理。证据提交前记录父SHA；提交后取得真实证据SHA并在返回结果报告，禁止为自引用SHA追加修改提交。
5. 完成两个本地提交后报告READY_FOR_VERIFICATION，并在用户调用明确请求继续复验时，返回同一任务已授权的review-fast-p aggregate复验，由新的独立reviewer核验；不创建新任务、不自动循环修复。若调用只授权执行，则提交handoff并停止等待独立复验，不自行宣告PASS。

不授权额外文件、amend、历史重写、push、merge、部署或本计划外修复。执行中若不能满足批准范围/命令/干净边界，保留真实失败证据并停止，不通过扩范围、跳测或重复无变化尝试掩盖问题。
