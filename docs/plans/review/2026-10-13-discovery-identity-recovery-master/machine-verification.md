# Aggregate Machine Verification — discovery-identity-recovery-master

## Epoch 1 — 2026-10-14T19:56:16+08:00

- Master plan: docs/plans/2026-10-13/discovery-identity-recovery-master.md
- Governing master SHA256: f69d8135ce0019ad36cc8939cf8101a6f584aac9983dec3dfc78eb2ae37ce238
- Recorded master identity: commit 36075ec06c5e4382f13606c995e24a13ec4470ce
- Invoked master identity: SAME; CONSISTENT; governing diff: none.
- Boundary: 9d353934b82ed3e36a193c70748e5efccb3d9e82..cd6a38d662da881ec453839921922a30602eacb3
- Evidence parent before evidence commit: 1408138b429297ad7b5a949d0b8e7839bdd4ee63
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master
- Branch: fast/2026-10-13-discovery-identity-recovery-master
- Resolution: DISCOVERED_FROM_GIT_WORKTREES; exact selected discovery result is preserved in ledger.md.
- Reviewer: /root/aggregate_reviewer, created after reviewed code commit, fork_turns=none; different from every recorded fast-p writer/verifier.
- Result: FAIL; convergence INITIAL; manual PENDING.
- Repair result: DRAFT_READY; docs/plans/fix/discovery-identity-recovery-master/repair.md; SHA256 845500de628b9e7fe8671171c3e90f49f1afc6bbac2f85e05b11a57be8bd4ca5
- Prior aggregate / repair lineage / evidence mode / historical repair approval / executor: N/A.
- Fast ledger: docs/plans/fast/2026-10-13-discovery-identity-recovery-master/ledger.md; SHA256 2bbeec0ca9bbaf2008aed29b4265862a089a84cdbc25f20160a3e6ef5fe361e2
- Fast handoff: docs/plans/fast/2026-10-13-discovery-identity-recovery-master/human-review-handoff.md; SHA256 ff9fa89083d24f9a3f6b37f798a8f201273df9c7b543d8a572316e7c4a390a64

Both ancestry checks exit0; code..evidence contains only fast-p docs. Complete cumulative diff:124 files,68 product/test files, all in approved child union. No old shared-file overwrite, applied migration change, existing shared-table field or ES mapping addition found. Reviewer read complete master, ten current child plans, baseline, fast ledger/handoff and all40 child brief/execution/verify-log/fix-log documents. Child PASS was not treated as final proof. All source paths below resolve under the exact retained worktree.

## Ordered child identities

| Child | Exact plan | Recorded commit | SHA256 |
|---|---|---|---|
| admission-relaxation | docs/plans/2026-10-13/discovery-admission-relaxation.md | f7c7604c268aad69df6afd15efe0eed91866a237 | e2ebfb727c056b8de709150304a0cf9bc0ea4b76f7e41ebec27fa452e5e281b2 |
| 01-duplicate | docs/plans/2026-10-13/discovery-identity-recovery-01-duplicate.md | b06a71898bc7850d2caeb244c9f8478f18a3b2ae | d78c2b0e05a203558820645cc440c82b4420a782768ab9b88be2d74deb937902 |
| 02-replay | docs/plans/2026-10-13/discovery-identity-recovery-02-replay.md | 58a9e511e327d61e8e3645c738921979ff26a518 | 4bee186d37236f0e000e07c39941915a8eb37283eda44e4804d1b1ee9950c407 |
| 04-registry | docs/plans/2026-10-13/discovery-person-04-registry.md | 9bf6b8ec1319863002f7d21f4320108a0a8ec2e3 | cdd91cdef308f651733d579bd1c5ff0140dbe7b0e8e7e3a851115a31d8c42c1d |
| 05-ingestion | docs/plans/2026-10-13/discovery-person-05-ingestion.md | 36075ec06c5e4382f13606c995e24a13ec4470ce | 924e929a9228d4ba302a8ce0df6017337e430cf986c70716ef2d50fc667d92ff |
| 06-query | docs/plans/2026-10-13/discovery-person-06-query.md | 8b320426228ff9cf5e55447826bdb009bcdfa34a | 404cd02b4dd50cd2aeef2d434f183591bc36ee7bc32e43137ca7fe990da71b31 |
| 07-ui | docs/plans/2026-10-13/discovery-person-07-ui.md | a2d4166bf6ff7e61313d69a420cd5f98e28a8911 | e0cad0a75d41f917d679a6eeadcf7010c8897bf27ea646e197af30c7f2a589d0 |
| 08-selection | docs/plans/2026-10-13/discovery-person-08-selection.md | 36075ec06c5e4382f13606c995e24a13ec4470ce | aed785ba70933fd10ef73ee9a3934c5714d195894e54f69e2b15386919869916 |
| 09-send-guard | docs/plans/2026-10-13/discovery-person-09-send-guard.md | 6a774804e918342bb65ae4549247e37fccd0d59b | 1489119289122de3e89935744084c1fc30b302c3f2a54404d423d6f112aa59de |
| 10-history | docs/plans/2026-10-13/discovery-person-10-history.md | 36075ec06c5e4382f13606c995e24a13ec4470ce | 122436d48050cd7c3811265136300754ef4ae739b34ab44b9cd3ba10dcda691b |

