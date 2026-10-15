# Repair Plan: discovery-identity-recovery-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-13/discovery-identity-recovery-master.md
Baseline SHA256: f69d8135ce0019ad36cc8939cf8101a6f584aac9983dec3dfc78eb2ae37ce238
Baseline recorded commit: 36075ec06c5e4382f13606c995e24a13ec4470ce
Verification report: docs/plans/review/2026-10-13-discovery-identity-recovery-master/machine-verification.md — Epoch 3（2026-10-15T13:43:21+08:00），FAIL / DIVERGING
Verification report SHA256 at planning: d960eb47e743b025e654ebd36a907eb0d8e8d926164b3b821f0f1bb20475a7ac（已提交的完整三epoch报告，不是/tmp单epoch副本）
Implementation boundary reviewed: 9d353934b82ed3e36a193c70748e5efccb3d9e82..3b6f854e64eae0361b86cbb6af9a9b21b0385f13
Reviewed code / next repair base: 3b6f854e64eae0361b86cbb6af9a9b21b0385f13
Evidence HEAD at planning: 73ba58f6e1f50eb03f61fcdb30c51e4ab97743c4
Prior repair SHA256: 1c3736141987d41bdbc1737bcd0756cf35f4a8fca0e97bc8cd661586dbeb1f59（旧执行批准不适用于本修订）
Prior repair execution: docs/plans/review/2026-10-13-discovery-identity-recovery-master/repair-execution.md，SHA256 702bfbbfe66a77c9c83f0fe5a77dd5f1cd1bc5fba2fb2306f1bc248a7280ad5e
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master
Branch: fast/2026-10-13-discovery-identity-recovery-master

所有仓库相对路径只在上述worktree解析。规划开始时HEAD/index/worktree干净；不存在上版计划所记的待归属产品diff。本次规划仅更新本文件，不运行新一轮审查/测试，不改产品、测试、review证据、index或HEAD。历史版本保留于Git。

## Objective

保留已验证修复，以真实业务边界闭合8个当前P1：发送前当前具体邮箱资格、并发人物根不变量、读取完整性、维护expected及执行归属、现存层proof完成条件、邮箱状态展示。完成本8项可返回READY_FOR_VERIFICATION；这不等于B-1/B-2补齐，也不等于master机器PASS或人工验收通过。

## Authority and Convergence Decision

- governing master identity：CONSISTENT；master、十个ordered child、A1—A14均不修改。精确子计划身份及各amendment的规则/原因/人源批准，继续使用 `docs/plans/fast/2026-10-13-discovery-identity-recovery-master/ledger.md` §Children/§Amendments，SHA256 `2bbeec0ca9bbaf2008aed29b4265862a089a84cdbc25f20160a3e6ef5fe361e2`；同目录 `human-review-handoff.md` SHA256 `ff9fa89083d24f9a3f6b37f798a8f201273df9c7b543d8a572316e7c4a390a64`。Epoch3授权矩阵已逐文件核实，master governing diff为空。
- Epoch3的FAIL / DIVERGING与反例原样保留，不改判PROGRESSING。本次人裁定之前禁止自动继续repair。
- 控制器向人提出实质不同策略：自身contact只豁免历史重复、必须重新验证全部当前具体邮箱资格；锁后检查根revision和完整作者键；不存在与读取失败分离；身份补证/统计必要响应结构失败关闭；expected绑定每个现存层真实版本、无执行证明不认自身续跑；完整姓名不等于proof、EMAIL_INVALID不等于可发送；先实际三入口/共享真库/真实guard/假SMTP辨别测试。性能另列；Initial的0L外键问题另求人裁定；V-28明确增列Writer，不沿旧授权反复打补丁。
- 人源回复原文：**「好的 你来更新repair吧」**。此批准不同策略及同路径规划，解除DIVERGING的规划停点；未批准执行本草案、修FK、改schema或降低master门槛。没有未解决的本8项授权冲突。FK另为NEW_SCOPE_REQUIRED前置决策；它阻塞完整B-2正向证明，不阻塞本8项规划与可独立验证的修复。
- 未来明确批准本计划后，产品授权从上版33文件收敛为28个现有文件：删除三caller产品、维护controller产品、两CLI产品及replay测试等7个无必要修改入口，明确加入Writer/WriterTest两文件。原表外Writer由本次人裁定及01/02原合同支撑；新草案须再经execute-p批准才可实施。

## Findings in Scope

