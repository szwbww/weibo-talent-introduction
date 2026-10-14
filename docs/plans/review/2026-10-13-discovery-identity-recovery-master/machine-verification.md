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
