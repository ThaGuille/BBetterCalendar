---
name: save-plan
description: Save a design / implementation plan to `.claude/plans/<slug>.md` so it survives the conversation. Use after producing a plan (ExitPlanMode or a written design proposal), or when the user says "save the plan" / "commit this plan to disk".
---

# save-plan

Write the current plan to disk so future sessions can find it.

## File layout

```
.claude/plans/<kebab-slug>.md
```

- **Slug**: short, descriptive, kebab-case. Reuse an existing slug if updating that plan; otherwise pick a new one. Avoid the cutesy auto-generated names (`greedy-spinning-hickey`) for new plans — pick something descriptive (`add-reminder-popup`, `room-real-migrations`).
- **One file per plan.** Don't append unrelated plans to an existing file.

## Required header

Start the file with:

```markdown
# <Plan title>

**Status:** proposed | in progress | merged | abandoned
**Created:** YYYY-MM-DD
**Last updated:** YYYY-MM-DD

## Summary
<one paragraph: what this plan changes and why>

## Plan
<numbered steps, file paths with line refs where useful>

## Open questions
<bullets — what still needs an answer>

## Verify
<how the user / future Claude will know it worked>
```

## Steps

1. Determine the slug.
   - If updating: ask the user which existing plan in `.claude/plans/` to update (don't guess). Bump `Last updated:`.
   - If new: derive from the topic. Confirm with the user if non-obvious.
2. Convert today's date in the user's environment. The CLAUDE.md `currentDate` (or system clock if absent) is authoritative.
3. Write the file with the **Write** tool. Don't run `git add`/`commit` unless the user asks — these files are intentionally uncommitted history.
4. Link from related code (only if useful) via a `// see .claude/plans/<slug>.md` comment.

## Don't

- Don't delete or overwrite an existing plan with a different topic — pick a new slug instead.
- Don't strip the status header even on update.
- Don't write the plan inline into CLAUDE.md or the README — `.claude/plans/` is the home.

## Cross-refs

[`.claude/docs/workflows.md`](../../docs/workflows.md) §8 documents the plan convention.
