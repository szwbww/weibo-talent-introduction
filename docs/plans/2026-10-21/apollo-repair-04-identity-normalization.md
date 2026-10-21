# 04 · 有来源的机构别名与职称兼容

状态：可交付执行，尚未实施。修订：r2（2026-10-21）。基线：`962fcfe924ad7562a4ea917d174180df82ac3b5c`。依赖：03 完成并复验通过。

## 需求描述

可观察结果：新规则版本识别已证实的 Geneva 双语机构对应，以及明确相互佐证的 Assistenzprofessor/Assistant Professor 职称；Nicolas 留档可用来验证规则差异。

必须保留：旧规则0/1、TUM别名版本1、SELF策略、姓名歧义/ORCID冲突/人工保护；APOLLO_UNCLEAR可准入，单姓名或多个同名不能准入。姓名+教授路线仍标记已明确，但不等于核实了博士及年龄。

不做：机构自动翻译、模糊编辑距离、只看机构关键词；UNKNOWN日期变CURRENT；全球职称词典；批量改写历史结论。

## 关键不变量

### Invariant I-1: 规则版本冻结
- Rule：新增 ruleVersion=2、institutionAliasVersion=2，只新run启用；缺省ruleVersion=0仍按旧语义，已存1不变；新run settings.ruleVersion冻结，Worker消费，旧run缺setting但新建payload沿原行为1。
- Applies to：Registration.begin、Worker首次payload、Rules、Gate/Query/Replay读取
- Violation consequence：上线后旧准入重验失败或静默改判。
- 来源：K-institution-rule-persisted-context

### Invariant I-2: 机构别名必须可证实
- Rule：版本2只在版本1 TUM之外增加 University of Geneva ↔ Université de Genève；官方来源随代码保存；严格完整主体对应，显式同命名空间ID冲突不能被别名覆盖。
- Applies to：Normalizer.compareWithAliases、Rules匹配原因
- Violation consequence：跨语言误认同名机构。
- 来源：original

### Invariant I-3: 职称保留原值及等级
- Rule：Assistenzprofessor 只有候选headline以 Assistant Professor 明确开头且无否定/退休/学生限定时，在rule2视为assistant_professor；原title不改写，不标为associate。仍须姓名匹配、唯一支持候选和其他现有保护。
- Applies to：Rules.matchReason / professor predicate
- Violation consequence：把助理教授变成副教授，或跳过人身份判断。
- 来源：真实Nicolas样本/用户既有教授路线

### Invariant I-4: 纯规则不等于最终准入
- Rule：新版本对照需列纯规则差异、Gate差异、Commit保护差异；不得直接将离线 APOLLO_CLEAR 写 admission 或 ES。
- Applies to：测试及受限对照
- Violation consequence：绕过RND/IDENTITY_CHANGED/人工保护。
- 来源：K-discovery-admission-read-write-parity

## 现状审计

Normalizer:285–305 只有TUM版本1；Rules:115把任何新增别名命中都记TUM，必须同时修正标签。Rules:251 只支持中英文教授文本，Nicolas title=Assistenzprofessor 未匹配；headline 有明确 Assistant Professor。Rules:258只接受CURRENT、绑定身份的ORCID任职，该限制保持。

Rules.evaluate 是 Worker、AdmissionGate 和 QueryService 共用入口；Gate DTO默认ruleVersion=0且忽略未知字段，已经传递版本，不需为版本2另改Gate源码，但要测试。Registration:63冻结alias版本，Worker:82硬编码新payload ruleVersion=1，需要一起调整。只扩展已存在settings_json、ruleVersion、aliasVersion，不新增SQL列或ES字段。

共享表结构、全量读写路径见 [证据审计 E-6/E-7](apollo-repair-retention-evidence.md)，作为本计划规范性组成部分，执行/复验不可跳过。本子计划只修改下表白名单；其余路径为回归边界。

## 实现方案

1. **版本入口（I-1）**：RegistrationService 新run settings 增加 ruleVersion=2；Worker 首次payload从run读取并校验支持范围，已存在payload使用其冻结值。Rules接受0..2，名称SELF相关>=1分支沿用。未知版本仍失败关闭。设置缺失的旧run遵循已有新payload=1行为，不能拿当前默认2补齐。
2. **来源可追踪别名（I-2）**：Normalizer保持版本0/1逐字语义，版本2使用小型代码内不可变条目（key、names、introducedVersion、sourceUrls）；仅TUM和Geneva。Geneva来源 `https://www.unige.ch/en/` 与 `https://www.unige.ch/en/contact/`。不创建外部机构服务。新增机构原因 APOLLO_INSTITUTION_ALIAS_GENEVA_V2；现有TUM标签不改；Rules按实际命中返回别名标签，禁止统一套TUM。
3. **职称兼容（I-3）**：Rules在ruleVersion=2的Apollo教授判定增加精确 title=Assistenzprofessor 与明确headline前缀的联合判断，沿用原负面词保护；不得将headline任意出现professor作为条件。现有title的英语/中文规则保持。Rules.matchReason增加可选ruleVersion参数默认旧行为；Rules.evaluate中候选筛选、masked候选冲突与alias原因比较三处，以及Worker的surname回退判定、DETAIL强候选预筛选两处，均传冻结版本。Rules.emailNameMatches内部只接受当前/历史机构命中，保留此既有范围，不因新增教授兼容而让“邮箱名+教授”成为新路线。禁止静默扩大这个入口。
4. **一致性验证（I-1/I-4）**：在Rules/Normalizer/Worker/Gate/Query现有测试写Nicolas最小脱敏输入、TUM旧样本及同名冲突样本。固定 evaluatedOn；两条修复分别与组合跑，Nicolas组合只算1人。用原留档生成内存v2对照输入，不改原SQL/evidence JSON。Gate在同一v2输入+持久结果上必须一致；Commit的人工/身份/RND保护原样保留。

