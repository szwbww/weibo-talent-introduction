## Light Verification: LIGHT_FAIL
Child: 02（`docs/plans/2026-10-04/mailbox-progress-02-frontend.md`；brief `docs/plans/fast/2026-10-04-mailbox-progress-master/children/02/brief.md`）
Boundary: dbe79c2bfb466bedb2c70d767bdd34082307579c..cc69a8f0649f025bd69e80ecac4c5097fc8343b6（head = `feat(fast-p): implement 02`）
Verifier: VerifyMailboxProgress02

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|Authorized scope|PASS|`git show --name-only cc69a8f` = 恰好 6 个授权文件（`src/main/resources/static/{index.html,mailbox-chat.js,styles.css}`、`src/test/js/{mailboxChatBehavior,mailboxChatStyle,mailboxSuspension}.test.js`）；`git diff --name-status dbe79c2..cc69a8f` 中 `src/**` 仅这 6 项，区间其余改动全为 `docs/plans/fast/**` 控制方工件。`git status --porcelain` 干净。`styles.css` 为纯追加（+21/-0），`mailbox-chat.css` `git diff` 为空（字节未动）。|
|Plan and invariants|FAIL|见 F-1：T-2 明确要求的 `refreshListWithFallback` null 短路未实现。其余逐条有证据（下详）。|
|Required commands|PASS|`node --check src/main/resources/static/mailbox-chat.js` exit 0；6 文件 `node --test` exit 0 = **270/270** pass（与 execution.md 一致）；`node --test src/test/js/*.test.js` exit 0 = **1451/1451** pass、fail 0（baseline 1434，增量 +17 全通过）；JDK11 `mvn test` exit 1 = surefire **Tests run 4547 / F0 / E19 / Skipped 13**（baseline 逐字一致），唯一失败类 `mailbox.service.ExpertContactLocationServiceTest 19/0/19`（`<init>:38 » IllegalStateException: 国家时区目录配置错误：国家 CL 的时区…`），无第二失败类；`exec-maven-plugin` 未执行（surefire 先失败，与 baseline 同）。|
|Downstream interfaces|PASS|前端仅消费 01 接口：`mailbox-chat.js:4916-4950` PUT `/api/mail/mailbox/conversations/{id}/progress-status` body `JSON.stringify({status})`、从回包读 `data.progressStatus`；`:541-542,1518-1525` `providedOnly`（`CHIP_PROVIDED`→`{providedOnly:true}`，`conversationsParams` 写 `providedOnly=true`）；`:70-76` 列表项 `progressStatus`（`progressStatusOf` 读 `item.progressStatus`，缺字段回退 `followed`）；`:1914-1962,2293-2325` 挂起 GET/DELETE 回包经 `progressStatusOf(state)`。后端 `MailboxConversationController.kt:239,258,290`、`MailboxSuspensionService.kt:31,184-191` 与 brief 契约一致。|

### Evidence detail

