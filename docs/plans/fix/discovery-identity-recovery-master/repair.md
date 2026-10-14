# Repair Plan: discovery-identity-recovery-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-13/discovery-identity-recovery-master.md
Baseline SHA256: f69d8135ce0019ad36cc8939cf8101a6f584aac9983dec3dfc78eb2ae37ce238
Baseline recorded commit: 36075ec06c5e4382f13606c995e24a13ec4470ce
Verification report: docs/plans/review/2026-10-13-discovery-identity-recovery-master/machine-verification.md — Epoch 2，FAIL / DIVERGING
Verification report SHA256 at planning: 7997793ff45408ea4a83cbb023b70e0f36c96b8e31f4f78170bc7af2d9141dd7
Implementation boundary reviewed: 9d353934b82ed3e36a193c70748e5efccb3d9e82..b08b521c12b3d0fbbf219bbb7c8a9fc61a2ec2e9
Reviewed code / next repair base: b08b521c12b3d0fbbf219bbb7c8a9fc61a2ec2e9
Evidence HEAD at planning: 5a82206e9b0f171c332dee7512953b90179f0459
Prior repair SHA256: 845500de628b9e7fe8671171c3e90f49f1afc6bbac2f85e05b11a57be8bd4ca5（历史执行证明不适用于本修订内容）
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master
Branch: fast/2026-10-13-discovery-identity-recovery-master

所有下列仓库相对路径只在上述 worktree 解析；本次规划唯一可写文件为本 repair.md。

## Objective

保留 Epoch 2 已验证的修复，修复11个当前P1：先使健康首发正确完成且不误认自身尝试为历史，再闭合当前人物身份/邮箱资格、未知投递保护、恢复/维护预期与历史关联，最后确保查询失败和失效展示不伪装成功。仅规划，不执行产品修改或测试。

## Authority and Convergence Decision

- governing master identity CONSISTENT；A1—A14规则、原因和人工批准继续有效，精确记录位于 docs/plans/fast/2026-10-13-discovery-identity-recovery-master/ledger.md，SHA256 2bbeec0ca9bbaf2008aed29b4265862a089a84cdbc25f20160a3e6ef5fe361e2；fast handoff同目录human-review-handoff.md SHA256 ff9fa89083d24f9a3f6b37f798a8f201273df9c7b543d8a572316e7c4a390a64。
- Epoch 2 FAIL / DIVERGING保持不变。控制器提出不同策略：保留已验证修复，先区分本次NEW/PREPARED与既有历史恢复正常首发，保留SENT/UNKNOWN/SENDING；再完整组实时复验与SMTP保守分类，再恢复/维护幂等、历史关联/版本，再查询完整性与失效显示，最后补直接相关真实入口回归及核对性能证据。
- 人工明确回复：「好的 就按你说的来 你是不是要更新 repair？」此裁定批准不同修复策略及同路径计划更新，解除repair-p的DIVERGING规划停点；不是对尚未形成的新计划的执行批准。旧execute-p调用只批准旧hash，不能用于执行本修订。
- 只依据已审b08b521代码及Epoch2证据设计；不以当前未提交代码判断任何finding已解决。11项均REPAIRABLE，无新产品范围或schema，旧47文件授权缩为下表33个现有文件。

## Existing Unreviewed Worktree Changes

规划期间外部产品写入仍在变化；本规划作者未写这些文件，也不将其归为本轮实现或已验证成果。最初控制器提供guard diff hash d95f0b247ee13acf7d2f6f8ba7ac7c6183dc4e81620bd77d89e07f51be7c09af；随后观察到guard及其他文件继续变化。以下仅为 2026-10-15T07:05:23+08:00 的未审快照，不声称最终静止：

