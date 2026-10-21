#!/usr/bin/env python3
"""Generate the Phosphor image sets for the iOS asset catalogue from PhosphorIcons.kt.

The Android icons are `ImageVector`s built from a single SVG path each, in
app/src/main/kotlin/org/openstrap/app/ui/theme/PhosphorIcons.kt. iOS gets the same
paths as template SVGs in an asset catalogue, so `PhosphorIcon` can hand out an
`Image` that tints like the Compose `Icon()` does.

  gen_icons.py            write (or rewrite) every imageset
  gen_icons.py --check    write nothing; verify the catalogue matches the source

Checks in --check, all fatal:
  * the number of imagesets equals the number of `icon("` calls in PhosphorIcons.kt
  * every imageset holds the SVG its Contents.json names
  * every SVG parses with xml.etree, has viewBox="0 0 256 256" and one <path d=...>
  * every Contents.json is a template image with vector preservation on

Stdlib only.
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/kotlin/org/openstrap/app/ui/theme/PhosphorIcons.kt"
CATALOGUE = ROOT / "iosApp/OpenStrap/Assets.xcassets"
PHOSPHOR = CATALOGUE / "Phosphor"

# `val House: ImageVector by lazy { icon("House", "M219…") }` and the autoMirror form
# `icon("ArrowLeft", autoMirror = true, paths = arrayOf("…"))` both start the same way.
ICON = re.compile(r'val\s+(\w+)\s*:\s*ImageVector\s+by\s+lazy\s*\{\s*icon\("([^"]+)"')
QUOTED = re.compile(r'"([^"]*)"')

CATALOGUE_CONTENTS = {"info": {"author": "xcode", "version": 1}}
PHOSPHOR_CONTENTS = {"info": {"author": "xcode", "version": 1}}


def parse_source(path=SOURCE):
    """Every icon in the Kotlin file, in file order: [(kotlin name, [path d, ...])]."""
    text = path.read_text(encoding="utf-8")
    icons = []
    for line in text.splitlines():
        match = ICON.search(line)
        if not match:
            continue
        name = match.group(2)
        # The paths are the remaining quoted strings on the line. The icon's own name is
        # already consumed by the first match, so nothing else in this line is a string.
        paths = QUOTED.findall(line[match.end():])
        if not paths:
            raise SystemExit(f"{path}: icon {name} has no path data")
        icons.append((name, paths))
    return icons


def icon_calls(path=SOURCE):
    return path.read_text(encoding="utf-8").count('icon("')


def svg_for(paths):
    body = "".join(f'<path d="{d}" fill="#000000"/>' for d in paths)
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" '
        f'width="256" height="256">{body}</svg>\n'
    )


def contents_for(name):
    return {
        "images": [{"filename": f"{name}.svg", "idiom": "universal"}],
        "info": {"author": "xcode", "version": 1},
        "properties": {
            "preserves-vector-representation": True,
            "template-rendering-intent": "template",
        },
    }


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def write(icons):
    PHOSPHOR.mkdir(parents=True, exist_ok=True)
    write_json(CATALOGUE / "Contents.json", CATALOGUE_CONTENTS)
    write_json(PHOSPHOR / "Contents.json", PHOSPHOR_CONTENTS)
    wanted = set()
    for name, paths in icons:
        imageset = PHOSPHOR / f"{name}.imageset"
        imageset.mkdir(parents=True, exist_ok=True)
        (imageset / f"{name}.svg").write_text(svg_for(paths), encoding="utf-8")
        write_json(imageset / "Contents.json", contents_for(name))
        wanted.add(imageset.name)
    # Icons deleted from the Kotlin file must not linger in the catalogue.
    for existing in sorted(PHOSPHOR.iterdir()):
        if existing.is_dir() and existing.suffix == ".imageset" and existing.name not in wanted:
            for child in existing.iterdir():
                child.unlink()
            existing.rmdir()
            print(f"removed {existing.name}")
    print(f"{len(icons)} icons")


def check(icons):
    problems = []
    imagesets = sorted(p for p in PHOSPHOR.iterdir() if p.suffix == ".imageset") if PHOSPHOR.is_dir() else []
    calls = icon_calls()
    if len(imagesets) != calls:
        problems.append(f"{len(imagesets)} imagesets but {calls} icon(\" calls in {SOURCE.name}")
    if len(imagesets) != len(icons):
        problems.append(f"{len(imagesets)} imagesets but {len(icons)} icons parsed")

    for name, paths in icons:
        imageset = PHOSPHOR / f"{name}.imageset"
        svg = imageset / f"{name}.svg"
        contents = imageset / "Contents.json"
        if not svg.is_file():
            problems.append(f"{name}: no {name}.svg")
            continue
        if not contents.is_file():
            problems.append(f"{name}: no Contents.json")
            continue
        try:
            root = ET.fromstring(svg.read_text(encoding="utf-8"))
        except ET.ParseError as error:
            problems.append(f"{name}: SVG does not parse ({error})")
            continue
        if root.get("viewBox") != "0 0 256 256":
            problems.append(f"{name}: viewBox is {root.get('viewBox')!r}")
        path_elements = [e for e in root.iter() if e.tag.endswith("path")]
        if len(path_elements) != len(paths):
            problems.append(f"{name}: {len(path_elements)} <path> elements, expected {len(paths)}")
        for element in path_elements:
            if not element.get("d"):
                problems.append(f"{name}: a <path> has no d")
        try:
            meta = json.loads(contents.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            problems.append(f"{name}: Contents.json does not parse ({error})")
            continue
        properties = meta.get("properties", {})
        if properties.get("template-rendering-intent") != "template":
            problems.append(f"{name}: template-rendering-intent is not template")
        if properties.get("preserves-vector-representation") is not True:
            problems.append(f"{name}: preserves-vector-representation is not true")
        if [image.get("filename") for image in meta.get("images", [])] != [f"{name}.svg"]:
            problems.append(f"{name}: Contents.json does not name {name}.svg")

    print(f"{len(imagesets)} imagesets, {calls} icon(\" calls, {len(icons)} icons parsed")
    if problems:
        for problem in problems:
            print(f"FAIL {problem}")
        return 1
    print("OK")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify only, write nothing")
    args = parser.parse_args()
    icons = parse_source()
    if args.check:
        return check(icons)
    write(icons)
    return 0


if __name__ == "__main__":
    sys.exit(main())
