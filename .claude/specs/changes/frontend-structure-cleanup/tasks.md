# Tasks — frontend-structure-cleanup

- [x] T1 Global: MainActivity NoActionBar + drop setupActionBarWithNavController; bottom nav unlabeled
- [x] T2 Home: streak in the header, timer config in the card, drop toolbar/menu provider; studied label; mode chip without dashes; block-mode states; play-button shadow
- [x] T3 Home: task frame + reserved focus column (alignment)
- [x] T4 Progress: drop "Charts" + card title; tabs above with short labels; TimeRange.label(Context)
- [x] T5 Calendar: drop "Add for this day"; FAB → selected day; square today marker + fill on selection; empty state; Month/Week segmented
- [x] T6 Projects: drop header; overdue only if not finished (list/detail/progress); deadline date; back button in detail
- [x] T7 Strings: extract literals; values-es/strings.xml complete
- [x] T8 Verify: /check (assembleDebug + lint) + ui-tester on device (4 tabs + project detail + Month/Week switch)
  - [x] installDebug OK; lintDebug: 0 errors (MissingQuantity es → fixed with `many`)
  - [x] Visual check of the 4 tabs + project detail via screenshots on the device
  - [x] ui-tester (flows + crash scan): 6/6 PASS, no FATAL EXCEPTION
