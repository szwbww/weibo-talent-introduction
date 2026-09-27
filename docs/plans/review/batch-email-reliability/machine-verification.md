# Aggregate Machine Verification — batch-email-reliability

## Epoch 1 — 2026-09-27T08:01:47Z

- Master plan: docs/plans/2026-09-26/batch-email-reliability-plan.md
- Governing master SHA-256: d0226d4fd73fd6e542d77a85ceab3d9285e0aacef4047668c0e7983735890a77
- Recorded identity commit: 38ba555b4147970ee77569e71f863955e2c4a2b5
- Invoked master SHA-256: 6f4905cd9be32b1db62bea9d2d79e3c1bad050c3627fbfdaa2d8f31334bb6f10
- Master identity state: AMENDMENT_RECORDED
- Boundary: 64c0394a940bd79c2ecc04e5c497650f045faa75..418c77ff35fff6a570ded92f5bb64e523a603f50
- Evidence HEAD: ef652f6402692d9b722992c2f8bfbe1fcb3616eb
- Reviewer: /root/aggregate_reviewer; fresh fork_turns=none; created after reviewed code commit
- Requested phase: aggregate/master
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/batch-email-reliability-plan/repair.md / DRAFT_READY
- Manual acceptance: PENDING
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun
- Branch: fast/batch-email-reliability-rerun
- Worktree resolution: DISCOVERED_FROM_GIT_WORKTREES

以下保存独立审查员完整 review-p 结论、合同矩阵、命令证据、复现及 repair-p 输出。路径相对上述工作树；格式作统一，未省略失败、跳过或不可用证据。

### Verification Result: FAIL

全部必需测试命令通过；独立复现确认一项 P1：历史过滤预扫描忽略取消，全部过滤后的提前返回将已取消执行报告为 COMPLETED/SUCCESS。

### Identity and Boundary

Governing amendments A1–A5：完整 diff 确认仅修改主计划及四子计划第4行的工作区/分支绑定。依据主计划“分解与执行次序”；原因：Bind the fresh run to a distinct isolated worktree and branch while retaining the paused prior run；批准记录 HUMAN:好的 你来执行吧; 2026-09-27T02:04:04Z。未追认新增产品/测试范围。完整主计划差异保存在同目录 ledger.md 的 Governing Diff。

| Artifact | Verified SHA-256 |
|---|---|
| Child 01: docs/plans/2026-09-26/batch-email-01-history-query.md | 80f5a58f8928f3d40fcb6c6bc0ba8e03476a08f2397e2a3743891a8bba89327a |
| Child 02: docs/plans/2026-09-26/batch-email-02-filter-backend.md | 9be4fb6f45b2108701ae28dbb470cd9d3218f06d16a56b84951aac98cec851ad |
| Child 03: docs/plans/2026-09-26/batch-email-03-failure-policy.md | 59ee99ab29b493c74fb05513ce2a9b08cefe9e768082ce7223a1f7b1605ba555 |
| Child 04: docs/plans/2026-09-26/batch-email-04-filter-ui.md | 3eefdeb756b98f5247492356671aae2f8ca0b62aff1eaba9f8649fec0c257dae |
| Fast ledger: docs/plans/fast/batch-email-reliability/ledger.md | 036f9ba7613eb1bbaa8432ed7dc1156642a32bfc8324c0b156dd2720842492e7 |
| Fast handoff: docs/plans/fast/batch-email-reliability/human-review-handoff.md | 3010b6956cfdd236136563ef93ec9a47990b343099e712a9fd36ee214edd7e28 |

独立核对：

- 完整 base..final_code_head 差异包含21个产品/测试文件，均在主计划21文件授权集合内：authorized=21 changed=21 unauthorized=[]。
- final_code_head..evidence_head 仅包含 fast-p 文档。
- base→code→evidence 祖先检查通过。
- 当前源码与 418c77ff35fff6a570ded92f5bb64e523a603f50 完全一致；index无修改。
- 审查员未 stage、commit、修改产品、测试或审查证据。审查员唯一新增仓库文件为后述 repair.md；本报告由控制器保存。

### Commands

