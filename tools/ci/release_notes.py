# Schreibt den obersten Abschnitt des Update-Verlaufs nach out/notes.md und gibt die Version aus.
import pathlib


def latest(path):
    text = pathlib.Path(path).read_text(encoding="utf-8")
    title, _, body = text.split("\n## ")[1].partition("\n")
    return title.split()[0], body.strip()


version, german = latest("changelog/CHANGELOG.md")
_, english = latest("changelog/CHANGELOG.en.md")
pathlib.Path("out").mkdir(exist_ok=True)
pathlib.Path("out/notes.md").write_text(
    f"### Deutsch\n{german}\n\n### English\n{english}\n\n"
    "[Update-Verlauf](../../blob/main/changelog/CHANGELOG.md) · "
    "[Changelog](../../blob/main/changelog/CHANGELOG.en.md)\n",
    encoding="utf-8",
)
print(version)
