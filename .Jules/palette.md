## 2026-07-02 - [Compose Radio Button Accessibility]
**Learning:** [Using `Modifier.clickable` on a parent row with a nested `RadioButton` that has its own `onClick` creates confusing double touch targets and roles for screen readers. The parent should have the selectable semantic.]
**Action:** [Use `Modifier.selectable(role = Role.RadioButton)` on the parent `Row` and set the nested `RadioButton`'s `onClick` to `null`. This makes the entire row a single, properly announced selectable radio button target for accessibility tools.]
