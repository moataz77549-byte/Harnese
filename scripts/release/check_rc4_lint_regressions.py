#!/usr/bin/env python3
"""Hold this targeted hotfix to an exact pre-existing lint-error baseline.
The complete lint XML is still stored, and any new error is a build failure.
"""
import collections, json, pathlib, sys, xml.etree.ElementTree as ET

report, baseline = map(pathlib.Path, sys.argv[1:])
if not report.is_file():
    raise SystemExit("Lint report unavailable")
def key(issue):
    return issue["id"], issue["file"], issue["message"]
known = collections.Counter(key(x) for x in json.loads(baseline.read_text()))
actual = collections.Counter()
for e in ET.parse(report).getroot().findall("issue"):
    if e.attrib.get("severity") not in ("Error", "Fatal"):
        continue
    f = e.find("location").attrib["file"]
    p = pathlib.Path(f)
    if p.is_absolute():
        try:
            f = p.relative_to(pathlib.Path.cwd()).as_posix()
        except ValueError:
            f = f.split("/Harnese/Harnese/", 1)[-1]
    actual[(e.attrib["id"], f, e.attrib["message"])] += 1
extra = actual - known
if extra:
    for (rule, f, message), count in extra.items():
        print(f"NEW ERROR ({count}) {rule}: {f}: {message}")
    raise SystemExit("Unexpected lint errors; release blocked")
print(f"PASS: no new lint errors; {sum(actual.values())} exact pre-existing errors retained in full report.")
