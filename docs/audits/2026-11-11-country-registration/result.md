# 国家补全登记兼容修复

范围：本地 main，修复 COUNTRY_V2 国家补全与登记校验不一致。无线上写入、无重启、无任务启动。

## 已确认原因

线上 WAR SHA-256 `286e1e1c1e41b5effe06f8e3ec4f66364713adebac62e6a341ec06f4981065a1` 对应 `26e67ae`。
2026-11-11 08:49:30/31，任务 #33642 两次 `DISCOVERY_REGISTRATION_RESERVE_FAILED`，异常均指向 `DiscoveryVerificationRegistrationService.validateReceipt:356` 的 originalInstitutionEvidence 非空断言。
`9f2bb43` 已允许无机构档案从绑定 ORCID 获取国家、保持机构证据为空；登记仍要求机构证据，导致中止 OpenAlex 来源。

## 修复边界

- COUNTRY_V2、无机构、补全前后均无机构 token：仅允许 ORCID 来源、已验证身份与非空 ORCID、匹配国家回执通过。
- 有机构或任一 token 存在：保留原 token 必填、来源一致、签名有效校验。
- 接受已有 COUNTRY_V1 回执，仍执行原有签名/国家校验；未知版本仍拒绝。
- 已持久化的 RESERVED 快照沿用原值恢复，不重新查询 ORCID、不改历史快照。
- 不吞掉其他登记失败，不改准入规则、游标推进或下载超时策略。

## 验证

修复前：DiscoveryCountryRegistrationTest 24 项，3 项错误，分别为首次国家补全登记、已保存的失败快照恢复、V1 签名回执恢复。错误分别为 Required value was null / Unknown country supplement version。
修复后：59 项全部通过，0 失败、0 错误、0 跳过。其中登记 24、补全服务 14、隔离 MySQL 流程 10、补全刷新 2、准入 Gate 11。主代码与测试编译通过；git diff --check 通过。

首次集成测试被沙箱阻止访问 Docker；确认本地 Docker 正常后授权重跑，隔离 MySQL 测试通过。未连接生产测试。

命令：Java 11，MAVEN_SKIP_RC=true；mvn -o test -DskipNodeTests=true（指定相关测试）；编译后 mvn -o surefire:test -Dtest=DiscoveryCountryRegistrationTest,DiscoveryCountrySupplementServiceTest,DiscoveryCountrySupplementFlowIT,DiscoveryCountryRefreshServiceTest,DiscoveryVerificationAdmissionGateTest。

交付：修改 1 个生产文件、1 个测试文件及本记录，按用户授权提交本地，未发布。上线后的实际恢复数量需任务续跑验证；此次不声称修复下载超时或保证采集量恢复到历史水平。