| Finding | Severity / disposition | Requirement | Evidenced root cause / lineage |
|---|---|---|---|
| V-27 | P1 / REPAIRABLE；NEW REGRESSION | master I-7/I-8；09 I-3；原repair R-1/R-3 | P3E/P3F：自身contact引起ALREADY_CONTACTED早退，guard豁免同时漏掉尚未执行的具体邮箱资格；当前NEEDS_REVIEW仍实际fake SMTP=1。 |
| V-3 | P1 / REPAIRABLE；PERSISTENT | master I-4；04 I-2 | P3J：service锁前校验与repository锁后提交脱节，两个不同OpenAlex键并发挂同一根。顺序全名/成员校验已修，不能回退。 |
| V-5 | P1 / REPAIRABLE；PERSISTENT | master I-4/I-7/I-8；08 I-1/I-3 | P3A：RAW失败被当空层，其他层有文档即放行；当前全组事实仍不完整。 |
| V-28 | P1 / REPAIRABLE；NEW（累计边界中新发现） | master I-3；01 I-1/I-2；02 I-2 | P3G：Writer唯一性搜索timeout/failed shard仍被接受为唯一doc并写proof。不得误写成第二轮repair新增回归。 |
| V-13 | P1 / REPAIRABLE；PERSISTENT | master I-6/I-8；06 I-3 | P3B：聚合缺aggregations/bucket/buckets仍返回合法空桶。已修count/hits/SQL错误必须保持。 |
| V-14 | P1 / REPAIRABLE；PERSISTENT | master I-4/I-8；10 I-1；原repair R-6 | P3C/P3D：第三方同形登记被认自身续跑且未import却称true；非RAW seqNo/primaryTerm未进expected。 |
| V-29 | P1 / REPAIRABLE；NEW（累计边界中新发现） | master I-3/I-8；01 I-3；02 I-3 | P3H：混合姓名层恢复把完整姓名但无proof层标ALREADY_VERIFIED，整体REPAIRED；与已修V-19区分。 |
| V-12 | P1 / REPAIRABLE；PERSISTENT | master I-5/I-7；06 I-2；07 I-1/S-2 | P3I：当前EMAIL_INVALID仍投影ADMITTED，既有renderer显示“可发送”；stale→UNKNOWN原修复保留。 |

## Findings Excluded

| Finding / item | Reason / disposition |
|---|---|
| V-1,V-2,V-4,V-6,V-7,V-8,V-9,V-10,V-11,V-15,V-16,V-17,V-18,V-19,V-20,V-22,V-25,V-26 | EXCLUDED：Epoch3原根因RESOLVED；仅作回归约束，不重新开缺陷任务。V-26自阻断解决不代表V-27资格旁路合格。 |
| V-21,V-23,V-24 | EXCLUDED：非mandatory P2（名称/可选覆盖/注释），无人工扩大授权。 |
| B-1 | EXCLUDED as product repair：真实形状51,829条、当前代码p95≤2s/额外heap≤128MiB缺证；仍BLOCKED。允许复用/核对合法既有证据，不造数据、不改预算、不自动做性能优化。 |
| B-2 | EXCLUDED as product repair：三实际入口完整健康/竞争/接受后异常证据缺口，仍BLOCKED。下述R-1只对确证V-27安排直接相关回归，另如实采集B-2正向结果；缺证不是第9个产品修复任务。 |
| Initial attemptId=0L FK缺陷 | EXCLUDED / NEW_SCOPE_REQUIRED：master base已有；不是V-27，不准顺手修、mock业务持久化、删除FK或接受异常当健康完成。另见前置人决策。 |
| 其他Observation、环境记账、未发现错误的可选矩阵 | EXCLUDED：不修无关模块、时区、旧标题、知识文档或原历史证据。 |

## Unchanged Contract

- 仅本次当前discovered范围；冻结名单仍须显式授权。A1保留：10,686是参考、03不执行，161待核组/两个根不自动合并，不承诺恢复收益。
- 原docId、业务键、邮箱、contact/alias/邮件记录、运营/人工/学术事实不覆盖；不猜姓名、不伪人工认可、不造contact解锁、不删历史解锁。仅04写人物关系、09写首发保护；不改SSOT准入规则、来源核验/预算/cookie/私有审计。
- 身份、准入、具体邮箱发送资格互相独立。当前完整组硬保护、具体地址原scope/验证/抑制全部保留；自身行豁免只能作用于历史重复。SAFE_RETRY仍只原地址；合格primary优先、跨页共享人物集合不回退。
- 保留默认关闭旧行为、关闭不撤销既有保护；REQUIRES_NEW持久SENDING、成功原事务SENT联动、UNKNOWN不自动重发、未调用SMTP的受限清理。ES/MySQL/SMTP不是单一事务。
- 保留count/hits/SQL失败明确不可用、原深分页与范围外单位、memberCount、两宿主链接/转义/异步隔离。后端沿用已有DTO/状态值；不改app.js/index.html/CSS/缓存键。
- 不新增文件、迁移、schema/ES mapping或服务；现有私有类型/方法参数可为本任务有限调整。SEAM只准因授权源码移动同步精确噪声path/line/context，不增真实写入口、不删除断言/失效排除校验。
- 无生产SMTP、生产apply/迁移/开关操作、删除、清队列、部署、push、merge、amend或历史改写。只用临时MySQL、假SMTP、合法匿名夹具。Master/fast/review报告只读；执行时唯一额外证据写入见末尾handoff。

