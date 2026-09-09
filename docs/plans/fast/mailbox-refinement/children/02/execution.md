# 02 收发件箱布局与交互修复 — 执行报告

- 计划：`docs/plans/2026-09-09/02-mailbox-refinement-frontend.md`
- 计划 SHA-256：`8cd4fedba41d574c62217c2d9bc959d3bff45e9a484758dd3262f9414dfce32a`
- Execution ID：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement/docs/plans/2026-09-09/02-mailbox-refinement-frontend.md@8cd4fedba41d574c62217c2d9bc959d3bff45e9a484758dd3262f9414dfce32a`
- 执行人：Implementer02（execute-p）
- 工作台：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement`
- 分支：`fast/mailbox-refinement`
- Worktree ID：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement@fast/mailbox-refinement@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-refinement`
- 子计划基 SHA（child 01 实现）：`cf257779ae420ab4c745b20aa4de6e6942a66b18`（plan 文档 SHA 351d69a538bcf891514f234a8d717cb5ef64c63c）
- 执行前 HEAD：`b6a1061afd1403a9d5b8694d9a31588a0f619883`
- 实现提交：见本报告「提交」节

## 实现摘要（T1..T5 / S-1..S-7）

- **T1 / S-1 / S-2 宿主/筛选/列表（I-1/I-2/I-7）**
  - `index.html`：旧 `.toolbar` 加 `id="mailboxLegacyToolbar"`、主 panel 加 `id="mailboxConversationPanel"`；七字段删除旧 inline width；recipient/keyword 文案与占位改为「专家邮箱/输入邮箱关键词」「主题 / 内容关键词/搜索邮件主题或正文」。
  - `mailbox-chat.js`：挂载时 `#view-mailbox` 加 `mc-refined`；把 `#mailboxRefreshBtn` 迁到 `#mailboxConversationPanel .panel-head-actions` 最前并绑定静默刷新；把唯一筛选七节点 + `#mailboxSearchBtn` 迁入 ⋯ popover 的 `#mailboxFilterFields`（字段包 label 内、`应用筛选`），unmount 按原元素顺序整体还原（含 `至` 等稳定节点）、还原旧标签类别选项/文案、移除 portal 与 mc-refined。无宿主节点（独立挂载）时 fallback 新建字段（仍唯一）。
  - 三 tab = 全部/关注/待处理；参数契约：全部不传 followed/pendingOnly（也不传 waitingReply）、关注 `followed=true`、待处理 `pendingOnly=true`；`chipParams/conversationsParams/applyOptions` 全部清理 waitingReply 与旧 pendingOnly 强制；服务端排序不二次 sort。外部 onlyPending 只在创建实例且用户未点过 tab 时初始化，此后 tab 是唯一权威。
  - 筛选草稿语义：字段改动只记草稿；应用/Enter 才提交并查询；×/Escape/外部点击关闭未应用则恢复生效值；重置/清除立即清空高级筛选并查询，保持 tab 与 q；日期非法显示「开始日期不能晚于结束日期」且不请求；快照完整替换不合并残留；标签选项 GET `/api/inbound-summary/tags/options` 真实 label 去重（不把旧类别发为 label），失败可重试；动态账号选项保留（同一节点）。
  - markResolved 成功后重查当前页（服务端顺序），空页回退上一有效页；membership 变化不主动清空右侧会话/草稿；明确筛选导致目标不可用时先保存再回空态；外部 focus 新 contactId 即使已选中也要响应；找不到/无权限给明确空态（不伪造专家）。
