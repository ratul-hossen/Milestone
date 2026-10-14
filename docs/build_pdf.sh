#!/usr/bin/env bash
# Builds docs/Milestone_Documentation_BN.pdf from the Bangla Markdown.
# Markdown -> HTML (pandoc, MathML equations) -> PDF (headless Chrome, which shapes Bangla text correctly).
# Fonts: Times New Roman for English (falls back to Liberation Serif) + Noto Serif Bengali for Bangla.
#   To get real Times New Roman on Ubuntu: sudo apt install ttf-mscorefonts-installer, then re-run this script.
set -euo pipefail
cd "$(dirname "$0")"
SRC=Milestone_Documentation_BN.md
HTML=$(mktemp --suffix=.html -p .)
trap 'rm -f "$HTML"' EXIT

pandoc "$SRC" -f markdown-implicit_figures -t html5 --standalone --mathml \
  --css ieee.css --metadata pagetitle="Milestone Documentation" -o "$HTML"

# Wrap everything after the title block in a two-column container (IEEE layout).
python3 - "$HTML" <<'EOF'
import re, sys
p = sys.argv[1]; s = open(p, encoding="utf-8").read()
s = s.replace("</header>", "</header>\n<div class=\"ieee-columns\">", 1)
s = s.replace("</body>", "</div>\n</body>", 1)
# pandoc adds default inline styles that fight the IEEE look; drop them (ieee.css is linked instead).
s = re.sub(r"<style>.*?</style>", "", s, flags=re.S)
# IEEE captions: "TABLE N: ..." goes INSIDE its table (above it), and "Fig. N: ..." is kept together with its image,
# so a caption can never be stranded on another column or page.
s = re.sub(r"<p><strong>(TABLE [IVX]+:.*?)</strong></p>\s*<table>", r"<table>\n<caption>\1</caption>", s, flags=re.S)
s = re.sub(r"(<p><img [^>]*></p>)\s*(<p><strong>Fig\. \d+:</strong>.*?</p>)", r'<div class="figure">\1\2</div>', s, flags=re.S)
open(p, "w", encoding="utf-8").write(s)
EOF

CHROME=$(command -v google-chrome || command -v chromium || command -v chromium-browser)
"$CHROME" --headless=new --disable-gpu --no-pdf-header-footer --allow-file-access-from-files \
  --print-to-pdf="$PWD/Milestone_Documentation_BN.pdf" "file://$PWD/$(basename "$HTML")" 2>/dev/null
echo "Wrote $PWD/Milestone_Documentation_BN.pdf"
