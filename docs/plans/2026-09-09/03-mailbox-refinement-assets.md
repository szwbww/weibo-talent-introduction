# 03 静态资源版本统一更新

状态：待实施；依赖01/02验证完成。仅8文件、一个静态资源注册子系统。本计划不部署。

## 需求描述

使用户正常刷新即可加载本轮完整资源，避免新DOM与旧JS/CSS混用。

必须保留：全部导航/脚本加载顺序、02逐字样式、原测试断言强度。不做：CDN/打包链/部署流程改造。

## 关键不变量

### Invariant I-1: 资源键统一
- Rule：index.html的styles.css、expert-materials.css、mailbox-chat.css、trust-reply-workbench.js、expert-materials.js、mailbox-chat.js、app.js共7处 `v=20260907-material-chat` 同时改为 `v=20260909-mailbox-refinement`。新旧值全仓检索，按本次证据命中的7个测试同步literal。
- Applies to：index.html及本计划7测试文件。
- Violation consequence：浏览器旧缓存/构建固定键失败。
- 来源：K-frontend-cache-key-triad（当前已扩展为7个生产资源）。

### Invariant I-2: 版本阶段不改行为
- Rule：只改资源query version及相同测试常量/测试名注释；不得削弱assert、排除测试、追加CSS覆盖或改业务DOM。
- Applies to：全部文件。
- Violation consequence：验证结果失真，02样式漂移。
- 来源：原始。

## 样式契约

### S-1: 注册保持与02完全相同
- 复用：index.html:11-13、2109-2112原link/script注册，href/src只替换query value；02所有DOM/CSS保持逐字。
- 新增CSS：无；新增DOM：无。禁止修改class/style和资源加载顺序。

## 现状审计

index.html当前键`20260907-material-chat`。按值全文 `rg -l '20260907-material-chat' src/test` 实测7文件，即下方白名单；不按经验宣称永远7文件。读取浏览器缓存由这些href/src触发，唯一版本写入是index；7测试读同值literal，不涉及DB/ES/SMTP。

## 实现方案

### T1（I-1/I-2/S-1）

先全文反查旧键，确认仍是白名单；逐字替换为新键。若出现新增命中，不擅自越过10文件限制，先更新计划清单。仅当前子计划允许修改缓存键；02阶段保留旧键便于独立检查。

### T2（I-1/I-2/S-1）

全量JS测试与JDK11构建；浏览器访问隔离生产源码页面，普通刷新验证网络请求的7资源query均新键且成功，保留代码版本和截图。不会因文件名不变而加载旧缓存。

## 变更文件清单

| # | 精确路径 |
|---|---|
| 1 | src/main/resources/static/index.html |
| 2 | src/test/js/overlayAndDialogContrast.test.js |
| 3 | src/test/js/ragWorkbenchRender.test.js |
| 4 | src/test/js/checkRepliesRelocation.test.js |
| 5 | src/test/js/trustReplyWorkbenchSharedMount.test.js |
| 6 | src/test/js/batchSendTaskConsoleVisualFix.test.js |
| 7 | src/test/js/ragKnowledgeBasePage.test.js |
| 8 | src/test/js/manualReplySubjectPrefill.test.js |

## 验收标准

- I-1：7生产引用同新值，7固定值测试仍assert同资源存在；src/main/resources/static/index.html及src/test无旧键。
- I-2：diff只含query version/literal与对应注释测试名；全量JS及Maven test/package成功。不能以跳过测试交付。
- S-1：02CSS SHA/DOM不变，7资源顺序不变；真实浏览器普通刷新加载新键。

```sh
rg -n '20260907-material-chat|20260909-mailbox-refinement' src/main/resources/static/index.html src/test
node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test package
```

## 人工验收清单

### A-1: 缓存版本和总体验收
- 前置条件：隔离环境先打开旧版页面，然后更新为01/02/03产物；开发者工具不勾Disable cache。
- 操作步骤：1 普通刷新；2 查看资源请求；3 打开⋯/管理/译文/材料；4 再看人工回复与工作台。
- 预期结果：7资源均请求20260909-mailbox-refinement且200或正确验证后的304；无旧JS错误；布局符合02；所有原按钮及共享组件保留。不得只用强制清缓存刷新证明版本策略。
- 覆盖：I-1/I-2/S-1、需求及全部保留项。
