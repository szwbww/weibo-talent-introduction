# A4 / G-0 运行时证据（2026-10-02）

本文件是 `docs/plans/2026-10-02/contact-timing-00-master.md`《修正记录 A4》与
`docs/plans/fast/2026-10-02-contact-timing-00-master/ledger.md` Amendments A4 的原始证据。
判据定义见 A4：随包目录 ⊆ 运行期 tzdb、构建 JDK 与生产 JVM 同族、固定时刻偏移断言。
探针：`scripts/tzdb_catalog_probe.py`（随包发布，构建端与生产端各跑一次）。

## 1. 构建 JDK（本机）

| JVM | 版本 | 结果 |
|---|---|---|
| `~/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk`（A4 后 `build_command` 绑定） | 11.0.32.1 / 604 ids | **PASS**：418 缺失 0；meeting 532 未覆盖 0；偏移 10/10 |
| `/Library/Java/JavaVirtualMachines/zulu-11.jdk`（A4 前的发布 JDK） | 11.0.15 / 601 ids | **FAIL**：418 缺失 3（`America/Ciudad_Juarez`、`America/Coyhaique`、`Europe/Kyiv`）；偏移 7/10（`America/Mexico_City` 期望 -06:00 实际 -05:00） |

全量构建：`JAVA_HOME=…zulu-11.0.32.jdk/Contents/Home mvn clean package` 在发布 revision
`25eb414`（a8c5561 的构件等价提交，差异仅 scripts/docs）的 detached worktree 内 **exit 0**，
产出 WAR 48,842,668 字节；Node 用例 1296 pass / 0 fail。

## 2. 生产 JVM（`root@150.158.92.103`，无重启取证）

| JVM | 版本 | 结果 |
|---|---|---|
| 切换前（当前在跑）`/usr/lib/jvm/java-11-openjdk` | OpenJDK 11.0.23 / 603 ids，`lib/tzdb.dat → /usr/share/javazi-1.8/tzdb.dat`（el7 `tzdata` 包，仓库上限 `tzdata-2024a`） | **FAIL**：418 缺失 1（`America/Coyhaique`）；偏移 9/10 |
| 切换后（新装）`/usr/lib/jvm/zulu11.90.205-ca-jdk11.0.32.1-linux_x64` | Zulu 11.0.32.1 / 604 ids | **PASS**：418 缺失 0；meeting 532 未覆盖 0；偏移 10/10 |

`setenv.sh` 切换（备份 `setenv.sh.bak-20261002-jdk`）：

```diff
- export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
- export JRE_HOME=/usr/lib/jvm/java-11-openjdk
+ export JAVA_HOME=/usr/lib/jvm/zulu11.90.205-ca-jdk11.0.32.1-linux_x64
+ export JRE_HOME=/usr/lib/jvm/zulu11.90.205-ca-jdk11.0.32.1-linux_x64
```

## 3. 发布记录（publish_feature）

- Job：`job_20261002-100330_publish-production_2cafce65`，`completed` / exit 0。
- 源 revision：`a8c55613c7ddbc9e4a76837b45d2f8e99de7e63a`；构件 sha256 `780c63c373df2399…`，大小 48,842,668。
- 迁移：`migration applied: V146 create_expert_contact_location`（V51–V145 全部 skipped）。
- 部署：`Tomcat started.` / `status=passed`；备份 `talent.war.release_20261002-100333_a8c55613c7dd.bak`。
- 说明：构建期 19 errors（`ExpertContactLocationServiceTest`）在 A4 后消失；`MeetingConfirmationServiceTest`
  的 2 个 tzdb 红点由本次重新生成的会议目录消除。

## 4. 发布后核验（生产）

- 在跑 JVM：`/usr/lib/jvm/zulu11.90.205-ca-jdk11.0.32.1-linux_x64/bin/java`（`/proc/<pid>/exe` 实测）。
- 部署构件：`webapps/talent.war` 48,842,668 字节 @ 2026-10-02 10:10；`unzip -l` 含
  `ExpertContactLocation{Catalog,Controller,Service}.class`、`ReplyTimeRecommender.class`、
  `contact-country-timezones.json`、`meeting-timezones-zh.properties`。
- 对该 JVM 再跑探针：**PASS**（418/418、meeting 0 未覆盖、偏移 10/10）。
- DB：`expert_contact_location` 存在（FK→`expert_contact`，utf8mb4）；`multi_ai_kit_schema_history`
  中 V146 = `applied`。
- 启动日志：`Started TalentIntroductionApplication in 11.092 seconds` @ 10:11:07；启动后
  `ERROR`/`SEVERE` 计数 0；10:10:07 的 `IllegalStateException: 此Web应用程序实例已停止`
  来自旧 context 停止期的 `mysql-cj-abandoned-connection-cleanup` 线程，属停机噪声。
- HTTP：`http://150.158.92.103/talent/` → 200；`/talent/api/task-executions` → 401（鉴权正常）。

## 5. 未覆盖 / 残余

- 人工浏览器验收（A-1/A-2/A-3）仍未执行，属 G-0 之外的人工门禁。
- 目录快照声明 IANA 2026c，可运行 tzdb 最高 2026b（无 JDK 11 携带 2026c）；两者规则差异不可验证。
- `review-fast-p` 的正式 aggregate 复验未重跑；本文件提供的即为该 gate 缺失的 G-0 生产侧证据。
