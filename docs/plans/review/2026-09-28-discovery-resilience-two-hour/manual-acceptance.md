# Manual Acceptance — discovery-resilience-two-hour

## Epoch 2 — 2026-09-28T05:10Z

- Reviewed code boundary: `f98e27c7538d091bfcdecfcb6ffc10360a35ba04..8e622680c9fafeb68c6b220da8ef47c947ff2fcd`
- Machine report epoch: `machine-verification.md` — Epoch 2 (machine result PASS)
- Status: PENDING

Items are copied from the master plan's 人工验收清单 (A-1..A-8) with its 前置条件/操作步骤/预期结果; no optional product requirements were added. Human results must be recorded with an item verdict plus evidence or note; the agent never performs or infers these checks.

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | YES | 隔离验收环境配置 OpenAlex+Crossref、每页 100；OpenAlex 第一页成功、第二页 C7 连续 3 次指定握手中断后返回 100 篇且无下一页；Crossref 返回 100 篇后穷尽；恢复列表 30s/120s/300s，固定输入可复跑 fixture。手动启动深度发现，打开任务弹窗，查看完整进度日志与 fixture 的按时间排列请求记录。 | OpenAlex 第二页前三次均 C7，随后 Crossref 至少开始一次，再回访 OpenAlex C7；回访不早于第三次失败后 30 秒；OpenAlex 最终 200 篇、sourceFailureCount=0、无终态 SEARCH_FAILED，游标仅成功后标 EXHAUSTED。 | PENDING | — | — | — |
| A-2 | YES | 验收环境仅启用 OpenAlex，预置 Codec 生成的 ACTIVE/C7；全部搜索请求返回相同握手中断；单轮时间足够。启动 → 等到所有有限恢复结束 → 查看请求总数与任务状态 → 重启验收应用并撤除故障 → 再运行相同条件。 | 第一轮 12 次请求、论文 0、SEARCH_FAILED 一次、任务 FAILED；检查点仍 ACTIVE/C7。第二轮第一请求为 C7，不从第一页重扫，不从进度日志自动复活旧等待。 | PENDING | — | — | — |
| A-3 | YES | A-1 场景，且已收录的 1 位专家在等待期间由已有补全流程晋升。保持弹窗轮询；查看表格下 summary 与停止原因；请求 `GET /api/task-progress/EXPERT_DISCOVERY/logs?executionId=<本次id>&batchOnly=false`；故障恢复后刷新。 | 等待时显示 `RETRY_WAIT`、`1/3` 和明确北京时间；晋升显示 1 仍保留等待提示；恢复开始后无过期 nextRetryAt，最终文案不再称“等待”；完整日志有安排和回访事件，无每 100ms 一条的刷屏。 | PENDING | — | — | — |
| A-4 | YES | 验收环境用真实两小时 cron 或可控时钟推进；北京时间 02:00 有一次正常完成或 PARTIAL_SUCCESS 记录，04:00 前空闲。到 04:00 查看是否有新的 SCHEDULED 记录；让该轮持续到 06:00 并查看记录数；当前轮结束后到 08:00 再次查看。 | 04:00 新增且继续原查询游标；06:00 不新增、不并发、不补排；08:00 再新增；02:00 历史记录不会封锁 04:00；初始化文案显示当前 cron 和 Asia/Shanghai。 | PENDING | — | — | — |
| A-5 | YES | 同步模式、某轮处于等待恢复；下个定时点可推进。点“取消任务”并检查随后请求记录；到下一定时点；再取消该轮，设置 `EXPERT_DISCOVERY_CRON=-` 并重启；跨过两个定时点后手动启动。 | 取消后不发下一次恢复搜索，当前轮 CANCELLED；未关 cron 时下一定时点仍可启动；cron 关闭后两个点均没有 SCHEDULED 新记录；手动可启动且从原入口继续；操作提示不承诺“永久暂停”。 | PENDING | — | — | — |
| A-6 | YES | 隔离环境准备 3 组 fixture（姓名邮箱不匹配、方向不合格、RAW 写入失败）；另有总论文上限 600 且先成功 500 的分页输入。分别运行三组并查任务原因与筛选层；运行 500+恢复后 100+额外下一页场景；查请求计数和 checkpoint 累计量。 | 身份/方向不合格者仍不入筛选层；RAW 失败停为 RAW_WRITE_INCOMPLETE 且不进入 TLS 恢复组，游标不前进；cap 600 时只处理 600，累计增量 600，不请求第 601 篇；没有为提高产量改姓名/邮箱。 | PENDING | — | — | — |
| A-7 | YES | 隔离预算 store 准备一个官方周期，首次响应 429 带 Retry-After；另一组余额低于发现保留门槛；使用验收 stub，不能清空生产账本。运行 429 组并查看恢复请求数；运行低余额组并在下个两小时点再次触发；查看账本、其他来源与补全请求。 | 429/余额不足均 BUDGET_DEFERRED，不进入 30/120/300 秒网络恢复；第二轮不把余额重置 10000；补全保留额度与 UNKNOWN 仍在；其他来源按原规则继续。 | PENDING | — | — | — |
| A-8 | YES | 隔离环境现有 pipeline 已 PAUSED；另建同步模式、deadline 短于下一恢复时刻的运行；禁止在生产切 pipeline。pipeline 环境经过两小时 cron 和恢复 tick；同步环境触发短 deadline 故障；查看状态和后续请求。 | pipeline 保持 PAUSED、没有新窗口；同步轮停止为 TIME_BUDGET 且无超时后的搜索，保留 ACTIVE 入口与 pendingWork，不标穷尽；不会修改 TLS 信任配置。 | PENDING | — | — | — |

## Human Sign-off
- Decision: PENDING (awaiting human-reported results for A-1..A-8 and an explicit sign-off naming boundary `8e622680c9fafeb68c6b220da8ef47c947ff2fcd`)
- Boundary: `8e622680c9fafeb68c6b220da8ef47c947ff2fcd`
- Reporter: —
- Timestamp: —
- Note: —
