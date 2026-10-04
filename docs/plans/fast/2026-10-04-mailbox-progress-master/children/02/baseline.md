# Child 02 Baseline — 前端与全量回归

- 运行位置：worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master`；起始 HEAD `5b5b092fdb496ed7c2ebf075d1f8375d4717341c`（seed + artifacts 提交；源码与 master base `e28e53fd898edd62905a0d45a6bf90396b18b1bf` 一致）。
- 环境：JDK `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`；node v25.7.0；日志 `/tmp/fastp-mbp-b4.log`、`/tmp/fastp-mbp-b5.log`。

## 命令与基线结果

| # | 命令 | exit | 计数 | 结果 |
|---|---|---:|---|---|
| B4 | `node --test src/test/js/*.test.js` | 0 | tests **1434**, suites 274, pass **1434**, fail 0 | 全量 JS 干净 |
| B5 | `mvn test` | 1 | surefire Tests run **4547**, F0, Errors **19**, Skipped 13 | 唯一失败类见下 |
| B0 | `node --check src/main/resources/static/mailbox-chat.js` | 0 | — | 语法基线干净 |

B5 唯一失败类：

- `com.weibo.talentintroduction.mail.service.ExpertContactLocationServiceTest`：19 tests / 0F / 19E，全部为
  `<init>:38 » IllegalStateException: 国家时区目录配置错误：国家 CL 的时区...`（既有环境/配置失败，与 mailbox-progress 计划无关；关联并行 meeting-country-timezone 工作）。
- 因 surefire 失败中止构建，`mvn test` 尾部的 `exec-maven-plugin:node-test` 未执行；全量 JS 基线以 B4 单独结果为准（全绿 1434/1434）。

## 判定规则

- 实施后 B4 必须 1434+新增用例 全 pass、fail 0；B0 `node --check` exit 0。
- B5：允许保留的唯一既有失败为 `ExpertContactLocationServiceTest` 的 19 个 error；该类 error 数不得增加，不得出现其它失败类；若 surefire 通过而进入 exec node-test，该步骤必须全绿（以 B4 同源命令为准）。
- 已知既有状态：B5 不会执行到 node-test（surefire 先失败），因此"`mvn test` 未跑到 node 阶段"不是新增问题；以 B4 结果代替 node 阶段证据并注明。