全部命令 cwd 为上述工作树；Maven串行运行。计数格式为“运行/失败/错误/跳过”。

| ID | Exact command | Result | Fresh evidence |
|---|---|---|---|
| C1 | JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test | PASS | exit 0；JVM 4112/0/0/13；绑定JS1198通过、0失败、0跳过；15:42:12 +08完成；/private/tmp/aggregate-batch-full-mvn.log |
| C2 | JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchEmailVerificationServiceTest,TaskRetentionMigrationTest test | PASS | exit 0；39/0/0/0；绑定JS1198通过；15:56:08 +08完成；/private/tmp/aggregate-batch-focused01.log |
| C3 | DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=BatchEmailVerificationRepositoryIT -Dapi.version=1.40 test | PASS | exit 0；真实MySQL8.0.36，14/0/0/0；绑定JS1198通过；15:45:24 +08完成；/private/tmp/aggregate-batch-mysql-history.log |
| C4 | JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchSendTaskConfigServiceTest,ManualInitialOutreachServiceTest,OutreachTargetIteratorTest,BatchSendTaskRuntimeIntegrationTest test | PASS | exit 0；272/0/0/0；绑定JS1198通过；15:58:35 +08完成；/private/tmp/aggregate-batch-focused02.log |
| C5 | DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest -Dapi.version=1.40 test | PASS | exit 0；真实MySQL8.0.36，33/0/0/0；包含V141→V142、旧值false、新值true读写与列约束；绑定JS1198通过；15:53:45 +08完成；/private/tmp/aggregate-batch-mysql-migration.log |
| C6 | JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest test | PASS | exit 0；224/0/0/0；绑定JS1198通过；16:00:57 +08完成；/private/tmp/aggregate-batch-focused03.log |
| C7 | node --test src/test/js/batchEmailVerification.test.js | PASS | exit 0；33测试、4 suites、0失败、0跳过；/private/tmp/aggregate-batch-js-focused.log |
| C8 | node --test src/test/js/*.test.js | PASS | exit 0；1198测试、236 suites、0失败、0跳过；/private/tmp/aggregate-batch-js-all.log |
| C9 | git diff --check | PASS | exit 0；验证结束及repair文档写入后均检查 |
| C10 | git diff --check 64c0394a940bd79c2ecc04e5c497650f045faa75..418c77ff35fff6a570ded92f5bb64e523a603f50 | PASS | exit 0；完整提交边界检查 |

C3/C5的 DOCKER_HOST、-Dapi.version=1.40 是已记录的环境兼容参数。两项必须启用的MySQL验证均真实运行、零跳过；未使用C1的跳过结果充当数据库验证。

C1如实保留13个跳过，包括Flyway opt-in及其它条件性/禁用测试。全部必需命令均为本次新鲜结果，未复用子计划叙述。临时日志只作原始命令输出定位，本报告已持久化退出码、计数及关键复现；临时日志不是后续执行前置依赖。

### Source Evidence Index

以下文件均相对本报告开头的精确工作树；冒号后的数字为已核对行号。

| Code | Exact repository-relative file |
|---|---|
| S1 | src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt |
| S2 | src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt |
| S3 | src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt |
| S4 | src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt |
| S5 | src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt |
| S6 | src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt |
| S7 | src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt |
| S8 | src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt |
| S9 | src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt |
| S10 | src/main/resources/static/app.js |
| S11 | src/main/resources/static/index.html |
| S12 | src/main/resources/db/migration/V142__add_exclude_verified_unavailable_emails.sql |
| S13 | src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt |
| S14 | src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt |
| T1 | src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt |
| T2 | src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt |
| T3 | src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt |
| T4 | src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt |
| T5 | src/test/js/batchEmailVerification.test.js |

### Contract Matrix

01–04指已冻结的四份子计划。所有机器项目均为强制项；人工项目保留PENDING，不代做。

| ID / Source | Requirement | Mandatory | Required evidence | Verdict | Evidence |
|---|---|---|---|---|---|
| M-1 / 主计划执行次序、授权清单 | 正确身份、完整代码边界、仅21授权文件；证据提交不得代替代码HEAD | 是 | Git边界、哈希、全差异 | PASS | Identity and Boundary；21/21授权、无额外产品文件 |
| M-2 / 主计划非目标 | 无黑名单、缓存、ES字段、额外付费请求、HTTP重试扩张、自动重验/补发、SMTP重构 | 是 | 完整产品差异及调用路径 | PASS | 完整21文件差异；S2仅新增只读helper；S12仅一列；HTTP协议/重试路径未改 |
| M-3 / 主计划验证 | 完整Maven、两项真实MySQL、子计划命令、diff检查 | 是 | 新鲜命令与计数 | PASS | C1–C10 |
| 01-I1 / T1、T3、T4 | 原始有效行、严格一年下界/包含上界、合法状态；查询与保留一致 | 是 | SQL、服务路径、真实查询/清理 | PASS | S1:79、209；S3:180；T1:338、370、405；C2/C3 |
| 01-I2 / T1、T2 | 先按checked_at/id取最新有效行，再判断undeliverable；ERROR/reuse不覆盖 | 是 | anti-join、测试不同时间/同秒/错误/邮箱 | PASS | S1:209；S2:80；T1:338；C3 |
| 01-I3 / T2 | trim/ROOT lowercase、去空去重、保留+tag、500分块、空输入零SQL、固定北京now、只读且DB错误传播 | 是 | helper及无交互/分块断言 | PASS | S2:80、404、588；T2:68、92、116；C2 |
| 01-PRESERVE | 单邮箱复用、HTTP249两次/500ms、审计字段、清理分批/排序不变 | 是 | 完整差异与旧回归 | PASS | S1:95；S2:231；S3:180；C2/C3/C6 |
| 02-I1 / T1、T4 | 存量/历史false、新建true、nullable update保留、显式false、legacy/启停/软删保留、snapshot贯通 | 是 | 全写读路径、JSON、真实迁移 | PASS | S4:38、109、140；S5:39、184、376；S6:66、103、149、173、199、532、684；S12:1；T4:77；C4/C5 |
| 02-I2 / T1、T2 | 历史过滤与实时验证独立；off零历史；无密钥可过滤；材料允许历史过滤、拒实时验证 | 是 | 四组合及入口校验 | PASS | S7:555、582、1623；S9:419；T3:237、334、374；C4；独立四组合探针见下 |
| 02-I3 / T2 | 介绍按profile邮箱、材料按contact邮箱；预估/执行同helper、固定now、保序/重试seen优先级 | 是 | 双来源/材料调用链 | PASS | S7:479、564、1298、1599、1621；T3:237、273、334；C4 |
| 02-I4-PAGING / T3 | 原始页判末页；整页过滤/重复不截断；有用页后offset归零 | 是 | 迭代器与回归 | PASS | S8:26、42；S7:582；C4的11项迭代器测试 |
| 02-I4-CANCEL / T3:103、验收I4、A3 | 长扫描可取消；取消后不再取页，持久化CANCELLED，不误报COMPLETED | 是 | 执行全链与取消复现 | FAIL | V-1：S7:564、572、1658绕开取消；独立probe得到取消=true但COMPLETED/SUCCESS |
| 02-I5 / T2、T4 | 派生排除数、2/1/3/2 fixture、被排除者无验证/发送/联系绑定/标签/跳过副作用 | 是 | 预估/实际发送、无副作用断言 | PASS | S7:479、1298、1599、1645、1878；T3:273、374；C4 |
| 02-PERF / T2、验收性能结构 | on用500批scroll计数，不积累全量ES profiles；off保留_count；无每邮箱SQL | 是 | 调用结构、批处理测试 | PASS | S7:1623、1658；S13:715；S2:80；C2/C4。取消问题独立计入V-1 |
| 02-PRESERVE | 退订、已发、门禁、账号、额度、手动不回写；不改既有跨层估算/深分页范围 | 是 | 完整差异、旧测试、快照入口 | PASS | S7过滤发生在既有目标生成/发送前；S9:355；C1/C4 |
| 03-I1 / T1、T2 | 三目标级码暂缓；依赖/审计/未知码停止；HTTP映射及249重试不变 | 是 | 分类集合、编排、HTTP回归 | PASS | S1:415；S7:752；S2:231、253；T3:5421、5444；C6 |
| 03-I2 / T2 | ERROR和原始错误证据保留；SKIPPED+DEFERRED+NOT_REQUIRED；不标坏、不发送 | 是 | 验证持久化→严格收尾→展示 | PASS | S2:139、174、182；S7:752；T3:5315；C6/C7 |
| 03-I3 / T2 | 先严格审计再计跳过；不耗成功额度；继续B/C；审计失败停止；全暂缓完成 | 是 | 成功收件人、计数、审计/取消测试 | PASS | S7:654、752；T3:5315、5355、5372、5400；C6 |
| 03-I4 / T3 | 全局失败保留sent/remaining和FAILED/PARTIAL_SUCCESS；旧runtime暂停、普通SMTP部分成功不变；现代cron不变 | 是 | 结果→旧runtime/现代执行路径 | PASS | S7:777、1793；S9:318、355、500；C6 |
| 03-I5 / T4 | 仅已落DEFERRED的ERROR显示暂缓；旧ERROR保持原解释；summary.errors原口径 | 是 | 实际字段分支与JS回归 | PASS | S10:20105、20114、20206；T5:493、523；C7 |
| 03-S1 | 固定提示、warn/error、既有DOM/CSS，不承诺自动重试 | 是 | DOM/字符串/差异 | PASS | S10:18842、20069、20084；S11:1448、1710；C7；styles.css零diff |
| 04-I1 / T1 | 新建/独立手动true，来源缺失false；草稿/保存/预估/执行/diff/确认完整；clear source默认；旧历史原样 | 是 | 全字段链、来源隔离、JSON测试 | PASS | S10:18066、18945、19104、19199、19222、19257、19306、19352、19431、19549、19597；S5:39；T5:1000；C4/C7 |
| 04-I2 / T1 | 材料/实时验证off/模板门禁不可用不禁用历史开关 | 是 | 事件及状态函数、行为测试 | PASS | S10:18889、18902、20568；T5:258、1102；C7；后端四组合探针 |
| 04-I3 / T2 | 选中响应供total/excluded；缺省0；仍1/2请求；500ms、seq、错误清空 | 是 | 异步行为测试及源码 | PASS | S10:18983、18992、19004；T5:1063、1102、1130；C7 |
| 04-S1-MACHINE / T1、T3 | 两段指定DOM唯一、相邻、可访问label、无inline/new CSS、填值同步label | 是 | 真实HTML与计划代码块比较、JS | PASS | S11:1423、1682；S10:18069、19260；两段HTML归一空白后均精确匹配；C7 |
| 04-S2 / T1、T2 | 对应排除数文案、开关off隐藏排除数、沿用diff/参数/列表样式 | 是 | 展示分支和JS | PASS | S10:17916、18992、19387、19431、19493、19549；C7 |
| HUMAN-01 / 01 A1–A2 | 最新覆盖、真实清理不复活旧坏结果的人工确认 | 是，人工 | 人工结果及证据 | N/A | PENDING |
| HUMAN-02 / 02 A1–A4 | 配置迁移、同范围预估/SMTP sink、材料/翻页/取消、手动快照独立 | 是，人工 | 人工结果及证据 | N/A | PENDING |
| HUMAN-03 / 03 A1–A3 | 两次249后补额度、402整批停止/旧runtime、审计/历史日志 | 是，人工 | 人工结果及证据 | N/A | PENDING |
| HUMAN-04 / 04 A1–A3及S1视觉项 | Tab/Space、1100/窄屏、计数/来源差异、实际执行与历史 | 是，人工 | 人工结果及证据 | N/A | PENDING；未将历史浏览器smoke认作本次人工验收 |

### Additional Cross-Contract Evidence

独立临时探针调用当前编译后的真实 ManualInitialOutreachService，仅外部依赖使用既有测试fixture/Mockito。四种组合均通过：

~~~text
MATRIX filter=false live=false sent=2 historyCalls=0 liveCalls=0 PASS
MATRIX filter=false live=true sent=2 historyCalls=0 liveCalls=2 PASS
MATRIX filter=true live=false sent=1 historyCalls=2 liveCalls=0 PASS
MATRIX filter=true live=true sent=1 historyCalls=2 liveCalls=1 PASS
~~~

exit 0；源码 /private/tmp/AggregateFilterMatrixProbe.java，输出 /private/tmp/aggregate-batch-filter-matrix.log。历史过滤启用时，预估及执行页各读取一次；关闭时零历史调用。未实际发送邮件或调用付费验证。

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | 首次聚合审查确认：历史预扫描不响应取消，空结果提前返回COMPLETED/SUCCESS |
| Child 01 / F-1 | RESOLVED | 独立检查T1:412将较新PASS所属execution设为100天前；真实deleteOlderThan与C3验证14/0/0/0，未复活旧坏结果 |

Prior aggregate report/findings: N/A。此次INITIAL不以子计划曾经修复F-1推导为聚合PROGRESSING。

### Findings — P1

**V-1 — 历史过滤预扫描忽略取消，零候选提前返回覆盖取消终态。**

合同依据：docs/plans/2026-09-26/batch-email-02-filter-backend.md:103要求“长扫描需可取消……持久化 CANCELLED，不误报 COMPLETED”，验收I-4/A-3要求取消后不继续取页。

精确路径：

1. S7:564执行前计数入口调用新增过滤版 countEsTargets。
2. S7:1658 scroll计数callback没有取消参数/检查，callback第1671行恒返回true，且继续下一漏斗层。
3. 若扫描后全部被过滤，S7:572空结果分支直接返回 emptyResult(COMPLETED, null)；S7:1784固定 wasCancelled=false，绕开第1105行取消收尾。
4. taskFinalStatus因此为SUCCESS；S14:167–198直接采用该值持久化。
5. S13:754既有scroll实现已支持callback=false停止并finally清理，无需扩大修复到搜索基础设施。

影响：用户取消后仍扫描后续ES页/漏斗层、读取历史；全部被排除时最终进度及审计被记录为正常完成。最小修复范围：ManualInitialOutreachService.kt及其现有测试文件。

### V-1 Reproduction

以下源码可重建诊断，无需依赖临时文件长期存在。它调用当前真实服务，复用现有测试fixture；scroll替身遵循生产 handler=false 即停止的语义。首个callback开始前设置取消，历史结果将全部候选排除。

保存为 /private/tmp/AggregateCancelProbe.java：

~~~java
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.weibo.talentintroduction.campaign.service.*;
import com.weibo.talentintroduction.campaign.domain.*;
import com.weibo.talentintroduction.expert.service.*;
import com.weibo.talentintroduction.expert.domain.*;
import com.weibo.talentintroduction.task.service.*;
import org.mockito.Mockito;
import kotlin.jvm.functions.Function1;

public class AggregateCancelProbe {
  static Object field(Object o, String n) throws Exception {
    Field f = o.getClass().getDeclaredField(n);
    f.setAccessible(true);
    return f.get(o);
  }

  static Object invoke(Object o, String n, Object... args) throws Exception {
    Method m = Arrays.stream(o.getClass().getDeclaredMethods())
        .filter(x -> x.getName().equals(n)).findFirst().orElseThrow();
    m.setAccessible(true);
    return m.invoke(o, args);
  }

  public static void main(String[] args) throws Exception {
    ManualInitialOutreachServiceTest fixture =
        new ManualInitialOutreachServiceTest();
    fixture.setUp();
    ExpertProfile bad =
        (ExpertProfile) invoke(fixture, "expert", "E071", "bad@example.com");
    invoke(fixture, "stubIntroSendPipeline",
        invoke(fixture, "account", "chen"), Collections.singletonList(bad));

    ExpertSearchService search =
        (ExpertSearchService) field(fixture, "expertSearchService");
    BatchEmailVerificationService history =
        (BatchEmailVerificationService) field(fixture, "batchEmailVerificationService");
    TaskProgressStore progress =
        (TaskProgressStore) field(fixture, "progressStore");

    AtomicBoolean cancelled = new AtomicBoolean(false);
    AtomicInteger pages = new AtomicInteger();

    Mockito.when(progress.isCancelled(
        Mockito.eq("MANUAL_INITIAL_OUTREACH"), Mockito.eq(12345L)))
        .thenAnswer(i -> cancelled.get());
    Mockito.when(history.findKnownUndeliverableEmails(
        Mockito.anyCollection(), Mockito.any()))
        .thenReturn(Collections.singleton("bad@example.com"));

    Mockito.doAnswer(i -> {
      Function1<List<ExpertProfile>, Boolean> handler = i.getArgument(3);
      pages.incrementAndGet();
      cancelled.set(true);
      boolean keepGoing = handler.invoke(Collections.singletonList(bad));
      if (keepGoing) {
        pages.incrementAndGet();
        handler.invoke(Collections.singletonList(bad));
      }
      return null;
    }).when(search).scrollExpertsFiltered(
        Mockito.any(), Mockito.anyList(), Mockito.eq(500), Mockito.any());

    ObjectMapper mapper = new ObjectMapper().registerModule(new KotlinModule());
    BatchExecutionSnapshot snapshot = mapper.readValue(
        "{\"mailType\":\"INTRODUCTION\",\"roundSize\":10,\"roundsPerRun\":1,"
        + "\"perMailIntervalMs\":0,\"perRoundIntervalMs\":0,"
        + "\"selfCheckTtlMinutes\":30,"
        + "\"excludeVerifiedUnavailableEmails\":true}",
        BatchExecutionSnapshot.class);

    ManualOutreachResult result =
        ((ManualInitialOutreachService) field(fixture, "service"))
        .run(snapshot, 12345L, ExecutionMode.MANUAL, false);

    System.out.println("PROBE cancelled=" + cancelled.get()
        + " pages=" + pages.get()
        + " finalStatus=" + result.getFinalStatus()
        + " taskFinalStatus=" + result.getTaskFinalStatus()
        + " wasCancelled=" + result.getWasCancelled());

    if (!cancelled.get() || pages.get() != 4
        || !result.getFinalStatus().equals("COMPLETED")
        || result.getWasCancelled()) {
      throw new AssertionError("Unexpected reproduction result");
    }
  }
}
~~~

实际执行命令，cwd为目标工作树：

~~~sh
probe_cp=$(sed -n 's/.*name="java.class.path" value="\([^"]*\)".*/\1/p' target/surefire-reports/TEST-com.weibo.talentintroduction.campaign.service.ManualInitialOutreachServiceTest.xml)
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/javac -cp "$probe_cp" /private/tmp/AggregateCancelProbe.java &&
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp "/private/tmp:$probe_cp" AggregateCancelProbe > /private/tmp/aggregate-batch-cancel-probe.log 2>&1
result=$?
tail -n 10 /private/tmp/aggregate-batch-cancel-probe.log
exit $result
~~~

