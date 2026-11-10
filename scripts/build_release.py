"""Build and sign a release using the existing Wallpaper Z key and lineage."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
SIGNING_DIR = Path(os.environ.get("WALLPAPERZ_SIGNING_DIR", Path.home() / ".wallpaperz-signing"))


def main():
    config = json.loads((SIGNING_DIR / "signing-config.json").read_text(encoding="utf-8"))
    keystore = SIGNING_DIR / "wallpaperz-release.jks"
    if not keystore.is_file():
        raise FileNotFoundError(keystore)
    sdk = Path(os.environ["ANDROID_HOME"])
    java = Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")
    build_tools = sdk / "build-tools" / "36.0.0"
    apksigner = [str(java), "-jar", str(build_tools / "lib" / "apksigner.jar")]
    gradle = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    gradle_command = [str(gradle)] if os.name == "nt" else ["sh", str(gradle)]
    subprocess.run(gradle_command + ["--max-workers=1", "assembleRelease", *sys.argv[1:]], cwd=ROOT, check=True)

    apk_dir = ROOT / "app" / "build" / "outputs" / "apk" / "release"
    metadata = json.loads((apk_dir / "output-metadata.json").read_text(encoding="utf-8"))
    build = metadata["elements"][0]
    output = ROOT / "deliverables" / f"wallpaperz-v{build['versionName']}.apk"
    output.parent.mkdir(exist_ok=True)
    signing_env = dict(os.environ, WALLPAPERZ_STORE_PASSWORD=config["storePassword"],
                       WALLPAPERZ_KEY_PASSWORD=config["keyPassword"])
    # Android 14+ supports rotation. Only the new private key is needed; the
    # checked-in lineage proves the upgrade from the previously shipped key.
    subprocess.run(apksigner + [
        "sign", "--ks", str(keystore), "--ks-key-alias", config["keyAlias"],
        "--ks-pass", "env:WALLPAPERZ_STORE_PASSWORD", "--key-pass", "env:WALLPAPERZ_KEY_PASSWORD",
        "--lineage", str(ROOT / "signing" / "wallpaperz.lineage"),
        "--rotation-min-sdk-version", "33", "--v1-signing-enabled", "false",
        "--v2-signing-enabled", "false", "--v3-signing-enabled", "true",
        "--v4-signing-enabled", "false", "--debuggable-apk-permitted", "false",
        "--out", str(output), str(apk_dir / build["outputFile"]),
    ], env=signing_env, check=True)
    subprocess.run(apksigner + ["verify", "--verbose", "--print-certs", str(output)], check=True)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix(".apk.sha256").write_text(f"{digest}  {output.name}\n", encoding="utf-8")
    print(f"Release APK: {output}")
    print(f"SHA-256: {digest}")


if __name__ == "__main__":
    main()