| Existing dirty file | Observed diff SHA256 | Future scope |
|---|---|---|
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt | 0beb745c963d62064f3ced296e1f15d0165707d460c6672c35d676fe6a6bfd7e | 在33文件内；须按对应任务核对与验证，未自动视为完成 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt | 1dd51c863bbf562dc8d80f2aea88019c7c79212a0b6aa0896ba2db0b25a10222 | 在33文件内；须按对应任务核对与验证，未自动视为完成 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt | 7f782508c42a16b08549e7c286a4772d3bebb351df8d1225223435b33d4c4b1c | 在33文件内；须按对应任务核对与验证，未自动视为完成 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt | ffc356908014064800ab3c34ca335f4b476d8f4a121c1e821c2d2c49b168818b | 在33文件内；须按对应任务核对与验证，未自动视为完成 |
| src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt | 1d8227c0dd517788960b7f380b2dc12665b4cd821fd6e2fb8252db601e72ca2b | 在33文件内；须按对应任务核对与验证，未自动视为完成 |
| src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt | 97d0467499e616bc8de6b47debf50f5703f2697cfac83c3ee0d29da63a9d7c88 | 在33文件内；须按对应任务核对与验证，未自动视为完成 |
| src/main/resources/static/app.js | 6677fec047e629875adf25c60d07f3112fe966341926b1140f6b617bbfbb9253 | 不在本计划授权内；保持原样，不自动修改/收纳/提交 |
| tools/discovery-identity-recovery/replay.py | 45d1a02d1e06db6cf973fcb72e9a3ce0361a77bce3b050e90379b96c71718121 | 在33文件内；须按对应任务核对与验证，未自动视为完成 |

后续execute-p前重新记录HEAD、index和dirty归属/内容，区分已审提交、外部已有diff与本执行新增修改。明确批准本新计划后，可验证并接续位于授权文件且符合本任务行为范围的已有diff；不要求默认清理、重做或覆盖它。范围外差异（当前含app.js）不得自动加入授权或产品提交；保持原样。若其他差异持续改动同一文件、影响必需测试输入或阻止辨认提交边界，在相关执行/证据步骤报告具体冲突；不擅自stash/reset/丢弃，也不把混合工作区通过声称为精确提交通过。纯文档规划不因此阻断。

## Findings in Scope

| Finding | Severity / disposition | Requirement | Current root cause and evidence |
|---|---|---|---|
| V-26 | P1 / REPAIRABLE，NEW REGRESSION | master I-7/I-8；09 I-1/I-2 | 本次NEW/PREPARED被claim历史复核阻断；P2E/P2O |
| V-1 | P1 / REPAIRABLE，PERSISTENT | master发布/I-8；04开关 | 默认关闭无保护也要求mapping；P2F |
| V-2 | P1 / REPAIRABLE，PERSISTENT | master I-4；04/05/08 | 非live既有成员被丢弃、旧映射仍选址；P2G/P2H |
| V-3 | P1 / REPAIRABLE，PERSISTENT | 04 I-2；master I-4 | 递增登记漏核已有根全部当前成员；P2A/P2G |
| V-5 | P1 / REPAIRABLE，PERSISTENT | 08 I-1/I-3；09 I-3 | inBatch/claim复用缓存profile，不读取当前完整组；P2H |
| V-8 | P1 / REPAIRABLE，PERSISTENT | 09 I-2；master I-8 | 任意非空SMTP响应码被视为未接受，含250；P2J |
| V-4 | P1 / REPAIRABLE，PERSISTENT | 02 I-3；05 I-2；master I-3/I-8 | 准入失败仍APPLIED，CLI成功终态跳过；P2K |
| V-14 | P1 / REPAIRABLE，PERSISTENT | 10 I-1；master I-4/I-8 | expected不完整、第三方proof/revision变化误认自身续跑；P2B/P2C |
| V-15 | P1 / REPAIRABLE，PERSISTENT | 10 I-2/I-3；09 I-3 | 历史业务键关联漏导入、rollback缺原revision证明；P2M/P2D |
| V-13 | P1 / REPAIRABLE，PERSISTENT | 06 I-3；master I-8 | 缺hits/count部分响应及SQL关系失败伪精确；P2I/P2N |
| V-12 | P1 / REPAIRABLE，PERSISTENT | 06 I-2；07 I-1/S-2；master I-5/I-7 | stale与ADMITTED投影脱节，现UI仍显示可发送；P2L |

## Findings Excluded

| Finding / item | Reason / disposition |
|---|---|
| V-6,V-7,V-9,V-10,V-11,V-16,V-17,V-18,V-19,V-20,V-22,V-25 | EXCLUDED：Epoch2已RESOLVED的12项，不重开修复任务；保留其已通过行为作为回归约束。 |
| V-21,V-23,V-24 | EXCLUDED：非mandatory测试名称、可选覆盖、注释；不扩入本轮。 |
| B-1 | EXCLUDED as product defect：51,829条真实形状数据、p95≤2s/额外heap≤128MiB仍缺证。没有数据/当前代码测量报告则继续BLOCKED；不虚构、豁免或添加未批准性能产品改造。 |
| B-2 | EXCLUDED as product defect：三真实入口组合证据缺口。R-1/R-4为V-26/V-8确证行为安排真DB/假SMTP回归，可提供其证据；由后续独立审查判定是否补齐，不单独造产品任务。 |
| 其他Observation、旧epoch环境/记账/文档问题 | EXCLUDED：保留原报告，不修无关时区、旧名称、历史计数或其他模块。 |