已核实通过的门禁 2 检查点：
- S-2 逐字块：`awk '/mailbox-progress-contract:start/,/mailbox-progress-contract:end/'` 从计划与 `styles.css` 各取 21 行，`diff` 为空 → 字节一致；仅 5 个新 class，均在该块字面声明；`mailbox-chat.css` 无语 `mailbox-progress` 规则。
- 11 资源键：`index.html` 5 CSS + 6 JS 全部 `?v=20261004-mailbox-progress`（`grep -o '?v=…'|uniq -c` = 11），资源名/顺序/数量不变；`src/test` 旧键 `20261004-mailbox-suspension-followup` 精确命中 **0**。
- Tab/菜单词表：`mailbox-chat.js:58-66` `FILTER_CHIPS` = 全部/已提供/跟进中/待处理/已挂起/已回复/待匹配；`PROGRESS_MENU`（:46-50）NONE→跟进中/已提供、FOLLOWING→取消跟进/已提供、PROVIDED→跟进中/取消提供；`PROGRESS_INVALID_LABEL="状态不可用"`，非法状态禁用（`progressActionsHtml` :1589-1607）。
- 无「进入跟进/进入提供」字面量（全仓仅 :79 注释反向声明）；无星号/勾/箭头等新增图标（`grep '★|☆|✓|✔'` 无 UI 命中）。
- 详情旧关注控件已删、无残留：`grep 'mc-follow|mc-toggle-follow|toggleFollow|renderFollowButtons|setFollowControlsDisabled'` 在 `mailbox-chat.js`/`index.html`/`app.js` **0 命中**；`mailboxChatStyle.test.js` 亦断言无 `关注`。
- 待匹配无菜单：`renderUnmatchedPerson`（:1666-1680）不含 `progressActionsHtml`；卡片菜单在 `[data-role=person-actions]` 内、不入 `mc-person-main`。
- 无提前改状态：`setProgress`（:4916-4954）在 PUT 前仅 `freezeDefaultProbe()`+`setProgressBusy`+`renderList`，`updateProgressLocal`（:4902-4914）只在 PUT `.then` 内调用。
- per-contact busy：`progressBusy`（Set，:112）+ `setProgressBusy`（:4834-4839），`progressActionsHtml` 继承 `instance.progressBusy.has(contactKey)`；同状态重复直接返回（:4931）。
- 写成功/列表失败分离：`fetchList` 失败返回 null 并置 `list.error`；`setProgress` `.then` 用 `contextToken === listContextToken()` 且 `list.error && !loading` → `"状态已保存，列表刷新失败，请重试"`，否则 ok（:4946-4952）；`.catch` 仅报写入失败。
- 挂起 seq/归类：`setProgress` 命中同专家挂起时 `suspension.seq += 1` 并 `loadSuspensionState(id)`（:4939-4944）；`loadSuspensionState` 自身再 `seq += 1` 且回包按 `mySeq`/`suspensionContextAlive` 拦截（:1947-1959）；`afterSuspensionRemoved`（:2293-2325）pending>0→待处理、否则 PROVIDED→已提供 / FOLLOWING→跟进中 / 其余已回复。
- 测试证据：`mailboxChatBehavior.test.js` 新增六转换/非法禁用/互斥/无提前改/写成功刷新失败/末页回退/改 A 不串 B/菜单键盘与详情待匹配；`mailboxSuspension.test.js` 新增归类矩阵含 PROVIDED、PROVIDED 提示、旧挂起 GET 迟到不覆盖、缺字段兼容。

### AUTO_FIX
- **F-1（gate 2 FAIL）** 计划 T-2 / brief T-2 明确要求：`refreshListWithFallback` 首查与回退 fetch 后先检查 `disposed || data == null` 并短路（「命中即返回，不再做分页或选中项协调」，避免旧请求后续动作影响新页面）。证据：`src/main/resources/static/mailbox-chat.js:2511-2524` 与基线 `dbe79c2` **逐字未改**（`diff <(git show dbe79c2:…refreshListWithFallback) <(…current…)` = `REFRESH-FN-UNCHANGED`），函数内仅有 `if (instance.disposed)`，无 `data == null` / `retryData == null`；`grep 'data == null|retryData'` 无该守卫。`execution.md` 却把「`refreshListWithFallback` null 短路」列为已实现证据，属未落地声明。相关用例 `mailboxChatBehavior.test.js:2761-2793`（「过期列表回包 null」）并未区分该路径（数据 null 在 `fetchList` 成功分支被当作空列表、`total=0`，故不触发回退），不构成该短路的直接代码/测试证据。最小授权修正（该文件已在白名单内）：在 :2512 `.then((data) => {` 内改为 `if (instance.disposed || data == null) return data;`，并在 :2519 回退分支改为 `if (instance.disposed || retryData == null) return retryData;`。经推演该修正不改变现有用例结果（末页回退用例回包非 null；写成功刷新失败用例仍由 `list.error` 分支提示）。

### RECORD_ONLY
- N/A（本片唯一未落地项已计入 F-1，非「无关」事项；S-2 块位置由实施者在 execution.md 已声明偏差——为不撞既有 `mailboxSuspensionStyle.test.js` 的 `trimEnd().endsWith(END)`，块插于 suspension 合同块之前而非文件最末；块字节与计划一致、机器要求（字节包含）达成，不单独立项。）

### Required Action
- AUTO_FIX
