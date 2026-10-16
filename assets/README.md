# Launcher artwork

`icon.svg` is the editable wallet artwork; `icon.png` is its 512px export.
The Android foreground lives in `app/src/main/res/drawable/ic_launcher_fg.xml`.
Keep its wallet paths in sync with the SVG when changing the design.

Regenerate the PNG with ImageMagick:

```sh
magick -background none assets/icon.svg assets/icon.png
```

Android 13+ launchers can recolor the foreground through the adaptive icon's
monochrome layer when themed icons are enabled. The regular icon uses
`ic_launcher_bg` from `app/src/main/res/values/colors.xml`.
