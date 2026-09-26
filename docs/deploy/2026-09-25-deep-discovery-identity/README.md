# 深度发现身份防错修复与发布

2026-09-25，已上线。用户授权修复；新批次人工验收由用户另行通知，自动核验任务已删除。未触发新批次、未恢复发送、未修改历史邮件或存量专家身份。

## 发布结果

- 工作树 `/Users/lukai/IdeaProjects/weibo-talent-introduction`，分支 `main`，基线 `f9c8dce2d1f2efe09d9ccb0c439498d2e91f22f5`；未提交、未推送。
- 生产 `150.158.92.103`，`/opt/apache-tomcat-9.0.71/webapps/talent.war`。
- 备份 `/opt/talent/backups/deep-discovery-identity-20260925-203450/`，包含发布前后 WAR、补丁及逐文件清单。
- 发布前 SHA256 `d93f905b9e763c3a2e50cf2219d97fdc66cbb5efefe16168560c2e4c1391c2f1`。
- 发布后 SHA256 `0746909091c6578e279932cfd2dd208178778839c79cc4a77fd4683f69e1c001`。
- 以生产 WAR 为基底更新 152 项、移除 2 个失效编译生成类；所有其余包内文件逐项哈希不变。包括共享 DTO 构造器变更影响的调用类，避免只替换 DTO 导致运行时链接错误。
- 四个既有 DTO 的生产/基线文件哈希不同，但 `javap -c -p` 指令与签名一致，差异属于调试信息；其余替换文件与基线一致。详见 `patch-manifest.json`。
- 发布前任务、论文处理、学术补全的 RUNNING 数均为 0，发送自动配置开启数为 0。Tomcat 自动重部署，20:35:09 启动成功；健康接口 HTTP 200，152 项展开文件哈希一致，两个旧类不存在。
- 三层 ES 均已有 `identityVerification` mapping；发送配置 1/3/4/5 的 `auto_enabled` 发布前后均为 0。没有执行批次验收或实际发送。

## 修复行为

1. JATS 共用通讯段不再给引用者广播全部邮箱。仅使用唯一作者节点、明确姓名/原文缩写与限定文本范围；身份冲突整组清空。真实同名不合并，一人多邮箱保留。
2. 通过 JATS 原文或公开 ORCID 记录确认的身份携带来源摘要、规则版本与绑定邮箱/姓名；PDF/Core 字符串推断和搜索摘要只留作线索。证据不足不新建专家、不晋升、不首发。
3. 新专家用规范化邮箱派生业务键，RAW/CANDIDATE 原子 create 避免并发覆盖。旧业务键不重命名；重复邮箱不覆盖既有身份。仅存量自身已验证且一致时允许幂等补建学术任务。
4. 队列结果显式版本 `20260925`；旧/无版本结果隔离为 `IDENTITY_EXTRACTION_VERSION_UNSUPPORTED`，保留结果供追溯。没有自动批量清空缓存或重抽取。
5. 学术补全读取凭证绑定的真实外部 ID，不使用深度发现历史业务键猜测 ORCID。回写校验身份快照；缺失索引层不重建。
6. 最终删除名单 1,923 个规范化邮箱的 SHA256 精确匹配归档；默认阻止重新导入。此前排除的 Xingquan Zhu 不在名单。
7. 首发查询/统计、联系人重试、创建联系人前和实际发送前均检查身份；材料提醒与回复的既有选择规则不变。

## 验证证据

执行自查采用 fix-v 的约束审计方式，审计阶段为 AUDIT_ONLY；无独立代理审计声明。执行基线及哈希见 `plan-identities.json`。六个子阶段均在原修复授权内实施。存储阶段另同步 `ExpertIndexServiceTest` 的字段数 34→35，这是新增 mapping 的机械断言调整；该阶段总范围 10 个文件。原计划文件保持原哈希。

