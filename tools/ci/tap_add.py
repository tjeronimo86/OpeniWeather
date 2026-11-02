# Sucht im UI-Dump den Bestaetigen-Knopf des Widget-Dialogs und gibt dessen Mitte aus.
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    label = (node.get("text") or "").lower()
    if label in ("add automatically", "add to home screen", "add", "hinzufügen"):
        x0, y0, x1, y1 = map(int, re.findall(r"\d+", node.get("bounds")))
        print((x0 + x1) // 2, (y0 + y1) // 2)
        break
