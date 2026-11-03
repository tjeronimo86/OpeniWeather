# Schreibt den obersten Abschnitt des Update-Verlaufs nach out/notes.md und gibt die Version aus.
import pathlib

text = pathlib.Path("changelog/CHANGELOG.md").read_text(encoding="utf-8")
section = text.split("\n## ")[1]
title, _, body = section.partition("\n")
pathlib.Path("out").mkdir(exist_ok=True)
pathlib.Path("out/notes.md").write_text(
    body.strip() + "\n\n[Gesamter Update-Verlauf](../../blob/main/changelog/CHANGELOG.md)\n",
    encoding="utf-8",
)
print(title.split()[0])
