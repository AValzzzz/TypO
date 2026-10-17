# TypO

## Installation

Download the file for your platform from the [latest release](https://github.com/AValzzzz/TypO/releases/latest).
Every installer below also adds TypO to the **Open with** menu for `.docx` files.

| Platform | File | How to install |
|---|---|---|
| Windows | `TypO-windows.msi` | Double-click it and follow the installer. Adds Start menu and desktop shortcuts. |
| Windows (portable) | `TypO-windows.zip` | Extract it anywhere and run `TypO\TypO.exe`. No installation, but no **Open with** either. |
| macOS | `TypO-mac.dmg` | Open it and drag **TypO** into **Applications**. |
| Debian / Ubuntu | `TypO-linux.deb` | `sudo apt install ./TypO-linux.deb` |
| Arch Linux | `TypO-arch-x86_64.pkg.tar.zst` | `sudo pacman -U TypO-arch-x86_64.pkg.tar.zst` |
| Other Linux | `TypO-linux.tar.gz` | Extract it, then run `./install.sh` inside the `TypO` folder to add the menu entry and **Open with** for your user (`./install.sh --uninstall` removes them). Run it again if you move the folder. |
| Linux (Java 25+) | `TypO.jar` | `java -jar TypO.jar` |

The Windows and macOS builds are not signed, so the first launch shows a warning:

- **Windows:** in the SmartScreen dialog, click **More info → Run anyway**.
- **macOS:** right-click TypO in Applications and choose **Open**, or allow it in
  **System Settings → Privacy & Security → Open Anyway**.

## Themes

Pick the app theme in **Settings → App theme**. TypO ships with *Rosé Pine Dawn*, *Rosé Pine Moon* and *Nord Snow*.

To add your own, click **Import…** next to the theme list and choose a `.json` file. It is copied to
`~/.typo/themes/`, so it stays available after a restart (importing a theme with the same name replaces it).

Only five colors are required; the others are derived when missing:

```json
{
  "name": "My theme",
  "background": "#faf4ed",
  "surface": "#fffaf3",
  "overlay": "#f2e9e1",
  "text": "#575279",
  "accent": "#907aa9"
}
```

Optional keys: `muted`, `subtle`, `highlightLow`, `highlightMed`, `highlightHigh`, `success`, `error`,
`warning`, `onAccent` (text on accent-colored buttons), `selection` (text selection, defaults to the accent at 35 %
opacity), `card`, `backdropTop`, `backdropBottom` (the gradient behind the pages) and `shadow`.
Colors accept any CSS notation (`#rrggbb`, `#rrggbbaa`, `rgb(...)`, names). The built-in themes in
`src/main/resources/com/example/themes/` are complete examples.