## Authorized Files

本表28个现有文件是未来批准后的精确产品/测试边界，不授权同目录其他文件。文件ID仅为路径别名；每任务按ID明确引用本表精确路径。仅为运行测试而读取其他文件不产生写授权。未实际需要改动的授权文件保持不变。

| File ID | Exact file | Purpose / authority |
|---|---|---|
| G | src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt | R-1：自身历史归因与当前邮箱许可分离；09/A7 |
| GT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt | R-1/R-3：真实三入口、共享MySQL、真实guard回归；09/A7 |
| INITT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt | R-1：实际Initial调用契约及撤销资格回归；09/A7；不授权Initial产品/FK修改 |
| MIT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | R-1：实际ManualInitial调用契约及撤销资格回归；08/A6、09/A7 |
| MEMT | src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt | R-1：实际ManualExpert调用契约及撤销资格回归；09/A7 |
| P | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt | R-2：校验快照与锁后revision衔接；04/A2 |
| IR | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt | R-4/R-7：不完整唯一性禁止补证、现存层目标proof闭合；01/02/05/A3 |
| R | src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt | R-2：锁内核验完整根及条件提交；04/A2 |
| SEL | src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt | R-1/R-3：当前具体邮箱许可、分层读取失败传播；08/A6 |
| PT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt | R-2：实际服务并发交错与重读回归；04/A2 |
| IRT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt | R-4/R-7：真实Writer边界下零写及混合姓名层回归；01/02/05/A3 |
| RT | src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt | R-2：真实事务仓储不相容挂接回归；04/A2 |
| SELT | src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt | R-1/R-3：自身contact与当前许可、失败/不存在对照；08/A6 |
| REC | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt | R-4/R-7：唯一性失败传播及同申请目标续跑完成条件；02/05/A3 |
| RECT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt | R-4/R-7：preview/apply/续跑辨别回归；02/05/A3 |
| MAINT | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt | R-6：全层实际版本expected、无归属证据不得假续跑；10/A8 |
| MAT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt | R-6：独立登记与非RAW版本变化反例；10/A8 |
| MCT | src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt | R-6：现有HTTP请求/响应的保守拒绝和版本摘要往返；10/A8；仅测试 |
| CPT | tools/discovery-person-recovery/test_reconcile.py | R-6：现有CLI审计保留全层版本、失败非完成；10/A8；仅测试 |
| Q | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt | R-5/R-8：既有邮箱展示投影不把EMAIL_INVALID标作许可；06/A4、07/A5 |
| ES | src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt | R-3/R-5：人物读取失败和必要聚合结构完整性；06/A4 |
| QT | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt | R-5/R-8：统计不可用及已知邮箱异常投影；06/A4 |
| EST | src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt | R-3/R-5：分层批读与聚合响应辨别性夹具；06/A4 |
| ECT | src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt | R-5：ES列表统计错误传播，不伪200空结果；06/A4 |
| CCT | src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt | R-5：DB列表人物统计错误传播；06/A4 |
| SEAM | src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt | R-5/R-8：仅授权源码移动必要的精确噪声path/line/context同步；保留V-25闭包 |
| W | src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt | R-4/R-7：唯一性响应完整性及现有proof CAS；01/02；明确新增至本修订授权表 |
| WT | src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt | R-4/R-7：真实HTTP响应/局部proof CAS回归；01/02；明确新增至本修订授权表 |

三caller产品文件（InitialOutreachService.kt、ManualInitialOutreachService.kt、ManualExpertMailService.kt）、ManualOutreachTxHelper.kt、维护controller产品、两CLI产品及domain/schema均不在表内。现有caller已经传实际contact/attempt归因，本轮在guard/recipient闭合许可，实际caller测试检验接线；若确有必要新增产品修改，带具体证据停在该项请求范围决定，不自行扩表。

## Repair Tasks

8个任务按根因分组，先写能暴露当前反例的测试，再改行为；先完成R-1实际入口测试骨架及首发资格边界，再R-2→R-3→R-4→R-5→R-6→R-7→R-8。共享文件顺序接续，不覆盖已有修复。每项均受Unchanged Contract限制；不以增加相似单测替代实际反例。C命令下一节完整展开。

### R-1: 自身历史豁免不得跳过当前具体邮箱许可

