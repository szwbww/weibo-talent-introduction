# Child 04 — Baseline Command Evidence

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master`
- Branch: `fast/2026-09-29-discovery-repair-00-master`
- Baseline revision: `b9ec45b008f5c4f965679b99575db0ea8fe50731` (plan-seed + ledger init; product code byte-identical to master base `1cd59e31164d11e962203e31c9f61310f2bc5912`)
- Runner: controller (fast-p setup)
- Run at: 2026-09-29 ~12:55 (+08:00). Raw log: `/tmp/fastp-baseline-c.log` (controller host, not committed)

## CMD1 — full JS suite at master-base product code

```
node --test src/test/js/*.test.js
```

exit 0: **tests 1203 / pass 1203 / fail 0 / skipped 0**, suites 236, duration ≈ 3.35 s.

## CMD2 — index cache keys at master-base product code

```
grep -o '?v=[A-Za-z0-9._-]*' src/main/resources/static/index.html | sort | uniq -c
```

Result: 11 occurrences of the single key `20260929-mailbox-replied`. No test file pins the current `?v=` key (`grep -rl 'v=2026' src/test/js/` → no hits); `src/test/js/taskActivityCenter.test.js` only asserts historical keys (`20260920-manual-material-upload`, `20260922-task-activity-center`, `20260903-bounce-warning`) are absent.

## Notes

- Child 04's four-file command (`discoveryScheduleSetting.test.js` + three existing VM tests) has one new file absent at baseline; the three existing files are part of the 1203 green tests above.
- No `mvn` was run here; the controller runs `mvn clean package` once after the last child.
