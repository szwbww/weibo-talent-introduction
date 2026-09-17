# fast-p 子计划 02 执行报告

- 子计划：`docs/plans/2026-09-17/global-world-clock-02-registration.md`（身份 `commit:96a0a411c88eaa447c2c6512f9c70fa8d8aaffb1`）
- 主计划：`docs/plans/2026-09-17/global-world-clock-master.md`
- child_base_sha（02 产品基线，= 01 code head）：`4c4c85c3ae236307a4435ca382929dc88c74eaa0`
- 分派时 HEAD：`7be8357258b132374f59a828e58d5da618438003`（01 轻量验证证据提交）
- 工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-global-world-clock-master`
- 分支：`fast/global-world-clock-master`
- 执行环境：Node `v25.7.0`（macOS arm64）
- 实现提交：`474445a3f9b84921decbf7f28c1a2b995fa6b897` — `feat(fast-p): implement 02`

## 结果

`READY_FOR_VERIFICATION`

## 交付文件（提交内容 `git show --name-only`，恰 10 项）

| # | 文件 | 变更 | sha256 |
|---|---|---|---|
| 1 | `src/main/resources/static/index.html` | +11/−19 | `75bac8af2b434786b3efc928f79508deaa196f5de3b1b6d07079dd6d2125e4c9` |
| 2 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | +5/−4 | `5f5e6a378e4d253304749a76ddcd066a6a9c7bdc83b380c4d135fc5b9cba3a4d` |
| 3 | `src/test/js/checkRepliesRelocation.test.js` | +5/−4 | `d160f5eb4e01f1d33509ae0c759ab5dd46d877f32bbdcdbe69cba4388a20f052` |
| 4 | `src/test/js/mailboxChatStyle.test.js` | +1/−1 | `15f4e9f482db0a6445e44b6f252a961c51f98bcd0af239880343e71ea6bd2260` |
| 5 | `src/test/js/manualReplySubjectPrefill.test.js` | +6/−5 | `0160ebae25fcc3ddf2da34f16711a6370583b40fdca53380738d279a07451b36` |
| 6 | `src/test/js/meetingConfirmationAssets.test.js` | +88/−18 | `70e326134b2f795c8dc430042a39a3b265b860e3131c8f66795cb296e6e6c14b` |
| 7 | `src/test/js/overlayAndDialogContrast.test.js` | +7/−6 | `27e58860158df80c2744b6d9bf74607259f633c43cd3cae32d0469cadace81d4` |
| 8 | `src/test/js/ragKnowledgeBasePage.test.js` | +8/−7 | `27e4e29cccb832239775d2c97155a3db3c3799d4865fe027c712fd7a17477c75` |
| 9 | `src/test/js/ragWorkbenchRender.test.js` | +4/−3 | `6fd6a6e2ac60abbf1f8cf9cbfd8adebf5deb2c6f260f50c2264fdca5eb278424` |
| 10 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | +7/−6 | `f04598cbb710083acbdf44f6a97ba8a94e7a35b0759f5ef68ba9294e6dae0e61` |

未新增/删除任何文件；`git show --stat --format='' HEAD` 合计 `10 files changed, 142 insertions(+), 73 deletions(-)`。

## 范围前置核对（编辑前）

### 固定缓存键命中集合（PLAN_CONFLICT 门）

```text
$ rg -l '20260914-followup-email' src/test
src/test/js/manualReplySubjectPrefill.test.js
src/test/js/ragKnowledgeBasePage.test.js
src/test/js/overlayAndDialogContrast.test.js
src/test/js/mailboxChatStyle.test.js
src/test/js/meetingConfirmationAssets.test.js
src/test/js/batchSendTaskConsoleVisualFix.test.js
src/test/js/trustReplyWorkbenchSharedMount.test.js
src/test/js/checkRepliesRelocation.test.js
src/test/js/ragWorkbenchRender.test.js
```

9 份命中，与计划变更清单第 2～10 项完全一致（集合相等，非「至少包含」）→ 不触发 `PLAN_CONFLICT`。

编辑后复核：

```text
$ rg -l '20260914-followup-email' src/test src/main/resources/static/index.html
（0 命中，exit=1）
$ rg -l '20260917-global-world-clock' src/test | sort
src/test/js/batchSendTaskConsoleVisualFix.test.js
src/test/js/checkRepliesRelocation.test.js
src/test/js/mailboxChatStyle.test.js
src/test/js/manualReplySubjectPrefill.test.js
src/test/js/meetingConfirmationAssets.test.js
src/test/js/overlayAndDialogContrast.test.js
src/test/js/ragKnowledgeBasePage.test.js
src/test/js/ragWorkbenchRender.test.js
src/test/js/trustReplyWorkbenchSharedMount.test.js
```

恰 9 份，未新增第 11 个文件。

### 01 交付物存在性

`src/main/resources/static/world-clock.js`（1089 行）与 `world-clock.css`（152 行）已在 `4c4c85c` 提交中存在，本步骤只注册，未改动两者字节。

## index.html 变更（S-5/S-6）

### S-5 顶栏

删除原 `index.html:147-156` 的整个 `#showPollLogBtn` button（含其 SVG 与「轮询日志」文案）后，`.topnav-side` 恰为计划 S-5 的逐字 HTML。机器核对（按计划 fenced 块取子串比对）结果：