## Unchanged Contract

- 保留已验证修复及上述12个resolved finding结果。仅当前discovered；历史另需冻结ID；161待核组、两个既有根、原10,686名单及03存量执行不自动处理。
- 原docId/业务键/邮箱/contactId、人工与运营/学术事实、联系人/alias/邮件历史保留；不猜姓名、不伪造认可、不以删除历史解锁、不为合并虚建联系人。
- 04是人物关系写入口，09是人物首发保护写入口；原准入SSOT、逐邮箱验证/抑制、显式scope、配额、暂停、模板和发件人绑定保持。非INTRODUCTION及范围外行为保持。
- 既有持久SENDING及成功事务SENT联动、UNKNOWN不自动重发、未发退出清理不得回退；跨存储事实仍需复验，不声称ES/MySQL/SMTP同事务。
- 不新增文件、迁移、共享schema/ES字段；不改全局旧发现口径、SMTP分类器、其他资格服务。V-12优先且限定后端既有状态投影，app.js/index.html/CSS/缓存键均不在授权内。
- 只允许因本轮授权源码位置变化而机械同步OperatorStatusWriteSeamGuardTest的精确噪声path/line/context；不重开V-25，不增加真实写入口、删除断言或移除排除项失效检查。
- 无真实SMTP、生产apply、生产迁移/开关变更、物理删除、清队列、部署、push、merge、改写历史。原master、review/fast evidence及知识只读；执行后唯一额外证据写入见末尾handoff。

## Authorized Files

仅以下33个现有文件属于未来明确批准后的产品/测试变更边界。当前规划只改本repair。ID与精确路径一一对应，不授权同目录其他文件；本轮新提交的产品diff必须为此集合子集。

| File ID | Exact file | Purpose |
|---|---|---|
| G | src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt | R-1,R-2,R-3,R-4,R-7 |
| INIT | src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt | R-1,R-2,R-4 |
| MI | src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | R-1,R-2,R-4 |
| MEM | src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt | R-1,R-2,R-4 |
| GT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt | R-1,R-2,R-3,R-4,R-7 |
| INITT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt | R-1,R-2,R-4 |
| MIT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | R-1,R-2,R-4 |
| MEMT | src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt | R-1,R-2,R-4 |
| P | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt | R-3 |
| IR | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt | R-3 |
| R | src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt | R-3 |
| SEL | src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt | R-3 |
| PT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt | R-3 |
| IRT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt | R-3 |
| RT | src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt | R-3 |
| SELT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt | R-3 |
| REC | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt | R-5 |
| RECT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt | R-5 |
| RP | tools/discovery-identity-recovery/replay.py | R-5 |
| RPT | tools/discovery-identity-recovery/test_replay.py | R-5 |
| MAINT | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt | R-6,R-7 |
| MC | src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceController.kt | R-6 |
| MAT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt | R-6,R-7 |
| MCT | src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt | R-6 |
| CP | tools/discovery-person-recovery/reconcile.py | R-6 |
| CPT | tools/discovery-person-recovery/test_reconcile.py | R-6 |
| Q | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt | R-8,R-9 |
| ES | src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt | R-8 |
| QT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt | R-8,R-9 |
| EST | src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt | R-8 |
| ECT | src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt | R-8 |
| CCT | src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt | R-8 |
| SEAM | src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt | R-8,R-9；仅必要精确噪声位置同步，保留白名单闭包 |

## Repair Tasks

执行顺序R-1→R-2→R-3→R-4→R-5→R-6→R-7→R-8→R-9；先正常流程，再保护/失败流程。共享文件顺序接续；不从旧版本覆盖。以下每项均受Unchanged Contract约束；现有命令ID在下一节完整展开。辨别性用例应先在已审行为或保留反例上证明能捕捉缺陷，再修实现；外部已有diff只有经同等验证才能算完成。

### R-1: 区分本次发送尝试与既有历史

