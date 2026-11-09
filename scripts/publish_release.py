"""Publishes the shipped build to the public releases repo, where installed apps look for updates first.

2026-11-01: the source repo goes private (its history isn't for the public), and a private repo answers 404 to the
app, so releases live in example-org/DeskAI-releases: DeskAI.apk and web_dist/version.json, nothing else
(UpdateSource.REPOS). Run it after the ship commit (BUILD.md, "Ship"), with gh signed in as example-org:

    python scripts/publish_release.py

It copies the two files into a clone next to this repo (made on first use), commits and pushes. It refuses when
version.json and the APK's size disagree (an APK from another build would make phones update in a loop).
"""
import json
import os
import shutil
import subprocess
import sys

REPO = "example-org/DeskAI-releases"
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLONE = os.path.join(os.path.dirname(HERE), "DeskAI-releases")
README = """# DeskAI releases

Release builds of the DeskAI Android app. The app checks `web_dist/version.json` here for updates and downloads
`DeskAI.apk`. The source code is not in this repo.
"""


def git(*args, cwd=CLONE):
    subprocess.run(["git", *args], cwd=cwd, check=True)


def main() -> int:
    apk, manifest = os.path.join(HERE, "DeskAI.apk"), os.path.join(HERE, "web_dist", "version.json")
    with open(manifest, encoding="utf-8") as f:
        info = json.load(f)
    if info.get("fileSizeBytes") != os.path.getsize(apk):
        print(f"Refusing: version.json says {info.get('fileSizeBytes')} bytes, DeskAI.apk is {os.path.getsize(apk)}.")
        return 1
    if not os.path.isdir(os.path.join(CLONE, ".git")):
        git("clone", f"https://github.com/{REPO}.git", CLONE, cwd=os.path.dirname(HERE))
        # A public repo: never the global git identity (2026-11-01: it was the owner's personal email)
        git("config", "user.name", "Alex")
        git("config", "user.email", "alex@users.noreply.github.com")
    else:
        git("pull", "--ff-only")
    os.makedirs(os.path.join(CLONE, "web_dist"), exist_ok=True)
    shutil.copyfile(apk, os.path.join(CLONE, "DeskAI.apk"))
    shutil.copyfile(manifest, os.path.join(CLONE, "web_dist", "version.json"))
    readme = os.path.join(CLONE, "README.md")
    if not os.path.exists(readme):
        with open(readme, "w", encoding="utf-8", newline="\n") as f:
            f.write(README)
    git("add", "-A")
    if subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=CLONE).returncode == 0:
        print("Nothing new to publish.")
        return 0
    git("commit", "-q", "-m", f"DeskAI v{info['versionName']} (build {info['versionCode']})")
    git("branch", "-M", "main")
    git("push", "-q", "-u", "origin", "main")
    print(f"Published build {info['versionCode']} to {REPO}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