- Resolves: V-27（真实repair回归）。
- Root cause: Recipient:235先ALREADY_CONTACTED返回；Guard:538–558的ownContactOnly跳过剩余选址/许可。已验证自身contact的所有权并不证明当前邮箱可发。
- Files: `G`, `SEL`, `GT`, `SELT`, `INITT`, `MIT`, `MEMT`。
- Change: 从服务端真实归因证明中，仅剔除本次绑定contact/attempt的历史重复影响；仍执行完整组当前事实和硬保护，并对ticket的具体地址重跑当前准入、原scope、验证、抑制及地址一致性。own-contact分支必须产生与普通合格路径同等的具体邮箱资格证据，不能把ALREADY_CONTACTED理由列表当成其他资格通过证据。实际接口边界兼容现有三caller，不改其产品代码。
- Discriminating regression: 将P3E/P3F固化：reserve合格→本次NEW contact→compose前后将当前准入改NEEDS_REVIEW，真实Initial/ManualInitial/ManualExpert入口、同一真实MySQL体系、实际guard/recipient/selector/事务/相关仓储、假SMTP；三入口各资格撤销时SMTP=0，且无错误SENT。用同一边界验证抑制/验证失效不因自身contact而跳过，safe retry资格撤销不得换址。该必需负向回归不是“所有条件都阻断”的伪修复：实际recipient+guard未撤销对照必须获得claim；真实入口许可对照记录SMTP恰1及后续真实结果。Initial许可对照若命中已证FK，只作为“SMTP已发生、业务失败”证据，严格断言该已知因果链和持久SENDING，不称健康完成。
- Preserve/test: 冒用或其他contact/attempt、既有SENT/UNKNOWN/SENDING仍阻断；实际三个入口接受后业务事务失败/不明结果，跨另两入口再次请求SMTP累计仍1，须检查持久保护而非mock guard。原正常路径不得退回V-26自阻断，已证明未交付才允许原址SAFE_RETRY。现有误名healthy测试改为准确的接受后持久化失败用例，并将宽泛catch改成已知异常/约束与状态的精确断言；不吞未知异常。
- Existing verification: C02,C16,C17,C21,C22；B-2独立正向实验E01另列，不混作本项通过证据。
- Must not change: 真实身份归因、原业务事务、旧资格SSOT及全部既有保护；保留R-1负例后续状态可解释。
- Prohibited: 删除contact、伪造attemptId=0对应行、mock被验证guard/事务/业务持久化、吞异常、全局忽略历史、跳过具体资格、修FK、用SMTP≤1且全零代替许可正向对照。
- Machine completion: P3E/P3F及三个真实入口撤销资格均拒绝；许可的真实recipient/guard对照可claim，入口SMTP/事务结果分开记录；未知/已接受不能再发。Initial完整健康事务仍按B-2/FK状态保留，不要求本任务越权修复。

### R-2: 已有根在锁后仍满足同类作者键和原revision约束

- Resolves: V-3。
- Root cause: PersonService:113/371–388锁前已验证事实未绑定Repository:115–137锁后状态；A/B不同OpenAlex互不触发唯一键冲突却可同根提交。
- Files: `P`, `R`, `PT`, `RT`。
- Change: 把已核验根的revision/完整成员与作者键依据绑定到挂接决策；锁定根后重读当前完整键并验证条件，原revision变化不得依据旧结果直接写。冲突后沿现有有限重试重新读取全部当前事实，再决定STALE/NEEDS_REVIEW或合法提交。保持每一类别唯一可信作者键及完整规范全名/现存成员一致；不把CAS失败当成功。
- Discriminating regression: P3J原交错真实MySQL+实际service/repository：根仅ORCID；A含A100校验后暂停，B含A200提交，A继续。最终不得同根有A100/A200；失配方无新成员/键半提交且返回明确结果。相容同键对照可正常挂接；调度仅在真实调用边界安排，不以mock APPLIED/冲突替代数据库锁。检验revision与实际插入一致，原成员未改。
- Existing verification: C07,C08,C10,C11,C18,C21。
- Must not change: 原顺序全名、旧成员当前标签/指纹验证、稳定UUID/作者键唯一、排序锁与有限冲突重读；两个根仍待核。
- Prohibited: 全局锁、禁用全部挂接、把每类冲突仅作日志、迁移/schema变更或伪造服务事实。
- Machine completion: P3J无法产生冲突根；真事务相容对照通过；失败无部分写入，原解决V-2/V-22不回归。

### R-3: 分层读取明确区分不存在与失败

