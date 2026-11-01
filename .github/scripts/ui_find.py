"""Print the centre "x y" of a UI node from a uiautomator dump.

usage: ui_find.py dump.xml TEXT [first|last]
TEXT matches a node's text or content-desc exactly; prefix it with ~ to match a substring.
"last" picks the lowest node on screen (handy for the bottom navigation).
"""
import re
import sys
import xml.etree.ElementTree as ET

path, target = sys.argv[1], sys.argv[2]
pick = sys.argv[3] if len(sys.argv) > 3 else "first"
contains = target.startswith("~")
needle = target[1:] if contains else target

try:
    root = ET.parse(path).getroot()
except (ET.ParseError, FileNotFoundError):
    sys.exit(0)

matches = []
for node in root.iter("node"):
    for value in (node.get("text", ""), node.get("content-desc", "")):
        if (needle in value) if contains else (value == needle):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
            if x2 > x1 and y2 > y1:
                matches.append(((x1 + x2) // 2, (y1 + y2) // 2))
            break

if matches:
    x, y = max(matches, key=lambda m: m[1]) if pick == "last" else min(matches, key=lambda m: m[1])
    print(x, y)
