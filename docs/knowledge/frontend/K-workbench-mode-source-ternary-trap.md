---
id: K-workbench-mode-source-ternary-trap
domain: frontend
created: 2026-08-18
last_used: 2026-08-21
hit_count: 1
source: create-p:02-preview-into-workbench
severity: P1
---

经验：`trust-reply-workbench.js` 的 `validateMount()` 用**二元三目**校验 mode 与 source 的配对：

```js
const expectedSource = options.mode === MODES.SIMULATION ? SOURCES.TRAINING_MAIL : SOURCES.LIVE_INBOUND;
```

只要 `MODES` 里新增第三个模式，它就会被**静默当作 LIVE 放行**——拿到可生成、可采用、可写状态的
全部 LIVE 能力，而 mode 检查（`Object.values(MODES).includes`）已经先放行了它。
这类"枚举扩了一位、二元判定没跟上"的缺陷编译期与语法检查都发现不了。

正确做法：模式与来源的配对一律用**显式映射表**驱动，缺项即拒绝：

```js
const MODE_SOURCE = Object.freeze({ SIMULATION: TRAINING_MAIL, LIVE: LIVE_INBOUND, ... });
const expectedSource = MODE_SOURCE[options.mode];
if (!expectedSource) return rejectMount(host, "工作台模式无效");
```

配套：新增模式若是只读的，写操作闸门要收口在唯一的 `requestJson()` 入口
（`trust-reply-workbench.js:204`）做前置断言，而不是在每个调用处判断——后者必漏。

同一类对称性问题还有卸载：`unmountLiveTrustReply` 的调用点共 8 处
（`app.js` 的 1627 / 9722 / 9769 / 10019 / 10044 / 10058 / 10099 / 11567）。
新增第二个宿主时不要在 8 处各加一行，应收进一个 `unmountMailboxTrustReplyHosts()` 统一替换，
否则下次新增宿主必然又漏一处。

关联：[[K-shared-workbench-fixed-mode-host-adapter]]、[[K-ai-reply-modal-helper-scope]]。
