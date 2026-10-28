#!/usr/bin/env python3
"""Deploy only the Closie source whitelist to the existing Railway project."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
cli = os.environ.get("CLOSIE_RAILWAY_CLI") or shutil.which("railway")
if not cli:
    raise SystemExit("Install the official Railway CLI and sign in, or set CLOSIE_RAILWAY_CLI.")
revision = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=root,
                          capture_output=True, text=True, check=True).stdout.strip()
if subprocess.run(["git", "status", "--porcelain"], cwd=root, capture_output=True, text=True, check=True).stdout.strip():
    revision += "-dirty"
with tempfile.TemporaryDirectory(prefix="closie-railway-source-") as folder:
    stage = Path(folder)
    for name in ("Dockerfile", ".dockerignore", "pom.xml"):
        shutil.copyfile(root / name, stage / name)
    shutil.copytree(root / "src/main", stage / "src/main")
    (stage / "frontend").mkdir()
    for name in ("package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml", "index.html", "vite.config.js"):
        shutil.copyfile(root / "frontend" / name, stage / "frontend" / name)
    shutil.copytree(root / "frontend/src", stage / "frontend/src")
    subprocess.run([cli, "up", "--project", "eadea33a-f56c-4cbb-a54f-cf275f771a7f",
                    "--environment", "production", "--service", "834764d2-7fc9-40c0-8754-2937b3c89bfd",
                    "--detach", "--message", "Closie " + revision], cwd=stage, check=True)
print("Upload started. Verify deployment health in Railway; an upload is not a successful rollout.")
