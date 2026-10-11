# Per-app icon picker

Choose the global icon pack in **Settings → Icon pack**. To customize one app,
open its app info sheet and tap the icon pack preference or its picker button.
The picker lists every installed icon pack, regardless of whether it has an
automatic mapping for that app. Switching packs only changes the preview;
tap an icon to save the override.

The picker shows suggestions for the app first, then categories from the pack's
`drawable.xml`, preserving their order. Packs without that manifest fall back to
icons declared in `appfilter.xml`. Missing drawable resources are excluded.

- **Use this pack’s app icon** uses the pack's automatic mapping for the app,
  including dynamic clock/calendar support already provided by Murine.
- **Default (global)** removes the per-app override and follows the global pack.
- **Original app icon** uses the system icon even when a global pack is selected.

## Installing alongside the regular app

The debug build is named **Murine Test**, uses package `app.murinelauncher.debug`,
and shows version suffix `-icon-test`. It can be installed alongside
`app.murinelauncher` and has separate launcher settings and data. Build the
`assembleAospWithoutQuickstepDebug` variant and install its APK. To use it as the
home screen, select **Murine Test** in Android's default home app settings.

## Automated verification

Run `testAospWithoutQuickstepDebugUnitTest --tests
app.murinelauncher.icons.IconPackCategoriesTest` with the project's Gradle version.
The tests cover category order, uncategorized entries, empty categories, duplicate
and missing drawable names, blank titles, empty manifests, and malformed XML.

## Device checks

1. Install two icon packs and choose one globally. Open an app's icon picker
   without first setting a per-app pack. Verify the global pack opens initially.
2. Switch to the other pack, search, choose an icon, and verify both the workspace
   and app drawer update. Restart the launcher and verify the choice persists.
3. For an app with no automatic mapping in a pack, verify the pack is still listed
   and any available drawable can be selected. The automatic mapping action should
   be disabled for that app.
4. Choose **Original app icon**, change the global pack, and verify the original
   icon persists. Choose **Default (global)** and verify it follows the global pack.
5. Choose the automatic mapping action for an app with a mapping. Verify it uses
   that pack and retains the existing dynamic clock/calendar behavior.
6. Switch packs quickly, dismiss during loading, and rotate while the picker is
   open. Verify old loads do not replace the current pack and selection still works.
7. Search for a nonexistent name. Verify the empty search message appears. Test a
   pack without `drawable.xml`, a pack with invalid entries, and no installed packs.
8. Verify **Clear custom icons** in global settings restores global behavior.

Device checks require an Android device or emulator and real icon packs.
