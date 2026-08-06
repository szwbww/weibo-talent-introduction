# Fast-P Human Review Handoff

- Outcome: READY_FOR_HUMAN_REVIEW
- Master base: d911bd6
- Current/final code head: ef7e471
- Branch/worktree: fast/mail-reliability / /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability

## Child Status

| Child | Status | Code boundary | Fix rounds | Evidence commit |
|---|---|---|---|---|
| p4-task0-rfc8058 | LIGHT_PASS_WITH_NOTES | 92a678b..f2916674 | 0 | e704a58 |
| p1-expert-profile-absence | LIGHT_PASS_WITH_NOTES | f2916674..33e1ffb | 0 | 92b13aa |
| p3-outbound-message-id-01 | LIGHT_PASS_WITH_NOTES | 33e1ffb..025b875 | 0 | 68536d8 |
| p2-inbound-message-id-prefix | LIGHT_PASS | 025b875..ef7e471 | 0 | ff259b1 |

Commit lineage (all on `fast/mail-reliability`):
`d911bd6` (master base) → `92a678b` seed plans → `f2916674` p4 impl → `e704a58` p4 evidence → `9bbb046` M-1 arbitration docs → `33e1ffb` p1 impl → `92b13aa` p1 evidence → `025b875` p3 impl → `68536d8` p3 evidence → `ef7e471` p2 impl → `ff259b1` p2 evidence → handoff commit.

## RECORD_ONLY Index

| Observation | Child | Evidence | Source report |
|---|---|---|---|
| 既有测试 `send adds List-Unsubscribe headers when token service enabled` 的期望字面量随 J-7 修正（`List=One-Click` → `List-Unsubscribe=One-Click`）；测试名/条件/语义不变，属计划缺陷值修正的必然结果 | p4-task0-rfc8058 | SmtpMailDeliveryServiceTest.kt | verify-log.md (VerP4Task0) |
| M-1 仲裁：`mailboxInboundTags.test.js` 加入 P1 授权清单（第 6 文件）。计划任务 2.2 契约变更必然破坏 `:84` 裸数组桩；计划 Interaction point 3 仅盘点 expertTagBatchFix.test.js，遗漏此文件。修复（桩 → `{found:true,tags:[...]}`）由计划自身规则唯一确定。仲裁提交 `9bbb046`，已同步 M-1 矩阵 + P1 计划文件清单 + child brief | p1-expert-profile-absence | 00-main-plan-mail-reliability.md M-1 矩阵；expert-profile-absence-not-error.md 变更文件清单 row 6；brief.md item 6 | verify-log.md (VerP1) |
| boundary 内 11 个 controller 记账文档文件（doc-only，门 1 已排除） | p1-expert-profile-absence | e704a58/9bbb046 | verify-log.md (VerP1) |
| AutoMailReplyServiceTest 的 IP-4 kind 提取正则适配；MeetingScheduleServiceTest 的 Mockito captor → thenAnswer（Mockito 2.21 + Kotlin 非空参数 NPE）。两者仍证明所需断言 | p3-outbound-message-id-01 | 两个测试文件 | verify-log.md (VerP3) |
| 基线粒度差异（记录基线 JS 459/0 vs 全量 2187/0/0/4，均为 PASS 退出码 0，非 p2 引入） | p2-inbound-message-id-prefix | — | verify-log.md (VerP2) |
| `K-message-id-fingerprint.md` 的 `created` 未字面 bump，但已是 2026-08-06（= 当日，no-op） | p2-inbound-message-id-prefix | K-message-id-fingerprint.md | verify-log.md (VerP2) |
| boundary 含姊妹计划 p3 的证据提交 68536d8（4 个 docs 文件） | p2-inbound-message-id-prefix | — | verify-log.md (VerP2) |

## Pause/Resume

- Reason: N/A（无暂停）
- Resume from: N/A

## 留给人工评审的批次级事项（本次未执行，属 master 计划联合验收）

- **J-1**（P3×P2 闭环）：会议邀请发送 → 记录 `mail_record.message_id` → 测试专家 Gmail 回复 → 候选列表首项 `IN_REPLY_TO`/90。验证 P3 新格式经中继改写后仍被 P2 归一命中（两计划接缝的唯一覆盖点）。
- **J-2**（P1×P2）：无画像专家 `TEST-LUKAI-18014905480` 的完整处理流程（面板完整渲染 + `IN_REPLY_TO` 候选 + 绑定/人工回复走通）。
- **J-3**：全量回归门禁（各子计划 `## 验证命令` 的全量测试命令；机器可验部分各 verifier 已逐 child 跑通，人工按需复核）。
- **P4 其余阶段**（阶段 A/B/C/D，任务 1-9）：master 计划执行顺序 ⑤ 明确推迟 —— 需先观察 `material-reminder-01` 上线后 `MATERIAL_REMINDER` 是否进入 Gmail「主要」标签页再决策。
- M-5 知识写回已完成（`K-message-id-fingerprint.md` 含 P3 修正表两处 + P2 末段证伪；`K-outbound-message-id-single-factory.md` 与 `K-vendor-message-id-prefix.md` 互相 `[[链接]]`）。

No whole-system verification was performed.
