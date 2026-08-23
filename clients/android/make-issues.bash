#!/usr/bin/env bash

set -euo pipefail

echo "Creating Ado usability issues in:"
gh repo view --json nameWithOwner --jq '.nameWithOwner'
echo

gh issue create \
  --title "Add explicit completion controls for tasks and subtasks" \
  --body-file - <<'EOF'
## Problem

Completion behavior is currently inconsistent and partly gesture-only:

- Task row: tap opens, long-press completes.
- Simple subtask row: tap completes.
- Full subtask row: tap does nothing, long-press completes.

Long-press is difficult to discover and the behavior differs depending on context.

## Proposed improvement

Add a visible checkbox/check control to task and subtask rows.

Expected interaction:

- Tap the completion control -> toggle complete/incomplete.
- Tap task text/row -> open the task.
- Tap a URL -> open the URL without triggering completion or navigation.
- Existing Edit/Delete behavior remains unchanged.
- Existing finished-section behavior remains unchanged.
- Long-press completion may remain temporarily for backward compatibility.

Example:

    [ ] Milk
    [ ] Coffee
    [x] Bread

For tasks:

    [ ] Market                       8 open / 3 done

## Notes

This should be a UI/UX change only. Avoid data-model or database changes unless genuinely required.

Pay particular attention to event handling so link clicks, row clicks, and completion clicks do not interfere with each other.
EOF

gh issue create \
  --title "Reduce developer-style metadata in primary screens" \
  --body-file - <<'EOF'
## Problem

Some primary screens expose metadata in a way that feels more like internal/debug information than user-facing UI.

Examples include:

    list: Market
    Status: todo
    Created: 2026-...

Project headers may also show:

    list: Normal

alongside list configuration controls.

## Proposed improvement

Review metadata shown on Project and Task detail screens.

Goals:

- Hide `Normal` list type when it carries no useful information.
- Show special/generated list types only when useful.
- Replace raw/internal status text with user-friendly presentation where needed.
- Make dates human-readable.
- Move uncommon list-type/configuration controls into Edit or a More/settings action if appropriate.
- Keep useful information without cluttering the main task workflow.

Do not change the underlying data model as part of this issue.
EOF

gh issue create \
  --title "Use contextual project and task names in top app bars" \
  --body-file - <<'EOF'
## Problem

Detail screens use generic top-bar titles such as:

- `Tasks`
- `Subtasks`

When the page scrolls, the user can lose context about which project or task they are viewing.

## Proposed improvement

Use the current entity name in the top app bar.

Examples:

- Project detail top bar -> project name, such as `Home`
- Task detail top bar -> task name, such as `Market`

The body header can then avoid unnecessarily repeating the same title and instead focus on description, status, counts, and actions.

## Requirements

- Handle long names gracefully with normal Compose ellipsis behavior.
- Preserve navigation/back behavior.
- Do not introduce database/model changes.
EOF

gh issue create \
  --title "Improve rapid entry of checklist and market items" \
  --body-file - <<'EOF'
## Problem

Adding several ad-hoc items requires repeated dialog interaction:

Add -> type -> Save -> reopen Add -> type -> Save

Bulk add helps with planned entry, but quick interactive entry should be faster, especially for Market and checklist-style tasks.

## Proposed improvement

Improve the single-item entry flow.

Potential behavior:

- Autofocus the text field when Add opens.
- Bring up the keyboard immediately.
- Support a `Save + another` action or equivalent rapid-entry workflow.
- Keep the existing normal Save action.
- Make keyboard IME actions useful where appropriate.

## Constraints

Keep the workflow simple and Android-native.

Do not remove Bulk Add.

This issue should focus on interaction speed rather than redesigning the entire task editor.
EOF

gh issue create \
  --title "Reduce action clutter in full subtask rows" \
  --body-file - <<'EOF'
## Problem

Full subtask rows can expose several text actions on every row:

    Up  Down  Edit  Delete

This creates visual clutter and provides relatively small touch targets for infrequent actions.

## Proposed improvement

Simplify full subtask rows.

Consider:

- Keep frequent actions directly accessible.
- Move infrequent actions such as Edit/Delete into a More/overflow menu.
- Review the best UI for reordering separately.
- Use normal Material touch-target sizing.
- Preserve all current functionality.