- Resolves: V-5。
- Root cause: Recipient:323吞异常为空层；其他层存在即通过完整组核验。Search的批读接口也不能把必需响应失败伪装为合法缺档。
- Files: `SEL`, `SELT`, `ES`, `EST`, `GT`。
- Change: 当前完整组读取保留各层“成功/明确不存在/失败或不完整”语义；任一必需层查询失败、缺必要结构/条目或条目错误时整组不可验证，不从其他层/缓存替代。合法found=false与失败分开；原本允许缺失的层不被强制创建。对共享Search方法限定人物调用所需的严格读取边界，非人物旧API合同不扩大。
- Discriminating regression: P3A中RAW抛错而另两层有效必须排除；真实HTTP批读夹具的错误/不完整响应同样不可许可；对照为完整响应明确found=false、其余合法现存层，可按原合同判断。estimate/selection/reserve/claim均经当前同源结果；claim前再失读时SMTP=0，日志/排除状态可区分无记录与未知。
- Existing verification: C12,C16,C17,C21,C22。
- Must not change: 当前层选址、全组隐藏HOLD/冲突保护、inBatch必须重读、原标签/指纹检查及范围外零写。
- Prohibited: runCatching失败默认empty/null后许可、只读当前页、回退缓存、靠一层存在推断其他读取成功、无差别阻断合法缺层。
- Machine completion: P3A不再included；异常传播明确且合法缺层对照保持；持久化与发送零越权。

### R-4: 唯一性搜索未完整结束不得签发proof

- Resolves: V-28（累计边界新发现，非声明repair回归）。
- Root cause: Writer:727–749仅解析hits，Repair:72–89/Recovery:244把不完整结果当全集。
- Files: `W`, `WT`, `IR`, `IRT`, `REC`, `RECT`。
- Change: Writer的该唯一性用途搜索必须确认正常完整结束并有必要结构；timeout、failed shard、畸形/丢失必要hit身份/层信息、无法证明返回集合完整均明确不可用，不能悄悄mapNotNull收窄成一个doc。完整零命中与失败区分；保留现有命中上限下的保守拒绝。Repair/Recovery遇不可用不得补姓名/proof、登记或准入，不产生REPAIRED/APPLIED；返回已有失败/可恢复语义。
- Discriminating regression: P3G实际Writer HTTP返回timeout=true/failed=1且唯一可见a，Repair与Recovery关键入口均零proof写、零关系/准入成功；完整同doc跨层命中正向仍能原CAS补证，两个doc仍CONFLICT，合法零命中不冒充失败。缺必要结构作为同一完整性门禁的最小对照，不开无关ES矩阵。
- Existing verification: C03,C04,C05,C10,C21。
- Must not change: 真实docId、来源唯一对应、三层CAS、来源编号/JATS绑定、既有姓名/人工事实不可覆盖，未存在层不新建。
- Prohibited: 仅加强调用方计数但仍信partial search、从一个可见hit推唯一、放宽proof、改ES mapping/通用SMTP/新schema。
- Machine completion: P3G在任何proof写前失败关闭；完整唯一命中对照仍成功；错误具备可审计原因。

### R-5: 精确聚合必须具备必要桶结构

- Resolves: V-13。
- Root cause: Search:1406–1421缺聚合结构时MissingNode.map输出[]。
- Files: `ES`, `EST`, `Q`, `QT`, `ECT`, `CCT`, `SEAM`。
- Change: 人物查询所用聚合除已有完整性检查外，要求aggregations/bucket/buckets类型及被消费桶的必要key/doc_count结构有效，缺失/畸形明确查询不可用；合法空数组仍为零。通过实际Query和两列表消费链传播，不把失败转为200精确空桶/零人。共享Search变更保持非人物既有调用合同，必要时显式区分严格调用。
- Discriminating regression: P3B原响应经实际Search及人物统计调用链不可作为合法[]；正常空buckets返回空，正常计数/标签/地区/域名仍正确。沿既有controller测试验证失败外显；不添加新UI。
- Existing verification: C12,C21,C22。
- Must not change: 已修count/hits/scroll/SQL完整性、深页分块、原筛选语义和范围外计量单位；真实写入口白名单不变。
- Prohibited: 猜零、近似计数、只当前页去重、放宽必要结构或调整共享mapping、扩大SEAM白名单。
- Machine completion: P3B明确失败、合法空统计正常；两入口不伪精确成功；精确守卫通过。

### R-6: 完整expected及可证明归属决定维护结果

