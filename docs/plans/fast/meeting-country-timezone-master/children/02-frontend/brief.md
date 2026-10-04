# Child Brief — 02-frontend

本文件是 fast-p 控制器的派发契约。**完整契约 = 已批准子计划**，必须逐字读完再动手；本 brief 只补充身份、授权范围、命令与上游接口。

## 身份

- Child ID：`02-frontend`
- 批准子计划（权威，逐字读）：`docs/plans/2026-10-04/meeting-country-timezone-02-frontend.md`
- 子计划身份：`commit:fee3a7ca2f3b3b619ac46ee90dc88ddcb459f35f`
- 主计划：`docs/plans/2026-10-04/meeting-country-timezone-master.md`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-country-timezone-master`
- Branch：`fast/meeting-country-timezone-master`
- `child_base_sha`：由控制器在派发消息中给出（= 01-backend 的 Code head，记录于 ledger）
- 执行报告（写这里，提交由控制器负责）：`docs/plans/fast/meeting-country-timezone-master/children/02-frontend/execution.md`
- 只读证据目录：`docs/plans/2026-10-04/meeting-country-timezone-evidence/`（前端基线见 `code-baseline.md` 的 frontend/styles 段、`grep-receipts.md`）。

## 授权文件（只准改这 6 个，其余一律不动；也不得新建清单外文件）

| # | 文件 | 修改 |
|---:|---|---|
| 1 | `src/main/resources/static/meeting-confirmation.js` | 会议专用国家分组、状态/请求门禁、DOM/文案 |
| 2 | `src/main/resources/static/index.html` | 已有版本化资源统一 cache key |
| 3 | `src/test/js/meetingConfirmation.test.js` | 纯分组与组件状态/异步测试 |
| 4 | `src/test/js/meetingConfirmationIntegration.test.js` | 真实组件/宿主流程与字段 fixture |
| 5 | `src/test/js/meetingConfirmationStyle.test.js` | 新DOM与国家/UTC显示契约 |
| 6 | `src/test/js/mailboxOutboundAttachments.test.js` | 协同会议挂载 fixture/交互，附件共存回归 |

明确禁止改：`meeting-confirmation.css`（必须逐字不变）、`app.js`、`mailbox-chat.js`、`world-clock.js`、任何 Kotlin/Java、任何 Flyway 迁移、`docs/plans/2026-10-04/**`。

## 上游接口（01-backend 已交付，直接消费，不在 JS 重拼）

- `GET /api/mail/meeting-confirmation/time-zones?date=&startLocal=&endLocal=`（会议模式；仅改 Zoom URL 不重取）。
- `MeetingTimeZoneOption` 新增字段：`countryCode`、`countryLabelZh`、`countryLabelEn`、`canonicalZoneId`、`endOffsetSeconds`、`localTimeIssue`（无 issue 时 null）。
- 有效项：`offsetSeconds`=实际起点、`endOffsetSeconds`=终点；`localTimeIssue` 非 null 表示该项不可用于分组/预览。
- 国家为 null（SystemV）的项不进新国家列表；`countryCode=UTC` 是显式特殊项。
- 邮件文案/附件名由服务端生成；JS 只消费 preview 响应的 `meetingTime`/`attachment.filename`。

## 硬约束（主计划 + 子计划）

- F-1：分组 key=`countryCode + startOffsetSeconds + endOffsetSeconds`，不跨国合并；成员须有国家元信息、`localTimeIssue=null`、非空 `endOffsetSeconds`。显示 `巴西（UTC-3）`；跨偏移 `美国（UTC-5 → UTC-4）`。
- F-2：保留 raw member IDs；选组优先 `id==canonicalZoneId`，其次 raw ID 字典序；改期分拆 → 清选择并要求重选；绝不提交不在当前响应里的 ID。
- F-3：1 个 group 自动采用并整块隐藏；>1 才显示选择器；0 个提示检查时间；未填完整时间不产生可应用预览、不用 UTC 中午值代替。
- F-4：`zonesSeq` 独立于 `configSeq`；响应核对 open/disposed/seq/完整起止 key；旧响应不得恢复选择/预览；目录失败/缺元信息阻断预览与应用并提供重试，不沿用旧目录。
- F-5：所有可见标签 = 国家＋UTC，无城市/原始 IANA/`East Time`。
- F-6：`MailboxMeeting.filterZones` 与宿主挂载/载荷不变；不新增 `countryCode/groupKey` 到会议 input；旧文件名 SENT 卡片原样。
- F-7/S-1～S-4：不新增 CSS class、inline style；新 country `<select>` 复用 `.meeting-form select`；`[hidden]`/`.meeting-zone-field` 等原规则不得改；`index.html` 版本键统一为 `20261004-meeting-country-timezone`（执行时按实际键反查写死键的测试，见下）。
- 现有资源键（基线 11 项）为 `20261004-mailbox-suspension-followup`；执行前先 `rg -n '20261004-mailbox-suspension-followup' src/main/resources/static src/test` 复核全量命中与项数，一并更新；不得抹掉其他任务新增资源。
- 组件模板在 `meeting-confirmation.js` 内动态生成；DOM 存在性断言必须查真实组件模板源文本，不能只靠 DOM stub。

## 必跑命令（worktree 根目录）

```bash
node --check src/main/resources/static/meeting-confirmation.js
node --test src/test/js/meetingConfirmation.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/meetingConfirmationStyle.test.js src/test/js/mailboxOutboundAttachments.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/worldClock.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/mailboxCalendarIntegration.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn package
```

基线（控制器已记录）：`worldClock.test.js` 41 pass / 0 fail；其余 7 个 JS 文件 123 pass / 0 fail。`mvn package` 为发布门禁级构建；发现既有无关失败必须区分基线（记录原文），不得把全仓修复塞入本子计划。

## 提交与返回

- 用 `execute-p` 流程执行；只改授权文件；用提交 `feat(fast-p): implement 02-frontend` 落地**一次**本地提交；提交内**不得**包含 `docs/plans/**`。
- 禁止 push / merge / rebase / amend / squash / reset / 改历史；禁止动主工作树与其他 worktree。
- 执行报告写全：改动文件、关键决策、每条命令 + exit code + 计数、基线对照、残余风险；mock fixture 必须标明是 mock，真实摘要/文件名因果由 01 测试覆盖。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
- 需要计划外的行为决策或新文件 → 立即 `BLOCKED`（计划冲突），不要自行扩权。

## 本子计划的验收断言（自检清单，来自子计划验收标准）

F-1（Brazil 4 组、Sao_Paulo 与 Brazil/East 同组、跨国不合并、pair 显示、分钟偏移）；F-2（旧 Brazil/East payload 不变、canonical 代表、Denver/Phoenix 冬同夏拆清选择、raw gap 不换成员）；F-3（土耳其单选 hidden、BR 多选必选、未填时间不给 noon 结果、0 组阻断、saved/default 合法恢复）；F-4（逆序响应、旧失败后到、preview 旧回调、清空/关闭/切专家、失败重试；旧结果不得使 apply/download 变 enabled；未完成时间无多余请求；改 Zoom 不取目录）；F-5（可见标签无城市/IANA/East Time、请求仍真实 zoneId、正文不被 JS 改写）；F-6（filterZones/worldClock 回归、宿主 payload 原样、新旧附件名共存、普通附件与材料统计不变、/talent 前缀下载）；F-7/S-1～S-4（模板源文本新 ID、ARIA、无新 class/inline style、CSS bytes 与 target 相同、index 同一新键、800px/420px 目测留给人工）。