```text
S-5 in index: True
```

保留（`rg -n 'pollLogPanel|closePollLogPanelBtn|currentUserDisplay|logoutBtn' src/main/resources/static/index.html` 仍命中）：

- `#currentUserDisplay`（顶栏当前登录）
- `#logoutBtn` 及其原 SVG（SVG 三条 path/polyline/line 逐字未动）
- `#pollLogPanel`（`:623`）、`#pollLogBody`（`:641`）、`#closePollLogPanelBtn`（`:626`）——日志面板与 `app.js:7332-7389` 的 `showPollLog` 实现、`app.js:14114` 的关闭绑定零 diff。
- 未新增任何静态时钟 DOM；顶栏侧栏 `<button` 计数为 1（只有退出登录）。

### S-6 资源注册

`head` 的 4 个版本化 link → 5 行；`body` 末尾的 6 行（1 未版本化 runtime + 5 版本化 script）→ 7 行。机器核对：

```text
S-6 css in index: True
S-6 js  in index: True
versioned refs: 11
task-modal unversioned: True   unversioned-with-key: False
```

- 11 个引用全部使用 `20260917-global-world-clock`，逐个资源唯一。
- CSS 顺序：styles → expert-materials → mailbox-chat → meeting-confirmation → world-clock。
- JS 顺序：trust-reply-workbench → expert-materials → meeting-confirmation → mailbox-chat → app → world-clock（`world-clock.js` 在 `app.js` 之后）。
- `task-modal-runtime.js` 原位、未版本化。
- 全部相对路径（无前导 `/`），未写会话 visualization 路径；未新增 inline style / onclick。
- 除上述 3 处外 `index.html` 无其它 diff（`git diff` 仅 3 个 hunk）。

## 测试同步（T2）

统一键字面量 `20260914-followup-email` → `20260917-global-world-clock`；9 项计数断言 → 11；有序资源集合在 CSS 末位加 `world-clock.css`、JS 末位加 `world-clock.js`；同步更新含计数的测试名与文件头注释。业务断言逐条保留（未放宽为「任意版本通过」，未删任何原有断言）。

| 文件 | tests | fail | 改动点 |
|---|---|---|---|
| batchSendTaskConsoleVisualFix | 17 | 0 | 键字面量 ×2、assets 集合 +2、ordered 集合 +2 |
| checkRepliesRelocation | 17 | 0 | `CACHE_KEY`、keys.length 9→11、ordered +2、测试名 |
| mailboxChatStyle | 21 | 0 | `mailbox-chat.css?v=` 固定版本正则值 |
| manualReplySubjectPrefill | 6 | 0 | `CACHE_KEY`、keys.length 9→11、`Set.size`、ordered +2、测试名 |
| meetingConfirmationAssets | 13 | 0 | `CACHE_KEY`、`CSS_ORDER`/`JS_ORDER`、30/43/49 三处计数 9→11、61/62 正则、标题注释、标题文案；**新增 T4 5 例** |
| overlayAndDialogContrast | 5 | 0 | `CACHE_KEY`、keys.length 9→11、`Set.size`、ordered +2、测试名、文件头注释 |
| ragKnowledgeBasePage | 9 | 0 | 键字面量 ×3、集合 +2、keys.length 9→11、ordered +2、测试名文案 |
| ragWorkbenchRender | 7 | 0 | `CACHE_KEY`、keys.length 9→11、ordered +2 |
| trustReplyWorkbenchSharedMount | 5 | 0 | `CACHE_KEY`、keys.length 9→11、`Set.size`、ordered +2、测试名、文件头注释 |

