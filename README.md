# TypO

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