最终命令exit 0表示成功复现缺陷，不表示合同PASS。完整运行日志：

~~~text
2026-09-27 15:42:25.021 [main] INFO  c.w.t.c.s.ManualInitialOutreachService - Starting scheduled batch outreach, executionId=12345, mode=MANUAL, oneRoundOnly=false
2026-09-27 15:42:25.026 [main] INFO  c.w.t.c.s.ManualInitialOutreachService - Retryable targets: 0 (funnelLevels=[CANDIDATE, APPLICATION])
2026-09-27 15:42:25.031 [main] INFO  c.w.t.c.s.ManualInitialOutreachService - Outreach targets: 0 retryable, 0 ES estimate, 0 total estimate; config: roundSize=10, perMailMs=0, perRoundMs=0
PROBE cancelled=true pages=4 finalStatus=COMPLETED taskFinalStatus=SUCCESS wasCancelled=false
~~~

首次探针执行exit 1：诊断断言原先预期2次callback，而默认快照包含两个漏斗层，实际为4次。该次已经产生同一错误业务结果。随后仅把临时诊断断言改为4，最终exit 0；未修改产品或仓库测试。

### P2

N/A。

### Observations

- 主计划明确保留既有跨层/双来源预估重复、旧执行深分页边界；此次未扩修。
- 人工SMTP sink、页面布局/键盘及整体验收尚未执行；子计划既有机器或浏览器叙述不替代人工结果。

