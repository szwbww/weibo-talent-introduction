# 02 基线命令结果（控制方记录，基线提交 9d7e389→c486c5c 代码态）

| 命令 | 结果 |
|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | exit 0 |
| `node --test src/test/js/*.test.js` | tests 1385, pass 1367, fail 18, skipped 0, todo 0 |
| `cmp src/main/resources/static/mailbox-chat.css docs/plans/2026-10-03/mailbox-suspension-evidence/baseline-mailbox-chat.css` | exit 0（字节一致，sha256 0fd35402…） |
| `node --test src/test/js/mailboxSuspension.test.js src/test/js/mailboxSuspensionStyle.test.js` | 基线不存在（本片新增） |

## 18 个基线失败（全部为"11 个 ?v= 缓存键必须同值"断言；根因：并行工作区快照把 index.html:2355 mailbox-chat.js 一处的键改为 `20261003-mobile-core-03-generic-followup`，其余 10 处仍为 `20261003-mobile-core-03`）

| # | 失败用例（test at file:line） |
|---|---|
1. src/test/js/batchEmailVerification.test.js:932 I5: every versioned static asset shares one release key
2. src/test/js/batchSenderFilter.test.js:224 keeps the 11 versioned static asset keys on one common value (I-3)
3. src/test/js/checkRepliesRelocation.test.js:69 I-3: all eleven cache-busted assets share one current key
4. src/test/js/contactTimingStyle.test.js:180 全部 ?v= 同值，数量与位置不变，键格式合法
5. src/test/js/discoveryScheduleSetting.test.js:328 keeps every versioned asset on one non-legacy key (S-2)
6. src/test/js/mailOpenTracking.test.js:274 all eleven versioned assets in index.html carry one cache key
7. src/test/js/mailboxChatStyle.test.js:313 S-4 既有 11 个版本化资源统一为新键、标签与注册顺序不变
8. src/test/js/mailboxTemplateReferenceStyle.test.js:157 index.html 的 11 个版本化资源统一为新键且旧键已移除
9. src/test/js/manualReplySubjectPrefill.test.js:93 I-5: all eleven cache-busted assets share one current key
10. src/test/js/meetingConfirmationAssets.test.js:38 恰好 11 个带 ?v= 资源且全部等于 ${CACHE_KEY}，无旧键残留
11. src/test/js/mobileCoreNavigation.test.js:130 CSS exactly matches approved block, unique controls, resources share one new key
12. src/test/js/overlayAndDialogContrast.test.js:62 I-8: the eleven cache-busted assets share one key, in order
13. src/test/js/ragKnowledgeBasePage.test.js:340 G-5：11 处 ?v= 缓存键同值且等于 ${CACHE_KEY}，注册顺序合规
14. src/test/js/ragWorkbenchRender.test.js:393 G-8: index.html keeps the shared workbench script include and the G-5 single key
15. src/test/js/senderBindingDisplay.test.js:393 switches every already-versioned resource to the one current key without adding or retiring one
16. src/test/js/taskActivityCenter.test.js:676 I-8: all eleven versioned assets share the same cache key and the old one is gone
17. src/test/js/taskRecordsSemantics.test.js:275 08: task20240 preserves every source reason, safe legacy values, and the shared renderer
18. src/test/js/trustReplyWorkbenchSharedMount.test.js:180 G-5: the eleven cache-busted assets share one key (${CACHE_KEY})

- 测试均从 index.html 动态取第一个 `styles.css?v=` 作为 CACHE_KEY，再断言 11 处同值；`grep -rn "20261003-mobile-core" src/test/js/` 无固定值命中。
- 计划 T4 要求 02 把 11 处统一为 `20261003-mailbox-suspension`；届时这 18 个失败应全部转绿（除非个别测试另有独立断言）。
- 基线全量失败清单见 /tmp/fastp-baseline-js.log（worktree 外临时日志）。
