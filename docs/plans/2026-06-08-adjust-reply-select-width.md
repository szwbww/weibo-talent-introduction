# Adjust Manual Reply Dropdown Width Implementation Plan

> **For Antigravity:** REQUIRED WORKFLOW: Use `.agent/workflows/execute-plan.md` to execute this plan in single-flow mode.

**Goal:** Adjust the width of the reply mode select dropdown on the expert management page to 110px.

**Architecture:** Add a new CSS rule `#autoReplySelect` with width 110px in styles.css to style the dropdown for desktop layout.

**Tech Stack:** Vanilla HTML/CSS.

---

### Task 1: Update styles.css

**Files:**
- Modify: `src/main/resources/static/styles.css`

**Step 1: Write the minimal implementation**

Modify `src/main/resources/static/styles.css` near line 906:
```css
#indexLevelSelect {
    width: 120px;
    flex: 0 0 120px;
}

#autoReplySelect {
    width: 110px;
    flex: 0 0 110px;
}
```

**Step 2: Verify the change**

Check the changes in the CSS file to ensure syntax correctness.
Run: `git diff src/main/resources/static/styles.css`
Expected: Shows the added `#autoReplySelect` style block.

**Step 3: Commit**

```bash
git add src/main/resources/static/styles.css
git commit -m "style: set fixed width for manual reply dropdown"
```