### Fast-P RECORD_ONLY Re-evaluation

已读取四子计划完整brief/execution/fix-log/verify-log及终态handoff。

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child01终态RECORD_ONLY | 历史读取/保留一致性 | N/A，终态无此项 | 独立重查01-I1～I3及真实C3；历史F-1已解决 |
| Child02终态RECORD_ONLY | 过滤/分页/取消 | N/A，终态无此项 | 独立重查02矩阵；新增聚合V-1，未因LIGHT_PASS忽略 |
| Child03终态RECORD_ONLY | 失败分流/终态/日志 | N/A，终态无此项 | 独立源码及C6/C7 |
| Child04终态RECORD_ONLY | UI字段链/预估/样式 | N/A，终态无此项 | 独立源码、两段DOM比较及C7/C8 |
| Handoff RECORD_ONLY Index | 整体复核不得遗漏 | 与四终态报告一致 | 无遗留RECORD_ONLY；“无RECORD_ONLY”不等于聚合PASS |

### Evidence Boundaries

- 必需机器命令无缺失；两项真实MySQL验证均零跳过。
- 取消及四组合探针使用当前编译产品与Mockito依赖，未触发生产邮件、付费验证或生产迁移。
- 12项人工清单全部PENDING；未创建人工PASS或签字。
- 无既有聚合报告、聚合修复轮次；收敛状态为INITIAL。
- 依据决策优先级，已确认强制合同违反V-1，因此机器结果FAIL，即使全部测试命令通过。