- Resolves: V-14。
- Root cause: Maintenance:309–365用同根/同成员/revision0猜自身续跑；:596只绑定source.updatedAt及RAW版本。
- Files: `MAINT`, `MAT`, `MCT`, `CPT`。
- Change: 使用现有layerVersions及expected摘要表达每个现存RAW/CANDIDATE/APPLICATION真实seqNo/primaryTerm（并保留既有受保护事实），层存在集合也进入比较；不把updatedAt作为ES版本替代。保留当前SQL根/成员revision、proof/标签/身份、人工准入/历史保护读取与复核。缺本次执行的可信归属凭据时，不得根据同形目标状态推断自己执行或protectionImported；返回既有CHANGED_OR_FAILED/明确变化原因，要求原流程重新预览。不得归零第三方变化后“复原”预期以假认完成。本轮不新增receipt持久化/schema；现有接口无可信凭据的响应丢失重试应保守拒绝，不能凭客户端声明证明归属。
- Discriminating regression: P3C预览→独立登记同a,b→旧apply不得UNCHANGED/RESUMED_TARGET_STATE或protectionImported=true，且不新写；P3D仅改CANDIDATE或APPLICATION seqNo/primaryTerm、source/updatedAt不变，旧expected必须拒绝。完整未变预览的真实apply仍成功；返回protectionImported必须来自实际成功的09导入结果。通过现有controller和CLI测试确认版本摘要/审计字段保留、变化结果不计成功，重新预览后可按新事实执行；不要求修改这些产品接口。
- Existing verification: C18,C19,C17,C21。
- Must not change: 冻结子集51→50+1、互斥分母、cookie/私有审计、同库TransactionTemplate整组原子性、历史不变、rollback expectedRevision规则。
- Prohibited: 新建schema/receipt表、扩大HTTP/CLI产品范围、revision0即归属、抹去保护字段再比对、未知import写true、执行实际rollback或弱化expected。
- Machine completion: P3C/P3D被拒且零新关系/保护写；正常新预览apply成立；existing rollback与原子导入测试通过。缺归属证据的旧请求保守停止是允许行为，不虚构无凭据幂等完成。

### R-7: 完成必须覆盖所有现存层的目标proof

- Resolves: V-29（累计边界新发现）。
- Root cause: Repair:256把完整姓名直接当ALREADY_VERIFIED；Recovery混合姓名场景把上游结果当完整完成。
- Files: `IR`, `IRT`, `REC`, `RECT`, `W`, `WT`。
- Change: 混合姓名层的同一已授权恢复中，逐现存层判断目标姓名及目标proof；完整姓名但缺proof层仍须按现有版本CAS补proof，不能覆盖非空姓名/第三方proof。仅当前层真实已等于本次目标才ALREADY_VERIFIED；整体REPAIRED/APPLIED必须所有应完成现存层达到目标且原登记/准入后续完成。中途失败明确PARTIAL/冲突并保留原申请可验证续跑；不得因上一轮已补姓名而把同申请剩余目标proof的工作永久路由离开。初次全名仅缺proof的01路由保持。
- Discriminating regression: P3H RAW缺given无proof、CANDIDATE/APPLICATION完整相同姓名无proof，实际Writer+Repair/Recovery链最终各现存层目标proof齐全、名字/原ID不变；对其中一个层注入版本冲突，首次不得完整成功，解除后原目标续跑不重写已完成层且闭合登记/准入。第三种proof/姓名冲突仍停止，不从旧目标扩大为任意proof覆盖。
- Existing verification: C03,C04,C05,C06,C10,C21。
- Must not change: V-4准入失败PARTIAL、V-19原目标proof续跑、V-20来源绑定、预算/保护读取失败关闭、未存在层不新建。
- Prohibited: 全名等于已验证、仅RAW成功判总体成功、伪造proof、改学术事实、删除失败层/审计或修改CLI终态来掩盖服务完成错误。
- Machine completion: P3H不再存在null proof的ALREADY_VERIFIED；整体成功逐层可核对，PARTIAL可安全续跑，第三方冲突零覆盖。

### R-8: 已知邮箱不合格不得投影为可发送

- Resolves: V-12。
- Root cause: Query:279–285/367–382已读取EMAIL_INVALID却未参与许可投影。
- Files: `Q`, `QT`, `SEAM`。
- Change: 现有逐邮箱后端投影综合当前已知邮箱验证/运营异常及必需真值，明确EMAIL_INVALID或真值不可得不得输出会被现renderer解释为可发送的ADMITTED；沿用已有非许可状态，保留独立operatorStatus/contactStatus/admission事实语义，不改持久V148准入结论。现有真实selector/状态服务为真值，不再复制一套准入规则。
- Discriminating regression: P3I实际Query+BatchSelector，准入AUTO_PASSED、非stale、未抑制但当前contact EMAIL_INVALID，返回非许可状态；有效邮箱对照保持正确、stale/未知/HOLD/抑制仍不许可，持久准入不变。两现有详情宿主在既有DTO下不出现可发送误标。
- Existing verification: C12,C13,C14,C15,C21,C22。
- Must not change: 原docId/contactId链接、memberCount、前端两宿主布局/缓存合同、只读GET与逐邮箱独立状态。
- Prohibited: 改app.js或新增状态、把持久准入直接改拒绝、用另一个成员资格代替、扩大operatorStatus真实写入口。
- Machine completion: P3I不再ADMITTED；有效对照通过；当前JS测试及精确守卫通过，持久事实零改动。

## Evidence Prerequisite — B-1 / B-2 / Initial FK

这不是新增修复任务，也不是对master门槛的豁免。

1. B-1：当前实现的51,829真实形状匿名数据性能报告仍欠缺。小样本、常量、旧代码测量均不替代。若没有现成合法输入/报告，handoff明确BLOCKED；不得为了本修复完成而虚构结果或增性能产品改造。
2. B-2：最终仍须三个实际入口各健康首发恰1且原成功业务事务完成、同一真库同人跨入口竞争总数恰1、接受后业务失败/未知跨入口不再发。R-1真实资格撤销0与保护异常测试可补部分证据，但不替代健康正向。既有Initial测试宽泛catch不是健康通过。
3. 精确前置证据：当前 `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:180–184`调用 `ManualOutreachTxHelper.recordSuccess(... attemptId=0L)`；helper:35/85的非空attemptId直接成为mail_record.mail_send_attempt_id，触发`fk_mail_record_send_attempt`。Epoch3 P3F及C17实际库均保留DbActionExecutionException，SMTP已经1次，SENDING保持。base `9d353934b82ed3e36a193c70748e5efccb3d9e82`同文件原118行已传0L；故属于无关既有缺陷，不纳入8个P1。
4. 单独需人裁定的最小候选范围（**未授权**）：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt`、`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt`。候选目标为真实“无attempt”语义与成功持久化契约一致：现有MailRecord.mailSendAttemptId为Long?，可评估显式null及helper仅对存在attempt更新；不能将0伪造成有效记录。该方案涉及共享helper，须另行明确批准并评估已有ManualInitial有效attempt行为，不能凭本计划执行。这里只列决策边界，不新增第二份repair或默认拆分执行。
5. 本次用户批准的是先独立修8项、保留B-2阻塞的策略。此草案执行批准也不会顺带批准FK候选范围。若将来另批FK，独立验证其准确提交边界后才能用完整健康结果关闭B-2；本草案不预先断言该修复足够。

## Verification Commands

本轮仅规划，未重新运行测试。Epoch3的21条验收命令均首次exit0（C21 JUnit5085/0F/0E/20S、JS1680全过），仅为已审基线，不能证明下次实现。

未来执行分两类，不能混淆：

- **修复验收必需PASS集合**：C01—C19、C21、C22，共21条；最终修复边界新鲜执行，Maven串行。每条保留真实exit/测试计数/skips/失败及重跑日志。真MySQL必需用例须实际执行，不能让C21条件skip代替C07/C09/C11/C17。既有强制用例不能新加条件跳过；不得吞错或放宽断言以使命令绿。
- **必需采集、非本8项PASS门槛**：E01的B-2完整健康/竞争诊断；真实失败必须原样记录BLOCKED，不因exit非零把已独立完成的8项重新定义为未执行，也不把它当PASS。新B-2正向病例仅通过显式b2Acceptance开关隔离，必须实际执行E01且报告skips；开关不得用于R-1强制辨别回归或已有测试。若E01暴露本8项相关新失败而非已知FK/缺证，仍属于修复未完成，不得排除。

C20保留为已批准的旧Zulu11.0.15/tzdb2021e例外，不要求执行；C21现有Zulu11.0.32.1履行JDK11 clean package。C01—C19若出现同一时区环境限制，仅可用C21 JAVA_HOME对照并保留原失败，不改时区产品/删用例。临时Docker、假SMTP授权只限测试。以下全部在指定worktree运行：

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

- E01: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Db2Acceptance=true '-Dtest=DiscoveryPersonOutreachGuardServiceTest#b2HealthyInitial+b2HealthyManualInitial+b2HealthyManualExpert+b2SamePersonCompetition' test`

E01四个明确方法在既有GT文件中承载R-1许可对照的完整业务验收实验：各方法调用对应实际入口/真实guard及业务仓储/事务，SMTP替身只记录交付；前三项分别独立健康完成，第四项三入口共享同一个真实MySQL/person并竞争。断言必须包括SMTP精确次数和原业务成功持久化，不捕获后继续报告成功。已知Initial FK预期使其完整健康项失败；保留异常链/库约束/SMTP与保护状态，不改变生产逻辑绕过。C17仍执行所有R-1实际入口资格撤销、实际guard许可对照和接受后失败保护回归。E01不承担产品修复、不授予表外修改；不得为了让它绿构造id=0历史行或省略真实helper。