### meetingConfirmationAssets 新增 T4（5 例）

1. 新资源各自作为独立文件存在、以正确标签（link/script）注册恰 1 次且携带统一键。
2. `world-clock.css` 在最后一个旧 CSS（meeting-confirmation.css）之后；`world-clock.js` 在 `app.js` 之后且为最后一个带版本脚本；`meeting-confirmation.js` 仍在 `app.js` 之前（相对依赖顺序未破坏）。
3. 删除入口回归：`showPollLogBtn` 与 `showPollLog()` 零命中；`currentUserDisplay`/`logoutBtn`/`pollLogPanel`/`pollLogBody`/`closePollLogPanelBtn` 仍在。
4. 顶栏侧栏只剩一个 `<button>`，顺序为当前用户在前、退出登录在后，「退出登录」节点/文案保持。
5. 九个 `data-view` 集合与顺序逐字不变（monitoring/accounts/mail-templates/suppressions/contacts/mailbox/inbound-summary/ai-training/tasks）。

## 必跑命令与结果（均在工作区根目录执行）

| # | 命令 | 退出码 | tests | suites | pass | fail |
|---|---|---|---|---|---|---|
| 1 | `node --check src/main/resources/static/world-clock.js` | 0 | — | — | — | — |
| 2 | `node --test src/test/js/worldClock.test.js` | 0 | 41 | 8 | 41 | 0 |
| 3 | `node --test src/test/js/*.test.js` | 0 | 932 | 175 | 932 | 0 |
| 4 | `git diff --check` | 0 | — | — | — | — |

全量基线核对：种子提交 886 → 01（+41，`worldClock.test.js`）= 927 → 02（+5，`meetingConfirmationAssets.test.js` T4）= **932**，932 pass / 0 fail，符合「886+ pass、0 fail」。

未运行 Maven 构建、formatter、linter（按 brief 限定；Maven 非本纯静态变更的门禁）。

## 测试敏感度抽查（mutation，改后即还原）

对 `index.html` 注入偏差后运行 `node --test src/test/js/meetingConfirmationAssets.test.js`（基线 13/13/0），确认新增与更新的断言不是空断言：

| 注入 | tests | pass | fail |
|---|---|---|---|
| 基线（未注入） | 13 | 13 | 0 |
| M1 `world-clock.js` 键值改 `20260916-…` | 13 | 9 | **4** |
| M2 `world-clock.js` 移到 `app.js` 之前 | 13 | 11 | **2** |
| M3 重新插回 `#showPollLogBtn` | 13 | 11 | **2** |
| M4 删除 `world-clock.css` link | 13 | 8 | **5** |
| 还原后复核（`cmp` 与备份逐字节一致） | 13 | 13 | 0 |

## 偏离与说明

1. **`docs/plans/fast/**` 本轮不可排除地已受版本控制**：分派时 `docs/plans/fast/global-world-clock-master/` 已被控制器在 `7be8357` 提交跟踪（01 报告记为未跟踪的那一状态已过时）。提交时按文件名精确 `git add` 十个授权文件，未使用 `git add -A`。控制器拥有的 `docs/plans/fast/global-world-clock-master/ledger.md` 仍有未提交修改（` M`），**未被本提交包含**。本报告（含 `children/02/*.md`）同样留在提交之外。
2. **文件头注释计数同步**：`overlayAndDialogContrast.test.js:7` 与 `trustReplyWorkbenchSharedMount.test.js:5` 的头注释含「九联/九个」计数，已改为「11 个」；`meetingConfirmationAssets.test.js:3-12` 头注释扩为 4 条并说明 02 范围。属计划要求的「标题注释」同步，未改业务语义。
3. **未新增缓存键以外的断言放宽**：九个文件中的旧键负断言（`20260903-bounce-warning`、`20260910-mailbox-spacing`）按计划保留为「上一键 0 命中」，本次未改动。
4. **未修改** `world-clock.js`/`world-clock.css`（01 交付物）、`app.js`、`styles.css`、`meeting-confirmation.*`、后端、DB、`pom.xml`；未 push/merge/rebase/amend/squash。
5. **未执行浏览器人工验收**：A-9/A-10/A-11 需真实浏览器与测试环境，属后续人工阶段；DOM stub 绿测不构成布局结论。A-10 的旧缓存升级需上线后实测。
