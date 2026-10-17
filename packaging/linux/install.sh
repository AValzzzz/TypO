#!/bin/sh
set -e

DIR=$(cd "$(dirname "$0")" && pwd)
APPS="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
DESKTOP="$APPS/typo.desktop"

if [ "$1" = "--uninstall" ]; then
    rm -f "$DESKTOP"
    update-desktop-database "$APPS" 2>/dev/null || true
    echo "TypO unregistered."
    exit 0
fi

mkdir -p "$APPS"
cat > "$DESKTOP" <<DESKTOP_ENTRY
[Desktop Entry]
Type=Application
Name=TypO
Exec="$DIR/bin/TypO" %F
Icon=$DIR/typo.png
Terminal=false
Categories=Office;TextEditor;
MimeType=application/vnd.openxmlformats-officedocument.wordprocessingml.document;
DESKTOP_ENTRY

update-desktop-database "$APPS" 2>/dev/null || true
echo "TypO registered. If you move this folder, run ./install.sh again."
