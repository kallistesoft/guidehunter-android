#!/usr/bin/env python3
"""Writes the mock guides of scripts/dev-guides.json to the dev Firebase project (debug builds), never to production.

Each guide goes in `guides/{id}` and its contact in `guideContacts/{id}`; running it again overwrites them. Uses the
Firebase CLI's login (`firebase login`), whose account bypasses firestore.rules as the project owner.

    scripts/seed-dev-guides.py           # write every guide
    scripts/seed-dev-guides.py --delete  # remove them
"""

import json
import os
import subprocess
import sys
import urllib.request

PROJECT = "guidehunter-dev"  # debug builds only: app/src/debug/google-services.json
DOCUMENTS = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents"
GUIDES = os.path.join(os.path.dirname(__file__), "dev-guides.json")


def access_token():
    # Any CLI call refreshes the stored token when it has expired.
    subprocess.run(["firebase", "projects:list"], check=True, capture_output=True)
    config = os.path.expanduser("~/.config/configstore/firebase-tools.json")
    with open(config) as f:
        return json.load(f)["tokens"]["access_token"]


def value(v):
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, float):
        return {"doubleValue": v}
    if isinstance(v, list):
        return {"arrayValue": {"values": [value(x) for x in v]}}
    return {"stringValue": v}


def request(token, method, path, fields=None):
    body = json.dumps({"fields": {k: value(v) for k, v in fields.items()}}).encode() if fields else None
    req = urllib.request.Request(f"{DOCUMENTS}/{path}", data=body, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Content-Type", "application/json")
    urllib.request.urlopen(req).read()


def main():
    delete = "--delete" in sys.argv[1:]
    token = access_token()
    with open(GUIDES) as f:
        guides = json.load(f)
    for guide in guides:
        guide = dict(guide)
        guide_id = guide.pop("id")
        contact = guide.pop("contact")
        if delete:
            request(token, "DELETE", f"guides/{guide_id}")
            request(token, "DELETE", f"guideContacts/{guide_id}")
        else:
            request(token, "PATCH", f"guides/{guide_id}", guide)
            request(token, "PATCH", f"guideContacts/{guide_id}", contact)
    print(f"{'Deleted' if delete else 'Wrote'} {len(guides)} guides in {PROJECT}.")


if __name__ == "__main__":
    main()
