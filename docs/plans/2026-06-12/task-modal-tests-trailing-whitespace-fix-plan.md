# Task Modal JS 测试行尾空白清理计划

> 目标：清理 `taskModalStateMachine.test.js` 中的行尾空白，使 `git diff --check` 通过。
>
> 仅做格式清理。不得修改测试逻辑、生产代码、断言、时序或测试数据。

---

## 一、复验结果

功能验证全部通过：

- `node --test src/test/js/*.test.js`
  - 77 个测试通过
  - 0 失败
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
  - 471 个测试通过
  - 1 个跳过
  - 0 失败
- `node --check src/main/resources/static/app.js`
  - 通过
- `node --check src/main/resources/static/task-modal-runtime.js`
  - 通过

提交门禁失败：

```bash
git diff --cached --check
```

失败原因：新增测试文件存在 19 处 trailing whitespace。

---

## 二、问题文件

文件：

```text
src/test/js/taskModalStateMachine.test.js
```

当前检测到的行：

```text
31
51
62
73
422
425
451
461
493
589
613
641
670
675
705
887
907
912
937
```

这些行主要是仅包含空格的空白行。

---

## 三、修复要求

1. 删除上述行末尾的空格。
2. 空白行必须为空行，不得保留缩进空格。
3. 不得调整测试代码顺序。
4. 不得修改测试名称。
5. 不得修改断言。
6. 不得修改 watcher 实现。
7. 不得对文件做无关格式化。
8. 不得修改工作树中的无关文件：
   - `src/main/resources/application.yml`
   - `src/main/kotlin/com/weibo/talentintroduction/discovery/service/EuropePmcDataSource.kt`
   - 已删除的旧 `docs/plans/*` 文件

推荐使用项目格式化工具或仅针对行尾空白的机械清理。不要手工重排大段代码。

---

## 四、验证命令

先验证行尾空白：

```bash
git diff --check
```

JS 测试：

```bash
node --test src/test/js/*.test.js
```

JS 语法：

```bash
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js
```

完整 Maven：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

暂存目标功能文件后再次验证：

```bash
git diff --cached --check
```

必须确保工作树和暂存区两种检查均无输出、退出码为 0。

---

## 五、完成标准

- [ ] `taskModalStateMachine.test.js` 的 19 处行尾空白已删除。
- [ ] 文件无其他逻辑变化。
- [ ] `git diff --check` 通过。
- [ ] `git diff --cached --check` 通过。
- [ ] JS 77 个测试全部通过。
- [ ] Maven 471 个测试通过、1 个跳过。
- [ ] 两份 JS 语法检查通过。
- [ ] 无关配置、数据源文件和旧 plan 删除未被暂存。
- [ ] 修复后等待下一轮复验，不直接提交。