## Completion Criteria

- 8项included P1、8个根因任务均有对应机器辨别证据，P3A—P3J相关反例不再成立；所有RESOLVED根因保持，无本边界已知缺陷未处理。正常许可claim与实际SMTP对照不能被全阻断替代。
- C01—C19/C21/C22在最终可辨认产品边界通过；全部失败/重跑/skips完整保留。C20例外仅覆盖既有环境，不覆盖产品缺陷、B-1/B-2或FK。
- E01实际执行并如实逐项记录结果。若只因已知FK导致B-2完整正向失败，而本8项及21条修复验收通过，可对本有界修复提交并输出 `READY_FOR_VERIFICATION`，同时明确 `B-1: BLOCKED`、`B-2: BLOCKED — Initial FK prerequisite / remaining evidence`。不得输出master PASS、完整端到端PASS或READY_TO_INTEGRATE。其他非已知失败需按证据分类，不能统一归FK。
- 新产品/测试diff为28文件集合子集、每hunk对应本任务；没有schema/新文件/无关FK修复。执行前重新确认worktree/branch、产品3b6f854 ancestry、master及amendment身份和已有diff归属；若外部产品变化，保留并报告，不reset/stash覆盖、不把混合工作区结果写成精确提交证据。
- 允许独立修复完成与master门禁仍未满足同时存在：前者仅送独立复审，后者还需B-1/B-2充分证据、全master aggregate机器PASS及同code SHA人源验收。人工状态仍PENDING。
- 不产生自动下一轮、continuation、并行current repair或另一个产品计划。只有后续显式execute-p授权才实施。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
本次「好的 你来更新repair吧」仅授权上述策略与草案更新。旧hash的execute-p不能授权本修订。后续人源明确调用以下精确路径，才批准该时刻当前内容及下列有限交付：

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md`

无需把B-1/B-2变成已通过才能批准此有界修复；也不得把该批准误读为FK范围批准或降低master最终验收标准。

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md` invocation authorizes:

1. Only the 28 Authorized Files and the required verification/evidence commands in this plan. The Initial FK proposed boundary remains unapproved even where its test files overlap this table.
2. After all eight repair tasks and all 21 required PASS commands succeed, and E01 evidence is collected/classified under Completion Criteria, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only in-scope changes in Authorized Files. Product commit subject: `fix(discovery): enforce current eligibility and identity write contracts`. Known B-1/B-2 blockers remain explicitly recorded; E01 known-FK failure is not relabeled success.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/review/2026-10-13-discovery-identity-recovery-master/repair-execution.md` with exact human approval source, repair path/hash, pre/post code SHAs, evidence parent, authorized changed files, every command/exit/count/skip, all E01 failures and B-1/B-2/FK status, deviations, executor identity when exposed, and clean product/index evidence. Record honest unrelated dirty state if present. This is the only extra authorized execution-evidence file.
4. Exactly one docs-only evidence commit containing only that execution handoff. Evidence commit subject: `docs(review-fast-p): record identity contract repair execution`.
5. Returning to the already authorized review-fast-p aggregate re-review in the same task when the human invocation requests it. Aggregate review remains full `9d353934b82ed3e36a193c70748e5efccb3d9e82..new committed code head`, not only repair delta; preserve stable finding IDs and prior epochs.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, FK repair, schema change, or product repair beyond this plan. Executor does not edit master/fast evidence/review ledger/machine-verification/manual-acceptance/final-decision. Evidence commits may advance HEAD since planning; verify exact product ancestry and record actual SHAs, not the stale planning HEAD. Drafting authorizes no implementation or commit.

## Self-Review

- 8个current P1均REPAIRABLE，8任务逐项根因/精确文件/辨别回归/命令/禁止项/完成标准齐全；V-27为新回归，V-28/V-29仅为累计边界新发现，stable IDs不改。
- 28文件全部现存；原33删7加Writer/WriterTest两项。必要测试覆盖实际caller；产品caller/FK/helper、CLI、controller、schema均未偷偷扩权。SEAM仅必要精确位置维护。
- B-1/B-2、无关既有FK、optional P2和resolved任务排除；B-2采集与R-1确证回归分开。21条必需修复验收PASS与E01真实B-2失败可并存，READY_FOR_VERIFICATION不声称master合格；没有排除项反向成为本8项的无条件成功前提。
- 人裁定解除的是DIVERGING规划停点；未改写Epoch3结论，也未复用旧执行批准。无额外未决本8项authority conflict；FK需另行人源范围决策。
- 仅一个当前repair.md，后续一次有界产品commit与一次durable handoff commit；没有自动修复循环。本轮只读源码和既有报告，未执行产品修改或验证测试。
