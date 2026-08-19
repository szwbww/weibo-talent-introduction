# Manual Acceptance — docs/plans/2026-08-19/00-grounded-coverage-master.md

## Epoch 2 — 2026-08-19T12:30:43Z

- Reviewed code boundary: af1723f37021328f8ffa61261504727e514fbb4b..a7cceb2e3fdec25cecd4e3582135edefb3a5447f
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| P1-A1 | Yes | 骨科来信工作台 | 5 个事实；五个指定 chip；`GROUNDED · 依据充分` | PENDING | — | — | — |
| P1-A2 | Yes | 政府支持问法 | `Agency credentials and government cooperation` 与 `Programme sponsorship and organising level` 均绑定 | PENDING | — | — | — |
| P1-A3 | Yes | 存量状态迁移 | 正常加载、无 500/422、显示 STALE 且回落系统选择 | PENDING | — | — | — |
| P1-A4 | Yes | 企业身份问法 | 不绑定 id=6；绑定企业匹配事实 | PENDING | — | — | — |
| P1-A5 | Yes | 高频问法回归 | 报酬→薪资与资金支持；IP→知识产权边界；各 1 条 | PENDING | — | — | — |
| P1-A6 | Yes | 运营 keywords 保留 | 自定义词仍在；三个合作形式词已追加；`updated_at` 未刷新 | PENDING | — | — | — |
| P2-A1 | Yes | 骨科信 `[ASK_ENUM]` | `available=true enumerated=5 claimed=5 unrecognized=0 kind=FALLBACK` | PENDING | — | — | — |
| P2-A2 | Yes | 未覆盖问法 `[ASK_ENUM]` | `unrecognized>=1`；响应含逐字 quote | PENDING | — | — | — |
| P2-A3 | Yes | 影子期 UI/判定回归 | 三封信状态、计数、chip、处理方式一致；无新元素 | PENDING | — | — | — |
| P2-A4 | Yes | LLM 关闭 | 工作台正常；ASK_ENUM unavailable；状态与事实不变 | PENDING | — | — | — |
| P2-A5 | Yes | 自动回复 LLM 回归 | 未新增枚举器调用；ASK_ENUM unavailable | PENDING | — | — | — |
| P2-A6 | Yes | 外发内容隔离 | 正文不含未识别项 label/quote 或相关字样 | PENDING | — | — | — |
| P3-A1 | Yes | 鼠标拖拽调序 | 拖后顺序变更；刷新保留；计数不变 | PENDING | — | — | — |
| P3-A2 | Yes | 键盘调序 | 左移、焦点保留；首位继续左移无变化无错误 | PENDING | — | — | — |
| P3-A3 | Yes | 调序清空版本 | 确认提示正确；版本与整合结果清空 | PENDING | — | — | — |
| P3-A4 | Yes | 拖至删除按钮 | 换位、不删除、计数不变 | PENDING | — | — | — |
| P3-A5 | Yes | 增删回归 | 新增末位；删除原位；其余顺序不变 | PENDING | — | — | — |
| P3-A6 | Yes | 禁用态 | 拖拽/按键无效、无确认框 | PENDING | — | — | — |
| P3-A7 | Yes | 样式对照 | 手柄、透明度、落点线、chip 尺寸与既有样式满足 P3 S-2 契约 | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: a7cceb2e3fdec25cecd4e3582135edefb3a5447f
- Reporter: —
- Timestamp: —
- Note: —