- Resolves: V-26。
- Root cause: Initial:109/139、ManualInitial:1041/1135/1166 在 claim 前创建自身 NEW/PREPARED；Guard:264/511 把它们当既有历史。P2E/P2O 真库正常首发被拒，SMTP=0。
- Files: `G`, `INIT`, `MI`, `MEM`, `GT`, `INITT`, `MIT`, `MEMT`（精确路径逐一对应 Authorized Files，不授权其他文件）。
- Change: 让原本合格、无既有保护的已映射人物正常首发；本次 owner/选中地址/人物revision 关联的联系人与 attempt 不能被误当旧历史，也不能成为排除其他历史的借口。区分当前尝试必须依据服务器持有的真实关联，不能靠 status=NEW/PREPARED、传入任意 contactId 或全局忽略联系人。安全重试只复用已证明未交付的同一地址/尝试；不删联系人、审计或历史来解锁。
- Discriminating regression: 把 P2O 固化到既有真MySQL测试：三个实际入口各自满足原合法前置条件，使用实际相关 repository、实际 guard、边界假SMTP，合格无历史时分别单独调用恰1次并记录实际地址/原ID；再次请求不得再发。实际 ManualInitial 自建 NEW/PREPARED 也须完成首发；别人的 NEW/PREPARED、旧联系人、SENT/UNKNOWN/SENDING 仍阻断。最终组合回归在同一真库使用三个实际入口及实际 guard（不 mock guard），不同入口竞争同一人恰好1次SMTP（不能只断言≤1），不同人可分别完成；保存每入口与总调用计数。
- Existing verification: C02,C16,C17,C21,C22（完整命令见下节；最终实现稳定后新鲜执行）。
- Must not change: 保留已修复 REQUIRES_NEW、原成功事务SENT联动、未发退出清理、人物去重、配额/暂停/模板/发件人绑定；不恢复逐邮箱首发。
- Prohibited: 授权外文件/行为、新schema、可选重构、用mock替代被验证的真实guard/事务、跳过或弱化失败测试、生产操作。
- Machine completion: 正常首发实测恰1次；三个入口各自健康首发=1次、跨入口同人竞争恰1次；safe retry仅原地址；原历史保护不减弱。

### R-2: 落实关闭状态的旧行为与已有保护边界

- Resolves: V-1。
- Root cause: Gate 默认false，但三caller仍按旧宽 discovered 口径无条件要求mapping；P2F 未开启、无mapping、无保护也 PERSON_MISSING。
- Files: `G`, `INIT`, `MI`, `MEM`, `GT`, `INITT`, `MIT`, `MEMT`（精确路径逐一对应 Authorized Files，不授权其他文件）。
- Change: 使用已有共同开关区分尚未启用的人物流程与已建立的持久保护：未开启且无人物保护时保留原发送流程；已经有 SENT/UNKNOWN/SENDING 等保护时，关闭展示或配置不能撤销。开启后，人物范围内未映射/失效/不可读仍保守拒绝。不得全局收窄旧准入 isDiscoveryOutreach。
- Discriminating regression: 三实际caller分别验证默认关闭/无mapping/无保护维持旧行为；已有SENT/UNKNOWN/SENDING时即使关闭也SMTP=0；开启后无mapping不能退成旧直发。与R-1正常首发用同一真实guard边界。
- Existing verification: C02,C16,C17,C21,C22（完整命令见下节；最终实现稳定后新鲜执行）。
- Must not change: 保留05/06/08已生效的默认关闭逻辑与维护窗口受限映射能力；非INTRODUCTION/范围外行为不变。
- Prohibited: 授权外文件/行为、新schema、可选重构、用mock替代被验证的真实guard/事务、跳过或弱化失败测试、生产操作。
- Machine completion: 三种状态（未开启无保护、开启、关闭但有保护）行为可区分且都有辨别性断言。

### R-3: 在登记和发送边界核验完整当前人物组

- Resolves: V-2,V-3,V-5。
- Root cause: PersonService:88/174/342/363 仅核请求成员和SQL键；Registration:483 mapNotNull 丢弃失效既有成员；Recipient:313/317 复用inBatch缓存，Guard:264/465再次沿用。P2A/G/H 可复现。
- Files: `P`, `IR`, `R`, `SEL`, `G`, `PT`, `IRT`, `RT`, `SELT`, `GT`（精确路径逐一对应 Authorized Files，不授权其他文件）。
- Change: 登记新增成员前把既有根全部成员及作者键纳入当前复验；标签移除、缺失、证据/全名/邮箱/层间冲突不得静默删掉成员后继续挂接。锁/唯一键竞争后重读整组与revision，不能以首轮缓存提交。人物预估、选择、reserve、claim（含SAFE_RETRY）从当前持久事实确认完整组，再对实际邮箱执行原准入、显式筛选、验证、抑制；已在batch的成员也须复验当前标签/指纹，不能只加载missing。关系/事实无法完整读取时明确失效，不退成无保护的个人。
- Discriminating regression: P2A：先Jane Doe后John Smith同ORCID，不得同根；P2G：旧成员移标/不可读后新成员不得借丢弃旧成员挂接；P2H：reserve后改标签/邮箱/proof/准入，claim不得SMTP，断言真实当前读取发生。保留隐藏RAW HOLD、层外成员硬保护、作者键竞争后重读及真库并发相容/不相容成员用例；selector的具体邮箱资格与人数一致。
- Existing verification: C02,C03,C07,C08,C10,C11,C16,C17,C21,C22（完整命令见下节；最终实现稳定后新鲜执行）。
- Must not change: 保留原ID/作者键唯一/跨根待核、范围外零写、原准入SSOT、候选层级、primary合格优先与retry共享人物集合。MySQL与ES仍不是同一事务。
- Prohibited: 授权外文件/行为、新schema、可选重构、用mock替代被验证的真实guard/事务、跳过或弱化失败测试、生产操作。
- Machine completion: 完整根成员不被mapNotNull式遗漏；缓存反例失效；当前组、具体地址与revision通过后才可交付。

### R-4: 仅明确未接受的SMTP结果允许安全重试

- Resolves: V-8。
- Root cause: 三caller以 smtpResponseCode != null 判 SAFE_RETRY；P2J 实际分类器把250异常转FAILED/SUCCESS/250，caller释放保护。
- Files: `G`, `INIT`, `MI`, `MEM`, `GT`, `INITT`, `MIT`, `MEMT`（精确路径逐一对应 Authorized Files，不授权其他文件）。
- Change: 按现有投递结果的可证明语义分类，不将任意响应码等同未接受。已接受或无法确认未接受（含2xx后连接中断、无响应码、结果歧义）保留UNKNOWN或不可重发SENDING；只有明确未调用/明确未接受才可按既有规则原址SAFE_RETRY。使用当前分类器契约，不扩大为SMTP组件重写。
- Discriminating regression: 实际 SmtpErrorClassifier 处理250 queued后异常，经过三个实际caller均不进入可重发状态；无响应码/歧义同样保护，明确拒绝与未调用仍支持原址安全重试。真MySQL+实际guard+假SMTP接受后注入业务落库异常，后续其他入口不得第二次发送；测试不得mock被验证的guard或事务持久化。
- Existing verification: C02,C16,C17,C21,C22（完整命令见下节；最终实现稳定后新鲜执行）。
- Must not change: 保留V-7的独立持久SENDING、V-9的未发清理、明确永久失败策略与原错误审计；不依赖超时解锁。
- Prohibited: 授权外文件/行为、新schema、可选重构、用mock替代被验证的真实guard/事务、跳过或弱化失败测试、生产操作。
- Machine completion: 2xx/未知结果不释放；明确未交付用例可重试；成功后DB失败跨入口仍SMTP总数1。

### R-5: 准入后续失败保留可恢复状态

- Resolves: V-4。
- Root cause: Recovery:146 返回APPLIED/ADMISSION_REVERIFY_FAILED；replay:38/299 把APPLIED终结。P2K proof写成而准入失败后被永久跳过。
- Files: `REC`, `RECT`, `RP`, `RPT`（精确路径逐一对应 Authorized Files，不授权其他文件）。
- Change: 完整APPLIED必须覆盖该请求要求的准入后续步骤；失败返回明确未完成、可核对检查点，CLI不把它加入成功终态。下一次从已经验证的目标proof继续准入，不重写已成功层；原值/本次目标值之外的第三方事实仍停止。保留原已修复的保护读取fail-closed与登记失败处理。
- Discriminating regression: 实际Recovery/Writer边界先成功写proof再让准入抛错；首次非完整APPLIED，故障解除后同检查点完成准入且不重复写proof；CLI不跳过第一次未完成项，第三方proof变化仍拒绝，账本分别记补证/准入结果。
- Existing verification: C03,C04,C05,C06,C10,C21（完整命令见下节；最终实现稳定后新鲜执行）。
- Must not change: 保留PMCID来源核验、预算、cookie认证/私有审计、原PARTIAL续跑和人工保护；不清空账本或放宽任意已有proof。
- Prohibited: 授权外文件/行为、新schema、可选重构、用mock替代被验证的真实guard/事务、跳过或弱化失败测试、生产操作。
- Machine completion: P2K不再成为终态成功；续跑实际闭合准入链且历史层写入次数可核对。

