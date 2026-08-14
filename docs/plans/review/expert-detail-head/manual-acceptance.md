# Manual Acceptance — expert-detail-head

## Epoch 2 — 2026-08-15

- Reviewed code boundary: 90498efb768f74a2371e895d984bde1ac4743c49..82af050103285614a177d2ab4822be6f43861585
- Machine report epoch: 2 (PASS / PROGRESSING)
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| P1 A-1 | YES | 已绑定专家的预览出现真实签名 | 签名区显示绑定账号实值；无 5 项 sender 兜底徽标 | PENDING |  |  |  |
| P1 A-2 | YES | 收件人显示真实邮箱 | 面板底部收件人 = 专家真实邮箱，非 preview@local | PENDING |  |  |  |
| P1 A-3 | YES | 未绑定专家不报错 | 签名空白 + 兜底徽标；无红色错误；非满屏 `${}` | PENDING |  |  |  |
| P1 A-4 | YES | 模板编辑器抽屉显式账号优先 | 抽屉显式选 A 时签名显示 A，非绑定 B | PENDING |  |  |  |
| P1 A-5 | YES | 绑定到已禁用账号预览仍出签名 | 签名区仍显示该禁用账号 sender 信息；验收后恢复启用 | PENDING |  |  |  |
| P1 A-6 | YES | 改绑后重新进入详情预览随之改变 | 改绑 A→B 后重新进入详情，签名变 B | PENDING |  |  |  |
| P1 A-7 | YES | 首封发出后自动补绑，预览出现签名 | 未绑定专家发首封后，预览签名出现且与列表副行一致 | PENDING |  |  |  |
| P1 A-8 | YES | 预览账号与发送账号一致 | 外发记录发件账号 = 预览签名对应账号 A | PENDING |  |  |  |
| P1 A-9 | YES | 四个子标签未受影响 | 4 个标签顺序/数量正确，无空白面板 | PENDING |  |  |  |
| P2 A-1 | YES | 账号 pill 出现在操作栏、卡片消失 | pill `发件 LiLei ▾` + 绿点；metadata 卡片消失 | PENDING |  |  |  |
| P2 A-2 | YES | 浮层可开可关，保存生效 | 浮层展开/保存/清除标记/Esc/外点关闭正常 | PENDING |  |  |  |
| P2 A-3 | YES | 未保存闸门三处联动 | 改选未保存：pill 琥珀 + ⚠ 提示 + 发送置灰；复位与保存后恢复 | PENDING |  |  |  |
| P2 A-4 | YES | 未绑定态可见 | pill 逐字 `发件 未绑定` + 灰点 | PENDING |  |  |  |
| P2 A-5 | YES | 折叠区默认收起、可展开、切专家不重置 | 一行常驻；展开后切专家仍展开；可收起 | PENDING |  |  |  |
| P2 A-6 | YES | 标签并入姓名行 | chips + ＋ 在姓名行最右侧；无独立「专家标签」区块；四子标签正常 | PENDING |  |  |  |
| P2 A-7 | YES | 标签超过 3 个折叠与展开 | 只显示 3 chips + +N；展开后全部显示 | PENDING |  |  |  |
| P2 A-8 | YES | 无画像专家退化为内联 pill | `ES 无画像` 虚线 pill + 悬停 tooltip；无 ＋ 按钮 | PENDING |  |  |  |
| P2 A-9 | YES | 改绑后邮件预览签名随之改变 | 改绑 B 保存后切预览，签名变 B | PENDING |  |  |  |
| P2 A-10 | YES | 加删标签后姓名行不炸开 | 姓名行保持单行高；标签仍在右侧；刷新后集合一致 | PENDING |  |  |  |
| P2 A-11 | YES | 收发件箱标签区未受影响 | 块级区块 + 「+ 添加标签」文字按钮与改动前一致 | PENDING |  |  |  |
| P2 A-12 | YES | 发送仍用已保存绑定 | 不动 pill 直接发送成功，外发记录账号 = A | PENDING |  |  |  |
| P2 A-13 | YES | 状态/层级/回复模式保存行为未变 | 脏检查、保存确认、变更提示与刷新正常 | PENDING |  |  |  |
| P2 A-14 | YES | 专家列表标签与「已变更」标记未受影响 | 列表副行标签/标记样式与「账号：XXX」不变 | PENDING |  |  |  |
| P2 A-15 | YES | 窄面板下操作栏可用 | 收窄后控件换行不重叠；浮层 258px 左对齐完整可见 | PENDING |  |  |  |

## Human Sign-off

- Decision: PENDING
- Boundary: 82af050103285614a177d2ab4822be6f43861585
- Reporter:  | 
- Timestamp: 
- Note: 
