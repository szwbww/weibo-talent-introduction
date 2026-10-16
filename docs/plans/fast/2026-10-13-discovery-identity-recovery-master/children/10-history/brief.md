# Child Brief — 10-history（仅新发现历史人物合并与对账）

- Child ID: `10-history`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-13/discovery-person-10-history.md`（此 worktree 内已提交于 36075ec；**先完整阅读它，它是唯一权威合同**）。依据审计：`docs/audits/2026-10-13-discovered-person-duplicates/report.md`（已随 36075ec 提交，只读）。
- Master plan（全局合同）: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`（I-4/I-5/I-8；数据应用另属实施阶段，**本轮未获应用指令**）。
- child_base_sha: `657849948e400b054eb5b3c0c8d91c1e10260342`（09-send-guard 的 Code head，已 LIGHT_PASS_WITH_NOTES 验证）。
- 起始工作树 HEAD（开始前必须核对且 `git status` 干净）: 以 dispatch 消息给出的 `docs(fast-p): prepare child 10-history` 提交 SHA 为准（含前序 fast-p 记账提交 docs/plans/fast/**，属 controller 记账，不得回退、不得纳入你的提交）。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 要求使用 `execute-p` 技能执行本子计划。

## 前置已完成（可直接引用）
- 04-registry：`DiscoveryPersonService.register`（事务登记、结果枚举）+ 读查询；V156 三表。
- 09-send-guard：`DiscoveryPersonOutreachGuardService`（历史保护导入的唯一写路径：reserve/markSent/markUnknown/stateOf/`ReservationRow`）。
- 02-replay：回源能力（只可服务 discovered 子名单）。
- 05-ingestion：冻结后新增 discovered 由其增量接管（本计划只列差额，不混入旧分母）。

## Authorized files（精确 6 个；如需第 7 个文件先停止并返回 PLAN_CONFLICT）

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceController.kt` | 新增，沿用现有管理入口访问控制 |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt` | 新增 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt` | 新增 |
| 5 | `tools/discovery-person-recovery/reconcile.py` | 新增 |
| 6 | `tools/discovery-person-recovery/test_reconcile.py` | 新增 |

## Required commands（JDK 11 前缀不可省略；记录退出码与精确计数）
- C1：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test`
- C2（Python unittest）：`python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'`（若发现机制不适用改用可运行等价形式并记录实际命令）
- 每次 `mvn test` 自动执行 JS 门禁；失败数必须为 0（基线 1677/1677/0）。

## 必须兑现的合同要点（以子计划正文为准；这里是索引）
- I-1 冻结范围并实时复核：仅处理审计冻结的 51,829 个 discovered 原 docId；apply 重新读取三层、标签、身份证据、seq_no/primary_term、SQL 关系 revision、人工决定及历史引用；变更、范围外关联或不完整读取均跳过并报告，不使用快照强覆盖；冻结后新增 discovered 由 05 增量接管，另列差额，不混入旧分母。
- I-2 候选不等于已核准：3,741 组/8,426 档案只是高置信候选，4,685 是最大理论减少展示数；161 组/340 条保持独立待核；既有有效逐邮箱绑定凭证和可信作者 ID 是合并必要条件；有冲突/缺证先回源，不能仅因名字相同批准；应用每组所有成员检查完整后**一次提交**关系，失败组不部分合并。
- I-3 历史只关联不重写：旧 docId/contactId/mail_record/别名/人工决定均不改；创建人物关系并通过 09 导入已有成功/未知首发保护；多个联系人、发件人绑定或人工资料冲突不得选一覆盖其余；无法确定安全行为的组留待核；回滚只生成关系恢复提案，已有后续成员/发信/版本变化时禁止自动拆组并释放首发保护。
- 实现步骤（详见计划 1-6）：①只读预览（maintenance service/controller + 本地 CLI；preview 接受冻结 ID/候选组标识，服务端自行读取证据，不接受客户端"可信作者结论"；输出每条互斥主类 `MERGE_READY/REVIEW_REQUIRED/KEEP_SINGLE/CHANGED_OR_FAILED` 及每个邮箱当前准入/禁发原因，"可以合并"与"可以发送"分别判断；缺绑定证据者可一对一登记单例，但不可创建可信作者键或提高准入）；②复核材料（逐组保存成员 ID、邮箱、可信作者键、证据 source/hash/version、来源定位、层级、前后人物关系、三层版本、SQL revision、历史保护判断和原因；需要回源者以 02 能力只处理 discovered 子名单；回源失败留 REVIEW_REQUIRED）；③受限应用（apply 只接冻结 ID、规则版本、预览摘要及 expected 指纹，不接任意目标字段；服务端重复校验后以同库事务登记整组/导入历史保护；ES 不改；先 50 组试点由人工执行；幂等同请求返回 UNCHANGED）；④幂等与审计（CLI 落盘先原子写临时文件再 rename；保存各组结果和断点；响应未知先查当前关系；日志落盘失败立即停止；输出目录 0700、个人字段文件 0600；不往仓库公开报告粘贴全邮箱名单）；⑤回滚与增量核对（rollback-preview 生成逐组恢复提案及受影响人物/保护，不提供无条件生产回滚按钮）；⑥最终交付（互斥逐条 JSONL/CSV、旧 docId→personId→邮箱映射、处理组数/人物数/成员数/邮件可用状态、实际减少展示数、失败/变化/待核原因、非目标写入审计、前后查询对账、回滚提案；51,829 条每条恰好一个处置结果）。
- 边界：**本轮不执行任何生产应用/数据修改**（数据应用另属实施阶段且未获指令）；ES/联系人/邮件只读；不新增业务字段；无生产发送；`docs/plans/fast/**` 不得进入你的提交。
- 测试：用脱敏固定小样本覆盖每个主类及并发/中断；全量预览不是自动执行授权（CLI 默认 preview；apply 需显式参数+预览摘要+指纹）。

## 下游接口
- 最终交付物为实施阶段准备的产物（映射/报告/回滚提案），本 epoch 只需实现与测试，不生成生产名单产物。

## 报告与提交
- 完整结果写入：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/children/10-history/execution.md`（逐命令退出码/计数（含 C2 实际命令），改动摘要，未决问题）。
- 本地提交仅含 6 个授权文件：`feat(fast-p): implement 10-history`。
- 返回仅限：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不审查后续子计划、不修复无关行为、不 push/merge/改写历史；遇到计划未覆盖的行为决策或需要新文件：返回 BLOCKED/PLAN_CONFLICT。
