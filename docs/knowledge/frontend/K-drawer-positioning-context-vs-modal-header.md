---
id: K-drawer-positioning-context-vs-modal-header
domain: frontend
created: 2026-08-16
last_used: 2026-08-16
hit_count: 0
source: create-p:batch-console-log-drawer
severity: P1
---

经验：`position: absolute; top: 0; bottom: 0` 的侧边抽屉，若定位上下文是**整个弹窗**
（`.modal-content` 上有 `position: relative`），它会连弹窗 header 一起盖住，
把弹窗自己的关闭按钮埋在下面。当抽屉也有一个 × 时，两个 × 几乎同坐标，
用户点「关弹窗」实际点到「关抽屉」，弹窗关不掉。

实测（批量邮件任务控制台）：`.batch-send-close-btn` rect `[1233,205,28,28]`，
`#batchLogDrawerCloseBtn` rect `[1239,205,28,28]`，`elementFromPoint` 打在前者中心返回后者。

正确做法：给「header/tabs 之下的内容区」单独包一层定位容器，抽屉放进去：

```css
.<modal>-body {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}
```

包装层要同时接管原先 panel 的 flex 布局（`flex: 1; min-height: 0`），否则弹窗高度塌陷。

排查手法：对弹窗自身的关闭按钮取 rect，用 `document.elementFromPoint(cx, cy)`
看返回的是不是它本人 —— 返回别的元素即被遮挡。这条对任何「浮层 + 宿主各有一个关闭按钮」
的组合都适用。