## Approved amendments — exact fast ledger record

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-10-13/discovery-identity-recovery-master.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 需求描述（用户追加同人多邮箱合并）＋实现方案「当前追加：新发现人物合并（04—10）」 | 人工更新加入同人多邮箱合并范围：追加 04—10 子计划、10,686 名单降级为参考并停用原 03 存量执行，开发顺序改为先验收准入修正及 01/02 补证接口代码再 04—10 | HUMAN:用户消息「计划已经更新 你再看一下」（master 于 2026-10-13T22:40:55+0800 落盘，本 run 22:43+0800 快照提交） |
| A2 | docs/plans/2026-10-13/discovery-person-04-registry.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「04 人物主档」＋实现方案 1 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A3 | docs/plans/2026-10-13/discovery-person-05-ingestion.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「05 发现入口」＋实现方案 1—4 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A4 | docs/plans/2026-10-13/discovery-person-06-query.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「06 人物查询」＋实现方案（研究门禁：流式完整匹配集） | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A5 | docs/plans/2026-10-13/discovery-person-07-ui.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「07 多邮箱展示」＋样式契约 S-1/S-2 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A6 | docs/plans/2026-10-13/discovery-person-08-selection.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「08 发送目标」＋实现方案 1—3 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A7 | docs/plans/2026-10-13/discovery-person-09-send-guard.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「09 首发保护」＋实现方案 1—4 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A8 | docs/plans/2026-10-13/discovery-person-10-history.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「10 历史合并」＋实现方案 1—6 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A9 | docs/plans/2026-10-13/discovery-admission-relaxation.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:f7c7604c268aad69df6afd15efe0eed91866a237 | 实现方案步骤1（先完成既有准入修正：规则/CAS/重验）＋子计划变更文件清单第 11 文件条款 | epoch1 PLAN_CONFLICT：I-1 把六类软原因移入 hints 后，ExpertRevalidationServiceBehaviorTest 2 例仍断言旧阻断语义；按子计划「改第 11 个文件须先修订方案」，经人工批准增列该测试类并把 2 例改为新语义 | HUMAN:2026-10-13T23:06+0800 ask 回复「批准：更新 2 例为新语义」 |
| A10 | docs/plans/2026-10-13/discovery-identity-recovery-02-replay.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:58a9e511e327d61e8e3645c738921979ff26a518 | 实现方案 2「02 定向回源预览」＋子计划 I-4（请求仍走额度控制、不改计价策略） | epoch1 PLAN_CONFLICT：计划字面要求声明 Operation.SINGLETON，但既有 policy 对 /works/* 一律派生 LIST；按人工批准选项 A 授权第 11 文件并为 /works/{id} 补 SINGLETON 派生（0 credit；LIST/SEARCH/CONTENT 不变） | HUMAN:2026-10-14T00:52+0800 ask 回复「A：授权 policy，/works/{id}→SINGLETON」 |
| A11 | docs/plans/2026-10-13/discovery-person-04-registry.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | commit:9bf6b8ec1319863002f7d21f4320108a0a8ec2e3 | 变更文件清单「04 人物主档」＋实现方案 4（V156 真实库迁移验证） | V156 加入后 FlywayMigrationIntegrationTest 31 处全量迁移最新版本断言（155）失效，该文件不在 6 文件授权内；经人工批准增列为第 7 文件，更新 31 处断言并加 V156 三新表存在性断言 | HUMAN:2026-10-14T07:44+0800 ask 回复「批准：改 31 处断言 + 加 V156 断言」 |
| A12 | docs/plans/2026-10-13/discovery-person-07-ui.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | commit:a2d4166bf6ff7e61313d69a420cd5f98e28a8911 | 变更文件清单「07 多邮箱展示」＋样式契约 S-1/S-2 | 07 改 app.js，按 K-frontend-cache-key-triad 需 index.html 16 个资源键同值 bump；计划原列 2 文件未含 index.html，经人工批准增列并 bump 为 20261014-person-emails（已核实无测试/计划字面量硬钉当前键） | HUMAN:2026-10-14T12:17+0800 ask 回复「批准：bump 到 20261014-person-emails」 |
| A13 | docs/plans/2026-10-13/discovery-person-06-query.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | commit:8b320426228ff9cf5e55447826bdb009bcdfa34a | 07 子计划 I-1/S-1/A-1（列表显示邮箱数量）＋06 实现方案 2（ES API 人物投影） | 07 epoch1 核实出 ES 列表分支无成员数来源（06 的 PersonSearchResult 仅 experts+totalHits），07 无法在自身文件内补出；按人工批准选项「补全」修订 06：ES 列表人物投影携带 memberCount（同源批量读取），随后 06/07 各以新 epoch 重跑重核 | HUMAN:2026-10-14T12:33+0800 ask 回复「补全：改 06 加成员数，重跑 06+07」 |
| A14 | docs/plans/2026-10-13/discovery-person-09-send-guard.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | commit:6a774804e918342bb65ae4549247e37fccd0d59b | 变更文件清单「09 首发保护」＋实现方案 4（V157 真实库迁移验证） | V157 加入后 FlywayMigrationIntegrationTest 31 处全量迁移最新版本断言（156）失效，该文件不在 10 文件授权内；经人工批准增列为第 11 文件，更新 31 处断言并加 V157 表存在/唯一键断言（同 A11 口径） | HUMAN:2026-10-14T15:19+0800 ask 回复「批准：改断言 156→157 + V157 断言」 |


These recorded human approvals govern A1–A14. They do not waive master invariants, 06 performance or09 combined-entry concurrency.

## Fresh verification commands

Environment: Zulu11.0.15; Maven /opt/homebrew/bin/mvn; Node25.7.0; Docker29.4.0 OrbStack. Docker host access was approved; isolated test databases/fake SMTP only. C21 uses already-installed Zulu11.0.32.1. Raw logs and JSON indexes are /tmp/discovery-aggregate-review-20261014/; log paths and hashes below identify exact evidence.

| ID | Exact command | Exit | Counts/result | Wall seconds | Raw log SHA256 |
|---|---|---:|---|---:|---|
| C01 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest test` | 0 | JVM149/0F/0E/0S;JS1677 pass | 154.3 | ac07f48e0e25c5bc6756569c903d89f03f6621997a9c01f00ce97a3489de298d |
| C02 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test` | 0 | JVM216/0F/0E/0S;JS1677 pass | 147.3 | 29d4b0eaf12e9d1d739366ebedd7acb42be544ba2d79fc48510653b715145497 |
| C03 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test` | 0 | JVM304/0F/0E/0S;JS1677 pass | 205.1 | 75b77f5126b5a4ad28a8a2c705786d4296c6d95c8d0d37dd52ae2dff70253f4c |
| C04 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test` | 1 | JVM48/0F/0E/0S;JS1677:1676pass/1fail (retained) | 167.7 | f5e19e6aba9fdf26e85185be287c0b57b8bd05ad63e883c4369a9276cf0a15bf |
| C05 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest test` | 0 | JVM157/0F/0E/0S;JS1677 pass | 158.6 | 56f8363d06833508351f1024e4652b9b1554d0dc0e4b1576ef235086579f3858 |
| C06 | `python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'` | 0 | Python9 OK | 0.2 | eab4281419f49474c6773f172eab4705e82da9e9196e5418e92a366609bcb99e |
| C07 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test` | 0 | real MySQL6/0F/0E/0S;JS1677 pass | 173.9 | 47cb9de6a90eec60c1e00e59d3d1534205656a7c6b3d13d42bbe30213b36a7db |
| C08 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonServiceTest test` | 0 | JVM17/0F/0E/0S;JS1677 pass | 156.4 | 726b4d454ad815ff8a42d0e2aa03aff75698e678e9aae8859019cd96395baf0f |
| C09 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | 0 | real MySQL migrations46/0F/0E/0S;JS1677 pass;tests589.566s | 734.4 | bdb7c9f525fd7d8029ec51cbb754a7f9908ca62e090bd3bd4d5ee9582c4ccfe0 |
| C10 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test` | 0 | JVM272/0F/0E/0S;JS1677 pass | 207.9 | 07f81ee0c299a2f6ff8d43206bedcbe9afc84dbfd31c4f074f5035e6bed930cb |
| C11 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test` | 0 | JVM23/0F/0E/0S including real MySQL6;JS1677 pass | 158.8 | fcab66bdb35273684ee3399b440cbd97a7a103d9e4e30ef112fa45636430a1f9 |
| C12 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonQueryServiceTest,ExpertSearchServiceTest,ExpertIndexControllerTest,ExpertContactManagementControllerTest test` | 0 | JVM124/0F/0E/0S;JS1677 pass | 136.2 | 0ddf6c900164670648eba3e73a2cf6821c38553cae594348a60c7afa531c0744 |
| C13 | `node --check src/main/resources/static/app.js` | 0 | syntax PASS | 0.1 | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |
| C14 | `node --test src/test/js/discoveryPersonEmails.test.js` | 0 | JS20pass/0fail/0skip | 0.2 | ee1c822b541b971bd735f33f48940638d63d320e38d3457af87cfb774b5bf337 |
| C15 | `node --test src/test/js/*.test.js` | 0 | JS1677pass/0fail/0skip | 3.8 | 4a93aeef7b1169c43eaf4069f6d5faeb0f94ccfb4698aa4a62d961de39b6ac60 |
| C16 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test` | 0 | JVM238/0F/0E/0S;JS1677 pass | 159.4 | 3946536dfa91872e783a746e91d7ae4f969ac052bbc58208ae824eb7296916d7 |
| C17 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonOutreachGuardServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ManualExpertMailServiceTest test` | 0 | JVM253/0F/0E/0S including real MySQL guard11;JS1677 pass | 197.4 | 1bc9e0cab34f93c0fdfcc441c390633ec41f209135a36220ac58e53dd3e80865 |
| C18 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test` | 0 | JVM18/0F/0E/0S;JS1677 pass | 152.9 | 740017ad536a1daddc14420481e388c996044c21ffee3285c8132827269bb72b |
| C19 | `python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'` | 0 | Python14 OK | 0.2 | f2198f2cde05061e1fbe3bafb0f4155df9472c299f457c4e1f506eeb26692402 |
| C20 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | 1 | JVM4999/1F/19E/20S;Surefire failed;JS/package not completed | 335.7 | d4beeb50cabacd5f30b12974f4914b1071ad75436cf4d5974126d0d097f1b675 |
| C04R | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test` | 0 | JVM48/0F/0E/0S;JS1677 pass;wall includes interrupted-session time,not performance | 5414.4 | 8c5976eda4156d02510c4a8a408f35c5ff40c5f91b5300f6b8790ca9962510cb |
| C21 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn clean package` | 1 | JVM4999/1F/0E/20S;V-25 only;JS/package not completed | 356.5 | ac0f71de68aa9c4adf8a508121fdb578af4abde417894586b7f12d83cbcc8b0d |

Raw logs: each ID resolves to the same-name ID.log under the directory above. C09 continuously advanced and completed; not skipped after a hang. Default20 skipped in C20/C21 are not represented as actual DB passes; required DB suites ran explicitly with flags in C07/C09/C11/C17.

| Required source | Fresh command mapping |
|---|---|
| admission | C01,C02 |
| 01 | C03,C04,C04R |
| 02 | C05,C03,C06 |
| 04 | C07,C08,C09 |
| 05 | C10,C11 |
| 06 | C12 |
| 07 | C13,C14,C15 |
| 08 | C16 |
| 09 | C17,C09 |
| 10 | C18,C19 |
| master/CLAUDE.md | C20,C21 compatible JDK11 comparison |

### Retained first failure and environment comparison

C04 failed in unchanged src/test/js/expertMaterialsShared.test.js:1478/1497, “多选严格串行、一请求一文件、headers 为 {}；上传中禁用控件且 Esc 不关闭”: expected uploading, actual success; FakeServer12ms/async-flush timing. Related expert-materials.js/test have no boundary diff. Fresh `node --test src/test/js/expertMaterialsShared.test.js` exit0,27pass/0fail,2762.9ms, js-timing-focused.log; C15/C04R full JS pass. O-1 retains first exit1, no unrelated repair.

C20 errors: ZoneRulesException Unknown time-zone ID America/Coyhaique,19 errors in unchanged ExpertContactLocationServiceTest. TzProbe: Zulu11.0.15 tzdb2021e ZoneId.of exits1; installed11.0.32.1 tzdb2026b exits0. ExpertContactLocationCatalog.kt, related service/test and contact-country-timezones.json unchanged base..head. C21 errors0 confirms O-3 environment compatibility, while mandatory V-25 remains1 failure. No JDK/system/product mutation.

## Finding lineage and exact in-scope evidence

No prior aggregate epoch. V-1–V-25 are NEW; V-1–V-20 P1, V-25 mandatory P2, V-21–V-24 nonmandatory P2. B-1/B-2 evidence states are not severities. Stable IDs are preserved below and in repair.


| Finding | Severity | Requirement | Root Cause / Evidence | Disposition |
|---|---|---|---|---|
| V-1 总开关缺失 | P1 | master I-8、发布段；04 I-3 | 05–09 直接调用人物能力，04 所称默认关闭仅指旧版本无调用者。默认配置部署即改既有读写。 | REPAIRABLE |
| V-2 范围判定把历史发现来源当实时标签 | P1 | master I-4；04 I-3；05；10 I-1 | PersonService:155、Recovery:190、Registration:facts、Maintenance:369 使用 isDiscoveryMap（proof/source/tag 任一），移除 discovered 标签仍可登记/应用；P01/P06。 | REPAIRABLE |
| V-3 同人校验遗漏姓名与已有人物完整作者键 | P1 | 04 I-2；master I-4 | PersonService:61–89 不校验组全名；279–312 attach 不把既有根成员/作者键加入冲突检查，重试不重读 ES；P02/P03。 | REPAIRABLE |
| V-4 恢复链吞掉保护读取/人物登记失败 | P1 | 02 I-2；05 I-2；master I-3/I-8 | Recovery:207–214 联系人/alias 读异常当不存在；110–115 忽略 refresh FAILED/STALE/异常，138 返回 APPLIED；重试被194已有proof挡住。 | REPAIRABLE |
| V-5 发送只检查可见成员和缓存资格 | P1 | 08 I-1/I-3；09 I-3；master I-4/I-7 | Recipient:117,153,162,251–265 缺失/移层成员被丢弃，scope 先过滤隐藏 HOLD；无映射仍逐档案可发。Guard:133–154 SAFE_RETRY 绕历史复核，194–220 不重读 ES/准入；详情 profile null 直接无 guard；P08。 | REPAIRABLE |
| V-6 重试与新目标缺统一人物集合 | P1 | 08 I-2；master I-7 | ManualInitial:567–581,711–724,1967–1994 以 docId/orcid 去重，未把 personKeyOf 接入；P13 同人 retry D2 后 ES D1 又计1。Recipient:279 把全部成员邮箱当默认，未使用primaryDocId。 | REPAIRABLE |
| V-7 详情事务回滚撤销已开始投递的保护 | P1 | 09 I-2；master I-7/I-8 | ManualExpert:63/79 外层事务包含 guard reserve/claim、SMTP110、mailRecord121；Guard REQUIRED。P09 真MySQL SAFE_RETRY→SENDING→假SMTP→外层回滚后SAFE_RETRY，下一次Granted。 | REPAIRABLE |
| V-8 无法确认 SMTP 未接受却允许安全重试 | P1 | 09 I-2；master I-8 | Initial:164–166、ManualExpert:114–116 对所有非SENT标SAFE_RETRY；ManualInitial:1276仅INFRASTRUCTURE归UNKNOWN。实际SmtpMailDelivery:141–148把MessagingException变FAILED，Classifier:101无返回码为TRANSIENT，含交付后网络中断。 | REPAIRABLE |
| V-9 未发送退出遗留 RESERVED | P1 | 09 I-2；master I-8 | Initial:99–117 account/contact 异常在cleanup外；ManualInitial:1050,1068,1086,1110,1120,1147,1286 取消/compose/audit等退出未转换已持有ticket，后续永久IN_FLIGHT。 | REPAIRABLE |
| V-10 混合查询深分页被新上限拒绝 | P1 | 06 I-1；master I-6 | Query:90把from+size传size；Search:1254要求size≤1000。P04 from1000,size50 抛IllegalArgumentException。 | REPAIRABLE |
| V-11 DB联系人分组使用无效关系且改范围外单位 | P1 | 06 I-1/I-3；master I-4/I-6 | Query:247裸SQL映射不重核；ContactController:95–116 fallback orcidId把普通同orcid不同contactId合并并改排序；app默认新模式。 | REPAIRABLE |
| V-12 详情以旧filterResult冒充邮箱准入许可 | P1 | 06 I-2；07 I-1/S-2；master I-5/I-7 | Query:229 admissionStatus=filterResult，未读取V148/独立邮箱状态；app:13508 PASSED显示可发送，HOLD/抑制可能被误标。 | REPAIRABLE |
| V-13 ES不完整响应被算为精确人数 | P1 | 06 I-3；master I-8 | Search:1275,1310,1328 未拒绝timed_out/_shards.failed/body缺失/中途不完整；P12 total100且timed_out=true+failed shard正常返回0。 | REPAIRABLE |
| V-14 维护快照未绑定全部受保护事实且审计丢字段 | P1 | 10 I-1及步骤2；master I-4/I-8 | Maintenance:390仅updatedAt/RAW版本、494摘要仅docId+身份指纹；Domain:158不含proof/version/tag/seq/SQLrevision/人工历史；429/450/466读异常转null；239再次读后不对原expected。P10更换proof hash仍APPLIED；reconcile:263落盘遗漏原始复核字段。 | REPAIRABLE |
| V-15 历史保护未导入且事务/回滚契约未成立 | P1 | 10 I-2/I-3；09 I-3；master I-8 | Maintenance:228自调用@Transactional applyGroup231；248关系落库后258只读import；279未持久化SENT/UNKNOWN却报protectionImported。321回滚仅拦SENT/UNKNOWN，P11 SENDING仍safeToRollback=true。 | REPAIRABLE |
| V-16 子集试点和断点恢复无法通过全量摘要 | P1 | 10步骤3/4；master历史试点 | Maintenance:193用请求子集重建全量摘要；reconcile limit50/过滤done仍用原previewDigest，P05合法子集被PREVIEW_DIGEST_MISMATCH拒绝。 | REPAIRABLE |
| V-17 冻结清单处置不互斥 | P1 | 10 I-1；master I-1/数据交付 | Maintenance:66不拒绝候选组重叠，77重复累计；P07冻结2条、groups[a,b],[a]输出total3。 | REPAIRABLE |
| V-18 维护CLI无法通过现有会话认证 | P1 | 02/10维护入口可用性与既有访问控制 | replay:166、reconcile:223请求只有Content-Type，无cookie/auth参数；对应controller要求getSession(false).username，全部请求401。 | REPAIRABLE |
| V-19 02 部分成功无法按原请求继续 | P1 | 02 I-3；master I-3/I-8 | Recovery:100–107返回PARTIAL，但下一次inspect:194任一已有proof即拒绝；P15 首次PARTIAL，排除故障后preview=NEEDS_MANUAL:EXISTING_PROOF，同原hash apply=STALE。 | REPAIRABLE |
| V-20 PMCID 回源未核对原文来源编号 | P1 | 02 I-1验收“来源 ID 不符不签证”；master I-4 | Recovery:369–376为PMCID只构造空PaperMetadata并调用提取，不校验JATS article-id。P16真实原文article-id=PMC7759461，存量定位改PMC1234567、HTTP边界返回原字节，仍RECOVERABLE并签出proof；不 mock 身份解析。 | REPAIRABLE |
| V-25 operator_status 守卫噪声位置未随查询改动更新 | P2（mandatory） | master 必需 JDK11 clean package；CLAUDE.md 构建门禁 | OperatorStatusWriteSeamGuardTest.kt:163 在 C20/C21 均失败。ContactController:632、Query:228、IndexController:80/108/477 为只读参数/DTO 投影，旧精确排除项行号失效且新增投影未登记；原真实写入口两文件不变。基线扫描闭包通过；C21 4999 tests、1F/0E/20S。 | REPAIRABLE |


## Verification Result: FAIL

- Phase: aggregate/master
- Reviewer: /root/aggregate_reviewer; fresh fork_turns=none, no implementation/light-verification context.
- Convergence: INITIAL
- Manual acceptance: PENDING
- Result: 20 P1 mandatory behavioral violations, 1 mandatory P2 build violation; 2 mandatory evidence items BLOCKED.
- Routing: verify-p precedence gives confirmed mandatory violations FAIL; missing evidence remains BLOCKED, never converted into product defects.
- Repair planning: DRAFT_READY; no execution.

### Controller preservation note

This document persists the complete substantive review-p result returned by the independent reviewer: identity and amendments, every command/result, full contract matrix, finding lineage and detailed evidence, every RECORD_ONLY disposition, evidence boundaries, manual status, and repair-p handoff. Repeated identity/command/finding tables are consolidated without removing failed, blocked, or unavailable evidence. One transcription error is corrected: the reviewer typed child06 SHA256 `404cd02b4dd50cd2aeef2d434f183591bc36ee7bc4a62e43137ca7fe990da71b31`; fresh shasum proves `404cd02b4dd50cd2aeef2d434f183591bc36ee7bc32e43137ca7fe990da71b31`. No verdict or product content changed.

## Contract Matrix

All following items are mandatory unless explicitly marked otherwise. Source is governing master/current approved child plan; code paths resolve under the retained worktree. PASS marked “局部” proves only the named sub-contract and does not override adjacent FAIL.

| ID | Requirement / Source | Required evidence | Verdict and evidence |
|---|---|---|---|
| M-I1 | 五个方向不夸大故障或恢复人数，处置分母真实 | 统计、互斥处置、最终报告 | FAIL V-17；P07 冻结2条累计3条 |
| M-I1-旧名单 | 10,686仅背景，03不自动执行，不承诺5547全部恢复 | A1、执行边界 | PASS；03不在10child，无旧名单生产执行，无虚报恢复人数 |
| M-I2-准入 | proof不直接等于AUTO_PASSED，软硬原因分开 | Policy/Review/Revalidation、C01/C02/C05 | PASS（局部），准入调用及硬原因保留 |
| M-I2-发信资格 | 邮箱资格不能由身份或旧filterResult代替 | 06—09运行路径 | FAIL V-5/V-12 |
| M-I2-真实发送 | 本次实际邮件0 | 测试和诊断执行边界 | PASS，仅替身，无生产SMTP |
| M-I3 | 三层局部CAS、人工保护、部分失败可续跑 | writer/repair/recovery、C03/C04R/C05/P15 | FAIL V-4/V-19；01局部通过，02 fail-open/登记错误吞掉/PARTIAL不可续跑 |
| M-I4 | 冻结+实时标签、姓名邮箱绑定、完整作者键、不越范围 | 全链及反例 | FAIL V-2/V-3/V-5/V-14/V-20 |
| M-I5-存储 | SQL UUID人物真值，原docId/邮箱/联系人/alias保持 | V156/repository、C07/C09/C11 | PASS（结构），独立三表，无原ID改名或虚建联系人 |
| M-I5-逐邮箱 | 独立准入/验证/抑制状态 | Query/UI/发送 | FAIL V-5/V-12 |
| M-I6 | 两列表有效人物先聚合排序分页，范围外原单位 | Query/Search/两controller、C12/P04/P12 | FAIL V-10/V-11/V-13 |
| M-I6-筛选层级 | 同一成员完整匹配，RAW不污染CANDIDATE，旧引用保留 | C12/C14、查询链接 | PASS（所测局部） |
| M-I7 | 具体邮箱审核、完整人物保护、跨页retry、三INTRODUCTION入口 | C16/C17/P08/P09/P13、三入口并发 | FAIL V-5—V-9；完整并发另B-2 |
| M-I8 | 登记失败不成功，查询不全不伪总数，默认关闭完整开启，未知不重发 | 05—10错误/事务/开关 | FAIL V-1/V-4/V-7—V-9/V-13—V-16 |
| M-SCOPE | 批准文件并集、共享文件顺序继承 | 完整diff、10child文件表、A1—A14 | PASS，68产品/测试文件均在并集 |
| M-STORAGE | 不改既有共享schema/ES mapping，不删原档案 | 完整diff、V156/V157/C09 | PASS |
| M-NONGOALS | 不猜名、自动人工认可、真实发送、解析器全面重写、新审核UI | 完整diff/操作边界 | PASS（范围）；证据校验不足单列FAIL |
| M-RELEASE | 默认关闭总开关，回退UI不能撤首发保护 | master发布段、04读契约、接线 | FAIL V-1 |
| M-PERF | 51,829真实形状，p95≤2s、额外heap≤128MiB | 06研究门禁实测 | BLOCKED B-1 |
| M-COMMANDS | 当前brief定向命令、JDK11完整构建 | C01—C21 | FAIL V-25；定向最终通过；旧JDK19errors另O-3 |
| M-DATA | 互斥名单、docId→personId→邮箱、人物/档案/邮箱/减少展示数 | 10预览/应用/审计 | FAIL V-14/V-16/V-17；无历史生产交付 |
| admission-I1 | 缺资料软原因与明确不合格分离 | DiscoveryAdmissionPolicy:186/200、C01 | PASS |
| admission-I2 | 可信身份仍为前提 | Policy/Review、C01/C05 | PASS |
| admission-I3 | 准入唯一，补全不重新收紧 | ExpertRevalidationService:375附近、C01 | PASS |
| admission-I4 | 完整去重扫描、人工/CAS保护 | DiscoveryReviewService:855/1317附近、C01 | PASS |
| admission-I5 | 候选投影与实际发信分开 | C01/C02、无真实SMTP | PASS |
| admission-I6 | 显式发送条件继续生效 | C02 | PASS（准入局部） |
| admission-I7 | 新旧事实读取一致 | Policy/Revalidation、C01 | PASS |
| 01-I1 | 只补同身份缺证，不推测或覆盖身份 | IdentityRepairService:56/71/101/130/160、C03 | PASS |
| 01-I2 | 同邮箱唯一ID、三层版本保护 | ExpertIndexWriterService:704/940/978、C03/C04R | PASS |
| 01-I3 | partial/conflict明确、可重试 | 01独立repair路径、C03 | PASS；02缺陷另列 |
| 02-I1-正向 | 原文重新取证，不用旧摘要作证明 | P14真实原字节/解析器、C05 | PASS |
| 02-I1-来源 | 来源ID不符不得签证 | Recovery:369—376、P16 | FAIL V-20 |
| 02-I2 | 有限应用，保护联系人/alias/既有事实 | Recovery:207—214 | FAIL V-4 |
| 02-I3-只读/CAS | preview零写、apply复核来源和版本 | C05/CAS路径 | PASS（局部） |
| 02-I3-恢复 | PARTIAL/登记失败不完整成功，原申请续跑 | Recovery:100—115/194、P15 | FAIL V-4/V-19 |
| 02-I4 | 额度/SINGLETON/来源审计/断点工具 | C05/C06、A10、replay:166 | FAIL V-18；预算局部PASS |
| 04-I1 | 稳定UUID、doc唯一隶属、原事实不复制 | V156/repository/C07/C09/C11 | PASS |
| 04-I2 | 全名/每类键/已有根/竞争复验 | PersonService:61—89/279—312、P02/P03 | FAIL V-3 |
| 04-I3 | 实时来源+标签+ID+指纹，读发不盲信旧关系 | PersonService:155、P01 | FAIL V-2 |
| 04-开关 | 默认关闭，旧读写不变 | 04步骤3/验收I3、master发布 | FAIL V-1 |
| 05-I1 | 所有成功出口登记 | C10及接线 | FAIL V-4（完整成功），调用存在 |
| 05-I2 | ES/SQL中断可恢复，登记失败不成功 | Recovery:110—138、P15 | FAIL V-4/V-19 |
| 05-I3 | 单位清楚、预算不变、范围受限 | C10/C11/计数/source | 预算局部PASS；范围FAIL V-2 |
| 06-I1 | 完整匹配后分页，范围外doc/contact单位 | Query:90、Search:1254、ContactController:95—116、P04 | FAIL V-10/V-11 |
| 06-I2 | 旧引用、每邮箱自身状态 | Query:228/229、C12/C14 | 引用PASS；状态FAIL V-12 |
| 06-I3-完整性 | 完整结束才精确total，过期关系无效 | Search:1275/1310/1328、Query:247、P12 | FAIL V-11/V-13 |
| 06-I3-只读 | GET不登记修复创建联系人 | 查询调用图/C12 | PASS |
| 06-I3-性能 | 真实51,829规模预算实测 | 研究门禁/验收 | BLOCKED B-1 |
| 07-I1 | 后端人数/原ID/逐邮箱状态真实 | app/Query/C12/C14 | 状态FAIL V-12；memberCount/原ID PASS |
| 07-I2 | 两详情共用、异步隔离、转义 | C14的20项/C15/C04R/renderer | PASS（机器） |
| 07-S1 | 列表/头部数量与原布局 | A13/C14/app/index diff | PASS（机器），视觉人工PENDING |
| 07-S2 | 卡片独立状态/历史入口 | app:13508/C14 | 状态FAIL V-12；DOM/转义/入口PASS |
| 08-I1 | 先邮箱合格，完整人物保护后选址 | Recipient:117/153/162/251—265、P08 | FAIL V-5 |
| 08-I2 | 跨页/retry/新目标共用人集合/默认地址 | ManualInitial:567—581/711—724/1967—1994、P13 | FAIL V-6 |
| 08-I3 | 同源只读预估、数量一致 | C16/P08/P13 | 零写PASS；数量/保护FAIL V-5/V-6 |
| 09-I1-存储 | 人物INTRODUCTION唯一原子门禁 | V157/C09/C17 | PASS（guard局部） |
| 09-I1-接线 | 三真实入口组合/同库/假SMTP≤1 | 09测试合同/现测试边界 | BLOCKED B-2；事务V-7仍FAIL |
| 09-I2 | 持久保守状态、未知不释放、未发可恢复 | 三入口/guard/P09 | FAIL V-7/V-8/V-9 |
| 09-I3 | 全组历史/当前邮箱资格不绕过 | Guard:133—154/194—220、P08 | FAIL V-5/V-15 |
| 10-I1 | 冻结实时完整快照、互斥处置 | Maintenance:66/193/390/494、P05/P06/P07/P10 | FAIL V-2/V-14/V-16/V-17 |
| 10-I2 | 候选非核准、逐组复核/登记保护原子 | Maintenance:228—279/PersonService | FAIL V-3/V-15 |
| 10-I3 | 历史不重写、导入保护、安全回滚提案 | Maintenance:279/321、P11 | 原业务保持PASS；持久/回滚FAIL V-15 |
| 10-CLI | 50组试点/续跑/私有审计/认证 | reconcile/C19/P05 | FAIL V-14/V-16/V-18；权限/原子落盘局部PASS |

### Amendment file matrix

| Amendment / file | Mandatory rule/evidence | Verdict |
|---|---|---|
| A9 src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt | 两例新软原因语义，C01包含22项 | PASS；旧名称V-21 |
| A10 src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt | /works/{id} SINGLETON 0credit，其余计价保留，C05 Policy44 | PASS |
| A11 src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt | V156三表/版本断言，C09真库46 | PASS |
| A12 src/main/resources/static/index.html | 16资源键统一20261014-person-emails，无布局/CSS扩张 | PASS |
| A13 src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt | :98/307/326同批映射memberCount，无ES字段 | PASS（字段合同） |
| A13 src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt | :109/429/480投影memberCount | PASS（字段合同） |
| A13 src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt | :192—213人数/成员数，C12 | PASS |
| A13 src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt | :198—224响应，C12 | PASS |
| A14 src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt | V157表/主键/157版本，C09 | PASS；31/32计数漂移非行为 |

A2—A8新增child授权逐文件核对：04=7、05=8、06=8、07=3、08=10、09=11、10=6，含共享重叠。原master早期数量摘要不覆盖明确批准A9/A10/A11/A12/A14。授权PASS不豁免功能FAIL及研究/并发门禁。

## Independent diagnostic evidence

诊断源码/class/log只位于 `/tmp/discovery-aggregate-review-20261014/`，使用本工作树编译产物。诊断exit0表示成功执行并观察结果，非产品PASS。没有生产请求。

| Probe | Input and observed result | Finding |
|---|---|---|
| P01 | 保留proof/emailSource，仅移除字面discovered标签；仍CREATED、成员1 | V-2 |
| P02 | Jane Doe/John Smith共享合法ORCID；同人物成员2 | V-3 |
| P03 | 既有ORCID+OpenAlex A111，加入同ORCID+A222；ATTACHED同人物 | V-3 |
| P04 | 混合from1000,size50；IllegalArgumentException: size must be between 1 and 1000 | V-10 |
| P05 | 冻结[a,b]预览后合法子集[a]携原previewDigest；PREVIEW_DIGEST_MISMATCH | V-16 |
| P06 | 预览后移除a的discovered标签，携旧expected仍APPLIED | V-2/V-14 |
| P07 | 冻结2ID，组[a,b]、[a]；total3，MERGE_READY2+KEEP_SINGLE1 | V-17 |
| P08 | 同人d1人工拒绝但不在加载组，d2当前页合格；选d2 | V-5 |
| P09 | 真MySQL/真实Spring guard代理，持久SAFE_RETRY；等价详情外层事务reserve/claim→假SMTP1→落库异常回滚；durable恢复SAFE_RETRY，再reserve Granted | V-7；不是三实际入口E2E，不能补B-2 |
| P10 | proof evidenceHash a×64→b×64，身份字段不变；旧预览仍APPLIED | V-14 |
| P11 | guard SENDING；回滚提案safeToRollback=true | V-15 |
| P12 | 实际新ES scroll HTTP边界timed_out=true/failed shard1/total100/empty hits；正常records0 | V-13 |
| P13 | 实际ManualInitial countEsTargets：retry D2已计1，ES D1与D2同P1；旧doc/orcid seen使ES再加(1,0)，累计2 | V-6 |
| P14 | 真实归档JATS原字节、真实EuropePmcDataSource/JatsXmlEmailParser，仅HTTP替身；3邮箱3proven，RECOVERABLE/JATS_SHA256，hash/name一致 | 原文正向证据PASS |
| P15 | 实际Recovery首次RAW失败/CANDIDATE成功→PARTIAL；解除故障后preview NEEDS_MANUAL:EXISTING_PROOF，原hash apply STALE:EXISTING_PROOF | V-19 |
| P16 | 同P14原文article-id=PMC7759461，存量locator PMC1234567，HTTP返回原字节；仍RECOVERABLE/proof/source PMC1234567 | V-20 |

P14/P16归档 `src/test/resources/discovery/xml-route-recall.zip`，entry `docs/plans/2026-09-26/discovery-repair-evidence/diagnosis-source/evidence/round2/PMC7759461.xml`，76,483 bytes，SHA256 `ee45ae9621d29ac77fbbe489e29deede9d93271dd2abcb6c6523720d103ae29b`；与归档manifest/fetch一致，未mock身份解析。CLI诊断仅捕获Content-Type无session请求，并结合controller session username检查确认401通路；未访问生产。

## Detailed P1 findings

### V-1 — 默认关闭总开关缺失

master I-8/发布段、04步骤3/验收I3。DiscoveryPersonService.kt:45的“默认关闭”依赖早期没有调用者；05—09已无条件接入，查询/controller/选择无共同关闭边界，默认部署即改变旧读写。最小范围为人物公共启用边界及现有05—10调用者；关闭展示不得绕过既存发送保护。

### V-2 — 历史来源被当实时标签

master I-4、04 I-3、10 I-1。PersonService:155、Recovery:190、登记facts、Maintenance:369使用isDiscoveryMap，proof/source/tag任一满足。P01移标仍登记、P06移标仍应用，越过限定范围。修复限人物专属范围检查，不全局改变既有准入宽口径。

### V-3 — 规范全名与已有人物完整键未核验

04 I-2/master I-4。PersonService:61—89不核整个组全名；279—312附加成员未纳入根的全部成员/作者键，竞争重试沿用旧ES事实。P02不同名同ORCID合并；P03 ORCID+A111接受ORCID+A222。稳定键不能替代姓名邮箱绑定及每类键唯一。最小范围登记service/repository及真实竞争回归。

### V-4 — 恢复保护读取fail-open，登记失败仍成功

02 I-2、05 I-2、master I-3/I-8。Recovery:207—214联系人/alias异常runCatching转false/null；110—115忽略refresh FAILED/STALE/异常，138仍APPLIED。未知保护下身份写入，ES成功SQL失败假报完整成功，已有proof阻碍重试。范围恢复/补证衔接/CLI检查点。

### V-5 — 发送只看可见成员与缓存资格

08 I-1/I-3、09 I-3、master I-4/I-7。Recipient:117/153/162/251—265缺映射回普通记录，scope先过滤硬保护，仅指定层丢缺失成员。P08隐藏人工拒绝不能阻断另邮箱。Guard:133—154 SAFE_RETRY不重新收集历史；194—220依赖缓存profile/宽发现/有限邮箱检查，未重核当前ES/V148/关系/recipient。ManualExpert:198画像不可得可无guard。影响人工拒绝、移标、历史及资格变化绕过；范围选择/guard/详情接线。

### V-6 — retry/新目标无共享人物集合

08 I-2/master I-7。ManualInitial:567—581/711—724/1967—1994仍docId/orcid seen，personKeyOf未接入。P13同人retry D2与ES D1累计2。Recipient:279把全部成员邮箱当默认候选，不用primaryDocId优先。证明计数/选择重复；不单凭此宣称SMTP必重复，后续guard仍可能阻断。

### V-7 — SMTP后外层回滚撤销保护

09 I-2/master I-7/I-8。ManualExpert:63/79事务包含reserve/claim、110 SMTP、121业务写入；guard REQUIRED。P09真库可达既有SAFE_RETRY→SENDING→假SMTP→DB异常→回滚至SAFE_RETRY→再Granted。已交付后允许再发；不依赖新首行必能绕历史的假设。

### V-8 — 未证实未接收SMTP即SAFE_RETRY

09 I-2/master I-8。Initial:164—166、ManualExpert:114—116非SENT标SAFE_RETRY；ManualInitial:1276主要按INFRASTRUCTURE分未知。SmtpMailDelivery.kt:141—148把MessagingException变FAILED，classifier无响应码可TRANSIENT，包含服务端可能接受后的网络异常。须保留未知保护，不改全局分类器语义掩盖。

### V-9 — 未SMTP退出残留RESERVED

09 I-2/master I-8。Initial:99—117账户/联系人异常在cleanup外；ManualInitial:1050/1068/1086/1110/1120/1147/1286去重/compose/地址变化/取消/markSending/audit退出留ticket。未发却永久IN_FLIGHT。按是否尝试SMTP清理；不能超时释放SENDING/UNKNOWN。

### V-10 — 合法深分页失败

06 I-1/master I-6。Query:90把from+size作低层size；Search:1254限≤1000。P04第21页直接异常。不得为修复全量扫描范围外专家。

### V-11 — DB盲信旧关系且改范围外单位

06 I-1/I-3、master I-4/I-6。Query:247裸SQL映射无实时标签/指纹验证；ContactManagementController:95—116 fallback orcidId把普通不同contactId合一并改排序；app默认新模式。须恢复普通contactId单位及原过滤排序。

### V-12 — filterResult冒充逐邮箱许可

06 I-2、07 I-1/S-2、master I-5/I-7。Query:229旧filterResult写admissionStatus；app:13508 PASSED显示可发送，非V148及独立邮箱状态真值。HOLD/抑制可能误显可发送。正常/受限/未知须准确，展示主邮箱非许可。

### V-13 — ES部分失败伪精确人数

06 I-3/master I-8。Search:1275/1310/1328不拒timed_out/failed shards/缺body/不完整scroll。P12 total100故障响应正常0。应明确可重试不可用并关闭scroll，不发布部分精确数。

### V-14 — expected及审计缺完整保护事实

10 I-1/步骤2、master I-4/I-8。Maintenance:390主要updatedAt/RAW版本，494仅docId+身份指纹；DiscoveryPerson:158不含proof源hash/version、实时标签、三层seq、SQLrevision、人工历史。429/450/466必读异常转null，239新读未完整对原expected；reconcile:263省复核字段。P10 proofHash变化/P06移标仍APPLIED。不能新事实替代旧expected后称并发复核成功。

### V-15 — 历史保护未持久导入，原子及回滚不足

10 I-2/I-3、09 I-3、master I-8。Maintenance:228自调231@Transactional无代理；248关系先写，258import只读，279未写SENT/UNKNOWN却计protectionImported；321回滚仅拦部分状态/成员，P11 SENDING仍安全。SAFE_RETRY又不总查历史，故只读≠导入。须09唯一写服务持久导入、同库组原子、保守核对进行中/未知/关系/历史。

### V-16 — 全量digest拒合法子集试点/续跑

10步骤3/4、master试点。Maintenance:193按请求子集重建摘要；CLI limit或过滤done仍传全量digest。P05[a,b]预览仅[a]被拒。分离全量冻结清单身份与本次选中组，保持范围及幂等。

### V-17 — 处置非互斥

10 I-1、master I-1/数据交付。Maintenance:66不拒重叠，77逐组累计；P07冻结2条计3。入口拒重叠或明确互斥分区，不能虚报人物收益。

### V-18 — shipped CLI无session通路

02/10可用性与原鉴权。replay:166、reconcile:223只有Content-Type，无cookie参数/环境输入；controller需getSession(false).username。正常鉴权下CLI不可用。只安全传已有会话，不放开鉴权或审计凭证；诊断无生产请求。

### V-19 — 02 PARTIAL原申请不可续跑

02 I-3/master I-3/I-8。Recovery:100—107 PARTIAL后194任一已有proof即拒。P15解除故障后preview EXISTING_PROOF、旧apply STALE。辨别原请求已完成目标值与第三方变化，不能放开任意已有proof覆盖。

### V-20 — PMCID与原文编号不绑定

02 I-1明确“来源ID不符不签证”、master I-4。Recovery:369—376 PMCID构空PaperMetadata后提取，无JATS article-id核验。P16 PMC7759461用于PMC1234567仍RECOVERABLE；P14真实解析正向正常，负例是来源绑定遗漏而非mock身份。

## Mandatory P2 — V-25

master最终JDK11构建。OperatorStatusWriteSeamGuardTest.kt:163在C20/C21失败。预期真实写入口为ExpertOperatorStatusService.kt、ManualInitialOutreachService.kt；实际还命中DiscoveryPersonQueryService.kt、ExpertContactManagementController.kt、ExpertIndexController.kt。只读噪声位置：ContactManagementController:632 DTO参数、Query:228投影、IndexController:80查询参数、108/477 DTO投影。测试按path/line/context精确排除；旧行号失效、新投影未登记。基线scanner只命中原两个写入口且无失效排除，证明本实现引起。修复仅同步精确噪声，禁止加入真实白名单、删测试或放宽扫描。该测试未改，仅列入待批repair。

## Non-mandatory P2, observations, missing evidence

| ID | Classification | Evidence / disposition |
|---|---|---|
| V-21 | P2 non-mandatory / NEW | admission旧BehaviorTest名称，A9断言正确；排除修复 |
| V-22 | P2 non-mandatory / NEW | fake rejectNextCreate未用；repo并发不证明service完整ES复验；可选深度排除修复 |
| V-23 | P2 non-mandatory / NEW | SCOPE_CONFLICT缺专属计数断言，未证计数错误；排除 |
| V-24 | P2 non-mandatory / NEW | ALREADY_CONTACTED KDoc错写其他人工/冲突原因；排除 |
| O-1 | Observation | C04未改JS时序首败；focused27、C04R和其他全JS过，保留首败，不修基线 |
| O-2 | Observation | 01测试数来自前置准入；04 V154旧断言对齐既有V155；09文档31/实际32漂移；admission taskFailure按KDoc处理语义 |
| O-3 | Observation | 旧JDK tzdb19 errors，新已安装JDK11对照0errors；不改无关目录 |
| B-1 | BLOCKED mandatory evidence | 51,829真实形状脱敏数据p95≤2s/额外heap≤128MiB缺实测；brief延后、单测、小fixture、算法推断不能替代 |
| B-2 | BLOCKED mandatory evidence | 三实际入口组合、共享真库、假SMTP累计≤1缺完整E2E；C17是guard真库、service mock guard；P09等价外层事务不替代 |

B项不是severity，不作为产品repair任务。确证FAIL优先；全部缺陷修复后B项未补齐仍不得master PASS。

## Fast-P RECORD_ONLY Re-evaluation

| Source | Master requirement | Result | Evidence |
|---|---|---|---|
| admission O-1 | I-2准入语义 | nonblocking P2 V-21 | 两例断言正确，旧名不改行为 |
| admission O-2 | I-1计数 | Observation | ReviewService:1373附近KDoc；apply处理失败、dry-run冲突0有明确语义 |
| 01 O-1 | 测试证据 | Observation | ReviewServiceTest 35对旧27来自admission；01此测试diff为空 |
| 02 | N/A | N/A | 当前无RECORD_ONLY |
| 04 O-1 | I-4可靠键 | distinct本身PASS | 共享ORCID须去重；V-3是完整已有组冲突缺失 |
| 04 O-2 | I-4/I-8竞争 | catch必要，完整复验不足 | PessimisticLockingFailure覆盖死锁，不无条件成功；ES/全组重验仍V-3 |
| 04 O-3 | 真迁移/保持 | Observation | base V155已有meeting_record_id/fk_video_meeting_owner；V154断言10→11列、0→1FK与base对齐，C09过 |
| 04 O-4 | 测试深度 | nonblocking P2 V-22 | fake未用，repo6项不证明service重读ES |
| 05 O-1 | I-1计数/I-8 | nonblocking P2 V-23 | 缺专属断言，未证计数错误 |
| 06 epoch1 O-1 / epoch2 R-1 | I-6分页 | P1 V-10 | P04 from1000,size50确定异常 |
| 06 epoch1 O-2 / epoch2 R-2 | I-8研究/06 I-3 | BLOCKED B-1 | 强制真实性能缺失，brief不豁免 |
| 07 | N/A | N/A | 当前无RECORD_ONLY |
| 08 O-1 | 文档 | nonblocking P2 V-24 | ALREADY_CONTACTED KDoc |
| 09 O-1 | I-7/I-8/09 I-2 | P1 V-7 | 真MySQL SAFE_RETRY→SMTP→rollback→SAFE_RETRY→Granted |
| 09 O-2 | I-8/09 I-2 | P1 V-9 | reserve后未SMTP退出未清理 |
| 09 O-3 | 迁移记录 | Observation | 实际32处156→157及新增断言，31处漂移不改表结果 |
| 10 O-1 | I-7/I-8/10 I-2/I-3 | P1 V-15 | 只读不等价导入，未持久化，SAFE_RETRY可跳历史 |
| 10 O-2 | I-4/I-8/10 I-1 | P1 V-14/V-2 | P06/P10变更仍APPLIED，摘要不全 |
| 10 O-3 | I-8/10 I-2 | P1 V-15 | 自调用绕事务，repo单方法事务不等于整组登记+保护原子 |

## Manual acceptance

未执行、模拟或制造人工验收、截图、reporter/sign-off。Master A-1名单原文与零写、A-2补证恢复准入预估、A-3保护/冲突/部分成功/回滚、A-5三邮箱双列表旧链接三入口、A-6历史/移标/新增/重跑均PENDING；A-4原03回退按A1不在本轮，N/A。

完整child人工项状态：admission A-1缺资料、A-2硬负面身份、A-3存量去重人工、A-4补全、A-5显式条件数量、A-6行为资料；01 A-1重复补证、A-2冲突人工、A-3失败并发；02 A-1零写预览、A-2安全恢复hash、A-3来源并发保护、A-4断点备份预算；04 A-1登记重跑原值、A-2并发过期桥接；05 A-1新增重复补证、A-2中断；06 A-1两列表分页层级筛选、A-2旧链只读失败；07 A-1三邮箱两宿主、A-2窄屏异步；08 A-1地址、A-2跨邮箱保护；09 A-1三入口、A-2未知历史、A-3明确未发正常回归；10 A-1完整名单50组、A-2变化中断重跑：全部PENDING。未创建额外人工需求，未将机器证据当人工通过。

## Evidence boundaries and final reviewer state

- B-1性能、B-2实际三入口并发仍缺；P14补足真实原文正向，V-20负例仍在。
- 无03旧范围、10,686生产名单、历史生产apply、真实SMTP、生产开关切换或人工验收。
- /tmp诊断非持久审查文件；本报告持久保存方法/结果/限制/原始字节hash。
- Reviewer未改产品、测试、批准计划、fast evidence、aggregate evidence、index、HEAD、branch。
- Reviewer结束：HEAD=1408138b429297ad7b5a949d0b8e7839bdd4ee63；git diff --exit-code、git diff --cached --exit-code、git diff --check均0。status仅新增fix/discovery-identity-recovery-master/和控制器已有review/2026-10-13-discovery-identity-recovery-master/。其时machine-verification.md不存在。

## Repair Planning Result: DRAFT_READY

Baseline: docs/plans/2026-10-13/discovery-identity-recovery-master.md。Verification: FAIL / INITIAL。

唯一artifact：docs/plans/fix/discovery-identity-recovery-master/repair.md；SHA256 845500de628b9e7fe8671171c3e90f49f1afc6bbac2f85e05b11a57be8bd4ca5；61,462 bytes；DRAFT — HUMAN APPROVAL REQUIRED。

18根因任务、47现有精确文件，无占位符。46在本次68文件差异内，新增待授权既有OperatorStatusWriteSeamGuardTest.kt仅修V-25。V-1—V-20 P1与V-25 mandatory P2均REPAIRABLE；V-21—V-24、B-1/B-2、O-1—O-3排除。每任务含精确文件、根因、辨别回归、既有命令、禁止项、完成标准；无实施或自动续轮。

| Task | Findings | Required outcome |
|---|---|---|
| R-1 | V-1 | 默认关闭，既有首发保护不可随UI回退绕过 |
| R-2 | V-2 | 当前discovered范围 |
| R-3 | V-3 | 全名/全组作者键/竞争重验 |
| R-4 | V-4/V-19 | 错误显式、已验证检查点续跑 |
| R-5 | V-20 | PMCID原文编号绑定 |
| R-6 | V-5 | 完整硬保护、reserve/claim当前资格 |
| R-7 | V-6 | retry/新目标人seen、默认地址 |
| R-8 | V-7/V-8/V-9 | 持久保守发送、未知保护、preSMTP清理 |
| R-9 | V-10 | 合法深分页 |
| R-10 | V-13 | 部分ES失败并关闭scroll |
| R-11 | V-11 | 有效DB映射、普通contact单位 |
| R-12 | V-12 | 两详情真实状态 |
| R-13 | V-14 | 完整expected/私有审计 |
| R-14 | V-15 | 原子历史保护/保守回滚 |
| R-15 | V-16 | 全量冻结与子集分离/幂等 |
| R-16 | V-17 | 每冻结ID恰一处置 |
| R-17 | V-18 | CLI现有session认证 |
| R-18 | V-25 | 精确噪声位置、原写入口白名单 |

### One-Approval Execution Handoff / required human decision

repair已解析product subject `fix(discovery): repair person identity and outreach invariants`、evidence subject `docs(review): record discovery person repair execution`，唯一executor evidence为本工作树docs/plans/review/2026-10-13-discovery-identity-recovery-master/repair-execution.md，fast handoff仍只读。

后续仅明确人类来源exact execute-p批准限定修复、验证、一次产品commit和一次限定docs handoff commit。未授权amend/push/merge/部署/扩大范围/改aggregate裁定。

One permitted next action (approve, execute, persist durable handoff, then resume aggregate review):

```text
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md
After READY_FOR_VERIFICATION, resume $review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fast/2026-10-13-discovery-identity-recovery-master/human-review-handoff.md using the committed repair-execution.md. Do not ask me to relay executor metadata.
```

No implementation was performed. No product code was modified.


---

## Epoch 2 — 2026-10-15 (Asia/Shanghai)

## Verification Result: FAIL

Plan: [discovery-identity-recovery-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/2026-10-13/discovery-identity-recovery-master.md)
Phase: `aggregate/master`，Epoch 2
Reviewer: `/root/aggregate_reviewer_epoch2`，独立 post-repair reviewer；未参与实现、修复或轻量验证。
Implementation boundary: `9d353934b82ed3e36a193c70748e5efccb3d9e82..b08b521c12b3d0fbbf219bbb7c8a9fc61a2ec2e9`
Convergence: **DIVERGING**
Manual acceptance: **PENDING**

21 个必需命令全部首次退出 0。仍有 **11 个 P1 必需行为缺陷**：10 个旧问题持续、1 个新增正常首发回归。另有 3 个非阻断 P2；B-1/B-2 必需证据仍 BLOCKED。确认行为违反优先，整体结果为 FAIL。

### 身份、授权与审查边界

- Repository：`/Users/lukai/IdeaProjects/weibo-talent-introduction`
- Retained worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch：`fast/2026-10-13-discovery-identity-recovery-master`
- Prior code：`cd6a38d662da881ec453839921922a30602eacb3`
- Current code：`b08b521c12b3d0fbbf219bbb7c8a9fc61a2ec2e9`
- Evidence HEAD：`d6186c63930b8d897d45d069ba3cc90bce427887`
- Pre-execution evidence：`e60ed1379af8049853160d32f3305e0e811c618b`
- Resolution：`DISCOVERED_FROM_GIT_WORKTREES`；输入的 selected discovery JSON 唯一命中本工作树。未使用其他工作树 review ledger。
- Master identity：`CONSISTENT`；invoked 与 governing 相同，无 governing diff。
- Governing master SHA256：`f69d8135ce0019ad36cc8939cf8101a6f584aac9983dec3dfc78eb2ae37ce238`
- Recorded master commit：`36075ec06c5e4382f13606c995e24a13ec4470ce`
- Fast ledger SHA256：`2bbeec0ca9bbaf2008aed29b4265862a089a84cdbc25f20160a3e6ef5fe361e2`
- Fast handoff SHA256：`ff9fa89083d24f9a3f6b37f798a8f201273df9c7b543d8a572316e7c4a390a64`
- Approved repair：[repair.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md)
- Repair SHA256：`845500de628b9e7fe8671171c3e90f49f1afc6bbac2f85e05b11a57be8bd4ca5`
- Repair evidence mode：`DURABLE_HANDOFF`。
- Approval：人工精确调用 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/fix/discovery-identity-recovery-master/repair.md`，记录于 [repair-execution.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/review/2026-10-13-discovery-identity-recovery-master/repair-execution.md)。
- Executor：execute-p main integrator、RepairDiscoveryCore、RepairQueryUi、RepairOutreach；canonical session ID `UNAVAILABLE`，属可选元数据，不阻断本次验证。
- Prior report：[machine-verification.md，Epoch 1](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/review/2026-10-13-discovery-identity-recovery-master/machine-verification.md)。保留其 V-1—V-25、B-1/B-2 lineage；未继承其 verdict 作为当前证据。

已检查完整累计变更、主计划、十个当前子计划、baseline、fast ledger/handoff、全部 40 份 child brief/execution/verify-log/fix-log，以及批准 repair/handoff。中途 usage-limit 恢复延续同一 epoch、同一 runner，未重复已完成命令、未并发 Maven。

新鲜 scope audit：

| 边界 | 文件数 | 授权结论 |
|---|---:|---|
| prior code → current code | 40：20 产品/工具、20 测试 | 全部属于 repair 的 47 个 Authorized Files |
| master base → current code | 69：36 产品/工具、33 测试 | 全部属于批准 child 文件并集，加 repair 单独授权的 OperatorStatusWriteSeamGuardTest |
| 十 child 明确文件表并集 | 68 | 无累计范围外产品/测试文件 |
| current code → evidence HEAD | 1 | 仅 repair-execution.md |

精确文件核对记录：[scope-audit.json](/tmp/discovery-aggregate-epoch2/scope-audit.json)。child 文件表计数依次为 `11,8,11,7,8,8,3,10,11,6`，含共享重叠。

祖先检查 `base→current code`、`prior code→current code`、`current code→HEAD` 均退出 0。结束时 HEAD、branch、master/repair hash 未变；index 空；`git diff HEAD -- src tools` 空。唯一 tracked pending 是控制器的 review ledger。

### Ordered child identities

所有下列文件位于本 retained worktree 的 `docs/plans/2026-10-13/`；当前字节与记录 commit 一致。

| Child / exact filename | Recorded commit | SHA256 |
|---|---|---|
| admission-relaxation / discovery-admission-relaxation.md | f7c7604c268aad69df6afd15efe0eed91866a237 | e2ebfb727c056b8de709150304a0cf9bc0ea4b76f7e41ebec27fa452e5e281b2 |
| 01-duplicate / discovery-identity-recovery-01-duplicate.md | b06a71898bc7850d2caeb244c9f8478f18a3b2ae | d78c2b0e05a203558820645cc440c82b4420a782768ab9b88be2d74deb937902 |
| 02-replay / discovery-identity-recovery-02-replay.md | 58a9e511e327d61e8e3645c738921979ff26a518 | 4bee186d37236f0e000e07c39941915a8eb37283eda44e4804d1b1ee9950c407 |
| 04-registry / discovery-person-04-registry.md | 9bf6b8ec1319863002f7d21f4320108a0a8ec2e3 | cdd91cdef308f651733d579bd1c5ff0140dbe7b0e8e7e3a851115a31d8c42c1d |
| 05-ingestion / discovery-person-05-ingestion.md | 36075ec06c5e4382f13606c995e24a13ec4470ce | 924e929a9228d4ba302a8ce0df6017337e430cf986c70716ef2d50fc667d92ff |
| 06-query / discovery-person-06-query.md | 8b320426228ff9cf5e55447826bdb009bcdfa34a | 404cd02b4dd50cd2aeef2d434f183591bc36ee7bc32e43137ca7fe990da71b31 |
| 07-ui / discovery-person-07-ui.md | a2d4166bf6ff7e61313d69a420cd5f98e28a8911 | e0cad0a75d41f917d679a6eeadcf7010c8897bf27ea646e197af30c7f2a589d0 |
| 08-selection / discovery-person-08-selection.md | 36075ec06c5e4382f13606c995e24a13ec4470ce | aed785ba70933fd10ef73ee9a3934c5714d195894e54f69e2b15386919869916 |
| 09-send-guard / discovery-person-09-send-guard.md | 6a774804e918342bb65ae4549247e37fccd0d59b | 1489119289122de3e89935744084c1fc30b302c3f2a54404d423d6f112aa59de |
| 10-history / discovery-person-10-history.md | 36075ec06c5e4382f13606c995e24a13ec4470ce | 122436d48050cd7c3811265136300754ef4ae739b34ab44b9cd3ba10dcda691b |

### Approved amendments

下表沿用 fast ledger 的批准规则。身份缩写仅为表格引用：

- B：`b06a71898bc7850d2caeb244c9f8478f18a3b2ae`
- M：`36075ec06c5e4382f13606c995e24a13ec4470ce`
- H1：人工「计划已经更新 你再看一下」，2026-10-13 22:43+0800。

| ID | Before → After | Governing master rule、变更理由 | Human approval |
|---|---|---|---|
| A1 | B → M | 需求描述“用户追加同人多邮箱合并”及实现方案“当前追加：新发现人物合并（04—10）”；增加 04—10，10,686 降为背景，03 存量执行停用，admission/01/02 先于 04—10 | H1 |
| A2 | B → M | 主文件表 04、实现步骤1；建立人物关系与独立存储范围 | H1 |
| A3 | B → M | 主文件表 05、实现步骤1—4；发现与补证成功出口登记 | H1 |
| A4 | B → M | 主文件表 06、完整流式研究门禁；查询、精确聚合与分页 | H1 |
| A5 | B → M | 主文件表 07、S-1/S-2；两宿主多邮箱展示 | H1 |
| A6 | B → M | 主文件表 08、实现步骤1—3；逐邮箱筛选及人物去重 | H1 |
| A7 | B → M | 主文件表 09、实现步骤1—4；人物首发持久保护 | H1 |
| A8 | B → M | 主文件表 10、实现步骤1—6；冻结清单维护、历史保护及审计 | H1 |
| A9 | B → f7c7604c268aad69df6afd15efe0eed91866a237 | 准入/CAS/重验；追加 BehaviorTest，仅两例断言改为批准的新软原因语义 | 「批准：更新 2 例为新语义」，2026-10-13 23:06 |
| A10 | B → 58a9e511e327d61e8e3645c738921979ff26a518 | 实现步骤2/I-4；追加 OpenAlexRequestPolicy，`/works/{id}` 为 SINGLETON、0 credit，其他计价不变 | 「A：授权 policy，/works/{id}→SINGLETON」，2026-10-14 00:52 |
| A11 | M → 9bf6b8ec1319863002f7d21f4320108a0a8ec2e3 | 04/V156；追加迁移测试、最新版本及三表断言 | 「批准：改31处断言+加V156断言」，2026-10-14 07:44 |
| A12 | M → a2d4166bf6ff7e61313d69a420cd5f98e28a8911 | 07 缓存同步；追加 index.html，16 个资源键统一 | 「批准：bump到20261014-person-emails」，2026-10-14 12:17 |
| A13 | M → 8b320426228ff9cf5e55447826bdb009bcdfa34a | 07 I-1/S-1/A-1、06 实现步骤2；四文件补同批 memberCount | 「补全：改06加成员数，重跑06+07」，2026-10-14 12:33 |
| A14 | M → 6a774804e918342bb65ae4549247e37fccd0d59b | 09/V157；追加最新迁移版本、表/键约束断言 | 「批准：改断言156→157+V157断言」，2026-10-14 15:19 |

### Commands

全部从 retained worktree 执行。新鲜结果来自本 reviewer 的串行 runner；完整 stdout/stderr 与退出码保留于 [results.jsonl](/tmp/discovery-aggregate-epoch2/results.jsonl) 及同目录 `Cxx.log`。

计数格式：`T/F/E/S`＝测试数/失败/错误/跳过。16 个 Maven 命令均额外执行 JS：**1680 tests、1680 pass、0 fail、0 skipped**。

| ID | Exact command | Result / evidence |
|---|---|---|
| C01 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest test` | PASS，exit 0；149/0/0/0；160.68s |
| C02 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test` | PASS，exit 0；219/0/0/0；156.67s |
| C03 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test` | PASS，exit 0；306/0/0/0；218.33s |
| C04 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test` | PASS，exit 0；48/0/0/0；139.14s |
| C05 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest test` | PASS，exit 0；163/0/0/0；150.55s |
| C06 | `python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'` | PASS，exit 0；15 tests，OK；0.15s |
| C07 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test` | PASS，exit 0；真实 MySQL，6/0/0/0；167.56s |
| C08 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonServiceTest test` | PASS，exit 0；20/0/0/0；145.32s |
| C09 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | PASS，exit 0；真实迁移，46/0/0/0；669.74s，suite 531.456s |
| C10 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test` | PASS，exit 0；280/0/0/0；204.02s |
| C11 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test` | PASS，exit 0；26/0/0/0，其中真实仓储6；159.58s |
| C12 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonQueryServiceTest,ExpertSearchServiceTest,ExpertIndexControllerTest,ExpertContactManagementControllerTest test` | PASS，exit 0；143/0/0/0；131.01s |
| C13 | `node --check src/main/resources/static/app.js` | PASS，exit 0；语法检查无诊断；0.09s |
| C14 | `node --test src/test/js/discoveryPersonEmails.test.js` | PASS，exit 0；23 pass、0 fail、0 skipped；0.23s |
| C15 | `node --test src/test/js/*.test.js` | PASS，exit 0；1680 pass、0 fail、0 skipped；3.81s |
| C16 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test` | PASS，exit 0；245/0/0/0；169.79s |
| C17 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonOutreachGuardServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ManualExpertMailServiceTest test` | PASS，exit 0；263/0/0/0，其中真实guard16；177.32s |
| C18 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test` | PASS，exit 0；26/0/0/0；151.29s |
| C19 | `python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'` | PASS，exit 0；18 tests，OK；0.22s |
| C20 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | N/A，本 epoch 不执行；批准 repair §Verification Commands 明确保留旧 JDK tzdb 环境例外，由 C21 履行同一 JDK11 门禁 |
| C21 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn clean package` | PASS，exit 0；5047/0/0/20；JS1680 全通过；WAR/repackage 成功；344.34s |
| C22 | `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -Dtest=OperatorStatusWriteSeamGuardTest test` | PASS，exit 0；1/0/0/0；134.79s |

执行数：**21 个 distinct required commands＝16 Maven＋2 Python＋3 Node**。本 epoch 无必需命令首次失败、无必需命令重跑。

C20 例外仅针对已记录的 Zulu11.0.15/tzdb2021e 缺少 `America/Coyhaique`；未改产品时区数据。C21 使用已安装 Zulu11.0.32.1，未跳过失败用例。Maven3.9.11、Node25.7.0、Python3.13.1；Docker29.4 临时测试库，socket 访问经 host permission。

C21 的 20 skipped 保留原样：包含 opt-in 集成测试、3 个大视频条件测试，以及既有 EuropePMC 网络 smoke、RagLetterComposer 的 `@Disabled`；不视为已执行。主合同要求的人物仓储、迁移、guard 已由 C07/C09/C11/C17 实际执行，均非 skipped。

| Required source | Fresh command mapping |
|---|---|
| admission | C01、C02 |
| 01 | C03、C04 |
| 02 | C05、C03、C06 |
| 04 | C07、C08、C09 |
| 05 | C10、C11 |
| 06 | C12 |
| 07 | C13、C14、C15 |
| 08 | C16 |
| 09 | C17、C09 |
| 10 | C18、C19 |
| master/CLAUDE JDK11 gate | C21；C20 为批准环境例外 |
| repair R-18 | C22、C21 |

### Contract Matrix

下列机器项均 mandatory；局部 PASS 仅覆盖该行，不抵消其他行 FAIL。来源为上述 exact master/current approved child；V/P2/B 引用后文详细证据。

| ID | Requirement / source | Required evidence | Verdict / fresh evidence |
|---|---|---|---|
| M-I1 | 五方向不夸大故障或恢复人数，处置分母真实 | 互斥统计、最终报告 | PASS；重叠组拒绝，C18/C19；未宣称生产恢复人数 |
| M-I1-旧名单 | 10,686 仅背景，03 不自动执行，不承诺5547全恢复 | A1、执行边界 | PASS；未执行旧名单或03 |
| M-I2-准入 | proof 不直接等于 AUTO_PASSED，软硬原因分开 | Policy/Review/Revalidation | PASS，C01/C02/C05；准入政策仍独立 |
| M-I2-发信资格 | 身份或旧filterResult不能代替邮箱发送资格 | 06—09实际路径 | FAIL，V-5/V-12 |
| M-I2-真实发送 | 本次实际邮件0 | 执行边界 | PASS；仅替身，无真实SMTP |
| M-I3 | 三层局部CAS、人工保护、失败可续跑 | writer/recovery/CLI | FAIL，V-4；原部分层续跑 V-19 已修复 |
| M-I4 | 冻结及实时范围、姓名邮箱证据、完整作者键 | 全链当前事实复验 | FAIL，V-2/V-3/V-5/V-14；来源编号 V-20 已修复 |
| M-I5-存储 | SQL稳定UUID，保留原docId/邮箱/contact/alias | V156、真实repository | PASS（存储结构），C07/C09/C11 |
| M-I5-逐邮箱 | 每邮箱独立准入、验证、抑制事实 | Query/UI/发送 | FAIL，V-5/V-12 |
| M-I6 | 两列表有效人物先聚合排序分页，范围外单位不变 | Query/Search/controllers | FAIL，V-13；深分页 V-10、DB无效关系 V-11 原根因已修复 |
| M-I6-筛选层级 | 同一成员完整匹配，RAW不污染候选，旧引用保留 | 查询/renderer测试 | PASS（所测合同），C12/C14 |
| M-I7 | 完整人物保护、具体邮箱审核、跨页retry、三入口 | 08/09运行链、同库并发 | FAIL，V-5/V-8/V-26；完整三入口证据另 B-2 |
| M-I8 | 登记不成功不报完成、查询不全不伪总数、开启完整、未知不重发 | 失败/开关/生命周期 | FAIL，V-1/V-4/V-8/V-13/V-14/V-15/V-26 |
| M-SCOPE | 批准文件并集、共享文件顺序继承 | 完整累计diff、47-row repair授权 | PASS；69累计文件均获授权，40修复文件均在47表内 |
| M-STORAGE | 不改已有共享schema/ES mapping，不删原档案 | 完整diff、V156/V157 | PASS，C09；仅新增批准表 |
| M-NONGOALS | 不猜名、不批量人工认可、不真实发送、不全面重写解析器、不新增审核UI | 完整diff与操作边界 | PASS（范围）；证据校验缺陷另列 |
| M-RELEASE | 默认关闭；回退展示不撤既有首发保护 | master发布段、R-1 | FAIL，V-1 |
| M-PERF | 51,829真实形状、p95≤2s、额外heap≤128MiB | 当前代码实测 | BLOCKED，B-1 |
| M-COMMANDS | 所有定向命令、JDK11完整构建 | C01—C19/C21/C22 | PASS，21命令首次exit0；C20批准例外 |
| M-DATA | 互斥名单、映射及各计数、可核对审计 | 10预览/apply/rollback | FAIL，V-14/V-15；子集/互斥已修复；无生产历史交付 |
| admission-I1 | 缺资料软原因与明确不合格分离 | Policy/Review | PASS，C01 |
| admission-I2 | 可信身份仍为前提 | Policy/Recovery | PASS，C01/C05 |
| admission-I3 | 准入唯一、补全不重新收紧 | Revalidation | PASS，C01 |
| admission-I4 | 完整去重扫描、人工/CAS保护 | Review/all pages | PASS，C01 |
| admission-I5 | 候选投影与真实发信分开 | 准入/发送调用图 | PASS，C01/C02；无真实SMTP |
| admission-I6 | 显式发送条件继续生效 | 既有scope筛选 | PASS（准入局部），C02；新增人物首发回归另 V-26 |
| admission-I7 | 新旧事实读取一致 | Policy/Revalidation | PASS，C01 |
| 01-I1 | 只补同身份缺证，不推测或覆盖身份 | Repair/Writer | PASS，C03 |
| 01-I2 | 同邮箱原ID、三层版本保护 | Writer/CAS | PASS，C03/C04 |
| 01-I3 | partial/conflict明确、可重试 | 独立repair路径 | PASS，C03；02后续准入失败另 V-4 |
| 02-I1-正向 | 原文重新取证，旧摘要不作证明 | 真实解析器/归档字节 | PASS，C05 |
| 02-I1-来源 | 来源ID不符不得签证 | JATS article-id校验 | PASS，C05，V-20 RESOLVED |
| 02-I2 | 有限应用、联系人/alias读取失败保护 | Recovery | PASS（该子项），保护读取异常停止；C05 |
| 02-I3-只读/CAS | preview零写、apply复验来源与版本 | Recovery/Writer | PASS（该子项），C05 |
| 02-I3-恢复 | 后续登记/准入失败不报完整成功，原申请可续跑 | Recovery/CLI | FAIL，V-4、P2K；原层间PARTIAL V-19已修复 |
| 02-I4 | 预算/SINGLETON、来源审计、认证工具 | Policy/CLI | PASS，C05/C06；A10/V-18 |
| 04-I1 | 稳定UUID、doc唯一归属、原事实不复制 | V156/repository | PASS，C07/C09/C11 |
| 04-I2 | 全名、每类键、既有根完整成员、竞争复验 | PersonService | FAIL，V-3、P2A/P2G |
| 04-I3 | 当前来源/标签/ID/指纹；读发不盲信旧关系 | 登记/读取/选址 | FAIL，V-2/V-5 |
| 04-开关 | 默认关闭，未开启旧范围行为不变 | Gate/三发送入口 | FAIL，V-1、P2F |
| 05-I1 | 成功出口完成登记及后续链 | 发现/恢复接线 | FAIL，V-4；调用存在不等于完整成功 |
| 05-I2 | ES/SQL/准入中断可恢复，不吞失败 | Recovery/Registration | FAIL，V-4；V-19原根因已修复 |
| 05-I3 | 单位清楚、预算不变、范围受限 | 计数、Policy、当前范围 | 预算/计数PASS；范围FAIL V-2 |
| 06-I1 | 匹配后分页；范围外doc/contact单位 | Query/Search/controllers | PASS（分页/分组原根因），C12；V-10/V-11 RESOLVED |
| 06-I2 | 原链接与每邮箱自身状态 | Query/UI | 原链接PASS；状态FAIL V-12 |
| 06-I3-完整性 | 完整响应才发布精确total | Search `_count`/hits/SQL关系 | FAIL，V-13、P2I/P2N |
| 06-I3-只读 | GET不登记、不修复、不建contact | 调用图/C12 | PASS |
| 06-I3-性能 | 真实形状预算实测 | 研究门禁 | BLOCKED，B-1 |
| 07-I1 | 后端人数/原ID/逐邮箱状态真实 | DTO/renderer | memberCount/原ID PASS；状态FAIL V-12 |
| 07-I2 | 两宿主共用、异步隔离、转义 | C14/C15 | PASS（机器），23个定向JS全通过 |
| 07-S1 | 列表/头部数量与原布局 | A12/A13、DOM/资源diff | PASS（机器）；视觉人工PENDING |
| 07-S2 | 独立状态、旧历史入口 | renderer/Query | DOM/入口PASS；资格标签FAIL V-12 |
| 08-I1 | 逐邮箱资格、完整人物硬保护后选址 | Recipient/Guard | FAIL，V-5、P2H |
| 08-I2 | retry/新页共用人物集合、合格primary优先 | Iterator/ManualInitial/Recipient | PASS，C02/C16，V-6 RESOLVED |
| 08-I3 | 同源只读预估，资格与数量一致 | 预估/选址 | 零写PASS；当前资格FAIL V-5 |
| 09-I1-存储 | INTRODUCTION唯一原子持久门禁 | V157、真实guard | PASS（guard局部），C09/C17 |
| 09-I1-接线 | 三真实入口、共享真实DB、并发假SMTP≤1 | 真实组合测试 | BLOCKED B-2；正常首发另 FAIL V-26 |
| 09-I2 | 持久保守状态、未知不释放、未发可恢复 | 三入口与真实事务 | FAIL，V-8/V-26；V-7/V-9原根因已修复 |
| 09-I3 | 全组历史和当前邮箱资格不得绕过 | Guard/Recipient/import | FAIL，V-5/V-15 |
| 10-I1 | 冻结实时完整快照、互斥处置 | Maintenance/CLI | FAIL，V-14；实时标签、互斥、子集原问题已修复 |
| 10-I2 | 候选非核准、完整复验、登记与保护原子 | PersonService/Maintenance/import | FAIL，V-3/V-15；自调用事务缺陷已改为TransactionTemplate |
| 10-I3 | 历史不重写、导入保护、安全回滚提案 | import/rollback | 原业务保持PASS；保护/版本FAIL V-15 |
| 10-CLI | 50组试点/续跑/认证/私有审计 | C18/C19、expected校验 | 认证/子集PASS；错误“自身续跑”识别FAIL V-14 |

### Amendment file matrix

每个 retroactively authorized 文件单独核对；授权 PASS 不豁免功能 FAIL。

| Amendment / file | Mandatory governing rule / evidence | Verdict |
|---|---|---|
| A9 [ExpertRevalidationServiceBehaviorTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt) | 两例断言匹配新软原因政策，C01中22项 | PASS；旧名称保留为V-21 |
| A10 [OpenAlexRequestPolicy.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt) | `/works/{id}` SINGLETON/0credit，其他计价保留；C05 Policy44项 | PASS |
| A11 [FlywayMigrationIntegrationTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt) | V156三表/版本断言；已有V155事实对应旧断言纠正；C09真实46项 | PASS |
| A12 [index.html](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/resources/static/index.html) | 16资源键统一20261014-person-emails；无额外样式扩张 | PASS |
| A13 [DiscoveryPersonQueryService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt:130) | 同批有效关系产生memberCount，不新增ES字段 | PASS（字段合同） |
| A13 [ExpertIndexController.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt:109) | 响应投影memberCount | PASS（字段合同） |
| A13 [DiscoveryPersonQueryServiceTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt) | 人数/成员数合同；C12 Query23项 | PASS |
| A13 [ExpertIndexControllerTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt) | memberCount响应合同；C12 controller20项 | PASS |
| A14 [FlywayMigrationIntegrationTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt) | V157表/键/157最新版本；C09 | PASS；31/32文档计数偏差非行为问题 |
| repair R-18 [OperatorStatusWriteSeamGuardTest.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt:74) | 精确只读噪声位置、保持两真实写入口及失效排除检查 | PASS，C21/C22 |

A2—A8 建立新增 child 的实施授权范围；各文件表已逐文件与累计 diff 核对。无借旧 master 文件数量摘要否定明确批准的 A9—A14，也无借 amendment 放宽其书面规则。

### Approved repair completion matrix

| Repair task | Verdict | Evidence |
|---|---|---|
| R-1 默认关闭及既有保护 | FAIL | V-1；未开启且无mapping仍被无条件guard阻断 |
| R-2 实时范围 | FAIL | V-2/V-5；选址沿用无效当前标签/缓存关系 |
| R-3 整组竞争复验 | FAIL | V-3；递增登记未完整核验既有根成员 |
| R-4 恢复检查点 | FAIL | V-4；准入失败仍APPLIED；V-19原PARTIAL已修复 |
| R-5 精确来源 | PASS | V-20、C05 |
| R-6 完整人物保护/当前资格 | FAIL | V-5；新history复核又暴露V-26 |
| R-7 人物去重/primary | PASS | V-6、C02/C16 |
| R-8 持久保守生命周期 | FAIL | V-8/V-26；V-7/V-9原根因已修复 |
| R-9 深分页 | PASS | V-10、C12 |
| R-10 查询完整性 | FAIL | V-13 |
| R-11 DB分组 | PASS | V-11原无效关系/范围外单位根因；SQL失败伪精确另V-13 |
| R-12 逐邮箱真值 | FAIL | V-12；stale仍显示可发送 |
| R-13 完整维护快照 | FAIL | V-14 |
| R-14 历史保护/回滚 | FAIL | V-15 |
| R-15 子集及安全幂等 | FAIL | 子集V-16已修复；“自身续跑”误认第三方变化仍违反本任务，归V-14 |
| R-16 互斥处置 | PASS | V-17、C18/C19 |
| R-17 CLI认证 | PASS | V-18、C06/C19 |
| R-18 构建守卫 | PASS | V-25、C21/C22 |

### Finding Lineage

| Finding | State | Current evidence |
|---|---|---|
| V-1 P1 | PERSISTENT | gate已新增；三发送入口仍无条件接guard，默认关闭无mapping被拒，P2F |
| V-2 P1 | PERSISTENT | 多处live判定已修；注册丢失非live既有成员、mapped选址仍接纳非live，P2G/P2H |
| V-3 P1 | PERSISTENT | 请求组全名/作者键/冲突重读已加强；已有根完整成员仍漏验，P2A/P2G |
| V-4 P1 | PERSISTENT | 联系人读取fail-closed、登记失败PARTIAL已修；准入失败仍APPLIED且CLI终结，P2K |
| V-5 P1 | PERSISTENT | 隐藏HOLD、缺失成员保护已加强；inBatch缓存不重读，当前标签/指纹仍漏验，P2H |
| V-6 P1 | RESOLVED | retry/新目标/iterator接入共同人物集合，primary只在合格时优先；C02/C16 |
| V-7 P1 | RESOLVED | reserve/claim采用REQUIRES_NEW，外层回滚保留SENDING；C17真实guard测试 |
| V-8 P1 | PERSISTENT | 无响应码变UNKNOWN已修；任意非空响应码仍SAFE_RETRY，包括250，P2J |
| V-9 P1 | RESOLVED | 原account/contact/compose异常及ManualInitial未发退出清理已补；C02/C16/C17；正常流程回归另V-26 |
| V-10 P1 | RESOLVED | “其他”前缀分块读取≤1000，from1000/size50回归；C12 |
| V-11 P1 | RESOLVED | DB关系按当前档案校验，范围外保留contactId单位及顺序；C12 |
| V-12 P1 | PERSISTENT | 已读持久准入/抑制；stale状态仍独立于ADMITTED→可发送，P2L |
| V-13 P1 | PERSISTENT | 新search拒绝timeout/failed shard/null已修；缺hits与共享_count仍伪精确，P2I/P2N |
| V-14 P1 | PERSISTENT | 快照扩充、CLI审计加强；新增resume分支接受第三方proof/revision变化，P2B/P2C |
| V-15 P1 | PERSISTENT | TransactionTemplate、SENDING/RESERVED rollback保护已修；历史key漏导入、revision回滚仍不安全，P2M/P2D |
| V-16 P1 | RESOLVED | 全量冻结摘要与子集group expected分开；51组→50+1，C18/C19；错误resume归V-14 |
| V-17 P1 | RESOLVED | preview/apply拒绝重叠组，分母互斥；C18/C19 |
| V-18 P1 | RESOLVED | 两CLI明确cookie输入、401停止、审计不落凭证；C06/C19 |
| V-19 P1 | RESOLVED | 部分层目标值可按原检查点继续，第三值拒绝；C05/C06 |
| V-20 P1 | RESOLVED | 原JATS article-id核对locator；真实归档正负样本，C05 |
| V-21 P2 nonmandatory | PERSISTENT | 两个旧revalidation测试名称仍描述旧语义；授权限定断言，未发现行为问题 |
| V-22 P2 nonmandatory | RESOLVED | 新增真实触发fake create conflict后改变ES事实的辨别性测试；C08 |
| V-23 P2 nonmandatory | PERSISTENT | 05缺专门SCOPE_CONFLICT计数传播断言；源码未见相应功能错误 |
| V-24 P2 nonmandatory | PERSISTENT | ALREADY_CONTACTED KDoc仍为复制说明；无行为影响 |
| V-25 P2 mandatory | RESOLVED | 精确噪声排除项更新，真实写白名单不扩张；C21/C22均通过 |
| V-26 P1 | NEW / REGRESSION | 修复新增claim历史复核阻断自身刚创建的NEW联系人；真实Initial入口/真实MySQL，P2E/P2O |

计数仅作清单：旧25项中12 RESOLVED、13 PERSISTENT；新增1项P1。收敛判断不以数量减法代替行为判断。

### Findings — P1

**V-1：默认关闭仍改变未建立人物保护的旧发送行为。**

要求：master I-8/发布段、04开关、repair R-1 明确“保持未开启、无人物保护的旧范围行为”。

[DiscoveryPersonService.kt:45](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt:45) 新增默认 false gate，登记/查询/选址可关闭；但 [InitialOutreachService.kt:85](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:85)、[ManualInitialOutreachService.kt:1014](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1014)、[ManualExpertMailService.kt:212](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:212) 仍按旧宽范围 `isDiscoveryOutreach` 无条件调用人物guard。P2F 真MySQL输出 `OUTREACH_PERSON_MISSING`，即默认关闭、未映射、未建立保护也被拒。保留已建立SENT/UNKNOWN/SENDING保护，不能替代该旧行为要求。最小涉及范围：gate与三发送入口/guard接线。

**V-2：实时标签失效后，人物成员与选址仍可沿用旧关系。**

要求：master I-4、04 I-3、repair R-2。

[DiscoveryIdentityRepairService.kt:483](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt:483) 使用 `mapNotNull`，把读取不到或已不属于当前discovered范围的既有成员从复验快照直接删掉。P2G：旧成员移除标签后，新成员仍 ATTACHED 同一根。人物选址也未把当前映射与live/fingerprint一起验证；P2H直接传非live mapped profile仍 included。已修好的登记目标/维护范围谓词不覆盖这些路径。最小范围：整组快照构造与Recipient关系有效性检查。

**V-3：递增登记未核验既有根的全部当前成员身份。**

要求：04 I-2、master I-4、repair R-3。

[DiscoveryPersonService.kt:88](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt:88) 只验证请求快照；`:174` 全名集合来自该快照；[同文件:342](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt:342) 起解析现有根时合并SQL作者键，但未把该根全部当前成员姓名/证据纳入同等核验。

P2A两次实际register：先Jane Doe、再John Smith，同一合法ORCID；第二次 `ATTACHED`，memberCount=2。P2G证明正常registration入口在丢弃旧非live成员后同样可到达。最小范围：PersonService已有根解析、Registration整组快照。

**V-4：准入重验失败仍记完整APPLIED，CLI永久跳过后续恢复。**

要求：master I-3/I-8、02 I-3、05 I-2、repair R-4。

[DiscoveryIdentityRecoveryService.kt:146](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt:146) 捕获准入异常后返回 `APPLIED / ADMISSION_REVERIFY_FAILED`。P2K使用实际Recovery与Writer、HTTP fixture，准入服务抛错，得到该结果且 `admissionDecision=null`。

[replay.py:38](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/tools/discovery-identity-recovery/replay.py:38) 将APPLIED列为终态；`:299`读取该集合后跳过。因此仍未闭合准入链却被完成账本排除。已修复的保护读取fail-closed与跨层PARTIAL续跑不能解决此残留。最小范围：Recovery后续步骤状态与CLI续跑状态。

**V-5：发送前复验仍复用缓存profile，未重新验证当前标签/指纹。**

要求：08 I-1/I-3、09 I-3、master I-4/I-7、repair R-6。

[DiscoveryPersonRecipientService.kt:313](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt:313) 先把 `inBatch` 填入组，`:317`只对缺失成员加载ES；已在batch中的成员不重读。P2H在当前ES已移除标签时，缓存profile仍被included，ES read count=0。

[DiscoveryPersonOutreachGuardService.kt:264](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt:264) claim调用collectHistory，`:465`又把调用者profile交给同一selectAdmission；当前profile检查并非当前持久事实复验。最小范围：Recipient加载策略与reserve/claim当前事实边界。

**V-8：非空SMTP响应码被当作明确未接受，包括250成功接受码。**

要求：09 I-2、master I-8、repair R-8。

[InitialOutreachService.kt:179](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:179)、[ManualInitialOutreachService.kt:1290](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1290)、[ManualExpertMailService.kt:114](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt:114) 均对非SENT且 `smtpResponseCode != null` 标SAFE_RETRY。

P2J使用实际 [SmtpErrorClassifier.kt:33](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpErrorClassifier.kt:33) 处理 `MessagingException("250 2.0.0 queued, connection then lost")`，得到 `status=FAILED,errorCategory=SUCCESS,smtpResponseCode=250`；实际Initial入口随即调用markSafeRetry。其契约是“能证明未接受”，不是“存在任意响应码”。未模拟真实线上SMTP，不宣称发生了线上重复发送。最小范围：三个caller的SMTP结果分类与状态迁移。

**V-12：已失效成员仍显示“可发送”。**

要求：master I-5/I-7、06 I-2、07 I-1/S-2、repair R-12。

[DiscoveryPersonQueryService.kt:283](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt:283) 独立产生admissionStatus，`:285`另算stale；`:365`只看准入状态与抑制，未把关系失效纳入显示结论。[app.js:13510](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/resources/static/app.js:13510) 对ADMITTED直接显示可发送。

P2L实际Query＋实际BatchRecipientSelection读取移除标签的当前档案：同一成员返回 `stale=true, admissionStatus=ADMITTED`。旧filterResult替代真准入已修，但当前失效关系仍给出错误许可提示。最小范围：逐邮箱DTO状态与renderer许可标签。

**V-13：响应不完整仍可发布精确空结果或计数。**

要求：06 I-3、master I-8、repair R-10。

[ExpertSearchService.kt:1406](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:1406) 新完整性检查拒绝timeout/failed shard/null，但未要求正确hits结构；`:1284`、`:1324`把缺失hits读为空。P2I：缺hits的响应使search返回 `[]`，scroll正常完成0条。

[同文件:718](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:718) 共享 `_count` 未使用该完整性检查；P2N：`count=123,_shards.failed=1` 返回123，参与新人物查询精确总数。[DiscoveryPersonQueryService.kt:316](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt:316) 关系SQL读取失败返回空映射，也无法区分“无人映射”和“关系未知”。最小范围：人物查询依赖的完整响应/关系读取契约。

**V-14：新增续跑捷径把第三方proof或revision变化当成本次完成。**

要求：10 I-1、master I-4/I-8、repair R-13/R-15。

[DiscoveryPersonMaintenanceService.kt:262](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt:262) expected不一致时调用resumedTargetResult；`:275`关系摘要不一致同样调用。该函数[`:293`](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt:293)仅检查同一根、成员集合、非空sourceFingerprint及非CHANGED_OR_FAILED，未证明是原申请自己的目标写入；返回 `UNCHANGED, protectionImported=true`。

P2B：首次apply后第三方改变proof，再送旧expected仍 `UNCHANGED/RESUMED_TARGET_STATE`。P2C：SQL人物revision加42，旧请求同样成功。另`:528`各层版本仅取updatedAt，加RAW版本；`:607`人工状态取首个选择原因，未完整绑定持久准入revision；`:630`历史保护摘要只探guard状态。最小范围：维护快照及“本次已完成”识别。

**V-15：历史保护漏导入，rollback提案仍忽略人物revision变化。**

要求：10 I-2/I-3、09 I-3、master I-8、repair R-14。

[DiscoveryPersonOutreachGuardService.kt:369](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt:369) 仅把memberDocIds交给历史状态读取；`:395`只按这些键查SENT/attempt。当前系统允许业务orcidId与ES docId不同，同邮箱/alias关联亦不能被该键集合完整覆盖。

P2M真MySQL：已有同邮箱、不同业务键的SENT介绍邮件，import返回 `state="",created=false`，持久guard仍null，已有SENT数量1。该输入与原业务键≠ES docId契约相容。

[DiscoveryPersonMaintenanceService.kt:447](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt:447) 用 `memberRecords.any { it.personId != personId }` 代替版本变化检查；这些记录本就按该personId读取。P2D：revision加42后仍 `safeToRollback=true`。TransactionTemplate及SENDING/RESERVED阻断已修复，不能覆盖这两处残留。最小范围：历史关联键/持久导入与rollback版本证明。

**V-26：正常首次发送创建自身NEW联系人后，被新增claim历史复核永久阻断。**

要求：master“预估1人、选择1地址、合格无历史时正常首发”，09 I-1/I-2及repair保持项。

[InitialOutreachService.kt:109](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt:109) 在reserve后保存NEW联系人，再于`:139`调用claim。[ManualInitialOutreachService.kt:1041](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1041)也先建联系人，`:1135`保存PREPARED，再`:1166`claim。修复新增 [DiscoveryPersonOutreachGuardService.kt:264](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt:264) 的collectHistory，在`:511`以anyContactExists把当前尝试自己的新联系人当历史阻断。

P2E真MySQL：reserve Granted→加入本次NEW联系人→claim `OUTREACH_HISTORY_PROTECTED`；标SAFE_RETRY后再reserve仍被同一联系人拒绝。P2O进一步运行实际InitialOutreachService、实际ExpertContactRepository、真实MySQL guard：`requested=1,candidates=1,sent=0,failed=0,skipped=1`，持久contact=1、guard=SAFE_RETRY、假SMTP调用0。SMTP替身已配置成功，但未被调用。

该反例发生在健康、已映射、无旧历史的正常首发主链，不依赖异常注入。最小范围：当前尝试身份、联系人/attempt创建顺序与guard历史复核之间的契约。

### Findings — P2

- **V-21，PERSISTENT，nonmandatory**：[ExpertRevalidationServiceBehaviorTest.kt:422](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt:422) 等原名称仍描述旧研究资料门槛。A9限定两例断言，保持原名称不构成行为失败。
- **V-23，PERSISTENT，nonmandatory**：05仍缺专门驱动SCOPE_CONFLICT并断言`personScopeConflict`/原因传播的测试；[ExpertDiscoveryService.kt](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt)相关源码路径存在，未证明功能错误。
- **V-24，PERSISTENT，nonmandatory**：[DiscoveryPersonRecipientService.kt:53](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt:53) ALREADY_CONTACTED附近KDoc仍描述人工拒绝/身份冲突，属于说明不准确。
- **V-22已解决**：[DiscoveryPersonServiceTest.kt:305](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt:305)新增测试实际触发create冲突，再修改ES姓名，断言STALE/FINGERPRINT_MISMATCH；C08通过。旧测试未消费flag的备注保留为观察，不再把缺少该辨别性覆盖列为未解决。
- **V-25已解决**：精确噪声排除项更新，真实写入口仍仅两个；失效排除项检查未删除，C21/C22通过。

### Independent diagnostic evidence

全部诊断源码/class/log位于 `/tmp/discovery-aggregate-epoch2/`；使用本工作树当前编译产物。退出0表示成功运行并观察反例，不代表产品PASS。未修改仓库测试。

| Probe | 实际服务/边界与观察 | Finding |
|---|---|---|
| P2A | 实际PersonService，两次register不同全名同ORCID；第二次ATTACHED同根、2成员 | V-3 |
| P2B | 实际Maintenance，apply后proof第三方变化；旧expected仍UNCHANGED且protectionImported=true | V-14/V-15 |
| P2C | 实际Maintenance，人物revision+42；旧expected仍RESUMED_TARGET_STATE | V-14 |
| P2D | revision+42后rollback仍safeToRollback=true | V-15 |
| P2E | 真实Spring guard/真实MySQL；自身NEW联系人阻断claim与再次reserve | V-26 |
| P2F | 默认关闭、未映射discovered；真实guard返回PERSON_MISSING | V-1 |
| P2G | 实际Registration；已有成员移除live标签，被快照丢弃；新冲突姓名成员仍ATTACHED | V-2/V-3 |
| P2H | 实际Recipient及BatchSelector；当前ES已移标签，缓存档案included，ESread0；直接非live mapped档案也included | V-2/V-5 |
| P2I | 实际ExpertSearch；缺hits响应变search空列表、scroll完成0条 | V-13 |
| P2J | 实际SMTP分类器＋实际Initial入口；250异常结果触发markSafeRetry | V-8 |
| P2K | 实际Recovery/Writer与HTTP fixture；准入异常仍APPLIED/ADMISSION_REVERIFY_FAILED | V-4 |
| P2L | 实际Query/BatchSelector；移标当前档案返回stale=true且ADMITTED，UI映射可发送 | V-12 |
| P2M | 真实MySQL已有同邮箱不同业务键SENT；import未创建保护，state=null | V-15 |
| P2N | 实际_count读取；failed shard=1、count123仍返回123 | V-13 |
| P2O | 实际Initial＋实际contact repository＋真实guard/MySQL；正常一候选建contact后跳过，SMTP0，SAFE_RETRY | V-26 |

最终成功诊断组及原始记录：

| Harness | Compile/runtime | Final log |
|---|---|---|
| Epoch2Probe.java | javac0/java0 | [probe-unit.log](/tmp/discovery-aggregate-epoch2/probe-unit.log) |
| Epoch2DbProbe.java | javac0/java0 | [probe-db.log](/tmp/discovery-aggregate-epoch2/probe-db.log) |
| Epoch2ReadProbe.java | javac0/java0 | [probe-read-v3.log](/tmp/discovery-aggregate-epoch2/probe-read-v3.log) |
| Epoch2SendProbe.java | javac0/java0 | [probe-send.log](/tmp/discovery-aggregate-epoch2/probe-send.log) |
| Epoch2ImportProbe.java | JDK11 javac0/java0 | [probe-import-jdk11.log](/tmp/discovery-aggregate-epoch2/probe-import-jdk11.log) |
| Epoch2InitialDbProbe.java | JDK11 javac0/java0 | [probe-initial-db-v2.log](/tmp/discovery-aggregate-epoch2/probe-initial-db-v2.log) |

保留的首次harness失败与限制：

- Epoch2Probe最初引用错误的Kotlin常量访问形式，javac失败；修正为实际静态VERSION。属于诊断代码错误。
- SendProbe最初缺方法参数，compile失败；第一次runtime把Granted误作Claimed，出现harness异常。分别保留 [probe-send-compile.log](/tmp/discovery-aggregate-epoch2/probe-send-compile.log)、[probe-send-first-runtime-failure.log](/tmp/discovery-aggregate-epoch2/probe-send-first-runtime-failure.log)。中间一次准入mock配置了错误重载，未触发目标失败；[probe-send-pre-correction.log](/tmp/discovery-aggregate-epoch2/probe-send-pre-correction.log)不作为V-4证据，最终日志才是。
- ImportProbe首次由系统javac产生class65，JDK11只能读到55，runtime exit1；保留 [probe-import.log](/tmp/discovery-aggregate-epoch2/probe-import.log)。改用已安装JDK11编译器后compile/runtime均0，未改产品。
- InitialDbProbe首次成功编译，但DeliveredMail fixture传null errorCategory导致harness runtime1；保留 [probe-initial-db.log](/tmp/discovery-aggregate-epoch2/probe-initial-db.log)。修正fixture为SUCCESS后，最终v2执行成功并复现V-26。
- 首次scope审计脚本错误假定表内文件名带反引号，曾读得0；纠正解析明文文件表后得到47/40及累计69、范围外0。初次脚本结果不作为范围判断。
- P2O只补强一个真实Initial入口；不能替代B-2要求的三入口组合与并发。P2J验证实际分类器/caller契约，未模拟真实线上SMTP链路。
- 所有harness修正仅发生在`/tmp`，不是产品修复，也未把harness失败误列为产品缺陷。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Current independent evidence |
|---|---|---|---|
| admission O-1 | 准入语义、测试说明 | 非阻断P2，V-21 PERSISTENT | 断言符合A9；旧名称未同步 |
| admission O-2 | I-1计数真实性、失败语义 | Observation | taskFailure包含跨层冲突为明示设计；success+failure仍等于total，未违反规定终态口径 |
| 01 O-1 | 测试计数/顺序继承 | Observation | ReviewService35相对master-base27来自前置admission；C04新鲜35+13=48，无失败 |
| 02 RECORD_ONLY none | 02合同 | N/A | 无额外记录项；独立发现V-4不因此豁免 |
| 04 O-1 | I-4/04 I-2 | PASS（该规则） | 同类键distinct后仅多不同键冲突；共享同ORCID合法；C08 |
| 04 O-2 | 04竞争复验 | 局部PASS | deadlock/unique conflict返回重读，真实仓储C07/C11；完整已有根仍V-3 FAIL |
| 04 O-3 | A11迁移授权 | Observation / scope PASS | 原V155已增加列/FK，旧V154场景断言纠正对应既有事实；C09真实通过 |
| 04 O-4 | 冲突后重读测试深度 | V-22 RESOLVED | 新测试实际触发conflict并修改ES，断言STALE；旧未消费flag案例说明仍不精确 |
| 05 O-1 | 05计数传播覆盖 | 非阻断P2，V-23 PERSISTENT | 缺专门SCOPE_CONFLICT计数断言；未证明功能缺陷 |
| 06 Epoch1 O-1 | I-6完整分页 | V-10 RESOLVED | 前缀按≤1000分块，深分页回归C12 |
| 06 Epoch2 R-1 | 同上 | V-10 RESOLVED | 与O-1同根，未重复建finding |
| 06 Epoch1 O-2 | M-PERF/06 I-3 | BLOCKED B-1 | 缺真实51,829形状性能测量；诚实记录未测不能算通过 |
| 06 Epoch2 R-2 | 同上 | BLOCKED B-1 | 100000/10s失败语义测试不能替代p95/heap实测 |
| 07 RECORD_ONLY none | 07合同 | N/A | 无额外记录项；V-12由独立当前事实反例保留 |
| 08 O-1 | 说明可维护性 | 非阻断P2，V-24 PERSISTENT | ALREADY_CONTACTED KDoc复制错误 |
| 09 O-1 | I-7/I-8、09 I-2 | V-7 RESOLVED；B-2仍BLOCKED | REQUIRES_NEW真实guard外层回滚保留SENDING；不等同实际三入口组合 |
| 09 O-2 | 未发退出可恢复 | V-9 RESOLVED | 原account/contact异常清理已补；新增正常主链自阻断另V-26 |
| 09 O-3 | A14变更计数 | Observation | 实际32处旧版本pin变更，计划文字31；V157行为测试通过 |
| 10 O-1 | 历史保护导入 | V-15 PERSISTENT | 已新增持久import，但合法业务键差异仍漏历史，P2M |
| 10 O-2 | 冻结/当前事实绑定 | V-14 PERSISTENT | live与快照有所加强；P2B/P2C证明第三方proof/revision变化被resume接受 |
| 10 O-3 | 组事务边界 | 原自调用问题已修；不豁免V-15 | 显式TransactionTemplate；未独立执行完整维护组“关系＋import失败”真库原子回滚组合，不宣称该组合已证明 |

### Evidence Boundaries

**B-1，mandatory BLOCKED：真实规模性能门禁。**

[06计划:39](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/2026-10-13/discovery-person-06-query.md:39)要求51,829条真实形状脱敏数据，p95≤2秒、额外heap≤128MiB；`:63`要求有实测记录。本次没有取得该数据集或针对当前code head的测量报告。单元测试、100000条上限、10秒中断和scroll关闭均不能替代。未制造数据、未调用生产数据。

**B-2，mandatory BLOCKED：三个实际发送入口的组合运行证据。**

[09计划:43](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master/docs/plans/2026-10-13/discovery-person-09-send-guard.md:43)、`:65`要求真实数据库、不同实际入口并发、假SMTP调用数及成功后DB失败保护。C17真实guard16项证明guard局部锁/事务；caller测试mock了guard。P2O证明一个真实Initial入口的失败，不构成三入口并发/成功后回滚完整证据。该缺口保留，不转成猜测性P1，也未被executor自测结果豁免。

其他边界：

- 未做生产历史apply、真实发信、物理删除、部署或开关变更。
- 未执行人工验收、视觉签收或人为接受新代码边界。
- C21的20 skips及各诊断替身范围已明列。
- 缺executor canonical session ID不属于mandatory evidence缺口。
- 测试全部通过不推导完整功能合规；P2A—P2O保留实际反例。

### Manual acceptance

全部人为项目 **PENDING**，无人工结果、截图或签字被制造。原03对应master A-4按A1为N/A。

| Source | Items | Status |
|---|---|---|
| Master | A-1名单和证据；A-2增量与存量闭环；A-3保护和回滚；A-5同人多邮箱完整闭环；A-6历史保护与后续不再重复 | PENDING |
| Master | A-4来源回退是否值得实施 | N/A，03未纳入本次执行 |
| admission | A-1缺资料候选；A-2明确不合格/身份不可信；A-3去重/人工保护；A-4补全不再收紧；A-5发送条件/数量；A-6既有行为/显示 | PENDING |
| 01 | A-1重复补证；A-2冲突/人工状态；A-3中途失败/并发 | PENDING |
| 02 | A-1只读预览；A-2安全恢复/身份哈希；A-3来源/并发/范围；A-4断点/备份/预算 | PENDING |
| 04 | A-1登记/重跑；A-2并发/过期 | PENDING |
| 05 | A-1后续新增/重复补证；A-2中断恢复 | PENDING |
| 06 | A-1两列表/分页；A-2旧链接/只读/失败 | PENDING |
| 07 | A-1一人三邮箱/两入口；A-2窄屏/异步 | PENDING |
| 08 | A-1合格地址；A-2换邮箱不能绕保护 | PENDING |
| 09 | A-1三入口竞争；A-2未知结果/历史；A-3未发送恢复/正常回归 | PENDING |
| 10 | A-1完整名单/试点；A-2变化/中断/重复 | PENDING |

### Observations

- 完整 `git diff --check base current` 首次exit2，仅指出 fast child `02-replay/verify-log.md:24`新增EOF空白；产品/工具限定 `git diff --check ... -- src tools` exit0。未改只读证据文件。
- executor handoff将40文件分为21产品/19测试；独立按实际路径分类为20产品/工具＋20测试。授权集合与总数不受该记账偏差影响。
- A14的31/32处计数偏差、旧测试说明、既有编译warning未扩大为产品修复范围。
- 本epoch所有required命令首次通过；Epoch1的JS首次失败、旧JDK错误及V-25失败继续保留在先前报告，不冒充本epoch结果。

### Convergence

**DIVERGING。**

修复确实解决了深分页、关系分组、部分层续跑、来源编号、人物去重、原事务传播、原未发送退出清理、CLI认证、互斥/子集及构建问题；部分持续问题也已缩窄。

但新增 **V-26 是同级P1回归**：新历史复核从保护异常/重试分支扩展到健康首发主链，把本次自己的NEW联系人当旧历史，实际可达正常发送被阻断并持续不能重试。V-14新增续跑捷径同时仍接受第三方proof/revision变化。当前失败影响已涉及必须正常工作的主流程；不能仅凭已解决项数量较多判断PROGRESSING。该判断依据行为影响与新增回归，不采用finding计数阈值。

### Repair planning: N/A

未调用repair-p，未创建或更新repair.md。批准artifact保持原路径、原SHA256。未写aggregate报告、产品、测试或其他tracked证据；未stage、commit或变更branch/HEAD。

### Next Action

停止自动修复循环，提交人工裁定：确认新增V-26及残留保护/快照问题的后续修复策略，再决定是否授权修订同一路径repair计划。

此停点来自 [review-p/SKILL.md](/Users/lukai/.agents/skills/review-p/SKILL.md) 明文路由：`FAIL | STALLED or DIVERGING | Stop and request human adjudication.` 因此本轮不能自动生成下一轮repair或进入执行。

No product code was modified.


## Epoch 3 — 2026-10-15T13:43:21+08:00

## Verification Result: FAIL

- Requested phase: `aggregate/master`; skills: `review-p` → independent read-only `verify-p`, under `review-fast-p`.
- Reviewer: `/root/aggregate_review_epoch3`，修复提交后创建，未继承实施对话；未委派其他 reviewer。
- Plan: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`。
- Full implementation boundary: `9d353934b82ed3e36a193c70748e5efccb3d9e82..3b6f854e64eae0361b86cbb6af9a9b21b0385f13`。审查完整累计实现，不仅本轮修复。
- Convergence: **DIVERGING**。旧缺陷确有修复，但正常首发恢复引入当前准入失效后仍外发的 P1 回归；完整边界还发现唯一邮箱补证及跨层完成判定的 P1，所需核验/修复边界扩大。不得以通过测试数或 finding 数量相减判定收敛。
- Manual acceptance: **PENDING**；原03对应A-4为N/A，未启动旧名单或生产执行。
- Confirmed mandatory violations take precedence over B-1/B-2 evidence gaps；整体为 FAIL，不能把缺证变为 P1，也不能以缺证遮蔽已证实缺陷。

### Identity, authority, and post-repair boundary

所有仓库相对路径仅解析于 `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`，下文简称 selected worktree。分支 `fast/2026-10-13-discovery-identity-recovery-master`；evidence HEAD `038da49677c1893460d01f0d5b34cc7795c59b12`；final code HEAD `3b6f854e64eae0361b86cbb6af9a9b21b0385f13`。

Master SHA256 `f69d8135ce0019ad36cc8939cf8101a6f584aac9983dec3dfc78eb2ae37ce238`，记录提交 `36075ec06c5e4382f13606c995e24a13ec4470ce`。Invoked/Governing identity相同，`CONSISTENT`，governing diff为空。A1–A14是批准合同，不将A1追加的04–10误报未授权，也不将旧10,686名单或03执行混入当前范围。

Fast ledger：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/ledger.md`，SHA256 `2bbeec0ca9bbaf2008aed29b4265862a089a84cdbc25f20160a3e6ef5fe361e2`。Handoff同目录 `human-review-handoff.md`，SHA256 `ff9fa89083d24f9a3f6b37f798a8f201273df9c7b543d8a572316e7c4a390a64`。10个ordered children的brief/execution/verify-log/fix-log均存在。既有报告Epoch1/2及V-1–V-26、B-1/B-2作为lineage证据，未改写。

Worktree resolution `DISCOVERED_FROM_GIT_WORKTREES`。控制器提供并保存的精确发现命令：`python3 /Users/lukai/.agents/skills/review-fast-p/scripts/discover_fast_p.py --repo /Users/lukai/IdeaProjects/weibo-talent-introduction --master-plan docs/plans/2026-10-13/discovery-identity-recovery-master.md`，exit0、唯一SELECTED candidate即selected worktree、child_count=10、fast_final_code_head=`cd6a38d662da881ec453839921922a30602eacb3`。发现结果身份见selected review ledger §Exact selected discovery result；未读取其他worktree review ledger作审查输入。该fast head是发现身份，不替代本次final_code_head。

Post-repair evidence mode **DURABLE_HANDOFF**：`docs/plans/review/2026-10-13-discovery-identity-recovery-master/repair-execution.md` §Revised repair execution (round 2)。明确人源execute-p调用精确repair/hash的记录存在；canonical executor identity **UNAVAILABLE**，可见labels为execute-p main integrator、Round2SendPath、Round2IdentityScope、Round2QueryMaint、Round2Acceptance。身份不可用不构成阻塞；executor自测不是本次新鲜证据。其真实三入口/性能未补齐、旧JDK例外及Initial落库基线问题按原样保留。

Approved repair：`docs/plans/fix/discovery-identity-recovery-master/repair.md`，SHA256 `1c3736141987d41bdbc1737bcd0756cf35f4a8fca0e97bc8cd661586dbeb1f59`。修复边界 `b08b521c12b3d0fbbf219bbb7c8a9fc61a2ec2e9..3b6f854e64eae0361b86cbb6af9a9b21b0385f13`。独立Git核对：base及prior code均为final祖先；累计69个产品/测试文件均在approved child/amendment/repair并集中；本轮29个产品/测试文件均在repair的33-path Authorized Files内，越界0。原始diff与核对结果：`/tmp/discovery-aggregate-epoch3/{full-product.diff,repair-product.diff,scope-audit.json}`。功能FAIL不反向否定文件授权。

### Ordered child identities

| Order | Exact plan | Recorded commit | Current bytes |
|---|---|---|---|
| 1 / admission | `docs/plans/2026-10-13/discovery-admission-relaxation.md` | `f7c7604c268aad69df6afd15efe0eed91866a237` | IDENTICAL / sha256 `e2ebfb727c056b8de709150304a0cf9bc0ea4b76f7e41ebec27fa452e5e281b2` |
| 2 / 01 | `docs/plans/2026-10-13/discovery-identity-recovery-01-duplicate.md` | `b06a71898bc7850d2caeb244c9f8478f18a3b2ae` | IDENTICAL / sha256 `d78c2b0e05a203558820645cc440c82b4420a782768ab9b88be2d74deb937902` |
| 3 / 02 | `docs/plans/2026-10-13/discovery-identity-recovery-02-replay.md` | `58a9e511e327d61e8e3645c738921979ff26a518` | IDENTICAL / sha256 `4bee186d37236f0e000e07c39941915a8eb37283eda44e4804d1b1ee9950c407` |
| 4 / 04 | `docs/plans/2026-10-13/discovery-person-04-registry.md` | `9bf6b8ec1319863002f7d21f4320108a0a8ec2e3` | IDENTICAL / sha256 `cdd91cdef308f651733d579bd1c5ff0140dbe7b0e8e7e3a851115a31d8c42c1d` |
| 5 / 05 | `docs/plans/2026-10-13/discovery-person-05-ingestion.md` | `36075ec06c5e4382f13606c995e24a13ec4470ce` | IDENTICAL / sha256 `924e929a9228d4ba302a8ce0df6017337e430cf986c70716ef2d50fc667d92ff` |
| 6 / 06 | `docs/plans/2026-10-13/discovery-person-06-query.md` | `8b320426228ff9cf5e55447826bdb009bcdfa34a` | IDENTICAL / sha256 `404cd02b4dd50cd2aeef2d434f183591bc36ee7bc32e43137ca7fe990da71b31` |
| 7 / 07 | `docs/plans/2026-10-13/discovery-person-07-ui.md` | `a2d4166bf6ff7e61313d69a420cd5f98e28a8911` | IDENTICAL / sha256 `e0cad0a75d41f917d679a6eeadcf7010c8897bf27ea646e197af30c7f2a589d0` |
| 8 / 08 | `docs/plans/2026-10-13/discovery-person-08-selection.md` | `36075ec06c5e4382f13606c995e24a13ec4470ce` | IDENTICAL / sha256 `aed785ba70933fd10ef73ee9a3934c5714d195894e54f69e2b15386919869916` |
| 9 / 09 | `docs/plans/2026-10-13/discovery-person-09-send-guard.md` | `6a774804e918342bb65ae4549247e37fccd0d59b` | IDENTICAL / sha256 `1489119289122de3e89935744084c1fc30b302c3f2a54404d423d6f112aa59de` |
| 10 / 10 | `docs/plans/2026-10-13/discovery-person-10-history.md` | `36075ec06c5e4382f13606c995e24a13ec4470ce` | IDENTICAL / sha256 `122436d48050cd7c3811265136300754ef4ae739b34ab44b9cd3ba10dcda691b` |

### Approved amendments — exact fast ledger record

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-10-13/discovery-identity-recovery-master.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 需求描述（用户追加同人多邮箱合并）＋实现方案「当前追加：新发现人物合并（04—10）」 | 人工更新加入同人多邮箱合并范围：追加 04—10 子计划、10,686 名单降级为参考并停用原 03 存量执行，开发顺序改为先验收准入修正及 01/02 补证接口代码再 04—10 | HUMAN:用户消息「计划已经更新 你再看一下」（master 于 2026-10-13T22:40:55+0800 落盘，本 run 22:43+0800 快照提交） |
| A2 | docs/plans/2026-10-13/discovery-person-04-registry.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「04 人物主档」＋实现方案 1 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A3 | docs/plans/2026-10-13/discovery-person-05-ingestion.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「05 发现入口」＋实现方案 1—4 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A4 | docs/plans/2026-10-13/discovery-person-06-query.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「06 人物查询」＋实现方案（研究门禁：流式完整匹配集） | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A5 | docs/plans/2026-10-13/discovery-person-07-ui.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「07 多邮箱展示」＋样式契约 S-1/S-2 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A6 | docs/plans/2026-10-13/discovery-person-08-selection.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「08 发送目标」＋实现方案 1—3 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A7 | docs/plans/2026-10-13/discovery-person-09-send-guard.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「09 首发保护」＋实现方案 1—4 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A8 | docs/plans/2026-10-13/discovery-person-10-history.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | 变更文件清单「10 历史合并」＋实现方案 1—6 | 新增子计划纳入本次执行清单（A1 同一人工更新） | HUMAN:用户消息「计划已经更新 你再看一下」（2026-10-13T22:43+0800 快照提交） |
| A9 | docs/plans/2026-10-13/discovery-admission-relaxation.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:f7c7604c268aad69df6afd15efe0eed91866a237 | 实现方案步骤1（先完成既有准入修正：规则/CAS/重验）＋子计划变更文件清单第 11 文件条款 | epoch1 PLAN_CONFLICT：I-1 把六类软原因移入 hints 后，ExpertRevalidationServiceBehaviorTest 2 例仍断言旧阻断语义；按子计划「改第 11 个文件须先修订方案」，经人工批准增列该测试类并把 2 例改为新语义 | HUMAN:2026-10-13T23:06+0800 ask 回复「批准：更新 2 例为新语义」 |
| A10 | docs/plans/2026-10-13/discovery-identity-recovery-02-replay.md | commit:b06a71898bc7850d2caeb244c9f8478f18a3b2ae | commit:58a9e511e327d61e8e3645c738921979ff26a518 | 实现方案 2「02 定向回源预览」＋子计划 I-4（请求仍走额度控制、不改计价策略） | epoch1 PLAN_CONFLICT：计划字面要求声明 Operation.SINGLETON，但既有 policy 对 /works/* 一律派生 LIST；按人工批准选项 A 授权第 11 文件并为 /works/{id} 补 SINGLETON 派生（0 credit；LIST/SEARCH/CONTENT 不变） | HUMAN:2026-10-14T00:52+0800 ask 回复「A：授权 policy，/works/{id}→SINGLETON」 |
| A11 | docs/plans/2026-10-13/discovery-person-04-registry.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | commit:9bf6b8ec1319863002f7d21f4320108a0a8ec2e3 | 变更文件清单「04 人物主档」＋实现方案 4（V156 真实库迁移验证） | V156 加入后 FlywayMigrationIntegrationTest 31 处全量迁移最新版本断言（155）失效，该文件不在 6 文件授权内；经人工批准增列为第 7 文件，更新 31 处断言并加 V156 三新表存在性断言 | HUMAN:2026-10-14T07:44+0800 ask 回复「批准：改 31 处断言 + 加 V156 断言」 |
| A12 | docs/plans/2026-10-13/discovery-person-07-ui.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | commit:a2d4166bf6ff7e61313d69a420cd5f98e28a8911 | 变更文件清单「07 多邮箱展示」＋样式契约 S-1/S-2 | 07 改 app.js，按 K-frontend-cache-key-triad 需 index.html 16 个资源键同值 bump；计划原列 2 文件未含 index.html，经人工批准增列并 bump 为 20261014-person-emails（已核实无测试/计划字面量硬钉当前键） | HUMAN:2026-10-14T12:17+0800 ask 回复「批准：bump 到 20261014-person-emails」 |
| A13 | docs/plans/2026-10-13/discovery-person-06-query.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | commit:8b320426228ff9cf5e55447826bdb009bcdfa34a | 07 子计划 I-1/S-1/A-1（列表显示邮箱数量）＋06 实现方案 2（ES API 人物投影） | 07 epoch1 核实出 ES 列表分支无成员数来源（06 的 PersonSearchResult 仅 experts+totalHits），07 无法在自身文件内补出；按人工批准选项「补全」修订 06：ES 列表人物投影携带 memberCount（同源批量读取），随后 06/07 各以新 epoch 重跑重核 | HUMAN:2026-10-14T12:33+0800 ask 回复「补全：改 06 加成员数，重跑 06+07」 |
| A14 | docs/plans/2026-10-13/discovery-person-09-send-guard.md | commit:36075ec06c5e4382f13606c995e24a13ec4470ce | commit:6a774804e918342bb65ae4549247e37fccd0d59b | 变更文件清单「09 首发保护」＋实现方案 4（V157 真实库迁移验证） | V157 加入后 FlywayMigrationIntegrationTest 31 处全量迁移最新版本断言（156）失效，该文件不在 10 文件授权内；经人工批准增列为第 11 文件，更新 31 处断言并加 V157 表存在/唯一键断言（同 A11 口径） | HUMAN:2026-10-14T15:19+0800 ask 回复「批准：改断言 156→157 + V157 断言」 |


### Mandatory cumulative / amendment file matrix

A1建立04–10总范围；以下对每个累计产品/测试文件逐行核对书面授权。A2–A8的原始新增文件与A9–A14追加文件均显式列出。表中“授权PASS”仅表示该文件差异服务于列明规则；功能缺陷由Contract Matrix及V-ID判定，不以授权代替通过。R=本轮33表内；ΔR=本轮实际修复改动。

| Exact file | Governing child/amendment and master rule | Repair scope | Verdict |
|---|---|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | 06 A4：主文件表06/完整流式研究门禁/成员数投影 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt` | 08 A6：主文件表08/实现1–3，逐邮箱筛选再人物去重 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardService.kt` | 09 A7：主文件表09/实现1–4，三入口人物首发保护 | R / ΔR | 授权PASS；功能FAIL V-27 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientService.kt` | 08 A6：主文件表08/实现1–3，逐邮箱筛选再人物去重 | R / ΔR | 授权PASS；功能FAIL V-5/V-27 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 08 A6：主文件表08/实现1–3，逐邮箱筛选再人物去重；09 A7：主文件表09/实现1–4，三入口人物首发保护 | R / ΔR | 授权PASS；功能FAIL V-27运行链 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 08 A6：主文件表08/实现1–3，逐邮箱筛选再人物去重；09 A7：主文件表09/实现1–4，三入口人物首发保护 | R / ΔR | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt` | 09 A7：主文件表09/实现1–4，三入口人物首发保护 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt` | 08 A6：主文件表08/实现1–3，逐邮箱筛选再人物去重 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt` | 02 A10：定向来源回放与审计/预算 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryController.kt` | 02 original approved child：定向来源回放与审计/预算 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceController.kt` | 10 A8：主文件表10/实现1–6，冻结维护/审计/历史保护 | R / ΔR | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewController.kt` | admission original approved child：准入软硬原因/CAS/重验 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPerson.kt` | 04 A2：主文件表04/实现1，可靠人物关系与独立存储 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt` | 04 A2：主文件表04/实现1，可靠人物关系与独立存储 | R | 授权PASS；功能FAIL V-3 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt` | admission original approved child：准入软硬原因/CAS/重验 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryService.kt` | 02 original approved child：定向来源回放与审计/预算；05 A3：主文件表05/实现1–4，发现成功出口登记 | R / ΔR | 授权PASS；功能FAIL V-28/V-29调用链 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairService.kt` | 01 ：同身份三层局部补证；02 original approved child：定向来源回放与审计/预算；05 A3：主文件表05/实现1–4，发现成功出口登记 | R / ΔR | 授权PASS；功能FAIL V-28/V-29 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt` | 10 A8：主文件表10/实现1–6，冻结维护/审计/历史保护 | R / ΔR | 授权PASS；功能FAIL V-14 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt` | 06 A4/A13：主文件表06/完整流式研究门禁/成员数投影 | R / ΔR | 授权PASS；功能FAIL V-12/V-13调用链 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt` | 04 A2：主文件表04/实现1，可靠人物关系与独立存储 | R / ΔR | 授权PASS；功能FAIL V-3 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt` | 01 ：同身份三层局部补证；05 A3：主文件表05/实现1–4，发现成功出口登记 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt` | admission original approved child：准入软硬原因/CAS/重验 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` | admission original approved child：准入软硬原因/CAS/重验 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 01 ：同身份三层局部补证；05 A3：主文件表05/实现1–4，发现成功出口登记 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 02 original approved child：定向来源回放与审计/预算 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | 06 A4/A13：主文件表06/完整流式研究门禁/成员数投影 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | 01 ：同身份三层局部补证；02 original approved child：定向来源回放与审计/预算 | — | 授权PASS；功能FAIL V-28 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | admission original approved child：准入软硬原因/CAS/重验 | — | 授权PASS；对应Contract Matrix |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 06 A4：主文件表06/完整流式研究门禁/成员数投影 | R / ΔR | 授权PASS；功能FAIL V-13 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 09 A7：主文件表09/实现1–4，三入口人物首发保护 | R / ΔR | 授权PASS；对应Contract Matrix |
| `src/main/resources/db/migration/V156__create_discovery_person.sql` | 04 A2：主文件表04/实现1，可靠人物关系与独立存储 | — | 授权PASS；对应Contract Matrix |
| `src/main/resources/db/migration/V157__create_discovery_person_outreach.sql` | 09 A7：主文件表09/实现1–4，三入口人物首发保护 | — | 授权PASS；对应Contract Matrix |
| `src/main/resources/static/app.js` | 07 A5：主文件表07/S-1/S-2，原宿主邮箱展示与缓存同步 | — | 授权PASS；功能FAIL V-12消费后端ADMITTED |
| `src/main/resources/static/index.html` | 07 A12：主文件表07/S-1/S-2，原宿主邮箱展示与缓存同步 | — | 授权PASS；对应Contract Matrix |
| `src/test/js/discoveryPersonEmails.test.js` | 07 A5：主文件表07/S-1/S-2，原宿主邮箱展示与缓存同步 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 既有repair授权：精确写接缝守卫位置同步 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt` | 06 A4：主文件表06/完整流式研究门禁/成员数投影 | R | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 04 A11：主文件表04/实现1，可靠人物关系与独立存储；09 A14：主文件表09/实现1–4，三入口人物首发保护 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt` | 08 A6：主文件表08/实现1–3，逐邮箱筛选再人物去重 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonOutreachGuardServiceTest.kt` | 09 A7：主文件表09/实现1–4，三入口人物首发保护 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/DiscoveryPersonRecipientServiceTest.kt` | 08 A6：主文件表08/实现1–3，逐邮箱筛选再人物去重 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 08 A6：主文件表08/实现1–3，逐邮箱筛选再人物去重；09 A7：主文件表09/实现1–4，三入口人物首发保护 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 08 A6：主文件表08/实现1–3，逐邮箱筛选再人物去重；09 A7：主文件表09/实现1–4，三入口人物首发保护 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt` | 08 A6：主文件表08/实现1–3，逐邮箱筛选再人物去重 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryControllerTest.kt` | 02 original approved child：定向来源回放与审计/预算 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt` | 10 A8：主文件表10/实现1–6，冻结维护/审计/历史保护 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt` | admission original approved child：准入软硬原因/CAS/重验 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt` | 04 A2：主文件表04/实现1，可靠人物关系与独立存储 | R | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicyTest.kt` | admission original approved child：准入软硬原因/CAS/重验 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRecoveryServiceTest.kt` | 02 original approved child：定向来源回放与审计/预算；05 A3：主文件表05/实现1–4，发现成功出口登记 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryIdentityRepairServiceTest.kt` | 01 ：同身份三层局部补证；05 A3：主文件表05/实现1–4，发现成功出口登记 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt` | 10 A8：主文件表10/实现1–6，冻结维护/审计/历史保护 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt` | 06 A4/A13：主文件表06/完整流式研究门禁/成员数投影 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt` | 04 A2：主文件表04/实现1，可靠人物关系与独立存储 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt` | 01 ：同身份三层局部补证；05 A3：主文件表05/实现1–4，发现成功出口登记 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt` | admission original approved child：准入软硬原因/CAS/重验 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt` | admission original approved child：准入软硬原因/CAS/重验 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 01 ：同身份三层局部补证；05 A3：主文件表05/实现1–4，发现成功出口登记 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 02 original approved child：定向来源回放与审计/预算 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt` | 06 A4/A13：主文件表06/完整流式研究门禁/成员数投影 | R | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` | 01 ：同身份三层局部补证 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt` | admission A9：准入软硬原因/CAS/重验 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt` | admission original approved child：准入软硬原因/CAS/重验 | — | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 06 A4：主文件表06/完整流式研究门禁/成员数投影 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` | 09 A7：主文件表09/实现1–4，三入口人物首发保护 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `tools/discovery-identity-recovery/replay.py` | 02 original approved child：定向来源回放与审计/预算 | R / ΔR | 授权PASS；对应Contract Matrix |
| `tools/discovery-identity-recovery/test_replay.py` | 02 original approved child：定向来源回放与审计/预算 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |
| `tools/discovery-person-recovery/reconcile.py` | 10 A8：主文件表10/实现1–6，冻结维护/审计/历史保护 | R / ΔR | 授权PASS；对应Contract Matrix |
| `tools/discovery-person-recovery/test_reconcile.py` | 10 A8：主文件表10/实现1–6，冻结维护/审计/历史保护 | R / ΔR | 授权PASS；对应定向命令，测试不替代未覆盖运行证据 |

### Special amendment rule checks

| Amendment / exact file | Required written rule | Verdict / fresh evidence |
|---|---|---|
| A9 `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt` | 两例断言新软原因语义 | PASS C01；旧名仅V-21 |
| A10 `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt` | `/works/{id}` SINGLETON/0credit，其余计价不变 | PASS C05 |
| A11 `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | V156三独立表及最新版本 | PASS C09 |
| A12 `src/main/resources/static/index.html` | 16资源键同值20261014-person-emails，无额外UI扩张 | PASS；完整diff、资源键复核 |
| A13 `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt` | 同批人物映射memberCount，不新增ES字段 | PASS字段合同C12；其他功能FAIL不豁免 |
| A13 `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | ES列表透传memberCount | PASS C12 |
| A13 `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt` | 人数/成员数同源回归 | PASS C12 |
| A13 `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt` | 响应memberCount合同 | PASS C12 |
| A14 `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | V157最新版本、表/唯一键 | PASS C09；31/32文字偏差保留Observation |

### Commands — fresh exact execution

全部从selected worktree执行；所有Maven串行。完整stdout/stderr逐命令保存在`/tmp/discovery-aggregate-epoch3/Cxx.log`，退出码来自等待实际进程结束后的`results.jsonl`，不由test汇总推断。汇总解析读取完整日志，保留所有原日志及失败；没有产品修复或改变测试命令。

环境：Zulu11.0.15用于C01–C19内的Maven命令；C21/C22用Zulu11.0.32.1。Maven3.9.11、Node25.7.0、Python3.13.1；shell默认Maven环境的Java为25.0.1，但各必需Maven命令显式JAVA_HOME覆盖为指定JDK11。Docker29.4.0/OrbStack socket，必要执行经require_escalated批准，MySQL Testcontainers仅临时库。

| ID / exact command | Result | Exit / counts / full evidence |
|---|---|---|
| C01: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest test` | PASS | exit 0; 163.74s; JUnit 149 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C01.log` |
| C02: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test` | PASS | exit 0; 180.79s; JUnit 230 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C02.log` |
| C03: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryIdentityRepairServiceTest,ExpertIndexWriterServiceTest,DiscoveryPipelineServiceTest test` | PASS | exit 0; 251.79s; JUnit 308 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C03.log` |
| C04: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityTest,DiscoveryReviewServiceTest test` | PASS | exit 0; 172.11s; JUnit 48 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C04.log` |
| C05: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryIdentityRecoveryServiceTest,DiscoveryIdentityRecoveryControllerTest,OpenAlexDataSourceTest,OpenAlexRequestPolicyTest test` | PASS | exit 0; 159.75s; JUnit 164 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C05.log` |
| C06: `python3 -m unittest discover -s tools/discovery-identity-recovery -p 'test_*.py'` | PASS | exit 0; 0.12s; Python 16 tests / OK; `/tmp/discovery-aggregate-epoch3/C06.log` |
| C07: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test` | PASS | exit 0; 241.03s; JUnit 6 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C07.log` |
| C08: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonServiceTest test` | PASS | exit 0; 144.36s; JUnit 24 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C08.log` |
| C09: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | PASS | exit 0; 2060.79s; JUnit 46 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C09.log` |
| C10: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test` | PASS | exit 0; 220.04s; JUnit 283 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C10.log` |
| C11: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test` | PASS | exit 0; 195.29s; JUnit 30 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C11.log` |
| C12: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonQueryServiceTest,ExpertSearchServiceTest,ExpertIndexControllerTest,ExpertContactManagementControllerTest test` | PASS | exit 0; 145.06s; JUnit 150 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C12.log` |
| C13: `node --check src/main/resources/static/app.js` | PASS | exit 0; 0.09s; syntax check，无测试数; `/tmp/discovery-aggregate-epoch3/C13.log` |
| C14: `node --test src/test/js/discoveryPersonEmails.test.js` | PASS | exit 0; 0.24s; Node tests=23, suites=6, pass=23, fail=0, cancelled=0, skipped=0, todo=0; `/tmp/discovery-aggregate-epoch3/C14.log` |
| C15: `node --test src/test/js/*.test.js` | PASS | exit 0; 3.78s; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; `/tmp/discovery-aggregate-epoch3/C15.log` |
| C16: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonRecipientServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,OutreachTargetIteratorTest test` | PASS | exit 0; 158.65s; JUnit 259 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C16.log` |
| C17: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonOutreachGuardServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ManualExpertMailServiceTest test` | PASS | exit 0; 181.34s; JUnit 289 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C17.log` |
| C18: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test` | PASS | exit 0; 160.87s; JUnit 33 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C18.log` |
| C19: `python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'` | PASS | exit 0; 0.16s; Python 19 tests / OK; `/tmp/discovery-aggregate-epoch3/C19.log` |
| C20: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | N/A — approved exception | 未执行；repair明确旧Zulu11.0.15/tzdb例外，C21为同JDK11完整门禁；不是把本次失败删除 |
| C21: `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn clean package` | PASS | exit 0; 348.23s; JUnit 5085 tests / 0 failures / 0 errors / 20 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C21.log` |
| C22: `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -Dtest=OperatorStatusWriteSeamGuardTest test` | PASS | exit 0; 158.67s; JUnit 1 tests / 0 failures / 0 errors / 0 skipped; Node tests=1680, suites=322, pass=1680, fail=0, cancelled=0, skipped=0, todo=0; BUILD SUCCESS; `/tmp/discovery-aggregate-epoch3/C22.log` |

重复suite出现在多个必需命令中，计数不得简单相加当独立用例数。Maven阶段附带的Node测试计数另见相应完整日志；C15为全部JS独立命令。所有skip按实际记录，不把Docker条件跳过的C21替代C07/C09/C11/C17显式真库执行。

### Per-command JUnit suite counts

| Command | Suite result |
|---|---|
| C01 | Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.084 s - in com.weibo.talentintroduction.discovery.controller.DiscoveryReviewControllerTest |
| C01 | Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.108 s - in com.weibo.talentintroduction.discovery.service.DiscoveryReviewAllPagesTest |
| C01 | Tests run: 35, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.165 s - in com.weibo.talentintroduction.discovery.service.DiscoveryReviewServiceTest |
| C01 | Tests run: 22, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.179 s - in com.weibo.talentintroduction.discovery.service.DiscoveryAdmissionPolicyTest |
| C01 | Tests run: 22, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.152 s - in com.weibo.talentintroduction.expert.service.ExpertRevalidationServiceBehaviorTest |
| C01 | Tests run: 35, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.064 s - in com.weibo.talentintroduction.expert.service.ExpertRevalidationServiceTest |
| C02 | Tests run: 192, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 10.759 s - in com.weibo.talentintroduction.campaign.service.ManualInitialOutreachServiceTest |
| C02 | Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.001 s - in com.weibo.talentintroduction.campaign.service.BatchRecipientSelectionServiceTest |
| C02 | Tests run: 28, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.162 s - in com.weibo.talentintroduction.campaign.service.InitialOutreachServiceTest |
| C03 | Tests run: 190, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 84.657 s - in com.weibo.talentintroduction.discovery.service.ExpertDiscoveryServiceTest |
| C03 | Tests run: 49, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.791 s - in com.weibo.talentintroduction.discovery.service.DiscoveryPipelineServiceTest |
| C03 | Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.049 s - in com.weibo.talentintroduction.discovery.service.DiscoveryIdentityRepairServiceTest |
| C03 | Tests run: 49, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.064 s - in com.weibo.talentintroduction.expert.service.ExpertIndexWriterServiceTest |
| C04 | Tests run: 35, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.154 s - in com.weibo.talentintroduction.discovery.service.DiscoveryReviewServiceTest |
| C04 | Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.048 s - in com.weibo.talentintroduction.expert.domain.DiscoveryIdentityTest |
| C05 | Tests run: 44, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.304 s - in com.weibo.talentintroduction.config.OpenAlexRequestPolicyTest |
| C05 | Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.456 s - in com.weibo.talentintroduction.discovery.controller.DiscoveryIdentityRecoveryControllerTest |
| C05 | Tests run: 24, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.423 s - in com.weibo.talentintroduction.discovery.service.DiscoveryIdentityRecoveryServiceTest |
| C05 | Tests run: 89, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.361 s - in com.weibo.talentintroduction.discovery.service.OpenAlexDataSourceTest |
| C07 | Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 64.521 s - in com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepositoryTest |
| C08 | Tests run: 24, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.108 s - in com.weibo.talentintroduction.discovery.service.DiscoveryPersonServiceTest |
| C09 | Tests run: 46, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1,923.294 s - in com.weibo.talentintroduction.campaign.repository.FlywayMigrationIntegrationTest |
| C10 | Tests run: 190, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 62.792 s - in com.weibo.talentintroduction.discovery.service.ExpertDiscoveryServiceTest |
| C10 | Tests run: 24, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.133 s - in com.weibo.talentintroduction.discovery.service.DiscoveryIdentityRecoveryServiceTest |
| C10 | Tests run: 49, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.911 s - in com.weibo.talentintroduction.discovery.service.DiscoveryPipelineServiceTest |
| C10 | Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.041 s - in com.weibo.talentintroduction.discovery.service.DiscoveryIdentityRepairServiceTest |
| C11 | Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 25.8 s - in com.weibo.talentintroduction.discovery.repository.DiscoveryPersonRepositoryTest |
| C11 | Tests run: 24, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.461 s - in com.weibo.talentintroduction.discovery.service.DiscoveryPersonServiceTest |
| C12 | Tests run: 25, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.878 s - in com.weibo.talentintroduction.discovery.service.DiscoveryPersonQueryServiceTest |
| C12 | Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.534 s - in com.weibo.talentintroduction.campaign.controller.ExpertContactManagementControllerTest |
| C12 | Tests run: 20, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.11 s - in com.weibo.talentintroduction.expert.controller.ExpertIndexControllerTest |
| C12 | Tests run: 90, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.15 s - in com.weibo.talentintroduction.expert.service.ExpertSearchServiceTest |
| C16 | Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.048 s - in com.weibo.talentintroduction.campaign.service.OutreachTargetIteratorTest |
| C16 | Tests run: 192, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 10.352 s - in com.weibo.talentintroduction.campaign.service.ManualInitialOutreachServiceTest |
| C16 | Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s - in com.weibo.talentintroduction.campaign.service.BatchRecipientSelectionServiceTest |
| C16 | Tests run: 28, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.154 s - in com.weibo.talentintroduction.campaign.service.InitialOutreachServiceTest |
| C16 | Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.016 s - in com.weibo.talentintroduction.campaign.service.DiscoveryPersonRecipientServiceTest |
| C17 | Tests run: 41, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.887 s - in com.weibo.talentintroduction.mail.service.ManualExpertMailServiceTest |
| C17 | Tests run: 192, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.52 s - in com.weibo.talentintroduction.campaign.service.ManualInitialOutreachServiceTest |
| C17 | Tests run: 28, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.152 s - in com.weibo.talentintroduction.campaign.service.InitialOutreachServiceTest |
| C17 | Tests run: 28, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 23.819 s - in com.weibo.talentintroduction.campaign.service.DiscoveryPersonOutreachGuardServiceTest |
| C18 | Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.876 s - in com.weibo.talentintroduction.discovery.controller.DiscoveryPersonMaintenanceControllerTest |
| C18 | Tests run: 25, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.373 s - in com.weibo.talentintroduction.discovery.service.DiscoveryPersonMaintenanceServiceTest |
| C22 | Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.274 s - in com.weibo.talentintroduction.campaign.OperatorStatusWriteSeamGuardTest |

### Retained failures / diagnostics

必需命令首次执行全部exit0，无产品测试失败或重跑。

补充harness首次QueryStatusProbe编译exit1，原因是临时Java探针调用了不存在的方法名detail；日志`query-probe-compile.log`保留。仅修正/tmp探针为现有personDetail签名，第二次编译/运行exit0，日志`query-probe-v2-compile.log`、`query-probe-run.log`。这是探针错误，不计产品测试失败，也未删旧日志。

### Independent diagnostic evidence

所有探针读取当前final_code_head经C01后新鲜编译的target classes；没有借用旧epoch结果。classpath路径文件复用为依赖清单，指向当前selected target/classes及target/test-classes；其内容不是旧执行证据。HTTP/SMTP为边界替身，MySQL证据使用真实临时库；运行源码和完整日志均保留。

| Probe | Actual implementation and setup | Observed result / verdict | Source and logs / exits |
|---|---|---|
| P3A | actual Recipient，RAW读取失败，其他层有效 | 仍included P1/d1；V-5 FAIL | Epoch3Probe.java；probe-unit-v2-compile.log exit0；probe-unit-v2-run.log exit0 |
| P3B | actual Search，响应缺aggregation结构 | []伪合法空桶；V-13 FAIL | 同上 |
| P3C | actual Maintenance/Person/Writer，预览后独立登记 | UNCHANGED/protectionImported=true/importCalls0；V-14 FAIL | 同上 |
| P3D | actual Maintenance/Writer，candidate seqNo1→42 | 旧expected APPLIED；V-14 FAIL | 同上 |
| P3E | actual Recipient/Selector/guard/SQL，reserve后准入失效 | Claim成功、SENDING；V-27 FAIL | Epoch3DbProbe.java；probe-db-v2-compile.log exit0；probe-db-v2-run.log exit0 |
| P3F | actual Initial/guard/SQL，compose时准入改NEEDS_REVIEW | fake SMTP=1、admitted=false、SENDING；V-27 FAIL | 同上；业务落库异常原样保留 |
| P3G | actual Writer/Repair，timeout=true failedShard1 | proofWrites1、REPAIRED；V-28 FAIL | Epoch3Probe.java及probe-unit-v2-run.log exit0 |
| P3H | actual Writer/Repair，RAW空名、candidate全名无proof | 整体REPAIRED，candidate proof=null；V-29 FAIL | 同上 |
| P3I | actual Query+BatchSelector，contact EMAIL_INVALID | stale=false且ADMITTED；V-12 FAIL | QueryStatusProbe.java；query-probe-v2-compile.log/query-probe-run.log均exit0；首个编译错误日志保留 |
| P3J | actual Person/Writer、真实SQL/事务，A核验后插入B提交 | 同根两个不同OpenAlex键、两次ATTACHED；V-3 FAIL | RegistrationRaceProbe.java；race-probe-compile.log/race-probe-run.log均exit0 |

以上文件均在`/tmp/discovery-aggregate-epoch3/`。诊断进程exit0表示成功执行并捕获反例，不表示产品合规PASS。P3J只在仓储入口安排正常可发生的交错，读写全委派真实事务repository；P3F没有mock被审guard或事务。

复核P3I执行命令：`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp "$(cat /tmp/discovery-aggregate-epoch2/classpath.txt):/tmp/discovery-aggregate-epoch3" QueryStatusProbe`，stdout/stderr定向query-probe-run.log；exit0。P3J执行命令：`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dapi.version=1.41 -cp "$(cat /tmp/discovery-aggregate-epoch2/classpath.txt):/tmp/discovery-aggregate-epoch3" RegistrationRaceProbe`，stdout/stderr定向race-probe-run.log；exit0。

### Preserved counterexample outputs

以下为本epoch新鲜stdout原文；随机UUID/owner token仅为临时测试库值，没有生产资料。

```text
P3A-partial-layer-failure=PersonRecipientSelection(included=[PersonRecipientTarget(personId=P1, selectedDocId=d1, selectedEmail=d1@example.org, memberDocIds=[d1], exclusionReasons=[])], excluded=[], reasonHits={})
P3B-missing-aggregate-buckets=[]
P3C-independent-registration=DiscoveryPersonRegistrationResult(outcome=CREATED, personId=0de6a40e-ca77-440b-bdda-174cf32337ac, memberCount=2, reason=null)
P3C-old-request=Ok(results=[MaintenanceGroupApplyResult(groupKey=59b271ae1bbcb1d31d41929817f4b16fb439eb4f31520b5ad1d5ce98920a7138, status=UNCHANGED, personId=0de6a40e-ca77-440b-bdda-174cf32337ac, memberCount=2, protectionImported=true, reason=RESUMED_TARGET_STATE, personRevision=0)])
P3C-import-calls=0
P3D-candidate-seq-changed-1-to-42=Ok(results=[MaintenanceGroupApplyResult(groupKey=59b271ae1bbcb1d31d41929817f4b16fb439eb4f31520b5ad1d5ce98920a7138, status=APPLIED, personId=75c9015a-e74e-44af-a83a-e300fefd2e06, memberCount=2, protectionImported=false, reason=null, personRevision=0)])
P3G-incomplete-uniqueness=IdentityRepairResult(outcome=REPAIRED, proof=IdentityVerification(status=VERIFIED, version=20260925, email=jane@uni.edu, givenNames=Jane, familyNames=Doe, source=JATS_SHA256, evidenceHash=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, orcid=null, openAlexAuthorId=null), layers=[IdentityRepairLayer(level=RAW, outcome=REPAIRED, previousProof=null, currentProof=IdentityVerification(status=VERIFIED, version=20260925, email=jane@uni.edu, givenNames=Jane, familyNames=Doe, source=JATS_SHA256, evidenceHash=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, orcid=null, openAlexAuthorId=null)), IdentityRepairLayer(level=CANDIDATE, outcome=MISSING, previousProof=null, currentProof=null), IdentityRepairLayer(level=APPLICATION, outcome=MISSING, previousProof=null, currentProof=null)], personRegistration=null)
P3G-proof-writes=1
P3H-mixed-layer-repair=IdentityRepairResult(outcome=REPAIRED, proof=IdentityVerification(status=VERIFIED, version=20260925, email=jane@uni.edu, givenNames=Jane, familyNames=Doe, source=JATS_SHA256, evidenceHash=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, orcid=null, openAlexAuthorId=null), layers=[IdentityRepairLayer(level=RAW, outcome=REPAIRED, previousProof=null, currentProof=IdentityVerification(status=VERIFIED, version=20260925, email=jane@uni.edu, givenNames=Jane, familyNames=Doe, source=JATS_SHA256, evidenceHash=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, orcid=null, openAlexAuthorId=null)), IdentityRepairLayer(level=CANDIDATE, outcome=ALREADY_VERIFIED, previousProof=null, currentProof=null), IdentityRepairLayer(level=APPLICATION, outcome=MISSING, previousProof=null, currentProof=null)], personRegistration=null)
P3H-candidate-proof=null
P3E-initial-reserve=Granted(ticket=OutreachGuardTicket(personId=p-current, mailType=INTRODUCTION, revision=0, ownerToken=a3ef0e90-6a1f-449a-a1ec-f5b7b4350e6a, selectedDocId=doc-current, recipient=doc-current@example.test))
P3E-current-selection=PersonRecipientSelection(included=[], excluded=[PersonRecipientTarget(personId=p-current, selectedDocId=doc-current, selectedEmail=null, memberDocIds=[doc-current], exclusionReasons=[ALREADY_CONTACTED])], reasonHits={ALREADY_CONTACTED=1})
P3E-claim-after-admission-invalid=com.weibo.talentintroduction.campaign.service.OutreachClaimOutcome$Claimed@569026df
P3E-persisted-state=SENDING
P3E-no-real-SMTP=true
P3F-after-SMTP-business-error=DbActionExecutionException
P3F-actual-initial-SMTP-with-current-NEEDS_REVIEW=[D1@example.test]
P3F-current-admitted=false,state=SENDING
P3I-actual-selector-EMAIL_INVALID-detail=DiscoveryPersonDetail(personId=P1, revision=1, representativeDocId=d1, requestedDocId=d1, members=[DiscoveryPersonMember(docId=d1, levels=[RAW, CANDIDATE, APPLICATION], email=ada@example.com, contactId=1, contactStatus=WAITING_REPLY, operatorStatus=EMAIL_INVALID, admissionStatus=ADMITTED, representative=true, stale=false, evidenceSource=PAPER_FULLTEXT)], conflicts=[])
P3J-root=DiscoveryPersonRegistrationResult(outcome=CREATED, personId=b3e523dc-b0d2-4a1d-91ea-0272a4075b1b, memberCount=1, reason=null)
P3J-interleaved-B=DiscoveryPersonRegistrationResult(outcome=ATTACHED, personId=b3e523dc-b0d2-4a1d-91ea-0272a4075b1b, memberCount=2, reason=null)
P3J-delayed-A=DiscoveryPersonRegistrationResult(outcome=ATTACHED, personId=b3e523dc-b0d2-4a1d-91ea-0272a4075b1b, memberCount=3, reason=null)
P3J-final-keys=[DiscoveryPersonAuthorKeyRecord(authorKey=OPENALEX:A100, personId=b3e523dc-b0d2-4a1d-91ea-0272a4075b1b), DiscoveryPersonAuthorKeyRecord(authorKey=OPENALEX:A200, personId=b3e523dc-b0d2-4a1d-91ea-0272a4075b1b), DiscoveryPersonAuthorKeyRecord(authorKey=ORCID:0000-0002-1825-0097, personId=b3e523dc-b0d2-4a1d-91ea-0272a4075b1b)]
P3J-final-member-count=3
```

### Contract Matrix

机器项均mandatory，来源为精确master及相应当前批准child/repair；局部PASS只证明该行。C命令的精确字符串、真实exit、计数在后文Commands；P3为本reviewer新鲜独立诊断。Manual项单列，不代人验收。

| ID | Requirement / source | Required evidence | Verdict / evidence |
|---|---|---|---|
| M-I1 | 五方向不夸大；恢复/准入/发送分母区分 | 互斥处置与报告 | PASS；Maintenance preview互斥分桶，C18/C19；无生产恢复人数承诺 |
| M-I1-scope | A1：旧10,686为背景、03不执行、5547非承诺 | 操作/代码范围 | PASS；本次未启动旧名单或03 |
| M-I2-policy | proof不能直接置AUTO_PASSED；软硬原因分开 | Policy/Review/Revalidation | PASS；C01/C04/C05，Recovery经recordAutomaticConclusion |
| M-I2-permission | 身份、准入、具体邮箱许可分离 | 逐邮箱/claim/展示 | FAIL；V-5/V-12/V-27，P3A/P3I/P3F |
| M-I2-mail | 审查及历史流程实际邮件0 | 操作边界 | PASS；仅假SMTP，未生产发送 |
| M-I3 | 原ID、三层CAS、人工保护、部分失败不完整成功 | Writer/Repair/Recovery | FAIL；V-28/V-29，P3G/P3H；V-4原准入失败根因已修复 |
| M-I4 | frozen+live、当前姓名邮箱proof、作者键可靠 | Person/maintenance/发送 | FAIL；V-3/V-5/V-14，P3J/P3A/P3C/P3D |
| M-I5-storage | SQL稳定人物，原doc/email/contact/alias保留 | V156、repository | PASS；独立三表及原键保留，C07/C09/C11 |
| M-I5-facts | 每邮箱独立资格，不借他人或旧状态放行 | Query/selector/guard | FAIL；V-12/V-27 |
| M-I6 | 两列表先有效分组再分页/排序/统计 | Query/Search/controllers | FAIL；V-13聚合结构缺失仍伪精确；深页与DB原单位原根因已解决 |
| M-I6-layer | 同一成员完整匹配，RAW不带入候选 | 查询/两宿主 | PASS（该子项）；C12/C14，过滤后按层聚合 |
| M-I7 | 完整人物保护、逐邮箱审核、所有INTRODUCTION入口共享占位 | selector/三入口真实运行 | FAIL；V-5/V-27；三实际入口完整组合证据另B-2 BLOCKED |
| M-I8 | 失败可恢复、查询不全不造数、未知不重发 | 生命周期/读取/开启 | FAIL；V-13/V-14/V-27/V-29；V-1/V-4/V-8原根因解决 |
| M-SCOPE | 授权并集、共享文件顺序继承 | 全diff/33授权表 | PASS；累计69、本修复29、越界0 |
| M-STORAGE | 不改既有共享schema/ES mapping、不删原档案 | diff及迁移 | PASS；仅批准V156/V157新增独立存储，C09 |
| M-NONGOALS | 不猜名、不伪人工认可、不重写解析器、不新审核UI | 全diff/操作 | PASS（行为范围）；具体补证校验缺陷另列 |
| M-RELEASE | 默认关闭、关闭后已建首发保护不撤销 | Gate/三caller | PASS（代码与局部测试）；V-1原根因解决，B-2运行证据边界仍在 |
| M-PERF | 51,829真实形状，p95≤2s、额外heap≤128MiB | 当前代码实测报告 | BLOCKED B-1；不能用内存夹具或预算常量替代 |
| M-COMMANDS | 子计划定向、JDK11完整构建、守卫 | C01–C19/C21/C22 | PASS；21条必需命令全部实际exit0；C20明确批准例外 |
| M-DATA | 互斥名单、doc→person→email映射及审计 | Maintenance/CLI | FAIL V-14/V-3；工具局部统计PASS，未生产历史交付 |
| admission-I1 | 六类缺资料软化，明确负面保留 | policy测试 | PASS C01 |
| admission-I2 | 可信身份前提不松 | policy/恢复 | PASS（policy局部）C01/C05；上游唯一性FAIL另V-28 |
| admission-I3 | 准入SSOT、补全不重新收紧 | Revalidation | PASS C01 |
| admission-I4 | 完整按doc去重，人工/CAS保留 | Review/all pages | PASS C01；并发结论复读、dryRun零业务写 |
| admission-I5 | 投影与真实发信区分 | Review/发送链 | PASS（链条区分）C01/C02；具体首发门禁V-27 |
| admission-I6 | 显式scope继续生效 | 既有selector | PASS（局部）C02；新own-contact旁路V-27 |
| admission-I7 | 新旧事实同口径 | Policy/Revalidation | PASS C01 |
| 01-I1 | 同身份缺证才补、不推测覆盖 | Repair/Writer | FAIL V-28；唯一性查询不完整仍签proof |
| 01-I2 | 同邮箱真实ID、三层版本局部写 | Writer/CAS | CAS子项PASS C03/C04；唯一性前置FAIL V-28 |
| 01-I3 | partial/conflict明确，不伪完整成功 | Repair各层结果 | FAIL V-29；缺proof层误报ALREADY_VERIFIED |
| 02-I1-fresh | 精确新鲜原文，不用缓存摘要签证 | 原字节/解析器 | PASS C05；PMCID对应检查V-20保持解决 |
| 02-I1-source | 原文来源编号绑定存量locator | JATS article-id | PASS C05 |
| 02-I2 | 有限空名，学术/联系人/人工保护 | Recovery.inspect | PASS（排除项）；读取异常停止，C05；邮箱唯一性V-28 |
| 02-I3-readonly | preview零业务写，apply哈希+版本复验 | Recovery/Writer | PASS（该子项）C05 |
| 02-I3-resume | 三层/登记/准入完整、失败可续跑 | Recovery/CLI | FAIL V-29；V-4准入异常PARTIAL及正常目标proof续跑已解决，C05/C06 |
| 02-I4 | 预算SINGLETON、来源/规则审计、认证CLI | Policy/CLI | PASS C05/C06；A10、V-18保留 |
| 04-I1 | 稳定UUID、doc唯一、不改原事实 | repository真库 | PASS C07/C09/C11 |
| 04-I2 | 全名、每类键、锁后整组与revision核验 | Person/Repo竞争 | FAIL V-3 P3J；顺序已有根全名/成员核验改善但锁内仍无原版本保护 |
| 04-I3 | 当前live/来源/指纹，范围外只读冲突 | 登记/读发 | 登记顺序原根因PASS C08/C11；发送读取FAIL V-5 |
| 04-gate | 默认关闭无新人物强制门禁 | Gate/三caller | PASS（原V-1）C02/C16/C17；真实三入口全矩阵B-2 |
| 05-I1 | 新建/重复/补证/回源出口统一登记 | 入口/结果传播 | PASS（登记接线）C03/C10；上游缺proof完成判定V-29 |
| 05-I2 | 登记或准入失败不报完整成功 | Recovery/Pipeline | V-4原异常处理PASS C05/C06；完整身份层FAIL V-29 |
| 05-I3 | 计数/单位/预算及当前范围 | 入口统计/Policy | PASS（所审行为）C03/C10；V-23非mandatory覆盖观察 |
| 06-I1 | 匹配后分页，范围外按doc/contact原单位 | Query/controllers | PASS（分页/原单位）C12，V-10/V-11保持解决 |
| 06-I2 | 原链接+每邮箱真实状态 | DTO/renderer | 原链接PASS；资格标签FAIL V-12 P3I |
| 06-I3-complete | 必要响应结构完整才发布精确结果 | Search/SQL | FAIL V-13 P3B；count/hits/SQL原分支已修 |
| 06-I3-readonly | GET不登记、不补证、不建contact | 调用图/C12 | PASS |
| 06-I3-perf | 全真实形状预算研究门禁 | 实测 | BLOCKED B-1 |
| 07-I1 | 后端人数/原ID/独立状态真实 | DTO+两个宿主 | memberCount/原ID PASS；可发送状态FAIL V-12 |
| 07-I2 | 共用renderer、异步隔离、转义 | C14/C15 | PASS（机器） |
| 07-S1 | 原列表/头部布局与邮箱数 | app.js/index/A12/A13 | PASS（机器）；视觉人工PENDING |
| 07-S2 | 独立邮箱状态、旧业务入口 | renderer/Query | 入口PASS；邮箱异常可发送标签FAIL V-12 |
| 08-I1 | 当前全组硬保护+具体邮箱资格 | Recipient/Guard | FAIL V-5/V-27 P3A/P3F |
| 08-I2 | primary仅合格优先，retry/分页共享人物集合 | Recipient/Iterator/MI | PASS C02/C16，V-6保持解决 |
| 08-I3 | 预估只读，同源资格与数量 | estimate/selector | 零写PASS；依赖失败完整性FAIL V-5 |
| 09-I1-storage | INTRODUCTION/person唯一原子门禁 | V157/guard真库 | PASS（局部）C09/C17 |
| 09-I1-callers | 三实际入口各健康1次，共库竞争恰1次 | 真guard/真事务/假SMTP | BLOCKED B-2；已证当前Initial资格旁路FAIL V-27 |
| 09-I2 | SENDING独立持久、未知不重发、未发清理 | guard/三caller | 原V-7/V-8/V-9 PASS局部；B-2跨实际入口完整证据欠缺 |
| 09-I3 | 历史全组及当前邮箱资格不旁路 | Guard/Recipient/import | FAIL V-5/V-27；原历史业务键导入V-15已解决 |
| 10-I1 | frozen/live、完整expected、互斥分母 | Maintenance/CLI | FAIL V-14；子集及互斥V-16/V-17保持解决 |
| 10-I2 | 候选不等核准，完整复验、关系保护同事务 | Person/Maintenance | FAIL V-3/V-14；显式TransactionTemplate与导入回滚局部PASS |
| 10-I3 | 历史保持、保护导入、原revision回滚提案 | Guard/Maintenance/CLI | 原V-15历史key/rollback版本PASS；虚报导入FAIL V-14 |
| 10-CLI | 50组试点、续跑、cookie、私有审计 | C18/C19 | 子集/认证/权限PASS；服务端伪本次续跑FAIL V-14 |

### Approved repair completion matrix

| Repair task | Verdict | Fresh evidence |
|---|---|---|
| R-1 正常首发及本次尝试归因 | FAIL；运行证据另BLOCKED | V-26原自阻断已解除；V-27旁路当前准入；B-2未提供三个实际入口健康/竞争组合 |
| R-2 关闭边界 | PASS（原行为修复）；完整运行证据BLOCKED | Gate条件已进入三caller；已有人物保护不因关闭跳过；B-2 |
| R-3 完整当前组与锁后复验 | FAIL | V-3真库交错提交同根双OpenAlex、V-5分层读异常放行、V-27具体邮箱旁路 |
| R-4 SMTP保守分类 | PASS（原V-8）；完整组合证据BLOCKED | 三caller排除SUCCESS/250释放，真实guard保持SENDING/UNKNOWN；B-2 |
| R-5 准入后续可恢复 | PASS（原V-4） | Recovery:149返回PARTIAL；原申请目标proof可重预览后闭合；C05/C06；非同根新V-29另列 |
| R-6 完整expected/续跑归属/rollback | FAIL | P3C零import而protectionImported=true；P3D非RAW seqNo变化仍APPLIED；原revision回滚子项已改善 |
| R-7 业务关联历史及原子导入 | PASS（原V-15） | 实际MySQL businessKey/邮箱保护导入及维护整组导入异常回滚C17；伪resume导入另V-14 |
| R-8 查询完整性 | FAIL | 缺aggregation结构仍合法空桶P3B；count/hits/SQL原洞已补 |
| R-9 展示不得冒充许可 | FAIL | stale/读取失败已UNKNOWN；明确EMAIL_INVALID仍ADMITTED P3I |

### Finding Lineage

| Finding | State | Current evidence |
|---|---|---|
| V-1 P1 | RESOLVED | 三caller加入featureGate/knownPerson判断，默认关闭无mapping不再强行PERSON_MISSING；C02/C16/C17 |
| V-2 P1 | RESOLVED（原根因） | 登记不再mapNotNull丢弃非live既有成员，selector当前事实重新读取并校验live/指纹；C03/C08/C16；读取异常残洞归V-5 |
| V-3 P1 | PERSISTENT | 已有根顺序全名/成员/作者键核验补齐；锁前核验到锁后提交之间无revision或整组类别键约束，P3J真库同根A100/A200 |
| V-4 P1 | RESOLVED（原根因） | 准入异常从APPLIED改PARTIAL，CLI非终态续跑，完整目标proof可继续准入；C05/C06 |
| V-5 P1 | PERSISTENT | 缓存档案已改为当前读取；某层异常被空表吞掉，其他层存在即放行，P3A |
| V-6 P1 | RESOLVED | retry/ES/iterator共人物seenKeys，合格primary优先；C02/C16 |
| V-7 P1 | RESOLVED | reserve/claim REQUIRES_NEW，外层业务回滚保留SENDING；C17、P3F真实持久状态 |
| V-8 P1 | RESOLVED（原根因） | FAILED/SUCCESS/250不再SAFE_RETRY；三caller代码、C02/C17；无响应码继续保守 |
| V-9 P1 | RESOLVED | 未SMTP异常/continue/break finally清理为原址SAFE_RETRY；C02/C16/C17 |
| V-10 P1 | RESOLVED | 非discovered前缀按≤1000分块，深页回归C12 |
| V-11 P1 | RESOLVED | DB有效关系复验，范围外保留contactId单位，C12 |
| V-12 P1 | PERSISTENT | stale已UNKNOWN；EMAIL_INVALID仍ADMITTED→当前UI可发送，P3I |
| V-13 P1 | PERSISTENT | count/hits/SQL异常已拒绝；aggregateTerms缺aggregation必要结构仍[]，P3B |
| V-14 P1 | PERSISTENT | 原proof/revision变化部分收紧；独立登记同形态仍冒充本次续跑/保护已导入，且CANDIDATE版本不进expected，P3C/P3D |
| V-15 P1 | RESOLVED（原根因） | 不同businessKey/邮箱/alias保护读取、同事务回滚、expectedRevision缺失/变化拒rollback；C17/C18/C19；错误resume归V-14 |
| V-16 P1 | RESOLVED | 全冻结digest与子集expected分开；51→50+1仍通过C18/C19 |
| V-17 P1 | RESOLVED | 重叠候选组拒绝，主类分母互斥，C18/C19 |
| V-18 P1 | RESOLVED | 两CLI Cookie/session认证、401停止、私有输出不记凭证，C06/C19 |
| V-19 P1 | RESOLVED（原根因） | 原层间PARTIAL目标proof续跑/第三值拒绝保留，C05/C06；新混合全名缺proof分支归V-29 |
| V-20 P1 | RESOLVED | PMCID locator与JATS article-id一致才签证，原归档正负样本C05 |
| V-21 P2 nonmandatory | PERSISTENT | 两旧revalidation测试名称仍表述旧语义；断言经A9批准、无行为影响 |
| V-22 P2 nonmandatory | RESOLVED | 实际触发create conflict后改变ES事实的辨别性测试仍存在并新鲜执行C08 |
| V-23 P2 nonmandatory | PERSISTENT | 05缺专门SCOPE_CONFLICT计数传播断言，未因此认定产品缺陷 |
| V-24 P2 nonmandatory | PERSISTENT | ALREADY_CONTACTED KDoc复制说明仍在，无行为影响 |
| V-25 P2 mandatory | RESOLVED | 精确噪声位置同步，真实写入口白名单/失效排除检查保留；C21/C22 |
| V-26 P1 | RESOLVED（原自阻断根因） | 自身NEW/PREPARED可取得claim，真实Initial现调用SMTP；修复引入的具体准入旁路单列V-27，不能当完整发送PASS |
| V-27 P1 | NEW / REGRESSION | ownContactOnly忽略ALREADY_CONTACTED时同时跳过未执行的具体邮箱资格，真实Initial在当前NEEDS_REVIEW后SMTP=1，P3E/P3F |
| V-28 P1 | NEW（累计实现中新发现） | 邮箱唯一性_search timeout/failed shard仍接受单一hit并写proof，P3G |
| V-29 P1 | NEW（累计实现中新发现） | 空名修复的已有全名层缺proof却ALREADY_VERIFIED，整体REPAIRED，P3H |

不重编号、不把已修根因改写为从未出现；新发现V-28/V-29不声称由第二轮delta引入。计数不作为收敛依据。

### Findings — P1

**V-27 — 本次联系人归因旁路当前具体邮箱准入（NEW / REGRESSION）**

Requirement：master I-7/I-8、09 I-3、repair R-1/R-3要求claim前对实际邮箱复验当前准入；排除自身业务行不能排除其他资格保护。`campaign/service/DiscoveryPersonRecipientService.kt:235`在看到已绑定contact后先返回ALREADY_CONTACTED，尚未完成后面的具体邮箱筛选。`campaign/service/DiscoveryPersonOutreachGuardService.kt:538–558`以ownContactOnly忽略这个结果，同时跳过selectedEmail校验；没有继续执行此前被提前返回的逐邮箱资格步骤。

P3E使用实际recipient、实际BatchSelector、实际MySQL repos/事务guard：reserve时AUTO_PASSED，创建本次绑定NEW contact后将当前准入改NEEDS_REVIEW；recipient只回ALREADY_CONTACTED，claim仍Claimed，库中SENDING。P3F进一步通过实际InitialOutreachService，在compose边界把当前准入变为NEEDS_REVIEW，随后fake SMTP实际调用1次，目标D1@example.test；当前admitted=false，持久SENDING。边界模拟的只是ES/准入输入、compose和SMTP，未mock被审guard、事务或业务仓储。

影响：资格已失效仍进入外发，较V-26原正常流程全阻断发生安全性倒退。最小涉及Guard的自身contact归因与Recipient的具体邮箱资格接口；不能通过全局忽略contact或继续使用旧snapshot修补。真实SMTP为0。

**V-3 — 已有根挂接缺锁后完整状态验证（PERSISTENT）**

Requirement：master I-4、04 I-2、repair R-3要求并发后完整组与revision复验，同类作者键不能冲突。`discovery/service/DiscoveryPersonService.kt:371–388`在仓储事务前核验已有根成员和作者键；`:113`仅把personId、新members/newKeys交给attach；`discovery/repository/DiscoveryPersonRepository.kt:115–137`获取人物锁后没有比较原revision，也不再验证整个根的同类别键。

P3J实际Service/Writer+真实MySQL事务仓储，以确定性交错重现正常线程调度：root只有ORCID；A带同ORCID+OPENALEX:A100完成核验后暂缓attach，B带同ORCID+OPENALEX:A200先合法提交，随后A继续。两次均ATTACHED，最终同一个personId持有A100和A200，成员3。探针只在仓储调用边界安排交错；全部读写委派真实事务仓储，不伪造冲突结果。全名相同、live和proof均有效，反例隔离的是锁后状态约束。

影响：并发可自动合并明确不同OpenAlex身份。顺序新增同名/旧成员核验确已改善，但不满足本次明确锁后门禁。最小涉及PersonService与Repository的版本/整组核验衔接。

**V-5 — 某层当前事实读取失败仍可选中人物（PERSISTENT）**

Requirement：master I-4/I-7/I-8、08 I-1/I-3、repair R-3要求完整当前组；无法完整读取不得退成许可。`campaign/service/DiscoveryPersonRecipientService.kt:323`对每层findByDocumentIds异常使用getOrDefault(emptyList())；`:340–360`只要其他层仍有该成员，就不能区分不存在与该层读取失败。

P3A：RAW读取抛RAW unavailable，CANDIDATE/APPLICATION给有效d1，当前准入AUTO_PASSED；actual recipient返回included P1/d1，无exclusion。已修复“inBatch缓存不重读”不等于当前读取fail-closed。最小涉及Recipient分层读取结果/完整性传播。

**V-12 — 明确EMAIL_INVALID仍标“可发送”（PERSISTENT）**

Requirement：master I-5/I-7、06 I-2、07 I-1/S-2、repair R-9要求不把明确邮箱不合格/未知真值输出成当前renderer解释的许可。`discovery/service/DiscoveryPersonQueryService.kt:279–285`读取并暴露contact.operatorStatus，却仅按email、admissionState、stale调用perEmailAdmissionStatus；`:367–382`只检查stale/抑制/准入。`static/app.js:13510`将ADMITTED直接标“可发送”。

P3I实际Query+实际BatchSelector：持久准入AUTO_PASSED、关系有效、未退订，当前联系人明确operatorStatus=EMAIL_INVALID；返回stale=false、operatorStatus=EMAIL_INVALID、admissionStatus=ADMITTED。已修stale或读取异常UNKNOWN不能覆盖此明确不合格分支。最小涉及既有后端逐邮箱状态投影；不要求改持久准入或扩展前端。

**V-13 — 缺必要aggregation结构仍返回合法空桶（PERSISTENT）**

Requirement：master I-6/I-8、06 I-3、repair R-8要求用于精确统计的API必要结构完整。`expert/service/ExpertSearchService.kt:1406–1421`aggregateTerms虽调用requireCompleteResponse，后者`:1429`只检查body、timeout与failed shards；缺aggregations/bucket/buckets时MissingNode.map成为空列表。

P3B实际Search接受`{"timed_out":false,"_shards":{"failed":0}}`，输出`[]`。Query的标签/地区/域名统计消费该方法，错误被伪装为合法零桶。count/hits/SQL关系异常的旧反例已解决；剩余为同一完整性要求的聚合分支。最小涉及Search聚合结构校验及调用结果。

**V-14 — 无本次执行凭据却假认续跑；非RAW版本未绑定（PERSISTENT）**

Requirement：master I-4/I-8、10 I-1、repair R-6明确“同根同成员不构成归属证明；缺凭据包括响应丢失须STALE/变化待核，不能伪称protectionImported”。`discovery/service/DiscoveryPersonMaintenanceService.kt:309–365`重建空关系旧fingerprint后，凭当前成员同根、revision=0认定RESUMED_TARGET_STATE；没有本次request执行凭据。`:596`layerVersions只用source.updatedAt，只有RAW seqNo/primaryTerm进入成员证据，未绑定其他层实际版本。

P3C：先预览a,b；另一个来源UNRELATED_REQUEST经actual PersonService登记同一a,b（未执行维护guard import）；旧apply返回UNCHANGED、protectionImported=true、RESUMED_TARGET_STATE，importCalls=0。P3D：预览后只把CANDIDATE/a真实_seq_no从1改42、保持source/updatedAt，其旧apply仍APPLIED。两者使用actual Maintenance/Person/Writer与HTTP夹具，分别隔离归属凭据和版本遗漏。原revision回滚判断已改善，不消除本缺陷。最小涉及维护expected、归属结果凭据及审计链；不以本次新增schema作为推定方案。

**V-28 — 不完整邮箱搜索被当成唯一归属并写proof（NEW）**

Requirement：master I-3及保持项“来源唯一对应”、01 I-1/I-2、02 I-2要求邮箱在现有层中唯一命中原doc。`expert/service/ExpertIndexWriterService.kt:727–749`findDiscoveryHitsByEmail未检查timed_out/failed shards或必要完整性，仅解析hits；`discovery/service/DiscoveryIdentityRepairService.kt:72–89`据此distinct唯一即授权签证，Recovery:244亦消费同接口。

P3G actual Writer+Repair的HTTP边界返回timed_out=true、failed=1、仅a一个hit；服务仍REPAIRED，proofWrites=1。另一分片未返回时不能证明没有另一doc使用同邮箱。最小涉及Writer唯一性搜索及Repair/Recovery使用边界；Writer不在本轮33授权表，属于既有master/01批准范围，后续任何修复须重新形成明确授权，reviewer不直接改。

**V-29 — 混合姓名层修复将无proof层误报ALREADY_VERIFIED（NEW）**

Requirement：master I-3/I-8、01 I-3、02 I-3要求所有现存层局部补证，部分完成不得报完整成功。`discovery/service/DiscoveryIdentityRepairService.kt:256`只要该层两个姓名段均非空就返回ALREADY_VERIFIED，不检查/填入缺失proof。Recovery:285–311在至少一层缺名时允许此组合进入恢复，并将Repair.REPAIRED视为完成。

P3H actual Writer+Repair：RAW缺given且无proof，CANDIDATE同邮箱同完整姓名但无proof；RAW补齐，CANDIDATE层结果ALREADY_VERIFIED、previousProof/currentProof均null，整体REPAIRED，实际candidate proof仍null。按Recovery.inspect分支，随后所有层都有姓名但proof不齐会转01路径，不能宣称本次02完整可恢复链闭合。最小涉及repairEmptyNameProof的全名缺proof分支及Recovery完成判定。与V-19“某层409后原目标proof继续”的已修根因区分。

### Findings — P2

V-21、V-23、V-24保留为非mandatory观察：测试名称、可选SCOPE_CONFLICT专项断言、复制KDoc。它们不阻塞PASS，不加入修复范围。V-22、mandatory V-25保持已解决。具体位置及原批准语义保留在Epoch1/2；本次未重写原证据。

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Current evidence |
|---|---|---|---|
| admission O1 旧revalidation测试名 | I-2准入 | P2 nonmandatory V-21 PERSISTENT | A9仅批准两断言新语义；C01新鲜通过，旧名非行为缺陷 |
| admission O2 conflict计taskFailure | I-1/I-2统计 | Observation | dryRun冲突单列，apply冲突计failure是可见统计口径，无实际错误证据 |
| 01 O1 35 vs27用例记账 | I-3验证 | Observation | 历史执行记账差别不替代本次C03/C04实际计数 |
| 02 none | I-2/I-3 | N/A | 无RECORD_ONLY；新V-28/V-29按源码/诊断独立判定 |
| 04 O1 同类key distinct | I-4可靠合并 | 原问题PASS；并发另FAIL V-3 | 静态同ORCID去重正确；P3J证明锁后不同OpenAlex仍合入 |
| 04 O2 unique/deadlock重读 | I-4/I-8 | 局部PASS | MAX_CONFLICT_ATTEMPTS重读；本次反例不是unique/deadlock冲突，不能据此豁免V-3 |
| 04 O3 V154/V155迁移既有断言 | storage/迁移 | Observation | A11/A14按现有迁移事实更新；真库C09 |
| 04 O4 fake冲突覆盖不足 | I-4 | V-22 RESOLVED | 真实触发fake create conflict后改变ES facts的区分测试C08；非完整并发证明 |
| 05 O1 SCOPE_CONFLICT计数专项 | I-1/I-4 | P2 nonmandatory V-23 | 未发现该专项计数功能错误；不替代V-3/V-5 |
| 06 epoch1 O1 / epoch2 R1 深页 | I-6 | V-10 RESOLVED | 分块≤1000及from1000/size50回归C12 |
| 06 epoch1 O2 / epoch2 R2 性能 | I-6研究门禁 | BLOCKED B-1 | 51,829真实形状p95/heap仍缺，未豁免 |
| 07 none | I-6/UI | N/A | C13–C15机器项；人验PENDING |
| 08 O1 ALREADY_CONTACTED KDoc | I-7 | P2 nonmandatory V-24 | 注释误写不等行为；实际ownContact旁路V-27另证 |
| 09 O1 SMTP后外层回滚 | I-7/I-8 | V-7 RESOLVED；B-2 BLOCKED | 真guard REQUIRES_NEW及P3F SENDING保留；三实际入口完整组合仍缺 |
| 09 O2 未SMTP RESERVED清理 | I-8 | V-9 RESOLVED | 三caller清理及测试；V-26原自阻断解决，V-27安全回归单列 |
| 09 O3 A14 31/32计数 | 迁移 | Observation | 版本/表/键断言实际通过；文字数量不改变授权 |
| 10 O1 历史保护导入 | I-7/I-8 | 原V-15 RESOLVED；V-14 FAIL | 真库businessKey/邮箱保护导入正确；P3C根本未导入却声称true |
| 10 O2 expected绑定 | I-4/I-8 | V-14 PERSISTENT | P3C独立登记误归属，P3D非RAW版本遗漏 |
| 10 O3 自调用事务 | I-8/原子性 | 原缺陷RESOLVED | 显式TransactionTemplate、C17实际整组导入失败回滚；不扩写为所有维护语义PASS |

### Evidence Boundaries

- **B-1 PERSISTENT / BLOCKED**：没有当前代码、51,829条真实形状匿名数据的p95≤2s与额外heap≤128MiB可复核测量。selected交付与执行handoff明确未补齐。源码的2s/数量上限、单测小夹具、成功编译均不是研究门禁证据。不得伪造数据、放宽预算或自动批准性能产品改造。
- **B-2 PERSISTENT / BLOCKED**：C17真库guard测试及实际Initial已覆盖部分。当前`DiscoveryPersonOutreachGuardServiceTest.kt:670/701`只新增实际Initial运行，单独ManualInitial、ManualExpert实际入口+真实guard/仓储的健康首发各恰1次、三入口同一真库同人竞争恰1次、各入口成功后业务落库失败跨入口不重发的完整批准矩阵仍缺。其他caller单测mock guard不替代这项证据，≤1且全为0也不能证明正常首发。
- 当前实际Initial测试允许捕获成功后落库异常，只断言fake SMTP=1与保护状态；不是“健康路径完成原联系人/邮件成功事务”的证明。该`attemptId=0L`外键问题在master base已存在（`git show 9d353934b82ed3e36a193c70748e5efccb3d9e82:src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` exit0，原第118行即传0L），列Observation，未擅自变成新产品修复任务，但其环境/组合限制不能用于宣称B-2满足。
- 未执行真实SMTP、生产apply、生产迁移、开关修改、删除或回滚。人工视觉及业务验收没有替人执行，也未制造人源签署。
- 旧Zulu11.0.15的clean package C20有明确批准例外；以现有Zulu11.0.32.1的C21履行JDK11门禁。该例外不覆盖B-1/B-2或任何新缺陷。
- 所有额外诊断仅写`/tmp/discovery-aggregate-epoch3`及瞬时build/Testcontainers产物。没有修改产品、tests、计划、review evidence、index、HEAD或branch。

### Manual acceptance

| ID | Mandatory | Expected check | Human result |
|---|---:|---|---|
| A-1 | yes | 冻结名单/来源字节、逐ID唯一结论、preview业务零写 | PENDING |
| A-2 | yes | 缺证/空名闭环，冲突/不可得保留，准入与预估区分 | PENDING |
| A-3 | yes | HOLD/REJECTED/LEGACY_APPROVED、旧联系、层冲突/409、只提rollback | PENDING |
| A-4 | N/A | 03真实备用来源增益；A1当前未纳入03执行 | N/A；不宣称03完成 |
| A-5 | yes | 3邮箱1人、两个详情/旧链、预估1、三实际入口假SMTP恰1 | PENDING；机器缺陷/B-2另列 |
| A-6 | yes | frozen/live历史保护、第三邮箱增量、原业务保持、名单外零写 | PENDING |

机器FAIL阶段不能进入人工最终接受或集成。原人工批准只是精确方案/执行授权，不是本次新code SHA的人源验收签署。

### Observations and convergence

- C17实际Initial healthy测试中的既有attemptId=0L业务落库异常属于baseline范围外行为；原异常证据保留，未把它归入本次P1或自动扩大授权。
- 本轮V-1、V-2、V-4、V-8、V-15以及V-26原根因取得真实进展；其他先前RESOLVED项亦检查回归。V-3/V-5/V-12/V-13/V-14仍有必需分支漏洞。
- **DIVERGING**不是finding数量计算：最关键的新V-27把“正常首发被拒”变成“当前准入失效仍实际调用SMTP”；V-28把不完整唯一性查询转为身份写入，V-29把跨层不完整变成完成。核心安全写边界出现额外P1，且V-28涉及当前33表外的Writer，不能将范围增长包装成同轮机械修复。`PROGRESSING`要求无同等或更高级新回归，此条件不成立；`STALLED`亦不准确，因为实现及根因已有实质变化。

### Final reviewer state

HEAD `038da49677c1893460d01f0d5b34cc7795c59b12` unchanged；index clean（git diff --cached --quiet exit0）；product/test diff为空。git status仅：`M docs/plans/review/2026-10-13-discovery-identity-recovery-master/ledger.md`，即控制器已知的review ledger编辑。repair hash保持`1c3736141987d41bdbc1737bcd0756cf35f4a8fca0e97bc8cd661586dbeb1f59`。本reviewer未写任何tracked artifact；report.md仅为/tmp返回载体，由控制器按append-only规则保存。

### Repair planning: N/A

`review-p`对 **FAIL + DIVERGING** 必须停止并请求人裁定；本reviewer不调用repair-p、不改repair.md、不生成新修复轮、不执行产品修复。精确repair保持SHA256 `1c3736141987d41bdbc1737bcd0756cf35f4a8fca0e97bc8cd661586dbeb1f59`。

### Next Action

请求人裁定新的修复策略与明确边界：如何把自身contact归因与当前具体邮箱许可分开、如何使根挂接在锁后仍满足作者键约束，并处置持续expected/查询/展示缺陷及新补证问题；同时补齐B-1/B-2的必需证据。先完成裁定，再形成任何新repair；不沿用旧执行调用自动授权新增修复或Writer范围。

停点依据：[review-p SKILL.md](/Users/lukai/.agents/skills/review-p/SKILL.md)路由原文：`FAIL | STALLED or DIVERGING | Stop and request human adjudication.`；[verify-p SKILL.md](/Users/lukai/.agents/skills/verify-p/SKILL.md)规定`STALLED and DIVERGING require a human decision after this report.` 本次适用该明确停点，不是对已授权只读审查重复求许可。

No product code was modified.
