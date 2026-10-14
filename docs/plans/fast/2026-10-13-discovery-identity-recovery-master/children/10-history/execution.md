# 10-history 执行报告（epoch 1）

- Child: `10-history`；Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`；Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 起始 HEAD: `b1559e02274fd2a5a1f9991b7ecb132aaa0d1db9`（`git status --porcelain` 干净）
- 计划: `docs/plans/2026-10-13/discovery-person-10-history.md`（权威）；依据审计 `docs/audits/2026-10-13-discovered-person-duplicates/report.md`（只读）
- 授权文件: 6（精确）
- 提交信息: `feat(fast-p): implement 10-history`
- 边界遵守: 本轮不执行任何生产/全量 apply；ES/联系人/邮件只读；不新增业务字段；无生产发送；`docs/plans/fast/**` 未进入提交。

## 命令结果（本 epoch 新鲜执行，JDK 11）

| 命令 | 退出码 | 计数 |
|---|---|---|
| C1：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test` | 0 | Surefire: 18 tests / 0 failures / 0 errors（Service 11、Controller 7）；JS 门禁：tests 1677 / pass 1677 / fail 0 / skipped 0 |
| C2：`python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'` | 0 | Ran 14 tests — OK |

C2 实际命令（原样，发现机制可用）：`python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'`。

## 改动摘要（6 文件）

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt`（新增）
   - **只读预览**：仅接受冻结 docId 全集 + 候选组标识 + 规则版本；服务端自行从 ES 重读三层、标签、身份证据、`seq_no`/`primary_term`、SQL 关系 revision、人工准入/历史保护状态。每条冻结档案产出**互斥主类** `MERGE_READY / REVIEW_REQUIRED / KEEP_SINGLE / CHANGED_OR_FAILED`，`classCounts` 之和 = 冻结档案数。`canMerge` 与 `canSend` 分离；逐成员输出准入/禁发原因。
   - **受限应用**：只接冻结 ID、规则版本、预览摘要与逐组 expected 指纹；服务端重建预览并比对摘要 + 逐组指纹；整组成员实时复核后**每组一个事务**（`@Transactional applyGroup`）登记整组（04）并导入历史保护（09 只读面）；幂等同请求返回 `UNCHANGED`；任一成员不合格整组 `CHANGED_OR_FAILED`，不部分合并。
   - **回滚提案**：`rollbackPreview` 只生成逐组提案；已有后续成员或 SENT/UNKNOWN 首发保护时 `safeToRollback=false`（`ROLLBACK_UNSAFE`），不提供无条件拆组接口。
   - 原因 key 集中在 `MaintenanceReasonCodes`；`ProtectionImport` 区分 `IMPORTED/NONE/BLOCKED/UNKNOWN`。
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceController.kt`（新增）
   - `POST /api/expert-discovery/person-maintenance/preview|apply|rollback-preview`；沿用现有管理入口访问控制（登录态，actor 只取 `AuthSessionKeys.USERNAME`，忽略请求体伪造 actor）；未登录 401、参数/规则版本 400、预览摘要不一致 409、其余 200。
3. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt`（新增，11 用例）
   - 覆盖每主类、计数与摘要、逐邮箱准入/禁发原因、apply 幂等/摘要/指纹门禁、单例登记、预览后成员变化整组失败无半合并并恢复重试、并发登记冲突重读复验、回滚提案（含后续成员/已发送不安全）、规则版本/空名单拒绝。
4. `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt`（新增，7 用例）
   - `@WebMvcTest` + 真实 `AuthWebConfig`：三端点 401、预览 200/400、apply 200/409/400、回滚提案 200。
5. `tools/discovery-person-recovery/reconcile.py`（新增）
   - 本地 CLI：`preview`（默认）/`apply`/`rollback-preview`；落盘先原子写临时文件再 `os.replace`，目录 0700、文件 0600；保存各组结果与断点（按 groupKey 去重、`--resume`/断点续跑）；`apply` 需显式 `--confirm` + 预览摘要（来自 state 或 `--preview-digest`），支持 `--limit` 试点；完整邮箱只写 0600 明细文件，主报告不含全邮箱名单；不发邮件、不做生产写。
6. `tools/discovery-person-recovery/test_reconcile.py`（新增，14 用例）
   - 覆盖名单/组解析、摘要顺序敏感、groupKey 顺序无关、0700/0600 权限与原子落盘、预览留痕边界（主报告无邮箱）、resume 去重、apply 无 `--confirm`/无摘要拒绝、摘要与逐组指纹传递、断点跳过、`--limit` 试点、回滚提案（仅提案；无 apply 时只对 MERGE_READY）。

## 关键合同兑现（I-1/I-2/I-3）

- I-1：只处理传入的冻结 docId 全集；apply 实时重读三层/标签/证据/`seq_no`/`primary_term`/SQL revision；读取失败、非 discovered、范围外成员、成员变化 → `CHANGED_OR_FAILED` 跳过；不使用快照强覆盖；冻结后新增 discovered 属 05 增量（不在本服务分母内）。
- I-2：候选组在使用前逐成员服务端复核；缺可信作者键/多同类键/不连通/桥接已有人物根/关系指纹变化 → `REVIEW_REQUIRED`；整组一次提交、失败组不部分合并；单例登记不创建作者键、不提高准入。
- I-3：不改旧 docId/contactId/mail_record/别名/人工决定（只读）；关系经 04 登记；历史保护经 09 只读接口确认（联系人/别名/mail_record/attempt 由 09 在发送前持续读检查）；合并不触发发送；回滚只提案，已有后续成员/发信时禁止自动拆组。

## 未决问题 / 偏差（提请验证）

1. **历史保护“导入”为只读确认（刻意偏差）**：09 无独立“导入 SENT/UNKNOWN”写接口，只有 `reserveIntroduction`。若对**无历史**组调用它会在 guard 表插入 `RESERVED` 占位，而该占位的 ticket 被丢弃无法被 CAS 消费 → 会**永久阻塞**该人物首发（违背“合并不能让任何人都无法邀请”）。因此 `importHistoryProtection` 只经 09 的 `stateOf`（+ 08 人物级准入探针，即 09 历史检查第 1 步同一权威只读面）确认保护，不产生新状态行；实际换址阻断由 09 在真实发送前对全部成员的联系人/别名/mail_record/attempt 读检查持续提供。已按计划“通过同一服务导入历史禁止状态”的精神实现（调用 09 API），但“导入”不落新行，请验证阶段确认此语义。
2. **预览摘要范围**：`previewDigest` 绑定「规则版本 + 冻结 docId 全集」，不含可变分类/证据，使单组成员变化按**逐组 expected 指纹**跳过而非整批拒绝（满足 A-2「移标组跳过」）。因此 apply 的摘要须针对**本次提交的组子集**计算（CLI 由 preview/state 提供）。若计划期望“摘要绑定分类”，属语义分歧，请裁决。
3. **人工决定/三层版本**：`manualDecision` 复用 08 准入探针的排除原因（含人工拒绝/暂停）；`layerVersions` 采各层 `updatedAt`。未直连 `DiscoveryReviewService`（避免越权改文件）。
4. 本轮**未执行**任何生产 apply；CLI 与服务的 apply 均只在测试替身上验证。

## 提交

- 单提交 `feat(fast-p): implement 10-history`，仅含上述 6 个授权文件（`__pycache__` 不纳入）。