### R-6: 用完整expected事实约束维护续跑与回滚提案

- Resolves: V-14,V-15（rollback版本子根因）。
- Root cause: Maintenance:262/275/293 以同根同成员误认本次完成；:528/607/630快照不全；:447按同personId记录自比较代替revision。P2B/C/D。
- Files: `MAINT`, `MC`, `MAT`, `MCT`, `CP`, `CPT`（精确路径逐一对应 Authorized Files，不授权其他文件）。
- Change: 预览、提交前复核、续跑和rollback使用可核对的原expected：三层版本/当前标签/proof及身份、SQL人物/成员revision、持久人工准入和历史保护事实。HTTP/CLI只传递和保存必要预期及结果凭据，服务器重读验证，不采信客户端结论。仅证明属于原申请且当前仍等于其完整目标状态时报告UNCHANGED；同根同成员不构成归属证明。无法证明（包括响应丢失后缺凭据）明确STALE/变化待核，按原流程重新预览，不伪称完成或protectionImported。rollback缺预期版本、版本改变、新成员/保护状态均不得safeToRollback；只生成提案。
- Discriminating regression: 固化P2B/C/D：apply后第三方改proof、CANDIDATE版本或SQLrevision，携旧expected不得UNCHANGED/protectionImported/safeToRollback；人工准入/历史变化同样被捕获，读失败零新关系写。未变化的已证明本次目标可幂等；原51组→50+1、互斥分母、cookie和私有审计继续通过。controller/CLI往返保留预期与结果凭据，旧请求缺证据保守拒绝，不能默认安全。
- Existing verification: C18,C19,C21（完整命令见下节；最终实现稳定后新鲜执行）。
- Must not change: 保留冻结ID范围、已修复子集/互斥、TransactionTemplate、原历史只读、不直接rollback。只用现有请求/响应/审计和已有字段，不增schema；不无限放宽重试。
- Prohibited: 授权外文件/行为、新schema、可选重构、用mock替代被验证的真实guard/事务、跳过或弱化失败测试、生产操作。
- Machine completion: 任何第三方身份/版本变化均不伪装本次成功；rollback安全结论有明确原版本证明；审计可复核。

### R-7: 按真实业务关联导入已有首发保护

- Resolves: V-15（历史关联子根因）。
- Root cause: Guard:369/395仅用memberDocIds查历史，遗漏合法业务orcidId≠esDocId及同邮箱/alias关联；P2M真库SENT存在但无保护导入。
- Files: `G`, `MAINT`, `GT`, `MAT`（精确路径逐一对应 Authorized Files，不授权其他文件）。
- Change: 沿既有联系人、成员邮箱、alias与业务键关系完整查明所有成员的已成功/未知/在途首发历史，通过09现有写入口持久保守保护。无法确认历史时明确失败，不声称protectionImported；无历史不造永久占位。保持已有整组关系与保护导入同库事务，不重写联系人/邮件/alias。现有未审historyKeys diff只能在后续批准后核对、测试、接续，不视作已完成。
- Discriminating regression: 真实MySQL固化P2M：esDocId与业务键不同、同邮箱/alias命中SENT或未知attempt，必须持久导入对应保护，增加合格新邮箱仍SMTP=0；无历史组不产生永久RESERVED。用实际维护/关系/guard事务注入导入失败，关系与保护不半提交；验证旧业务行逐值未改。
- Existing verification: C07,C11,C17,C18,C21（完整命令见下节；最终实现稳定后新鲜执行）。
- Must not change: 保留原表/键、历史事实、已经建立的更强保护与REQUIRES_NEW发送边界；不能把任意selector排除当作历史已导入。
- Prohibited: 授权外文件/行为、新schema、可选重构、用mock替代被验证的真实guard/事务、跳过或弱化失败测试、生产操作。
- Machine completion: 合法历史关联不漏查；protectionImported与持久事实一致；整组失败无半提交。

### R-8: 查询依赖不完整时拒绝精确结果