- JDK 11 `mvn clean package`：PASS，4,030 项 JVM 测试中 4,017 通过、13 跳过，0 失败/错误；前端 1,185 项全部通过，JS 语法检查通过。13 个跳过的环境依赖/禁用用例不计为通过。
- 新增覆盖共享脚注、姓名标签边界、同名/缩写冲突、多 rid/重复 id、嵌套/编辑/公共地址、真实误判样本、无证据/删除名单拒绝、旧缓存隔离、原子 create 冲突、首次联系拦截，以及旧业务键不能用于补全。
- 四篇历史真实论文离线回放，共 14 个邮箱归属正确：PMC13241006、PMC13293455、PMC13280751、PMC13196294。包括 Ali Akbar Moosavi、Latha Thimmappa，以及 imaginglu→Jie Lu、wangwei37→Wei Wang、jointwwg→Weiguo Wang；没有以全部输出未知冒充成功。
- `git diff --check` 通过。最终日志 `/tmp/identity-full-build-final.log`、回放 `/tmp/identity-real-replay-final.txt`；摘要与日志哈希见 `test-summary.json`。

| 约束/路径 | 结果与证据 |
|---|---|
| parser I-1～I-6：唯一整体身份、边界与安全输入 | ✅ `JatsXmlEmailParser.kt:29,86,120`；JATS/PDF/Core/来源回归及历史 XML 回放 |
| storage/evidence I-1～I-3：对象读写、默认拒绝 | ✅ `DiscoveryIdentity.kt:44,60`、`ExpertSearchService.kt:506,592`；三层 mapping 已核对 |
| admission：两个准入入口与重复保护 | ✅ `ExpertDiscoveryService.kt:985,1307,1831,1883`；Writer 原子 create 回归 |
| 缓存状态和重试 | ✅ `ExpertDiscoveryService.kt:1605,1646`；旧缓存终态失败，新缓存复用，跨 ES/MySQL 重放测试通过 |
| 补全身份及并发回写 | ✅ `ExpertDiscoveryService.kt:2563,2635`；未知旧键 NoId、凭证 ID 查询、身份快照脚本回归 |
| 晋升路径 | ✅ `CandidateEligibilityService.kt:22`、`ExpertIndexWriterService.kt:435,554,608,699`；既有重审回归 |
| 首发四个入口 | ✅ `InitialOutreachService.kt:56,72,97`、`ManualInitialOutreachService.kt:129,652,788,885,1645`、`BatchExecutionModels.kt:89` |
| 累积/状态机/跨阶段/删除代码/范围 | ✅ 既有额度与任务计数未改；旧结果走既有终态路径；共享字段读写一致；旧编译类删除；无 UI/邮件模板/迁移改动 |

机器构建与上述检查通过；人工验收 PENDING，尤其新批次零错绑结果尚未验证。不会自行定时核验。

## 已知边界

- 历史深度发现档案若没有新版凭证，会被排除首发、晋升和学术身份推断。此前已修正/来源支持的档案也不会被无差别标成 VERIFIED。本轮未批量导入其历史证明；姓名与既有资料未回退，恢复这些档案资格仍需按最终核定证据定向回填。
- PDF/Core 仅有推断时新增量会下降；无证据线索仍保留在抽取/审计结果。
- 新版本旧缓存隔离可能增加带明确原因的失败任务；需要重试时必须按队列正式流程重新抽取，不能直接清空数据库字段绕过配额或租约。
- 未主动执行新的线上发现批次、真实发信或用户验收；上线健康检查不代表新批次质量验收完成。

## 回退

先确认线上仍为本报告发布后 SHA256，且没有活跃任务；从备份复制 `talent-before.war` 到 webapps 外暂存，再原子替换。保持发送暂停。新增 ES mapping 可保留；不得回滚专家数据、历史信件或批量修改验证凭证。旧代码缺少本轮门禁，回退后不可恢复发现/发送直到另行处置。