## 变更文件清单

| 序号 | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizer.kt` | 版本2有证据别名 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt` | 职称及准确原因/版本 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` | 新run冻结规则版本 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | 统一传冻结版本 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizerTest.kt` | 别名/ID冲突/旧版本 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt` | Nicolas及负例 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | 恢复版本与预筛选 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGateTest.kt` | 旧新Gate一致 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` | 读取重算版本一致 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt` | 仅迁移既有新 Registration run 的 institutionAliasVersion 预期 1→2；保留旧 run 回退及其余断言 |

共 10 个文件；1 个业务子系统；不修改前端、数据库 schema 或 ES mapping。新增测试数据放入列出的测试文件，不另建 fixture 文件。

## 验收标准

- I-1：rule0/1及alias0/1固定回归全部不变；新run2；旧run缺设置不升级；未知版本被拒绝；Gate/Query与Worker同输入同结论。
- I-2：Geneva法英在alias2匹配、alias1不匹配；无来源近似机构不匹配；明确ID冲突不被覆盖；不误写TUM原因。
- I-3：精确职称+明确headline命中；title单独、headline学生/退休/assistant to professor不新增命中；仍保存原title，等级不是associate。
- I-4：Nicolas仅1条纯规则提升；2个RND_OUT_OF_SCOPE和1个IDENTITY_CHANGED基线差异列为保护项，不能当新增效果；只改诊断不影响准入。
- 集成：新run→Worker→SQL→Gate复算→Query详情；历史读取/人工保护/SELF及APOLLO_UNCLEAR不退化。

### 执行命令与通过条件

以下命令在仓库根目录执行；本次只写计划，未运行这些测试。新增用例写入白名单测试文件，复用已有MockRestServiceServer/Mockito/Testcontainers；不新增Mock服务、测试后台或运行时调试接口。

```sh
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloMatchingV2Test test
```

通过条件：指定测试实际执行，失败0、错误0、必要用例跳过0；检查本次target/surefire-reports时间戳，旧报告不算通过。数据库IT明确检查Docker可用，不得用环境不可用跳过。测试结果与该步代码diff一起交复验；人工清单另行完成。

## 人工验收清单

### A-1: Nicolas两证据同一人
- 前置条件：测试环境导入冻结的item411最小输入，保留Apollo personId与原title、Geneva历史任职。
- 操作步骤：1. 用旧版与v2分别离线评估；2. 查新run逐条详情。
- 预期结果：旧输入PENDING；v2支持APOLLO_CLEAR；两个修复合并仍只1人，title保留Assistenzprofessor。
- 覆盖：I-1/I-2/I-3/I-4

### A-2: 旧版与保护回归
- 前置条件：测试环境准备旧TUM准入、SELF直接准入、APOLLO_UNCLEAR准入、两同名候选及人工拒绝项。
- 操作步骤：1. 查询旧记录；2. 触发正常重验；3. 对同名/人工项尝试正常提交。
- 预期结果：前三项保持既有资格语义；同名仍不自动准入；人工拒绝不被覆盖；旧记录ruleVersion不变。
- 覆盖：I-1/I-4；全部既有准入边界

### A-3: 不能把猜测变事实
- 前置条件：准备仅有Assistenzprofessor而无headline、冲突机构ID、缺任职日期三种样本。
- 操作步骤：1. 分别运行v2；2. 查看原因及证据。
- 预期结果：第一项无新增教授命中；第二项不靠别名覆盖；第三项日期仍UNKNOWN；没有副教授新断言。
- 覆盖：I-2/I-3


## 修正记录

- A2（2026-10-21，聚合审查 Epoch 1 后）：用户明确回复「批准 生成修复  repair」，批准上一轮给出的最小子04范围修订，并要求生成单一修复计划。依据总计划「执行约束（I-1/I-2/I-3）」第2条，将 `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt` 加入白名单，文件数9→10。仅允许迁移既有 `manual keyword scheduled and continuous freeze options and bind actual positive execution IDs` 测试中新 Registration run 的 `institutionAliasVersion` 预期1→已批准版本2；保留旧run回退1，以及其余生命周期、owner归属、请求、无HTTP与保护断言。禁止删除/跳过测试、回退生产默认版本或扩大其他文件/业务行为。对应审查 [V-2 / O-04-01](../review/apollo-repair-retention-master/machine-verification.md)；V-1脱敏修复仍沿子01原合同。此批准仅消除范围规划阻断并授权生成repair.md，尚未授权执行未生成的修复计划。
