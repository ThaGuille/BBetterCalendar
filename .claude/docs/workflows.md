# Workflows / Recipes

Step-by-step recipes for the common changes in this codebase. Each one ends with a "verify" step — don't skip it.

> Cross-refs: [`architectural_patterns.md`](architectural_patterns.md), [`style_guide.md`](style_guide.md), [`common_errors.md`](common_errors.md).

---

## 1. Build & run the app

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.bbettercalendar/.configuration.SplashActivity
```

If a build hangs: `.\gradlew.bat --stop` then retry. Full table of commands in [`windows_commands.md`](windows_commands.md).

---

## 2. Add a new popup (DialogFragment)

1. **Constant** — add `FOO_POPUP = N;` to [`PopupHelper.java`](../../app/src/main/java/com/example/bbettercalendar/popups/PopupHelper.java).
2. **Layout** — create `res/layout/popup_foo.xml`. Wrap in a card surface (`bg_card`) and use `TextAppearance.BBetter.*` for text. For width: `android:layout_width="match_parent"` inside a `RoundedDialog`-themed dialog.
3. **DialogFragment** — extend `DialogFragment`, override `onCreateView` to inflate the layout. In `onCreate` (or `onStart`), apply the theme via the `RoundedDialog` style:
   ```java
   setStyle(STYLE_NORMAL, R.style.RoundedDialog);
   ```
4. **Listener** — set the host via `setOnPopupListener(OnPopupListener<T>)` (or a dedicated listener interface if the result type is non-trivial — see `OnNotificationsPopupListener` for the precedent).
5. **Result** — call `listener.OnClosePopup(PopupHelper.FOO_POPUP, result)` in the dialog's save handler, then `dismiss()`.
6. **Host** — in the fragment/activity that opens it:
   - implement `OnPopupListener<ResultType>`
   - construct as a field: `FooPopup popup = new FooPopup();`
   - on open: `popup.setOnPopupListener(this); popup.show(getChildFragmentManager(), "foo");`
   - handle in `OnClosePopup(int type, ResultType result)`.

**Verify**: open the popup, save, confirm `OnClosePopup` fires with the expected value.

---

## 3. Add a field to `CalendarEntry`

1. **Entity** — add the field + getter/setter in [`CalendarEntry.java`](../../app/src/main/java/com/example/bbettercalendar/calendarEntries/CalendarEntry.java).
2. **Type converter** — if the type is not a primitive / `String` / `int` / `long`, add a `@TypeConverter` pair to [`DBConverter.java`](../../app/src/main/java/com/example/bbettercalendar/database/DBConverter.java) (Gson-encode it).
3. **Builder** — add a corresponding setter to `CalendarEntry.EventBuilder`, returning `this` for chaining.
4. **DB version** — bump `version` in `AppDatabase` `@Database(version = N)`. Because `fallbackToDestructiveMigration()` is on, **the DB will be wiped**. If this matters, write a real `Migration` and remove the fallback.
5. **AddEventActivity** — surface the new field in the create form, write it through the builder before `build()`.
6. **DAO** — if you need to query by it, add a `@Query` to [`CalendarEntryDAO.java`](../../app/src/main/java/com/example/bbettercalendar/calendarEntries/CalendarEntryDAO.java).
7. **UI mapping** — if the field is shown in the calendar, update `CalendarItem` + `CalendarItemMapper` (and `ColorResolver` if it affects colour).

**Verify**: create an entry with the new field; reopen the app; reload the day's events; field still there.

---

## 4. Add a new screen (fragment under `ui/`)

1. **Package** — `ui/<feature>/` with `<Feature>Fragment.java`, `<Feature>ViewModel.java`.
2. **Layout** — `res/layout/fragment_<feature>.xml`. Start from `fragment_home.xml` as a template (scrollable column of cards).
3. **Annotate** the fragment with `@AndroidEntryPoint`; inject Hilt deps via fields.
4. **Nav graph** — add a `<fragment>` to [`res/navigation/`](../../app/src/main/res/navigation/) and a menu entry in `res/menu/bottom_nav_menu.xml` (or wire as a destination of an existing fragment).
5. **String + icon** — add `title_<feature>` to `strings.xml` and an `ic_<feature>_v2_24.xml` drawable, using the `bb_*` palette.
6. **ViewModel** — extend `ViewModel` (or `AndroidViewModel` if you need a `Context`). Expose `LiveData` getters over private `MutableLiveData` fields. Background work via an `ExecutorService` field, post via `postValue()`.

**Verify**: launch the app, navigate to the tab, confirm initial render + ViewModel observers fire.

---

## 5. Add a new colour or design token

1. **Decide**: is this a *new semantic concept* (e.g. "warning") or just a tint variant of an existing one? New concept → add a `bb_*` token. Variant → reuse the existing token (e.g. apply alpha via `colorControlActivated`).
2. **Token** — add to `bb_*` block at the **top** of [`colors.xml`](../../app/src/main/res/values/colors.xml). Name semantically (`bb_warning`, not `bb_yellow`).
3. **Theme wiring** — if it should override a Material role (`colorTertiary`, etc.), add an `<item>` to `Theme.BBetter` in [`themes.xml`](../../app/src/main/res/values/themes.xml).
4. **Drawable / chip** — if there's a recurring shape (chip background, indicator), add a matching `bg_<name>.xml`.
5. **Don't** touch the legacy palette section.

**Verify**: render in the Android Studio preview against `Theme.BBetter`; spot-check on a real device for contrast.

---

## 6. Add a new dimension or text style

- A new spacing value not on the 4 / 8 / 16 / 25 dp scale → ask whether the design really needs it before adding. If yes, add a `spacing_<name>` to `dimens.xml`.
- A new text role → add `TextAppearance.BBetter.<Role>` to `styles_typography.xml`, inheriting from `TextAppearance.BBetter` (so the font family + colour are inherited).
- Reference via `android:textAppearance` / `android:textSize="@dimen/text_<role>"`, never inline.

---

## 7. Schema change with data preservation (no destructive wipe)

By default `AppDatabase` uses `fallbackToDestructiveMigration()`. If user data must survive:

1. Remove `fallbackToDestructiveMigration()` from the `Room.databaseBuilder` chain in [`AppDatabase.java`](../../app/src/main/java/com/example/bbettercalendar/database/AppDatabase.java).
2. Bump `@Database(version = N+1)`.
3. Write a `Migration(N, N+1)` (use the existing migration stubs in `DBMigration` as templates).
4. Add the migration via `.addMigrations(MIGRATION_N_TO_NPLUS1)`.
5. Bench the migration on a populated DB (export `/data/data/com.example.bbettercalendar/databases/...` via `adb` first).

**Don't** flip the migration mode "just for this PR" — either you commit to real migrations going forward, or you keep destructive and accept the wipe.

---

## 8. Plan documents

When you produce a plan (an `ExitPlanMode` payload, or a written design proposal), save it to:

```
.claude/plans/<kebab-slug>.md
```

- Slug: short, descriptive, kebab-case (`add-reminders-popup.md`).
- Body: paste the plan as Markdown. Include a short "Status" line at the top (`Status: proposed | in progress | merged | abandoned`).
- Link from related code comments or PR descriptions when applicable.
- Old plans are *not* deleted — they're our paper trail. Update `Status:` and leave them.

Existing examples in [`.claude/plans/`](../plans/):
- `greedy-spinning-hickey.md`
- `wondrous-gliding-fountain.md`
- `the-month-and-week-wiggly-teapot.md`
- `i-like-everything-but-breezy-lemon.md`

---

## 9. Vendoring an upstream library

The Alamkanak Week-View is vendored under [`ui/calendar/weekview/`](../../app/src/main/java/com/example/bbettercalendar/ui/calendar/weekview/) with its `LICENSE.txt`. If you ever vendor another:

1. Drop the sources under `ui/<feature>/<libname>/`.
2. Copy the LICENSE alongside as `LICENSE.txt`.
3. Copy any `res/values/attrs.xml` to `res/values/<libname>_attrs.xml` (prefix to avoid name collisions).
4. Document the source commit/version in a `// vendored from <repo>@<commit>` comment at the top of each ported file.
5. Add a note to [`architecture.md`](architecture.md) so future readers know which folders are vendored.

---

## 10. Quick verify checklist before saying "done"

- [ ] `.\gradlew.bat assembleDebug` succeeds.
- [ ] If UI changed: built & opened in an emulator/device — *not* just preview.
- [ ] No new references to legacy palette / themes (see [`style_guide.md`](style_guide.md) §1).
- [ ] No new DAO calls from the main thread.
- [ ] No new `setValue()` from a background thread.
- [ ] Strings & dimens externalised (no inline literals).
- [ ] If schema changed, version was bumped *and* destructive-migration impact acknowledged.