### Verification Next Action

V-1属于正确批准合同内的可修复实现问题，FAIL + INITIAL，已进入一次 repair-p 规划；未实施修复。

### Repair Planning Result: DRAFT_READY

- Baseline plan: docs/plans/2026-09-26/batch-email-reliability-plan.md
- Verification result: FAIL / INITIAL
- Repair artifact: docs/plans/fix/batch-email-reliability-plan/repair.md
- Repair SHA-256: 08f7a73c8c2534af1aec773bad78fe8e3d79dccb6afb5edfcf2d6b45a00fad78

Included Findings：

- V-1：REPAIRABLE，一个根因、一个修复任务R-1。
- 产品授权仅两文件：ManualInitialOutreachService.kt及其现有测试。
- 判别性回归覆盖预扫描取消、后续页/层停止、空结果取消优先、进度及 taskFinalStatus=CANCELLED，包括 oneRoundOnly=false/true。
- 不修改原计划、不扩schema/UI/搜索基础设施、不创建额外修复轮次。

Excluded Findings：N/A；既有非目标、人工验收及无证据建议不纳入修复。

One-Approval Handoff 已完整写入计划：

- 产品提交主题：fix(batch-email): honor cancellation during historical filtering
- 唯一执行交接文件：/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun/docs/plans/review/batch-email-reliability/repair-execution.md
- 证据提交主题：docs(review-fast-p): record batch email cancellation repair execution
- 明确记录人工批准来源、repair身份、前后code SHA、命令/计数、偏差、执行者、clean-state；仅该交接进入单独docs提交。
- 不授权push、merge、部署、amend、历史改写或额外产品文件。

Required Human Decision：批准该具体修复计划后执行。repair-p SKILL.md（/Users/lukai/.agents/skills/repair-p/SKILL.md）明确要求：“Execution is prohibited until the human explicitly approves this plan.” 当前审查授权仅允许规划，未代为批准或执行。

No implementation was performed. No product code was modified.

### Permitted Next Action

一次明确的人工请求可批准计划范围内修复、产品本地提交、持久化交接的单独证据提交，并在完成后回到已授权聚合复审：

~~~text
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun/docs/plans/fix/batch-email-reliability-plan/repair.md
完成计划授权的本地提交和 repair-execution.md 交接后，在同一任务继续 $review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun/docs/plans/fast/batch-email-reliability/human-review-handoff.md；使用持久化交接，无需我转述执行者元数据。
~~~
