#!/usr/bin/env python3
"""GitHub Actions status for the OpenStrap iOS branch. Stdlib only.

  ci.py status [SHA]        one line per workflow run for SHA (default: git HEAD)
  ci.py wait [SHA]          poll until every run for SHA has finished; exit 0 only if all succeeded
  ci.py logs RUN_ID         print the last 150 log lines of each failed job (needs the token file)

Environment:
  OPENSTRAP_REPO   owner/name (default romitraj13/openstrap)
  TOKEN_FILE       default /opt/data/tools/secrets/github-openstrap.token
The token is read from the file, sent only to api.github.com, and never printed.
"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

REPO = os.environ.get("OPENSTRAP_REPO", "romitraj13/openstrap")
TOKEN_FILE = os.environ.get("TOKEN_FILE", "/opt/data/tools/secrets/github-openstrap.token")
API = "https://api.github.com"


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _token():
    try:
        with open(TOKEN_FILE) as f:
            return f.read().strip() or None
    except OSError:
        return None


def _get(path, raw=False, need_token=False):
    req = urllib.request.Request(API + path, headers={"Accept": "application/vnd.github+json", "User-Agent": "openstrap-ci"})
    tok = _token()
    if tok:
        req.add_header("Authorization", "Bearer " + tok)
    elif need_token:
        sys.exit(f"token file {TOKEN_FILE} missing: credentials required, awaiting approval")
    if not raw:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read())
    # Job logs answer with a 302 to a signed storage URL. That URL rejects the Authorization
    # header, so fetch the redirect target with a fresh, unauthenticated request.
    try:
        with urllib.request.build_opener(_NoRedirect).open(req, timeout=60) as r:
            body = r.read()
    except urllib.error.HTTPError as e:
        if e.code not in (301, 302, 303, 307, 308):
            raise
        location = e.headers["Location"]
        with urllib.request.urlopen(urllib.request.Request(location, headers={"User-Agent": "openstrap-ci"}), timeout=120) as r:
            body = r.read()
    return body.decode("utf-8", "replace")


def _head():
    return subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()


def runs(sha):
    return _get(f"/repos/{REPO}/actions/runs?head_sha={sha}&per_page=50")["workflow_runs"]


def status(sha):
    rs = runs(sha)
    if not rs:
        print(f"no workflow runs for {sha[:7]} yet")
    for r in rs:
        print(f"{r['name']:<10} run={r['id']} status={r['status']} conclusion={r['conclusion']} {r['html_url']}")
    return rs


def wait(sha, timeout_s=45 * 60, every_s=60):
    deadline = time.time() + timeout_s
    while True:
        try:
            rs = runs(sha)
        except urllib.error.URLError as e:
            print(f"api error: {e}; retrying")
            rs = None
        if rs and all(r["status"] == "completed" for r in rs):
            status(sha)
            ok = all(r["conclusion"] == "success" for r in rs)
            print("ALL GREEN" if ok else "FAILED")
            return 0 if ok else 1
        if time.time() > deadline:
            print("timed out waiting for CI")
            status(sha)
            return 2
        time.sleep(every_s)


def logs(run_id):
    jobs = _get(f"/repos/{REPO}/actions/runs/{run_id}/jobs?per_page=50")["jobs"]
    failed = [j for j in jobs if j["conclusion"] not in ("success", "skipped")]
    if not failed:
        print("no failed jobs")
    for j in failed:
        failed_steps = [s["name"] for s in j.get("steps", []) if s.get("conclusion") == "failure"]
        print(f"=== job {j['name']} ({j['conclusion']}), failed steps: {failed_steps}")
        text = _get(f"/repos/{REPO}/actions/jobs/{j['id']}/logs", raw=True, need_token=True)
        print("\n".join(text.splitlines()[-150:]))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "status"
    arg = sys.argv[2] if len(sys.argv) > 2 else None
    if cmd == "status":
        status(arg or _head())
    elif cmd == "wait":
        sys.exit(wait(arg or _head()))
    elif cmd == "logs" and arg:
        logs(arg)
    else:
        sys.exit(__doc__)
