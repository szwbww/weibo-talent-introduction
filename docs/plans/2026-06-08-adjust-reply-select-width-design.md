# Design: Adjust Manual Reply Dropdown Width

## Problem Statement
On the expert management page, under the "Status and Hierarchy" row, the dropdown for switching reply mode (auto vs. manual reply) does not have a fixed width, causing it to stretch and look too long compared to the adjacent "Status" and "Hierarchy" dropdowns.

## Proposed Changes

### Stylesheets (`src/main/resources/static/styles.css`)
We will add a specific styling rule for `#autoReplySelect` to set a fixed width of 110px. This aligns with the existing widths for the other selects:
- `#operatorStatusSelect` (140px)
- `#indexLevelSelect` (120px)
- `#autoReplySelect` (110px)

```css
#autoReplySelect {
    width: 110px;
    flex: 0 0 110px;
}
```

This ensures the dropdown behaves correctly in the flexbox layout, matching the styles of the sibling elements. Responsive behavior (stretching to 100% on smaller screens) is already handled by the existing `@media (max-width: 768px)` block targeting all `select` elements within `.contact-head-status-row`.