- Resolves: V-13。
- Root cause: Search:1284/1324把缺hits当空；:718共享_count不验failed shard；Query:316关系SQL失败返回空映射。P2I/P2N。
- Files: `Q`, `ES`, `QT`, `EST`, `ECT`, `CCT`, `SEAM`（精确路径逐一对应 Authorized Files，不授权其他文件）。
- Change: 所有被人物查询用于精确total/分页/桶计数的响应必须具备该API必要结构并完整结束；缺/畸形hits、count、timeout、failed shard、中途scroll不全或关系SQL失败应明确查询不可用，不能退成合法零/未映射/原始档案人数。合法完整空结果仍为0。保持深分页分块与范围外原单位。严格完整性要求限定本人物查询调用链；若复用共享Search方法，使用兼容的显式调用边界，不能无条件改变非人物查询既有响应/错误合同。
- Discriminating regression: 实际ExpertSearch+Query fixture复现缺hits、count123但failed shard1、关系SQL异常，ES及DB列表入口不得200伪精确total；完整空结果返回0；正常深分页/聚合与scroll关闭不退化。若授权源码行移动导致守卫精确噪声位置失效，只同步该路径/行/context，逐项验证真实写白名单不变。
- Existing verification: C12,C21,C22（完整命令见下节；最终实现稳定后新鲜执行）。
- Must not change: 不改现有共享schema/ES mapping、原筛选语义、范围外分页单位及非人物调用既有合同；不近似计数、不用当前页去重，不删除守卫或扩大真实写白名单。
- Prohibited: 授权外文件/行为、新schema、可选重构、用mock替代被验证的真实guard/事务、跳过或弱化失败测试、生产操作。
- Machine completion: P2I/P2N明确不可用；SQL未知不冒充无关系；完整查询精确且原深分页仍通过。

### R-9: 后端展示投影不把失效成员标作可发送

- Resolves: V-12。
- Root cause: Query:283/285将ADMITTED与stale分离；现renderer对ADMITTED显示可发送。P2L stale=true仍ADMITTED。
- Files: `Q`, `QT`, `SEAM`（精确路径逐一对应 Authorized Files，不授权其他文件）。
- Change: 在既有后端逐邮箱展示投影中，将关系/标签/身份失效、验证不合格或必需资格真值不可得明确投影为既有非许可状态（如UNKNOWN），不得继续输出会被当前renderer解释为可发送的ADMITTED。持久V148准入事实本身不改；正常有效且符合既有逐邮箱资格者保持正确展示。沿用现有DTO/状态契约，不修改app.js或新增前端状态/资源。
- Discriminating regression: 实际Query+BatchSelector固化P2L：移标/过期关系、明确验证不合格或读取失败不输出ADMITTED；持久ADMITTED记录不被改写；有效对照与抑制/HOLD/未知分别准确。现有C14/C15在当前renderer合同下通过。必要时仅同步Q中既有只读投影的精确守卫位置。
- Existing verification: C12,C13,C14,C15,C21,C22（完整命令见下节；最终实现稳定后新鲜执行）。
- Must not change: 保留原docId/contactId入口、memberCount、两宿主、转义和异步隔离；不重写人工准入、不暗示跨所有活动的绝对发送许可，不改静态缓存契约。
- Prohibited: 授权外文件/行为、新schema、可选重构、用mock替代被验证的真实guard/事务、跳过或弱化失败测试、生产操作。
- Machine completion: 后端失效/未知投影不能触发当前UI的可发送标签；正常对照与原链接不回归。

## Verification Commands

本轮仅计划，未重跑任何验证。Epoch2的C01—C19/C21/C22均首次exit0；C21为5047/0F/0E/20S、JS1680全通过，仅作已审基线，不替代未来修复后的新鲜结果。未来执行在最终实现稳定后跑全部21条必需命令一次（共享命令不重复）；Maven必须串行，Docker仅临时测试库，SMTP为替身。R-1/R-4的真实入口组合测试放入既有C17测试文件并由C17实际执行，不能只在C21中被mysqlIt条件跳过。保留每次失败及重跑退出码/计数，不以旧PASS冒充新结果。

C20不要求重跑：已批准旧Zulu11.0.15/tzdb2021e环境例外保持，C21用现有Zulu11.0.32.1履行同一JDK11 clean package门禁。若C01—C19受同一tzdb限制，仅允许换为C21的JAVA_HOME作对照，并保留原失败；不得改时区产品或跳用例。C22保留为必需精确守卫回归，V-25不重开。以下命令均从指定worktree执行：

