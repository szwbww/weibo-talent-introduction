---
id: K-cron-echo-whitelist-not-blacklist
domain: frontend
created: 2026-08-15
last_used: 2026-08-15
hit_count: 0
source: create-p:p1-cron-echo-whitelist
severity: P1
---

把 cron 表达式反解成「每小时 / 每天 / 每周一 + 时间选择器」的**回显**逻辑，必须是**白名单**匹配：
只有完全符合预设格式的表达式才映射到预设频率，其余一律落到「自定义 cron」并原样回填。
黑名单式判断（"不是这几种就当每天"）会造成**静默数据损坏**。

## 事故形态（`showBatchConfigEditor`，2026-08-15 发现）

判断只看 `dow === "?"` 就认定「每天」，从不校验时/分字段是不是单值：

```js
var hour = cronParts[2], min = cronParts[1], dow = cronParts[5];
if (hour === "*" || hour === "*/1") { freq = "hourly"; ... }
else if (dow === "MON" || ...) { freq = "weekly"; ... }
else if (!dow || dow === "?" || dow === "*") { freq = "daily"; time = hour.padStart(2,"0") + ":" + min.padStart(2,"0"); }
else { freq = "custom"; ... }
```

输入 `0 0 9-17 * * ?` 时：`hour = "9-17"` → 落到 daily 分支 → `time = "9-17:00"` →
`<input type="time">` 拒绝非法值把 `.value` 置空 → 页面上 `9-17` **再无痕迹** →
保存时 `(time || "09:00")` 兜底成 `09:00` → 落库 `0 0 9 * * ?`。
**发送频次从 9 次/天静默降为 1 次/天，无任何提示。**

更隐蔽的一类：`0 0 9 1 * ?`（每月 1 号）。`hour = "9"` 是单值，`time` 能算出合法的 `09:00`，
回显"完全正常"，但**日字段 `1` 被静默丢弃**，保存后变成每天执行。

## 正确写法

```js
hourly : /^0 0 (\*|\*\/1) \* \* \?$/
daily  : /^0 (\d{1,2}) (\d{1,2}) \* \* \?$/    且 min∈[0,59] 且 hour∈[0,23]
weekly : /^0 (\d{1,2}) (\d{1,2}) \? \* MON$/   且 min∈[0,59] 且 hour∈[0,23]
其余   : custom + 原样回填
```

正则本身不够 —— 还要做数值范围判定（`0 70 9 * * ?` 的分钟越界）。

## 另一个必须显式处理的点：清空

编辑器 DOM 是复用的，不是每次重建。非 custom 分支**必须显式** `setVal(cronInput, "")`。
只在 custom 分支写这个框的实现，会在「编辑任务 A（custom）→ 取消 → 编辑任务 B（daily）→
手动切到自定义」时，把 A 的表达式保存到 B 上。

## 保存侧不要跟着改

自定义模式提交输入框原串是正确的（已有回归测试锁定）。修回显 bug 不需要动保存逻辑，
也不需要动后端 —— `CronExpression.parse` 的校验一直是对的。
