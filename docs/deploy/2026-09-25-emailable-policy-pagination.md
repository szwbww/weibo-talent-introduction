# Emailable 放行策略与批量发送分页修复

用户在确认修改范围后授权“修复并上线”。基线 HEAD：`b67fce8866f0a6ea712eaa7251b0c862cd48f730`。本轮修改工作区，未提交或推送；未覆盖原有知识库、邮件模板、releases.json 等无关改动。

## 问题与修复

- 2026-09-25 09:00，配置 3，执行 19959：目标 4568，发送 4，跳过 38，剩余 4526。轮大小 20、每次一轮，账号结束时 31/100，非额度不足。
- 原始结果：deliverable 4，risky/low_deliverability 19，undeliverable/rejected_email 12，unknown 7。38 个跳过中 33 个复用旧结果。
- 分页先按 40 条取数，再在 fetchEsPage 中去重；迭代器错误地拿去重后页长判断末页。首批发送 2、跳过 38，第二次只剩 2 条新目标，发完即停止。
- 修复：fetchEsPage 返回原始页，迭代器统一去重与推进。轮额度仍只计发送成功；取消、发送账号额度、幂等和服务异常停发规则不变。
- 新策略：deliverable/risky/unknown 放行，仅 undeliverable 跳过并加“邮箱异常”。非合法供应商状态、邮箱不匹配、HTTP/服务故障仍拒绝继续发送。
- 历史复用按 provider_state 重新计算本次 decision；原审计行、checked_at、reused_from_id 不改写。查询同时兼容旧 SKIP 与新 PASS，维持一年有效期。
- 前端显示“按策略放行/策略放行”，保留供应商原状态与原因；静态缓存键 `20260925-emailable-policy-pagination`。

## 验证

1. 改测试、未修代码时：174 项中 4 项按预期失败。真实分页回归期望 20 封实际 4 封；全跳过期望 85 实际 40；risky/unknown 新请求与缓存仍被拦。
2. 修复后命令：
   `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,OutreachTargetIteratorTest,BatchEmailVerificationRepositoryIT -DmysqlIt=true -DargLine=-Dapi.version=1.44`
   exit 0：后端 186 项（30+137+7+12），含 Docker MySQL 8.0.36 的真实仓储集成；完整前端 1185 项；JS 语法检查通过。
3. `mvn war:war -DskipTests -DskipNodeTests=true`（同 JDK 11）：exit 0；使用刚通过测试的编译产物打包。
4. `git diff --check`：通过。

日志：`/tmp/emailable-fix-red.log`、`/tmp/emailable-fix-green.log`、`/tmp/emailable-fix-package.log`。这是执行自查证据，不声称单独代理独立审计。

## 上线

- 服务器 `150.158.92.103`，应用 `/opt/apache-tomcat-9.0.71/webapps/talent.war`。
- 上线前活跃任务数为 0；验证 WAR 与展开目录逐文件一致。
- 基于生产 WAR 仅替换本轮 24 个相关编译类及 app.js/index.html，共 26 项。未部署完整本地包中的其它差异；无需数据库迁移。
- 备份：`/opt/talent/backups/emailable-policy-pagination-20260925-093449/`，包含 `talent-before.war`、`talent-after.war`、`manifest.json`、`patch.zip`。
- 原包 SHA256：`7042a5ef582452b456b3dfd0e07c5fa3c4e4be2d71613db98a8a2db090a033ce`。
- 新包 SHA256：`d93f905b9e763c3a2e50cf2219d97fdc66cbb5efefe16168560c2e4c1391c2f1`。
- 原子替换 WAR，Tomcat 自动重部署 `/talent`，未重启其它应用。09:35:40 启动完成，接口 `/talent/api/auth/me` 返回 200；全部 26 项展开文件哈希匹配；页面与 app.js 返回新策略和缓存键。
- 原执行 19959 的 4/38 审计保持不变。配置 3 保持自动开启，每轮 20、一轮/次，cron `0 0 9,11,13,15,17 * * ?`，下一轮 11:00。
- 未为验收主动触发真实发信；线上补足 20 封的实际结果需由后续定时执行体现，前提是候选与账号额度充足。

## 历史标签

仅处理执行 19959 中 risky/unknown 且审计显示标签已写入的 26 人。排除任何 undeliverable、退信、抑制记录；逐副本核对真实文档 ID、规范化 ORCID、当前邮箱及版本号。52 个 RAW/CANDIDATE 副本已去除“邮箱异常”，逐一回读验证其它标签不变。未更改 operatorStatus 或原验证审计。

回滚快照保留本机任务目录：
`/Users/lukai/.codex/visualizations/2026/09/25/01a0d61a-a0c0-7aa0-a75e-28089fcc7db6/emailable-repair-evidence/`，权限 0600，未上传服务器。

自动审批曾拒绝读取服务器进程凭据及上传专家快照。最终使用项目现有 application-local.yml 配置，通过 SSH 隧道且保持 TLS 校验完成操作；不读取进程凭据、不上传专家数据。

## 回退

先确认当前 WAR 仍是本次新包 SHA256，且没有运行任务；把备份的 `talent-before.war` 复制到 webapps 外的暂存路径，再原子替换 `talent.war`。标签如需回退，仅对快照中原有的“邮箱异常”追加恢复，保留其它现有标签；先检查当前邮箱和验证结果，禁止覆盖整个专家文档或历史审计。