- C01: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest test`
- C02: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test`
- C03: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test`
- C04: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test`
- C05: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest test`
- C06: `python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'`
- C07: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test`
- C08: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonServiceTest test`
- C09: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`
- C10: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test`
- C11: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test`
- C12: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonQueryServiceTest,ExpertSearchServiceTest,ExpertIndexControllerTest,ExpertContactManagementControllerTest test`
- C13: `node --check src/main/resources/static/app.js`
- C14: `node --test src/test/js/discoveryPersonEmails.test.js`
- C15: `node --test src/test/js/*.test.js`
- C16: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test`
- C17: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonOutreachGuardServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ManualExpertMailServiceTest test`
- C18: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test`
- C19: `python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'`
- C20: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
- C21: `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn clean package`
- C22: `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -Dtest=OperatorStatusWriteSeamGuardTest test`

## Completion Criteria

- 11个included P1全部通过相应当前实际路径辨别性验证；9个根因任务完成，无已知in-scope失败。正常首次发送必须有“恰1次”的正向证据，不能靠“全部阻断所以≤1”冒充完成。
- R-1/R-4覆盖三个实际入口、真实共享MySQL和实际guard；假SMTP精确记录每入口/总调用及成功后DB失败，保留并发/回滚持久状态证据。B-2是否最终补齐留给独立review；不是新增产品缺陷。
- C01—C19/C21/C22在最终可辨认实现边界新鲜通过；必需真库套件实际执行。精确记录skipped/环境例外和外部工作区污染，不能把混合状态测试当作未含这些差异的提交证据。
- 本执行新增/接续并提交的产品/测试diff限33文件；保留已审修复与未收纳外部diff，提交前逐hunk核对归属/授权。遇无法区分的竞争或外部测试输入，报告具体执行冲突，不自行删除、收纳或覆盖。
- B-1缺证时继续明确BLOCKED：本计划不得要求虚构性能数或偷偷做性能改造；无51,829真实形状实测就不宣称master PASS/READY_TO_INTEGRATE。性能未补齐不妨碍本11个已确认缺陷修复完成后返回READY_FOR_VERIFICATION，但仍须独立复审全部主合同。
- 人工验收PENDING；本计划不生成自动下一轮、continuation或并行repair，不授予发布/集成权限。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
人工本次“好的 就按你说的来 你是不是要更新 repair？”只批准本次不同策略与计划修订，不批准执行本新内容。后续明确人类来源的 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md` 才批准该路径当时内容及以下限定执行权限。旧repair的execute-p记录不能复用为本修订执行授权。

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md` invocation authorizes:

1. Only the 33 Authorized Files and required verification commands in this plan; pre-existing in-scope diff may be continued only after the prescribed ownership/scope review and verification. Unlisted external changes remain excluded.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only reviewed changes in Authorized Files, with product commit subject `fix(discovery): restore outreach and enforce current person facts`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/review/2026-10-13-discovery-identity-recovery-master/repair-execution.md` with the exact approval source, current repair identity/hash, pre/post code SHAs, adopted pre-existing versus new changes, every command/result/count, deviations, executor identity when exposed, and accurate worktree/index state. Do not label unrelated dirty work clean or include it in the product boundary. This is the only separately authorized execution-evidence file.
4. Exactly one docs-only evidence commit containing only that repair-execution.md, with evidence commit subject `docs(review): record revised discovery repair execution`.
5. Returning to the already authorized review-fast-p aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan. The executor does not write the master, fast-p evidence, review ledger, machine-verification.md, manual-acceptance.md or final-decision.md. Evidence-only commits may have advanced HEAD since planning; verify the exact worktree and reviewed product ancestry rather than treating an old evidence SHA as current. No commit or implementation is authorized by drafting this plan.

## Self-Review

- 11个current P1按9个根因任务组织；resolved12项、optional3项、B-1/B-2产品化、观察和无关基线均排除。
- 33文件均为旧47授权的子集；不新增schema/产品目标，不改静态资源。SEAM仅保留必需构建的精确位置合同。
- DIVERGING历史结论不改写；不同策略的人工裁定与后续新内容执行批准明确分开。
- 外部dirty只记录/保留，不作为本次修复证据；后续相符diff可验证接续，不强制清理重做。
- 一个current repair.md，一次限定产品提交与一次限定handoff提交；无自动下一轮。