Do not combine this issue with a broader redesign of completion behavior unless necessary. It should be possible to implement independently after explicit completion controls are available.
EOF

gh issue create \
  --title "Persist Simple vs Full view preference" \
  --body-file - <<'EOF'
## Problem

The Simple/Full view choice is currently local UI state and can reset as screens are recreated.

The choice behaves more like a user preference than temporary screen state.

## Proposed improvement

Persist the user's Simple/Full view preference.

Questions to resolve during implementation:

- Should one preference apply globally?
- Should Projects, Tasks, and Subtasks each remember their own preference?
- Should the preference use the existing DataStore settings mechanism?

Prefer the smallest predictable behavior.

Do not introduce Room storage for a UI preference.
EOF

gh issue create \
  --title "Review and potentially remove custom horizontal swipe-to-back gesture" \
  --body-file - <<'EOF'
## Problem

AdoScaffold currently interprets a leftward horizontal swipe across screen content as Back.

This is custom behavior and may conflict with scrolling, gestures inside content, or normal Android navigation expectations.

## Proposed work

Review the custom swipe-to-back implementation and decide whether it should:

- remain as-is,
- be constrained to a smaller gesture region,
- or be removed in favor of standard Android back behavior.

## Important

This issue should begin with UX/behavior review rather than immediately removing the gesture.

Check interaction with:

- scrollable lists,
- clickable links,
- row gestures,
- dialogs,
- Android system back gestures.

Document the chosen behavior before making a substantial implementation change.
EOF

gh issue create \
  --title "Improve empty states throughout the app" \
  --body-file - <<'EOF'
## Problem

Empty project/task/subtask lists can provide little guidance about what the user can do next.

## Proposed improvement

Review empty states throughout the app and provide concise contextual guidance.

Examples:

- Project with no tasks
- Task with no subtasks
- No completed items
- No templates

Where useful, include a direct action such as Add Task or Add Item.

Keep empty states compact. Avoid large onboarding-style screens.
EOF

gh issue create \
  --title "Use transient feedback for temporary UI messages" \
  --body-file - <<'EOF'
## Problem

Some temporary operation results may remain visible as persistent banners/messages even though the information is transient.

## Proposed improvement

Review operation feedback such as:

- save/import/export results,
- link-open failures,
- temporary success messages,
- recoverable errors.

Use Snackbar or another appropriate transient Material mechanism when the information does not need to remain permanently on screen.

Persistent errors or information that requires user action should remain visible when appropriate.

This should be a targeted consistency pass, not a rewrite of application state handling.
EOF

gh issue create \
  --title "Display user-friendly task dates" \
  --body-file - <<'EOF'
## Problem

Created/finished timestamps are useful but raw timestamp-style formatting is harder to scan.

## Proposed improvement

Present dates in a concise, human-readable local format.

Examples might include:

    Created Aug 12, 2026
    Finished Aug 13, 2026

or another format consistent with the user's Android locale.

## Requirements

- Respect the device locale where practical.
- Preserve the underlying timestamps exactly.
- Avoid changing import/export formats.
- Do not perform database migrations solely for display formatting.
EOF

gh issue create \
  --title "Evaluate search for long project and task lists" \
  --body-file - <<'EOF'
## Problem

As Ado accumulates projects, tasks, templates, and checklist items, navigating long lists may become cumbersome.

## Proposed work

Evaluate where lightweight search would materially improve navigation.

Potential targets:

- projects,
- tasks within a project,
- templates,
- possibly large subtask/checklist lists.

This issue is initially for UX/scoping.

Avoid implementing global full-text search or changing the database schema unless simpler in-memory filtering is demonstrably insufficient.
EOF

gh issue create \
  --title "Review touch target sizes for row actions" \
  --body-file - <<'EOF'
## Problem

Some row actions are rendered as small clickable text controls.

These can be difficult to tap reliably and may not meet normal Material touch-target expectations.

## Proposed improvement

Audit interactive controls in:

- task rows,
- subtask rows,
- project headers,
- bottom action areas,
- inline Edit/Delete/Up/Down actions.

Prefer Material buttons, icon buttons, menus, or appropriately padded clickable regions.

Do not increase visual clutter merely to increase the touch target. Invisible padding around controls is acceptable where appropriate.
EOF

echo
echo "Done. Current open issues:"
gh issue list --state open
