## 2026-06-30 - [Add Clear Affordance to Multiline Input]
**Learning:** For transient multiline inputs (like a chat or translation text box), lacking a clear button forces users to manually backspace or select-all-delete, which is a poor UX for frequent interactions.
**Action:** Always add a clear trailing icon button (with an accessible `contentDescription`) that appears when input is not empty to multiline text fields used for transient communication.
