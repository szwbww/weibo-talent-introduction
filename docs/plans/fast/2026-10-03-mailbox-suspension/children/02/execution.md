# 02 执行报告（挂起交互、默认 Tab 与完整 CSS）

- 状态：READY_FOR_VERIFICATION
- 实现提交：`0837c372f45d69374084113d8258d8c3320d748c` — `feat(fast-p): implement 02`
- child_base_sha：`94378f60c6f8d0f4b2a0231649e3b2c8888ef344`；执行前 HEAD `d115f4a3…`（01/01b 证据提交）
- 分支/工作区：`fast/2026-10-03-mailbox-suspension` @ `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-03-mailbox-suspension`
- 变更文件（恰 6 个，全部授权）：
  1. `src/main/resources/static/mailbox-chat.js`
  2. `src/main/resources/static/styles.css`
  3. `src/main/resources/static/index.html`
  4. `src/test/js/mailboxChatBehavior.test.js`
  5. `src/test/js/mailboxSuspension.test.js`（新）
  6. `src/test/js/mailboxSuspensionStyle.test.js`（新）
- 本报告未提交。

## 1. 必需命令（本 invocation fresh，cwd=worktree）

| 命令 | 结果 | 计数/退出码 |
|---|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0 |
| `node --test …mailboxChatBehavior …mailboxSuspension …mailboxSuspensionStyle …mailboxChatStyle` | PASS | tests 207 / pass 207 / fail 0 |
| `node --test src/test/js/*.test.js` | PASS | tests 1421 / pass 1421 / fail 0 |
| `cmp src/main/resources/static/mailbox-chat.css docs/…/baseline-mailbox-chat.css` | PASS | exit 0 |

### 与 baseline.md 对照
- baseline：tests 1385 / pass 1367 / fail 18（18 个全部是「11 个 ?v= 键必须同值」）。
- 本次：tests 1421 = baseline 1385 + 36 新增（mailboxSuspension 25 + mailboxSuspensionStyle 11）；fail 0——统一缓存键后 18 个基线失败全部转绿，未修改除授权测试外的任何测试文件。

## 2. 缓存键清单与 test 反查

- 执行前 `rg -n '\?v=' index.html`：11 处 = 10×`20261003-mobile-core-03` + 1×`20261003-mobile-core-03-generic-followup`（mailbox-chat.js）。
- 执行后：11 处均为 `20261003-mailbox-suspension`（`rg -c` = 11；`rg 'mobile-core'` 仅剩无版本键的 class 名 `mobile-core-nav`）。
- test 固定值反查：`rg -rn "20261003-mobile-core" src/test/` 无命中；改动后再次确认 `src/main/static/*.js` 与 `src/test/js/` 中不含新键字面量（`taskActivityCenter.test.js:697` 的「键字面量只能出现在 index.html」守卫通过；新样式测试只用动态取的 `cacheKey` 断言同值，不写死新键）。

## 3. S-4 CSS 字节校验

- `styles.css` 末尾逐字追加 `/* mailbox-suspension-contract:start */ … /* mailbox-suspension-contract:end */` 块。
- 校验：landed 块 == 计划 `mailbox-suspension-02-frontend.md` S-4 块 == evidence `target-suspension.css`，sha256 `00289a6c0a550c9c2946f304bfc00ca48f14767b08e82a2e818b7c571bde7da2`，9395 字节（含尾换行）。
- `mailbox-chat.css` 未改动（`cmp` exit 0，sha256 `0fd35402…`）。

## 4. 实现要点（T1–T4）

- **T1**：新增 `CHIP_SUSPENDED`，固定顺序 全部/关注/待处理/已挂起/已回复/待匹配；`chipParams`/`conversationsParams` 支持 `suspendedOnly`；普通新 mount 暂定「待处理」，首次成功列表 `total=0` 才切「关注」（`defaultProbe`，实例级、不写 localStorage）；用户点 Tab/改筛选/搜索即冻结探测；focus 深链接跳过探测；Tab 计数用同筛选 `page=0&size=1` 的 `total`（可复用当前列表结果，另一个一次请求，独立序号，失败隐藏 span）。
- **T2**：卡片 footer、详情头挂起/取消按钮、banner（仅 suspended）；N 一律用跨账号 `suspensionPendingCount`；已挂起即使 N>0 也 `data-pending=false`；行内原因 section（列表插在被点卡片后、详情插在 header 后）、PUT `{reason: trim||null}`、busy 防重、失败保留输入与表单；取消/结束直接 DELETE，成功后 `returnToMobileList` + 按回包归类（count>0→待处理；0+followed→关注；其余→已回复）。
- **T3**：`mc-mark-resolved` 只进原位确认；`mc-process-confirm` 才 POST `{note:null}`（去掉 `openDialog("mark-unmatched-resolved")`，保留 PendingMailOperationService 原副作用）；回包校验 `id` 与 `processStatus`，账号只取服务端 `resolvedBy`，仅本次成功标签、unmount 清理；成功后才 GET 挂起状态；完成行位于使计数归零的 PROCESSED 来信 article 内 footer 之后且唯一；继续挂起零写、文案改「已继续挂起」；结束 DELETE 恰 1 次。身份经 `GET /api/auth/me`，就绪前禁用新挂起/处理按钮。
- **T4**：S-4 逐字追加；index 统一键；行为测试更新默认语义；新增功能/样式测试；真浏览器验证见下。

## 5. 真浏览器验证（真实生产 HTML/CSS/JS + 一次性 /tmp harness，未提交）

- Harness：`/tmp/mc-verify/harness.html` + `/tmp/mc-verify/server.py`（Python 标准库静态服务 + /api 桩），仅服务真实 `styles.css`/`mailbox-chat.css`/`mailbox-chat.js`，不引用预览模拟适配器，无轮询/示例数据。仓库内未落任何 harness 文件。
- 桌面 1280×800：
  - 六个 Tab 顺序 全部/关注/待处理/已挂起/已回复/待匹配；默认进入待处理（`aria-pressed=true`，计数「待处理1」）；文档无横向溢出（scrollWidth=innerWidth=1280）。
  - 卡片 footer：未挂起 N=2 →「2 条待处理」`data-pending=true`；已挂起 N=0 →「已挂起 · 0 条待处理」`data-pending=false`、按钮「取消挂起」。
  - 详情：`mc-suspension` 为 `.mc-actions` 首个子元素；banner 背景 `rgb(241,245,249)`；详情按钮 border `rgb(184,196,211)`、卡片按钮 border `rgb(203,213,225)`；已挂起 Tab 文字 `rgb(71,85,105)`、下划线 `rgb(100,116,139)`；原因两行保留；`getComputedStyle(el,'::before').content === "none"`（无图标伪元素）；无横向溢出。
  - 行内原因：插在被点卡片之后，打开即聚焦 textarea，`maxlength=500`；499 字符+换行 → 计数「500 / 500」且无溢出；Escape 关闭并把焦点还给触发按钮。
  - 处理确认：第一次点击「待处理」→ 原位出现「取消 / 确认」，`__reqs` 中 0 次 POST；Escape 取消 → 回到「待处理」，仍 0 次 POST，焦点回到「待处理」；再确认 → 恰 1 次 POST。
  - 完成行：挂起且计数 0 的专家，唯一 `.mailbox-suspend-completion-line` 位于 PROCESSED 来信 article 内部；点「继续挂起」→ 文案「已继续挂起…」，DELETE 数 0，保留「结束挂起」。
- 移动 390×844：Tab chip / 卡片按钮 / 行内主次按钮 `min-height:44px`，原因 textarea `font-size:16px`，`data-mobile-pane=list` 单栏，文档无横向溢出（scrollWidth=390）。
- 限制：处理确认成功后“已处理 · admin”标签在桩环境被随后的原静默刷新（桩 messages 恒返回 MANUAL_REVIEW）回滚——与单元测试中「需服务端把该行变为 PROCESSED」的既有约束一致，非组件缺陷。完成行的“结束挂起→DELETE 且切 Tab”与“失败重试”只在单元测试覆盖，未在真浏览器复跑（预算内未执行）。

## 6. 验收标准逐条对照

- I-1：pending total>0 保留待处理且只发一次列表请求；total=0 切关注并重查；focus 不被覆盖（单测 + 浏览器）。✔
- I-2：结束仅依据 GET 状态；写失败不移卡/不清输入；已挂起 0 条仍在挂起页（单测 + 浏览器）。✔
- I-3：结束前 0 DELETE；继续挂起 0 DELETE 且改文案；计数>0 无提示行；无锚点不生成，仅保留 banner（单测 + 浏览器）。✔
- I-4：count>0→待处理 / 0+followed→关注 / 其余→已回复（单测 3 分支）。✔
- I-5：convEpoch/paneEpoch/seq 守卫；迟到挂起回包不覆盖新专家（单测）；草稿/滚动保持（既有 mobile-core 用例全绿）。✔
- I-6：原因 trim/500/转义/换行保留；XSS 纯文本；`mailbox-chat.css` 字节不动；无 inline；`::before content:none`（单测 + 浏览器）。✔
- I-7：点待处理与取消 0 POST；确认恰 1 次且 body `{note:null}` 不带操作人；回包 id 不符按失败；账号取服务端；无新 dialog/alert/confirm/prompt（单测 + 浏览器）。✔
- S-1..S-4：六 Tab、颜色/下划线/计数、独立 footer/banner/行内三类交互、S-4 字节一致（单测 + 浏览器）。✔
- 新 DOM id 源文本断言：新交互未引入新 id；样式测试断言 `id="view-mailbox"`/`id="mailboxList"` 存在于 index.html（K-dom-stub-tests-hide-dangling-refs）。✔

## 7. 偏差

- 身份读取失败不在 mount 阶段调用 `hostShowStatus`，改为在用户点击挂起/处理时给出「登录状态读取失败，请刷新重试」，并在就绪前禁用按钮。原因：mount 阶段弹全局错误会被既有非授权契约测试 `mailboxOutboundAttachments.test.js:1601`（断言取消选择零提示）判红，且计划“既有错误区域”未指定具体节点。风险：低（生产 `/api/auth/me` 正常时无差异）。
- 真浏览器未复跑“结束挂起→DELETE 切 Tab”“失败重试”；其余真浏览器项已记录，这两项由单测覆盖。
- 未运行 `mvn test`（本 child 门禁为独立 JS 测试；未改 Kotlin/资源引用，不影响其 JS 阶段）。

## 8. 未运行/未覆盖

- 未 push / merge / rebase / squash / amend / reset；未改授权外文件；未改迁移；未联网/连库/发信/部署。
- `mvn test`、Flyway IT 未运行（非本 child 门禁）。
