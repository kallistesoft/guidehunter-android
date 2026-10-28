#!/usr/bin/env python3
"""Publishes a guide's offer once reviewed: copies it to the map (`guides/{id}`), with the guide's profile as its
contact (`guideContacts/{id}`), and marks it published. Run it again after an edit to publish the edit.

The offer's email (sent when the guide sends or edits it) gives the command. Uses the Firebase CLI's login
(`firebase login`), whose account bypasses firestore.rules as the project owner.

    scripts/approve-offer.py guidehunter-dev --list        # the offers waiting for review
    scripts/approve-offer.py guidehunter-dev <offerId>     # publish one (guidehunter-55e70 for production)
"""

import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

# The fields that go on the map; the others (status, live, updatedAt) are the review's.
PUBLIC = ["ownerUid", "title", "description", "activity", "species", "region", "lat", "lng", "priceFrom", "tripLength", "departures", "everyYear"]


def access_token():
    # Any CLI call refreshes the stored token when it has expired.
    subprocess.run(["firebase", "projects:list"], check=True, capture_output=True)
    config = os.path.expanduser("~/.config/configstore/firebase-tools.json")
    with open(config) as f:
        return json.load(f)["tokens"]["access_token"]


class Firestore:
    def __init__(self, project):
        self.base = f"https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents"
        self.token = access_token()

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(f"{self.base}{path}", data=data, method=method)
        req.add_header("Authorization", f"Bearer {self.token}")
        req.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(req) as response:
            return json.load(response)

    def get(self, path):
        """The document's typed fields, or None if it doesn't exist."""
        try:
            return self.call("GET", f"/{path}").get("fields", {})
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            raise

    def set(self, path, fields):
        self.call("PATCH", f"/{path}", {"fields": fields})

    def update(self, path, fields):
        mask = "&".join(f"updateMask.fieldPaths={name}" for name in fields)
        self.call("PATCH", f"/{path}?{mask}", {"fields": fields})

    def pending(self):
        query = {"structuredQuery": {
            "from": [{"collectionId": "guideOffers"}],
            "where": {"fieldFilter": {"field": {"fieldPath": "status"}, "op": "EQUAL", "value": {"stringValue": "PENDING"}}},
        }}
        return [row["document"] for row in self.call("POST", ":runQuery", query) if "document" in row]


def text(fields, name):
    return fields.get(name, {}).get("stringValue", "")


def approve(db, offer_id):
    offer = db.get(f"guideOffers/{offer_id}")
    if offer is None:
        sys.exit(f"No offer {offer_id}.")
    owner = text(offer, "ownerUid")
    published = db.get(f"guides/{offer_id}")
    # A guide picks their offer's id: never let one overwrite another guide's published offer.
    if published is not None and text(published, "ownerUid") != owner:
        sys.exit(f"guides/{offer_id} belongs to another guide: not published.")
    profile = db.get(f"guideProfiles/{owner}")
    if not profile or not text(profile, "businessName"):
        sys.exit(f"The guide ({owner}) has no profile yet: not published.")

    guide = {name: offer[name] for name in PUBLIC if name in offer and "nullValue" not in offer[name]}
    guide["currency"] = {"stringValue": "CAD"}
    contact = {
        "ownerUid": offer["ownerUid"],
        "name": profile["businessName"],
        **{name: profile.get(name, {"stringValue": ""}) for name in ["email", "phone", "website"]},
    }
    db.set(f"guides/{offer_id}", guide)
    db.set(f"guideContacts/{offer_id}", contact)
    db.update(f"guideOffers/{offer_id}", {"status": {"stringValue": "PUBLISHED"}, "live": {"booleanValue": True}})
    print(f"Published {offer_id}: {text(offer, 'title')} ({text(profile, 'businessName')}).")


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    project, what = sys.argv[1:]
    db = Firestore(project)
    if what == "--list":
        for document in db.pending():
            fields = document["fields"]
            live = fields.get("live", {}).get("booleanValue", False)
            print(f"{document['name'].rsplit('/', 1)[1]}  {'edit' if live else 'new '}  {text(fields, 'title')}")
    else:
        approve(db, what)


if __name__ == "__main__":
    main()
