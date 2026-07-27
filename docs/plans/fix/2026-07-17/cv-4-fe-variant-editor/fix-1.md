# CV-4 复验修复计划 fix-1：同步删除旧 subject variant 测试依赖

## 结论

本轮 `fix-v` 复验未发现 CV-4 主实现路径偏离：

- QA 规则编辑器与回复片段编辑器已新增 `content-variants` 编辑区。
- 旧 `subjectVariants` / `variantGroup` / `subject-variant-*` 相关 DOM、JS、CSS grep 全仓零命中。
- QA 规则与回复片段保存 payload 已发送 `variants`，模板保存 payload 不再发送 `subjectVariants`。
- CUSTOM 已注册到下拉、`replySnippetTypeLabels`、`replySnippetTypes`，且 CUSTOM 面板隐藏默认列与「设默认」按钮。
- `node --check src/main/resources/static/app.js` 通过。
- `git diff --check` 与 `git diff --cached --check` 通过。

但复验不能放行：现有 Node 测试仍硬依赖原计划要求删除的旧函数，导致测试套件失败。

## P1-1：composeTemplatePreview.test.js 仍提取已删除函数

### 现象

执行：

```bash
node --test src/test/js/*.test.js
```

结果：

- Node tests：198 total，194 pass，4 fail。
- 4 个失败均来自 `src/test/js/composeTemplatePreview.test.js`。
- 失败原因一致：`Could not find collectComposeTemplatePreviewSubjectVariants in app.js`。

### 证据

`docs/plans/2026-07-09/cv-4-fe-variant-editor.md` S-2 明确要求删除：

- `collectSubjectVariants`
- `parseSubjectVariantsJson`
- `validateSubjectVariantInputs`
- 模板编辑器「主题变体」相关旧 UI

当前生产代码已完成删除，且全仓 grep：

```bash
rg "subject-variant|subjectVariantsContainer|addSubjectVariantBtn|variantGroupOptions|renderSubjectVariantRows|collectSubjectVariants|parseSubjectVariantsJson|validateSubjectVariantInputs|subjectVariants|variantGroup" src/main/resources/static src/test/js
```

除测试失败前的目标项外，旧生产引用为零。

但 `src/test/js/composeTemplatePreview.test.js:73-89` 的 sandbox 函数提取数组仍包含：

```js
"collectComposeTemplatePreviewSubjectVariants"
```

该函数已按 CV-4 下线，因此测试启动阶段直接抛错。

### 影响

这是测试与计划删除项不同步，不是生产行为缺陷。但它阻断 `node --test src/test/js/*.test.js`，也会阻断 Maven 生命周期中的 node-test 阶段，因此属于复验 P1。

## 修复范围

只改测试：

- `src/test/js/composeTemplatePreview.test.js`

不改生产代码。

## 修复步骤

1. 从 `createSandbox` 的函数提取数组中删除 `collectComposeTemplatePreviewSubjectVariants`。
2. 确认 `refreshComposeTemplatePreview` 当前不再调用该函数；若测试 sandbox 里有与 subject variants 相关的 mock DOM，也一并删除。
3. 不要在生产代码中恢复任何 `subjectVariants` / `subject-variant-*` / `variantGroup` 旧路径。

## 验证命令

```bash
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
git diff --cached --check
rg "subject-variant|subjectVariantsContainer|addSubjectVariantBtn|variantGroupOptions|renderSubjectVariantRows|collectSubjectVariants|parseSubjectVariantsJson|validateSubjectVariantInputs|subjectVariants|variantGroup" src/main/resources/static src/test/js
```

## 放行标准

- Node tests 全绿。
- Maven 全量测试全绿。
- 旧 subject variant / variantGroup grep 在生产静态资源中保持零命中。
- 不恢复任何 CV-4 明确下线的旧 UI、旧字段或旧函数。
