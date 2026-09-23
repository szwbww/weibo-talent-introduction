# c4 execution.md

## Execution Result: READY_FOR_VERIFICATION

- Plan: `docs/plans/2026-09-23/04-lukai-production-migration.md`（G-4、I-1…I-5）
- Child base (product boundary): `e77cb065ba6261317adc7060b2a7729052086407`（c3 终态）
- Execution epoch: 1（授权后首次执行；此前为 PAUSED_FOR_HUMAN）
- Authorization: 用户明确「授权」执行 04 的停轮询/停应用、DDL、逐 ID 删除与改归、账号配置 UPDATE、部署重启
- Window: 2026-09-23 21:12 → 21:52（停写 21:18:39，修复 COMMIT ≈21:33，部署 21:35，旧上下文清理后重启 21:49）
- Repository artifact: `docs/runbooks/repair-lukai-shared-inbox.md`（c4 唯一授权文件；已写入实际命令、逐 ID 分类、备份位置、前后结果与回滚）
- Product/test files changed: none（除上述 runbook）

## What was executed

| 阶段 | 结果 |
|---|---|
| 0 冻结与只读预检 | 确认生产为 main HEAD `9237d6f57333`、无 Flyway、两账号同 UIDVALIDITY `1782107786`；停写前无在途作业 |
| 1 停写 + 备份 + DDL | Tomcat 21:18:39 停（写冻结 20s 无变化）；全量备份 + 恢复验证（10 表逐表 OK）；手工等价 V134 DDL 应用（0 回填） |
| 1 分类 | 159 条 QF processing 逐 ID 分为：探针 95（可删）、物理重复 35（可合并）、错归真实来信 27（改归）；47 条 QF INBOUND mail_record 全部改归保留（依赖不可证明无损合并） |
| 2 事务修复 | 单事务 + 断言（`fail_count=0`，`COMMITTED`）：删 95 探针 + 35 重复、改归 27 processing + 47 mail_record、transfer/附件/标签去重与重指向、保留行标注物理 owner、账号 `LuKai_QF.inbound_mailbox_code='LuKai'` |
| 3 部署与核对 | 新 WAR（sha256 `cbcca08c…`，含 V134）部署、健康 200；部署后发现**旧备份目录被当作独立上下文以旧代码并行运行**（见下），移出并重启后单实例运行 |
| 3 收尾 | 旧实例残留的 3 条探针行按严格谓词删除（快照 `_bk_c4_20260923_probe3`） |

## Verification evidence（现场，只读）

- 账号：`LuKai_QF.inbound_mailbox_code='LuKai'`、`enabled=0`、SMTP/IMAP/限额与切换前逐列相同（事务内哈希断言 `SAME`）。
- 本组 processing：`LuKai MANUAL_REVIEW 27 / PROCESSED 109`、`LuKai_QF PROCESSED 2`；本组 `[self-check]` 待处理 = **0**。
- `id=383` 已不存在；`id=366/369` 仍为 `PROCESSED/MANUAL_RESOLVED`。
- 材料/意图完整：`expert_document` 126（未变）、`inbound_intent` 233（未变）、被删附件 6 条均为 owner 已有同名文件的重复元数据；`mail_attachment` 141→135、`mail_attachment_transfer` 82→72、`inbound_mail_tag` 428→397。
- 孤儿检查：9 类 FK/软引用在事务前后相等（6→6），未新增；唯一键冲突 0。
- 单实例轮询：21:50 周期只有 1 条 `AUTO_REPLY_ALL`（此前每轮 2 条）；`LuKai_QF` 游标冻结在 326；21:49 后新增 processing/mail_record = 0/0。

## Deviations / 现场新发现

1. **`webapps/talent.dir.bak-20260922-manual-scope` 被 Tomcat 当作独立上下文部署**，以 2026-09-22 的旧代码并行运行（旧调度 + 旧收信逻辑），是「部署后 QF 仍被轮询」的直接原因，也是每轮两条 cron 记录的原因。已将该目录整体移出到 `/opt/talent/backups/`（保留未删）并重启；未改任何产品代码。原计划未预见该情况，处置记录见 runbook §8b。
2. **3 条由旧上下文在 21:41 写入的探针行**（499/500/501）在处置后按同一严格判据删除（快照留存）。
3. **未执行**：测试信投递（A-1/A-2/A-5/A-6 需外部测试邮箱）与 A-4 目视核对，留待人工验收。
4. 生产既存隐患（弱口令、明文凭据、1.05 GB `catalina.out`）已记录，未在本窗口处置。

## 人工验收建议顺序

1. 打开专家会话（如 expert 2645/2382）确认同一封来信只有 1 条收件、账号显示为 LuKai；
2. 待处理列表确认探针为 0、`383` 不在其中；
3. 材料/意图页确认保留来信的附件与意图可读；
4. 从外部邮箱分别给 updates / QF 各发 1 封测试信 → 手动「检查回复」→ 各自只出现 1 条且账号正确；
5. 确认 `LuKai_QF` 账号页显示「共享收件箱主账号 = LuKai」且发信配置未变。
