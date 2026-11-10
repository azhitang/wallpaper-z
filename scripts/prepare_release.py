"""Generate the public update manifest from the exact signed build."""
import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    metadata = json.loads((ROOT / "app/build/outputs/apk/release/output-metadata.json").read_text(encoding="utf-8"))
    build = metadata["elements"][0]
    tag = "v" + build["versionName"]
    if os.environ.get("GITHUB_REF_TYPE") == "tag" and os.environ["GITHUB_REF_NAME"] != tag:
        raise ValueError("Release tag must match the APK versionName")
    apk = ROOT / "deliverables" / f"wallpaperz-{tag}.apk"
    manifest = {
        "packageName": metadata["applicationId"],
        "versionCode": build["versionCode"],
        "versionName": build["versionName"],
        "minSdk": metadata["minSdkVersionForDexing"],
        "apkUrl": f"https://github.com/azhitang/wallpaper-z/releases/download/{tag}/{apk.name}",
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "sizeBytes": apk.stat().st_size,
        "notes": (ROOT / "RELEASE_NOTES.md").read_text(encoding="utf-8").strip(),
    }
    (ROOT / "deliverables/update.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"tag={tag}\napk=deliverables/{apk.name}\n")
    print(f"Prepared {tag}: {manifest['sizeBytes']} bytes")


if __name__ == "__main__":
    main()
