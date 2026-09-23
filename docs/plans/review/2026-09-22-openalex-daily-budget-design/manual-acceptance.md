# Manual Acceptance — OpenAlex Daily Budget Design

## Epoch 2 — 2026-09-23

- Reviewed code boundary: `e2247680592603b091af791ef3629d70739a015b..f51512591f27ab1f21a81302f7f5f767d7f06695`
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | 测试环境模拟官方余额10 credits、预付100 credits；触发11个普通列表请求，观察日志与预算展示。 | 最多10个发出；免费剩余0、预付仍100；模拟reset后不重复起新预算。 | PENDING |  |  |  |
| A-2 | Yes | 各来源模拟100条唯一记录，OpenAlex下载延时；队列容量200、低水位100。启动统一手动入口，放开延时后查看来源任务和队列。 | 前源慢时后源仍有执行记录；容量不超过200；低于100恢复；已采集数与已处理数分别显示。 | PENDING |  |  |  |
| A-3 | Yes | 测试100篇、已保存50篇抽取结果，注入ES写入失败；重启、恢复ES并启动同一查询。 | 最终100篇完成；50篇已存结果不再下载；专家唯一数和补全任务唯一数等于无故障基线。 | PENDING |  |  |  |
| A-4 | Yes | 测试窗口1分钟、tick30秒、模拟reset两分钟后；观察自动续跑，再暂停、跨reset重启，最后人工恢复。 | 窗口交接后30秒内有后续执行；人工暂停跨reset/重启不派发；恢复从未完成任务继续。 | PENDING |  |  |  |
| A-5 | Yes | 测试环境含可晋升、无效邮箱、身份歧义、已有APPLICATION各一条；停用外发。分别用手动/定时入口执行，并在后台运行时点击检查回复。 | 两入口scope/来源一致；不合格或歧义不误晋升/补全；APPLICATION不重建候选；运营字段保持；合格专家自动补全；检查回复可启动；邮件发送数0。 | PENDING |  |  |  |

## Human Sign-off

- Decision: PENDING
- Boundary: `f51512591f27ab1f21a81302f7f5f767d7f06695`
- Reporter: user
- Timestamp: PENDING
- Note: PENDING
