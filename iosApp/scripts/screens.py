#!/usr/bin/env python3
"""Copy the iOS UI test screenshots from GitHub Actions into docs/ios/screenshots/.

  screens.py [SHA]     default: git HEAD

Finds the newest successful iOS workflow run for the commit, downloads its `screens`
artifact and writes every `U*-*.png` into docs/ios/screenshots/ as `<name>.png`
(`U1-designlab-1`, `U2-designlab-3`, …). Standard library only.

Environment:
  OPENSTRAP_REPO   owner/name (default romitraj13/openstrap)
  TOKEN_FILE       default /opt/data/tools/secrets/github-openstrap.token
The token is read from the file, sent only to api.github.com, and never printed.
"""
import io
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

REPO = os.environ.get("OPENSTRAP_REPO", "romitraj13/openstrap")
TOKEN_FILE = os.environ.get("TOKEN_FILE", "/opt/data/tools/secrets/github-openstrap.token")
API = "https://api.github.com"
ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "docs/ios/screenshots"

# U1-designlab-1, U2-designlab-12, ... The trailing parts of an exported attachment name are
# dropped: xcresulttool writes `U1-designlab-1_0_<uuid>.png`.
SHOT = re.compile(r"^(U\d+-[A-Za-z0-9]+-\d+)")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def token():
    try:
        with open(TOKEN_FILE) as f:
            return f.read().strip() or None
    except OSError:
        return None


def api(path):
    request = urllib.request.Request(
        API + path,
        headers={"Accept": "application/vnd.github+json", "User-Agent": "openstrap-screens"},
    )
    auth = token()
    if auth:
        request.add_header("Authorization", "Bearer " + auth)
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read())


def download(url):
    """GET a URL that answers with a redirect to signed storage, without sending the token on."""
    request = urllib.request.Request(url, headers={"User-Agent": "openstrap-screens"})
    auth = token()
    if auth:
        request.add_header("Authorization", "Bearer " + auth)
    try:
        with urllib.request.build_opener(NoRedirect).open(request, timeout=120) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        if error.code not in (301, 302, 303, 307, 308):
            raise
        location = error.headers["Location"]
        with urllib.request.urlopen(
            urllib.request.Request(location, headers={"User-Agent": "openstrap-screens"}), timeout=180
        ) as response:
            return response.read()


def head():
    return subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()


def newest_successful_run(sha):
    runs = api(f"/repos/{REPO}/actions/runs?head_sha={sha}&per_page=50")["workflow_runs"]
    for run in runs:  # newest first
        if run.get("name") == "iOS" and run.get("status") == "completed" and run.get("conclusion") == "success":
            return run
    return None


def screens_artifact(run_id):
    artifacts = api(f"/repos/{REPO}/actions/runs/{run_id}/artifacts?per_page=100")["artifacts"]
    for artifact in artifacts:
        if artifact.get("name") == "screens" and not artifact.get("expired"):
            return artifact
    return None


def names_from_manifest(payload):
    """xcresulttool writes a manifest mapping each exported file to its attachment name."""
    mapping = {}

    def walk(node):
        if isinstance(node, dict):
            exported = node.get("exportedFileName")
            suggested = node.get("suggestedHumanReadableName")
            if exported and suggested:
                mapping[exported] = suggested
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(payload)
    return mapping


def main():
    sha = sys.argv[1] if len(sys.argv) > 1 else head()
    run = newest_successful_run(sha)
    if run is None:
        print(f"no successful iOS run for {sha[:7]} yet")
        return 1
    artifact = screens_artifact(run["id"])
    if artifact is None:
        print(f"run {run['id']} has no `screens` artifact")
        return 1

    archive = zipfile.ZipFile(io.BytesIO(download(artifact["archive_download_url"])))
    names = archive.namelist()

    manifest = {}
    for name in names:
        if Path(name).name == "manifest.json":
            try:
                manifest = names_from_manifest(json.loads(archive.read(name)))
            except (json.JSONDecodeError, UnicodeDecodeError):
                manifest = {}
            break

    OUTPUT.mkdir(parents=True, exist_ok=True)
    written = []
    for name in names:
        leaf = Path(name).name
        if not leaf.lower().endswith(".png"):
            continue
        target = manifest.get(leaf)
        if target:
            target = Path(target)
            if target.suffix.lower() != ".png":
                target = target.with_suffix(".png")
            target = target.name
        else:
            match = SHOT.match(leaf)
            if not match:
                continue
            target = match.group(1) + ".png"
        if not SHOT.match(target):
            continue
        (OUTPUT / target).write_bytes(archive.read(name))
        written.append(target)

    for name in sorted(written):
        print(f"docs/ios/screenshots/{name}")
    print(f"{len(written)} screenshots from run {run['id']} ({sha[:7]})")
    return 0 if written else 1


if __name__ == "__main__":
    sys.exit(main())