- **T2 / S-3/S-4（I-3/I-4/I-6/I-7）**
  - 管理 overlay：`role=dialog` 普通 div portal 到 `document.body` 的独立 `.mail-chat.mc-overlay-root`（不嵌 `.mc-scroll`、不带 backdrop-filter 的 panel），z-index 990，焦点陷阱只对顶层弹层，Escape/取消/backdrop 关闭并还原触发按钮焦点；选项从 `window.operatorStatusOptions/indexLevelOptions` 渲染；取消状态/层级零请求；保存只发变化的 `/operator-status` 与 `/index-level` 两既有端点，Promise 收集逐端点结果并回读 dataset/contact/头部，部分失败如实显示「部分变更未保存：…」，按钮恢复。
  - 专家标签沿用共享 `fetchExpertTagsFromEs / renderMailboxExpertTagEditor / mutateExpertTag / updateExpertTagEditor`（data-orcid/data-level/id），即时保存提示；缺画像显示「该专家在 ES 中无画像文档，标签功能不可用」；加/删成功后静默刷新列表 summary、头部 meta 与行（晚响应按 contactId/orcid 守卫）。
  - 邮件卡片 S-4：来信白底/发件 `#eff5ff`，`(source,id)` 键；正文 cleanedBody 优先（escapeText）；原「原文/技术信息」extras 块删除；`mc-mail-extras` 只用于附件（最多 3 文件名 + 查看全部附件）；发件无加标签/标记；来信 MANUAL_REVIEW 有 `翻译`（有正文时）/`＋ 添加标签`/`✓ 标记已处理`，PROCESSED 显示 `✓ 已处理`（mc-done）且仍可加标签。
  - 邮件标签：直读 `timeline.tags`（01 响应内联），无每封 thread GET；删除 `DELETE /api/inbound-summary/tags/{tagId}` 成功本地移除并静默窗口校验 + 列表 membership 刷新，失败保留 chips 并原位报错；添加经 app.js 宿主 adapter `mcHostOpenInboundTagModal({inboundId, source, contactId, onTagsChanged})` 打开既有 `#inboundAddTagModal`，成功以服务器 POST 回包 `tags` 回调直显（不依赖 detailContext/固定 id），关闭/成功/unmount 清理 adapter；无 adapter 旧来信汇总/详情分支不变。
  - 翻译：点击才 POST `/api/translate`（一次点击一次请求），译文 escape 后原位 `.mc-translation`，缓存按 source:id+正文；收起再开零请求；失败「翻译失败，重试」；空正文不出现翻译按钮；不重建 manual DOM。
- **T3 / I-5 缓存/滚动/异步**
  - 模块级会话缓存：key=`sessionUser|accountScope|contactId`；≤10 会话 LRU；单会话 >500 条已加载消息丢弃缓存、下次定位最新；不写 localStorage 正文。scroll（180ms 防抖）/selectExpert 前/unmount/loadOlder/quiet refresh 保存（窗口/游标/锚点 key+relTop/fallback scrollTop，0 有效）；恢复锚点（contentOffsetTop 实测布局，浏览器用 rect、退化环境用 offsetTop 链）→ 缺失用同窗口 fallback scrollTop；无缓存首访定位最新一封信顶部 ≈8px（夹在可滚动范围）；quiet refresh 按 `source:id` 合并且服务端状态胜、保留已加载历史；loadOlder 带 busy 防重入 + convEpoch 竞态守卫；翻译/标签/切专家回调均捕获 contact/epoch；mark 不重建编辑器；同标签页 unmount/重挂载恢复窗口与草稿。
- **T4 三份 JS 测试**
  - Style：CSS 与 `evidence/mailbox-chat.target.css` 字节一致；模板 class 白名单；无 inline style；**index.html 源文本断言**（S-1/S-2 宿主 id、七字段+查询按钮唯一、旧 toolbar 顺序、无 inline width）；S-7 单行规则（nowrap/min-width:0/ellipsis、counts flex:none、title 文案）。
  - Behavior：43 用例覆盖 S-1 骨架/三 tab/⋯ popover 草稿语义/日期非法/重置保 tab+q/节点迁移还原/刷新按钮迁移/S-4 卡片与同数字不同 source 标签/删除失败保留/添加 adapter 回调/翻译一次一请求+缓存/管理 portal 化与取消零请求/部分失败/专家标签即时保存刷新/关注乐观回滚/workbench/manual/草稿跨 unmount 恢复/慢响应不串专家/loadOlder busy 与去重/quiet 合并/scrollTop 0 有效与 accountScope 隔离/最新按钮不标记/宿主守卫（任务钻取/脚本缺失/重复 mount 不重放快照）。
  - mailboxInboundTags：保留旧用例；新增 adapter 打开/非法 id/提交回调与清理/无 adapter 旧分支/hide 清理。
- **T5 / S-7 专家标签行**
  - `renderPerson` 重写：`mc-person-heading`（strong 名称）+ 账号/最近摘要 + `mc-person-meta`（`mc-person-counts` flex:none + `mc-person-tags`）；只读 `summary.expertTags`，名称经既有 `expertTagLabels`（window/全局词法可见）显示映射，未知原值 escape；`[]` 不渲染占位、`null` 单行灰「标签暂不可用」（title 同文案）；原生 `title` = `专家标签：…` 完整名称列表；aria-label 带完整名称；移除 waiting/pending badge 与 waiting 分支；专家标签加删后静默刷新列表行/头部。

