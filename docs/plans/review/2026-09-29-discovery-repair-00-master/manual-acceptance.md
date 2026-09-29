# Manual Acceptance — docs/plans/2026-09-29/discovery-repair-00-master.md

## Epoch 1 — 2026-09-29

- Reviewed code boundary: `1cd59e31164d11e962203e31c9f61310f2bc5912`..`075dc3e0c014a0cae6a908f871e65784fb944881`
- Machine report epoch: 1 (`docs/plans/review/2026-09-29-discovery-repair-00-master/machine-verification.md`)
- Status: PENDING
- Environment: retained worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master` @ branch `fast/2026-09-29-discovery-repair-00-master`; JDK 11 `zulu-11`; item 2 needs the app running against an isolated MySQL (`talent-introduction.scheduling.enabled` per deployment, sync mode default).

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1a | Y | 在隔离环境执行 01、02 的离线案例（01：子计划给定六份原文重放 + 合成案例；02：`orcid-collision-20260929` fixture 与三作者响应） | 错误邮箱关系 0；歧义作者补全写入 0（学术写入/再核验/标题请求均为 0） | | Machine side already green: `machine-verification.md` CM-1/CM-2, fresh commands 1–2 | | |
| A-1b | Y | 打开深度发现弹窗，通过 04 面板保存间隔 3 小时 | 保存动作启动发现 0 次；DB 只有 1 行设置；页面显示“已设置每 3 小时执行一次”与北京时间下次候选时间 | | Compare with CM-3/CM-4; API `GET/PUT /api/expert-discovery/schedule` | | |
| A-1c | Y | 查看 03 固定时钟下的启动记录（保存后 3/6/9 小时候选、跨午夜、运行中跳过不补排） | 定时按 3 小时生效；运行中的触发被跳过且不补排；保存不打断当前任务 | | Fixed-clock evidence in `children/03/verify-log.md`, CM-3 | | |
| A-1d | Y | 再手动启动一次发现（手动入口） | 手动执行仍可用，且与定时互斥（并发时 409/跳过一次）；查询条件、限额、检查点与取消语义不变 | | CM-5 | | |

## Human Sign-off
- Decision: PENDING
- Boundary: 075dc3e0c014a0cae6a908f871e65784fb944881
- Reporter:
- Timestamp:
- Note:
