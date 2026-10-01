# Manual Acceptance — Emailable 验证结果多选放行

## Epoch 1 — 2026-10-02T00:02:58+08:00

- Reviewed code boundary: `2b036ccce7e9956f6ea27420d8bc9e057a011da2..e6c36e294cb26ce36684a96a0908c305f871e4b4`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| Backend A-1 | Yes | 手动快照 `[deliverable]` | 明细 PASS deliverable；测试箱仅 1 封 | PENDING | `email-verification-allowlist-backend.md` A-1 | — | — |
| Backend A-2 | Yes | risky + `[deliverable]` | SKIP/POLICY_SKIP/NOT_REQUIRED；零邮件、无「邮箱异常」 | PENDING | backend A-2 | — | — |
| Backend A-3 | Yes | undeliverable + 三态列表 | SKIP/REJECTED；零邮件并新增「邮箱异常」或标签失败记录 | PENDING | backend A-3 | — | — |
| Backend A-4 | Yes | 验证关闭及超时 | 关闭时零验证；超时不发送且 `EMAIL_VERIFY_TIMEOUT`/DEFERRED、无异常标签 | PENDING | backend A-4 | — | — |
| Backend A-5 | Yes | 政策变更后的复用及保留 | reused fact 按本次策略 SKIP；原行一年内保留 | PENDING | backend A-5 | — | — |
| Backend A-6 | Yes | 空列表与旧请求 | `[]` 全跳过；缺字段旧请求 PASS 且复用时间；无异常标签 | PENDING | backend A-6 | — | — |
| Backend A-7 | Yes | 非法列表与材料提醒 | 均 4xx；无新执行、零 SMTP；unknown 合法而 invalid 非法 | PENDING | backend A-7 | — | — |
| Config A-1 | Yes | 新旧配置回显 | 旧 C 三态；新 N 仅 deliverable；既有 auto/cron/开关不变 | PENDING | `email-verification-allowlist-config.md` A-1 | — | — |
| Config A-2 | Yes | `[]` 与 undeliverable | `[]` 回显不变；undeliverable 400，配置不变且不发信 | PENDING | config A-2 | — | — |
| Config A-3 | Yes | 旧入口及生命周期 | `[unknown]` 经 legacy PUT、启停、软删均保留；无意外执行 | PENDING | config A-3 | — | — |
| Config A-4 | Yes | 定时与手动快照隔离 | X 仅 deliverable/risky SKIP；Y 仅 risky/PASS；来源配置和 X 快照不变 | PENDING | config A-4 | — | — |
| Frontend A-1 | Yes | 新建与旧配置 | 新建仅 deliverable、开关关闭；保存/重开和旧配置回显正确 | PENDING | `email-verification-allowlist-frontend.md` A-1 | — | — |
| Frontend A-2 | Yes | 手动覆盖与恢复 | 差异/确认页正确；重选和清空恢复默认；来源不被改写 | PENDING | frontend A-2 | — | — |
| Frontend A-3 | Yes | 全空、关闭、材料提醒 | 空选择保留；关再开不补勾；材料提醒禁用且切回不开启 | PENDING | frontend A-3 | — | — |
| Frontend A-4 | Yes | 真实执行与日志 | risky 按策略跳过；原状态可见、无需处理、零邮件、无新增异常标签 | PENDING | frontend A-4 | — | — |
| Frontend A-5 | Yes | 样式、键盘、原控件回归 | 1280/390 布局、焦点、禁用态及 2/1/30 原控件符合计划 | PENDING | frontend A-5 | — | — |
| Frontend A-6 | Yes | 预估与总开关 | 仅 preview 请求、无 Emailable 浏览器请求；版本键统一 | PENDING | frontend A-6 | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `e6c36e294cb26ce36684a96a0908c305f871e4b4`
- Reporter: —
- Timestamp: —
- Note: —