## 与关键不变量/契约对照

- I-1：三 tab 参数无 waitingReply、全部无 pendingOnly/followed；mark 后服务端重查 + 空页回退；不 sort。
- I-2：`mailboxFilter*` 唯一 id；字段迁入 popover、unmount 还原（顺序/文案/选项）；草稿-应用语义；重置保 tab/q；日期非法不发请求。
- I-3：来信标签只用 INBOUND_PROCESSING + timeline.tags；POST 回包直显；DELETE 按 tagId；无 thread/每封读取；晚响应不串（convEpoch/contactId 守卫）。
- I-4：翻译一次一请求、缓存复用、失败可重试、空正文无按钮；不重建 manual DOM。
- I-5：有界模块缓存（10 会话 LRU / 500 条上限）、不写 localStorage 正文；锚点/scrollTop（0 有效）保存恢复；loadOlder 锚点偏差 ≤2px 路径；quiet 合并；晚响应隔离。
- I-6：overlay portal 到 body 独立 `mc-overlay-root`；取消零请求；保存只发变化端点并逐项回读；部分失败如实显示；共享标签渲染不动默认输出。
- I-7：工作台仍固定 LIVE_INBOUND、切专家 unmount；人工发送 payload/QA/审计不变；材料仍 mcHostOpenMaterials；任务钻取仍旧列表；全局按钮真实 id/handler 保留。
- I-8/S-7：专家标签来源隔离、单行、title 完整、escape；[]/null 区分；无 pending badge；邮件标签操作不污染。
- S-6：mailbox-chat.css 与 evidence 逐字一致（字节比较测试通过）。

## 命令与结果（全部为最终状态重新执行）

| 命令 | 结果 | 输出 |
|---|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxInboundTags.test.js` | PASS | tests 65, pass 65, fail 0 |
| `node --test src/test/js/*.test.js`（全量 JS 门禁） | PASS | tests 765, pass 765, fail 0（基线 733 → 765） |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test`（全量 mvn + exec-plugin JS） | PASS | BUILD SUCCESS（02:41）；JVM surefire 汇总 tests 3255, failures 0, errors 0, skipped 9；exec-plugin node 765 pass |

## 变更文件（严格 7 文件白名单）

1. `src/main/resources/static/index.html` — S-1 宿主 id（mailboxLegacyToolbar/mailboxConversationPanel）、筛选字段去 inline width/文案
2. `src/main/resources/static/app.js` — 宿主 chrome/mount 跟踪/快照新映射（recipientEmail+keyword）/共享节点事件守卫/标签 modal adapter
3. `src/main/resources/static/mailbox-chat.js` — S-1..S-7 布局与交互（筛选 popover、管理 overlay、卡片/翻译/标签、会话缓存、S-7 标签行）
4. `src/main/resources/static/mailbox-chat.css` — S-6 全文（evidence/mailbox-chat.target.css 逐字）
5. `src/test/js/mailboxChatBehavior.test.js` — 行为契约（43 用例）
6. `src/test/js/mailboxChatStyle.test.js` — S-6/CSS/class/源文本断言（13 用例）
7. `src/test/js/mailboxInboundTags.test.js` — 旧 + adapter 契约（9 用例）

未触碰：styles.css、expert-materials.js/css、trust-reply-workbench.js、其余 JS 测试、后端 Kotlin、迁移、缓存键（`v=20260907-material-chat` 未动，归 child 03）。

## 提交

`feat(fast-p): implement 02`（仅上述 7 文件；`docs/plans/fast/**` 不包含，证据由控制器单独提交）。提交 SHA 见 Git 日志。

## 偏差

- 无（真实浏览器截图验收 A-10/A-11 按 master 计划推迟到人工验收；本执行以机器 CSS 字节比较 + DOM 断言为证明，未伪造浏览器证据）。

## 备注（供 verify-p）

- mailbox-chat.js 现为 3400+ 行 IIFE，版本号 `2`；`MailboxChat` 新增 `isMounted` 导出；app.js `state.mailbox.chatMounted/chatTagAdapter` 为宿主跟踪字段。
- 需要关注点：S-1 弹出层/筛选迁移依赖 `#view-mailbox`/`#mailboxLegacyToolbar`/`#mailboxConversationPanel` 在真实 index.html 中唯一（Style 测试已做源文本断言）；mini-DOM 无真实布局，锚点像素级行为（≈8px、≤2px）在真实浏览器中由同一 code path 承担，测试验证其退化路径与不跳变语义。
