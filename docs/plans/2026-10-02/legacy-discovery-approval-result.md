# 历史发现兼容实施结果

时间：2026-10-02，Asia/Shanghai。
授权依据：用户“这些全部都符合要求…兼容到现在的身份校验通过中”；随后明确选择“同时兼容身份和机构来源凭证；机构、国家等已有条件保留”。合同：legacy-discovery-approval.md；实施树 main，未提交 Git。

## 已上线结果

- 候选层 16,029 条全部写入并逐条复核通过。
- 同身份原始层 15,835 份、申请层 6 份同步；合计 31,870 份。
- 原始层另 194 份身份不同，未改；不新增业务文档。
- 使用 LEGACY_APPROVED / LEGACY_USER_APPROVED_20261002；没有伪造 VERIFIED、学术作者 ID 或机构原始来源 token。
- 唯一字段改动 identityVerification；保留机构、国家、分类、资格、联系状态、模板及发送配置。
- 原缺姓名字段的 2,923 条按实际 null/空值绑定，不编造姓名。

## 线上数据核算

使用已部署 RecipientScope/DiscoveryIdentity 类与线上 BatchEmailVerificationRepository 的只读查询；凭证与个人数据仅在原服务器内部使用，输出聚合。

条件：候选层，新发现，未联系，PRODUCTION_RND/ACADEMIC_RND/HYBRID_RND/UNKNOWN/UNCLASSIFIED，研究方向 ANY，模板门禁关，排除已验证不可用邮箱。

| 阶段 | 人数 |
|---|---:|
| 当前五类且未联系 | 25,414 |
| 身份验证或人工认可 | 25,414 |
| 机构非空 | 16,846 |
| 国家可映射 | 7,988 |
| filterResult=PASSED | 7,838 |
| 来源机构凭证或人工认可 | 6,734 |
| 排除已验证不可用邮箱 98 人后 | **6,636** |

本次 16,029 条中，5,919 条通过其余资格条件；再排除 5 个已验证不可用邮箱，净新增可选候选 **5,914**。

本次核算的发现候选总库为 37,844，早先审计为 36,869；期间已有发现任务增加数据。因此不将 6,636−563 全部归因于本次兼容。本数为 ES 候选 pending，未重新核算数据库 NEW 重试目标，不冒充浏览器总人数。Dia 当时已切换资料管理页面，未更改用户当前界面或触发发送。

## 备份与部署

服务器：root@150.158.92.103。
目录：/root/talent-data-backups/discovery-legacy-approval-20261002。

- candidate-manifest.jsonl：固定 16,029 个候选；SHA256=b001a87aea84ab98f096b676c8a8a568a1714e00155193914b369ce2cb759096。
- approval-cas-backup.jsonl：31,870 个逐条 CAS 快照与认可回执；原值缺失/null 可区别回滚。
- original/：两个部署类的原件；staged/：新类；apply.log、verify.log：聚合结果。
- 只替换 DiscoveryIdentity.class 与 RecipientScope$Companion.class，核对原/新 hash；其余方法经 javap 去常量池序号比较一致。
- DiscoveryIdentity 新类 SHA256=0734eab697f03759cc38ea7bd29b60cba90eb62ef18f4db092b2a60ceca59dd0。
- RecipientScope$Companion 新类 SHA256=99e3ef0931db8da73b9819d29bb8b1f35a1604a94c9972959175f7bc1bef5a84。
- Tomcat 优雅关闭后旧 IMAP 线程阻止进程退出；日志证实 HTTP/HTTPS 已关闭，再 SIGTERM 结束旧进程，启动新进程 7394。HTTPS 首页恢复 200。
- 首次 apply 全部写入后，收尾 _refresh 携带空 JSON 导致 400；仅修正 refresh 请求为无 body，14/14 shards 刷新成功；后续 verify 全部通过，无冲突，无失败写入。

回滚数据：在该目录执行 `python3 apply_legacy_discovery_approval.py --mode rollback`，只在当前凭证仍等于本次认可时撤回，保留并发业务字段。代码回滚需恢复 original/ 中两个类并重启。未实际执行生产回滚。

## fix-v 机器核验

模式：MECHANICAL_REPAIR（用户授权实施兼容；核验阶段未作产品行为修改）。初始核验，无历史 P1；基线为本轮两条明确用户指令，计划记录其实现边界。

编译 PASS；Kotlin 测试 **201 passed / 0 failed / 0 skipped**；Python **6 passed**；本次文件 diff check PASS。

命令：

```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=LegacyDiscoveryApprovalTest,DiscoveryIdentityTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest -DskipNodeTests=true
python3 -m unittest discover -s scripts -p test_apply_legacy_discovery_approval.py
git diff --check -- src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt scripts/apply_legacy_discovery_approval.py scripts/test_apply_legacy_discovery_approval.py src/test/kotlin/com/weibo/talentintroduction/expert/domain/LegacyDiscoveryApprovalTest.kt
```

- I-1 ✅ scripts/apply_legacy_discovery_approval.py:198：固定 manifest hash/数量/ID范围；prepare 不覆盖已有凭证；线上 verify 16,029/15,835/6。
- I-2 ✅ DiscoveryIdentity.kt:75、80：回执精确绑定；LegacyDiscoveryApprovalTest 覆盖 null/Unicode、字段改变、错误来源版本摘要、学术来源仍拒绝；线上 JVM 16,029/16,029 回执有效。
- I-3 ✅ BatchExecutionModels.kt:261：统一最终门禁；既有 169 项 ManualInitialOutreachServiceTest、20 项 InitialOutreachServiceTest 与 5 项新增用例通过。
- I-4 ✅ scripts/apply_legacy_discovery_approval.py:45、116、158：先备份、CAS、幂等、仅回滚本次字段；6 项 Python 测试通过，线上零冲突。
- Accumulation ✅ 固定清单、重复应用幂等；State-machine ✅ 准备/应用/验证/条件回滚；Cross-plan ✅ 来源身份语义保留；Deleted code N/A；No extras ✅；Scope compliance ✅。
- 人工验收 PENDING：A-1—A-4 由用户选择执行；机器核验不代表人工确认。未发送测试邮件。

## 2026-10-04 补交代码

10 月 2 日仅部署编译类，兼容源码、脚本与测试留在 main 工作区未提交。10 月 4 日核查时，线上 DiscoveryIdentity.class 与当时 original/ 备份及当前 WAR 内的类哈希一致，已不含历史人工认可兼容；数据中的认可回执仍保留。

依据用户“先提交一下代码”，本次将两个源码文件、Kotlin 回归测试、迁移脚本及 Python 测试、本计划与实施记录共 7 个文件纳入 main 提交，其他工作区改动保留。

提交前重新验证：上述 Kotlin 定向回归 201 项通过（0 失败、0 错误、0 跳过），Python 6 项通过，暂存差异检查通过。此次仅提交代码，未推送、未重新部署、未重跑数据迁移、未发送邮件；线上恢复仍待部署。
