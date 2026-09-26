# 新邮箱跳过历史邮件：上线记录

用户授权：2026-09-25「你上线吧，顺带把现在异常的邮件全部标记为已处理，后面不要再获取历史邮件了」。

## 发布内容与验证

- 仅发布 `new-mailbox-skip-history.md` 所列 6 个生产源码文件的编译类。基于生产 WAR 替换关联类，保留所有其他资源、配置和工作区无关修改；无需 schema 迁移。
- 无游标邮箱第一次轮询只读 UIDVALIDITY / UIDNEXT 并初始化当前位置；后续从该位置继续。退信补扫同样受本轮 UID 起点约束。原有账号保持断点进度。
- 116 项单元/真实 IMAP 协议测试通过，0 failures / 0 errors / 0 skipped；日志 `/private/tmp/new-mailbox-skip-history-tests-network.log`。
- 50 个相关编译产物中 44 个相较生产不同（含新增），删除 2 个旧匿名类；逐项验证展开目录哈希一致，其他 WAR entries 字节不变。
- 22:37:55 数据切换并原子替换 WAR；22:38:12 Tomcat 完成自动重部署。发布前两次确认 RUNNING 任务数为 0，未强制中断任务。
- 内网与公网 `/talent/api/auth/me` 均 HTTP 200。没有主动发送验收邮件。

## 历史邮件与审计

- 当前全部未处理异常为 LuKai_Gmail 的 10 封，均 MANUAL_REVIEW / UNMATCHED_CONTACT，expert_contact_id 均为空。
- IDs：510、512–520。按 PendingMailOperationService.markResolved 的状态与审计语义，在单一事务中更新为 PROCESSED / MANUAL_RESOLVED，记录 resolved_at / resolved_by，并插入 10 条 MARK_INBOUND_RESOLVED 审计。operator 为 `LuKai (via Codex)`，备注记录用户授权及发布标识。
- 未删除记录；验收时共 271 条 processing 记录，全部 PROCESSED，无剩余 MANUAL_REVIEW。
- Gmail 通过 TLS 只读 EXAMINE 获取位置，不读取旧邮件正文：UIDVALIDITY=1，UIDNEXT=442。游标由 11 移至 441；只接收 UID 大于 441 的后续邮件。其他账号游标未改动。
- 新邮箱基线按首次轮询时间建立；显式人工 UID 回补和 UIDVALIDITY 变化的原有处理语义保留。

## 备份与回退

服务器：`root@150.158.92.103`。
备份目录：`/opt/talent/backups/skip-history-20260925/`（服务器内留存，无完整生产 WAR 导出）。

- `talent-before.war` SHA256：`0746909091c6578e279932cfd2dd208178778839c79cc4a77fd4683f69e1c001`
- `talent-after.war` SHA256：`3828d03ba16c4d087a4fc87391bf2c805264b48c280f86ac6eb886bb602794e6`
- `manifest.json`、`deployment.json`、`patch.zip`：编译产物和差异清单。
- `data-before.json`、`data-change.json`、`cutover.json`：记录状态和游标前后值，无邮件正文或 IMAP 密码。
- 回退 WAR 前须核对当前 SHA256 仍为本次发布值且无运行任务；在 webapps 外暂存旧包后原子替换。不要自动回退 Gmail 游标，否则重新扫描历史；已处理记录保留审计，若需撤销按正常取消处理流程进行。

自动审批拒绝将完整生产 WAR 下载到本机（可能包含敏感配置）；改用服务器内备份与构包完成发布，无待审批阻塞。
