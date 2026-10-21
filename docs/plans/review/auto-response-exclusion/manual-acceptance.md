# Manual Acceptance — auto-response-exclusion

## Epoch 2 — 2026-10-21T09:32:00+08:00

- Reviewed code boundary: `5f1e99d4fe024d698759c16ae5e2c9ba40554648..de77c293063d0b502a8ac9c99ceeb7d4340101f2`
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| Main A-1 | Yes | 邮件头独立生效 | 首封 auto-replied 原文保留、已处理、待处理增量 0、原因 `AUTO_RESPONSE_HEADER`；原开关仍关闭。 | PENDING | — | — | — |
| Main A-2 | Yes | 首封窗口边界 | 300 秒首封已处理；301 秒首封 `MANUAL_REVIEW`；总待处理新增 1；迟检查不改变收发时间差。 | PENDING | — | — | — |
| Main A-3 | Yes | Hamid 历史修正与回滚 | dry-run 零写；首次 apply 1 行；会话待处理 1→0 且原文/挂起/跟进保留；重复 0；rollback 恢复 1。 | PENDING | — | — | — |
| Main A-4 | Yes | 原业务及人工处理保持 | 未关联 auto-replied 仍入原待匹配队列；除新分类/计数外基线一致；绑定、取消、预览、附件、发送、群通知保持。 | PENDING | — | — | — |
| Main A-5 | Yes | 共享账号、重复与时间缺失 | 成功信仅一条 processing；账号保持；重复不覆盖；缺实际时间不凭轮询时间命中；失败重试后才推进游标。 | PENDING | — | — | — |
| Main A-6 | Yes | 历史修正并发保护 | apply 跳过已人工处理行；rollback 不覆盖后续人工变化并报告冲突；不改专家、工单、ES、挂起备注。 | PENDING | — | — | — |
| Main A-7 | Yes | 第二封绝不重判 | 第二封和别名后续回复仍 `MANUAL_REVIEW`；首封已处理；挂起原因、跟进标记不变。 | PENDING | — | — | — |
| Main A-8 | Yes | 并发与已有历史 | 提交时仅第一封有资格；回滚时第二连接有资格；既有人工绑定或 INBOUND 历史均无首封资格。 | PENDING | — | — | — |
| Companion A-1 | Yes | 完整文案与原流程 | 21 封未关联队列分页为 20/1；显示“待确认”；搜索不变；绑定后进入专家正常处理。 | PENDING | — | — | — |
| Companion A-2 | Yes | 标签兼容、草稿隔离 | 旧标签筛选仍命中；badge 显示“待确认”；草稿 `draft-confirmation-check` 不被覆盖。 | PENDING | — | — | — |
| Companion A-3 | Yes | 空态、报错、样式及缓存 | 全部待确认文案正确；重试可恢复；桌面 12px/`#3762d8` 下划线、移动最小 44px；布局与其他操作保持。 | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `de77c293063d0b502a8ac9c99ceeb7d4340101f2`
- Reporter: —
- Timestamp: —
- Note: —
