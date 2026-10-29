#!/usr/bin/env python3
"""Prepare a private verification token; never enable or publish the webhook."""
import os
import re
import secrets
from pathlib import Path
from urllib.parse import urlsplit
import argparse

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--endpoint", help="Approved stable public HTTPS callback URL")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
if args.endpoint:
    url = urlsplit(args.endpoint)
    host = url.hostname or ""
    if (url.scheme != "https" or "." not in host or not re.fullmatch(r"[A-Za-z0-9.-]+", host) or re.fullmatch(r"[0-9.]+", host)
        or ":" in host or host.endswith((".local", ".localhost"))
        or url.username or url.password or url.query or url.fragment
        or url.port not in (None, 443) or url.path != "/api/ebay/account-deletion"):
        parser.error("Use a public HTTPS URL ending in /api/ebay/account-deletion")
state = root / ".local"
state.mkdir(exist_ok=True)
path = state / "ebay-notification-token"
if path.exists():
    token = path.read_text().strip()
    if not re.fullmatch(r"[A-Za-z0-9_-]{32,80}", token):
        parser.error("Existing token is invalid; it has not been changed")
else:
    token = secrets.token_urlsafe(48)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as handle:
        handle.write(token + "\n")
os.chmod(path, 0o600)
print(f"Verification token prepared in private local file: {path}")
if args.endpoint:
    env = root / ".env.local"
    lines = env.read_text().splitlines() if env.exists() else []
    keys = {"EBAY_NOTIFICATIONS_ENABLED", "EBAY_NOTIFICATION_ENDPOINT", "EBAY_NOTIFICATION_VERIFICATION_TOKEN"}
    lines = [line for line in lines if not any(re.match(r"\s*(?:export\s+)?" + key + r"\s*=", line) for key in keys)]
    lines += ["EBAY_NOTIFICATIONS_ENABLED=false", f"EBAY_NOTIFICATION_ENDPOINT='{args.endpoint}'", f"EBAY_NOTIFICATION_VERIFICATION_TOKEN='{token}'"]
    fd = os.open(env, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as handle:
        handle.write("\n".join(lines) + "\n")
    os.chmod(env, 0o600)
    print("Local configuration prepared. Notifications remain DISABLED. Other settings preserved.")
