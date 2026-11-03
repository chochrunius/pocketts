# AlfredTS Android icons

The original `alfredts-icon-primary.png` is unchanged. `alfredts-sheep-cutout.png`
is the reviewed transparent derivative created with the built-in ImageGen tool.
The reference sheep, white wool, pink nose and black/green armour are retained;
the rounded green border and backdrop are removed so the Android mask supplies
one outer shape. This is an AI-assisted derivative, not a byte-exact cutout.

## Android resources

- `drawable-xxxhdpi/ic_launcher_foreground.webp`: 432 × 432 px = 108 × 108 dp.
  The subject is fitted to a central circle of radius 32.5 dp (65 dp diameter),
  within Android's 66 dp safe circle. Transparent margins are for adaptive masking.
- `drawable/ic_launcher_background.xml`: full-bleed dark green radial gradient.
- `drawable/ic_launcher_monochrome.xml`: simple white sheep-head silhouette with
  transparent eyes/nose on a 108 dp canvas; Android supplies the theme colour.
- `drawable/ic_notification.xml`: white vector sheep on a transparent 24 dp
  canvas; separate scale for small status-bar sizes, no photograph.
- `drawable-xxxhdpi/alfredts_header.webp`: 96 × 96 px = 24 dp, without launcher
  margins. `AppHeader` keeps its existing row, 24 dp slot and 8 dp text spacing.
- Existing `mipmap-anydpi/ic_launcher.xml` and manifest references remain valid.
  minSdk is 29, so pre-26 legacy launcher resources are unnecessary.
  The existing monochrome element is used on supported Android launchers.

The XML foreground was replaced with a raster drawable under the same resource
name: this also keeps Compose painterResource consumers working directly.
Raster assets add about 29 kB before APK compression; source art is not packaged.

## Maintenance and previews

The packaged WebP files are the maintained raster assets. Large original PNGs,
the full-size cutout and the export script remain local and are intentionally
excluded from Git. The source artwork is not needed to build the application.

Optional local mask previews from the committed resources (Pillow/resvg-py are
tooling dependencies only):

```powershell
python -m venv dist/icon-tools
dist/icon-tools/Scripts/python.exe -m pip install Pillow resvg-py
dist/icon-tools/Scripts/python.exe artwork/AlfredTS_icon_assets/preview_android_icons.py
```

ImageGen edit prompt: Use case background-extraction. Create a clean transparent
cutout of exactly the same photorealistic white sheep head and black futuristic
armor with green accents from the supplied primary artwork. Preserve face,
eyes, pink nose, wool, both complete ears, pose and armor. Remove all green
rounded-square border, border glow and green background. Actual transparent
alpha outside sheep and shoulders; centered square canvas; complete ears and
wool, small transparent margin, shoulders taper naturally. No tile, frame,
text or external shadow. Android adaptive foreground; preserve reference.

Resource compilation, 70 app tests and the optimized shrunk build passed.
The user verified the branding and icon on a real Android device and accepted
the implementation. Future changes should recheck launcher masks, themed icons,
header scaling and monochrome notifications.

Reference: https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
