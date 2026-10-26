#!/usr/bin/env python3
"""Prints the app's on-screen elements, one per line: selector, interactions, tap point.

    android layout --flat | python3 -I screen_text.py

Much shorter than the raw layout JSON. System bars are left out. Example line:
    text="Become a guide"  CLICKABLE  @574,871
    desc="About"  @1006,226
The selector part goes straight into `android input tap` (text=Become a guide, desc=Show distances from my location).
Elements without a label (a card around its label) show as "-": tap their label instead.
"""
import json
import sys


def main():
    raw = sys.stdin.read()
    try:
        windows = json.loads(raw)
    except json.JSONDecodeError:
        # `android layout` prints progress lines before the JSON the first time it installs its server.
        start = raw.find("[")
        if start < 0:
            sys.exit("No layout JSON on stdin. Is a device connected and the app in front?")
        windows = json.loads(raw[start:])

    apps = [w for w in windows if w.get("type") == "APPLICATION"]
    if not apps:
        sys.exit("No app window on screen (home screen or a system dialog?).")
    for window in apps:
        print(f"== {window.get('window-title') or 'app window'}")
        for node in window.get("content", []):
            if node.get("text"):
                label = f'text="{node["text"]}"'
            elif node.get("content-desc"):
                label = f'desc="{node["content-desc"]}"'
            else:
                label = "-"
            actions = " ".join(a for a in node.get("interactions", []) if a != "FOCUSABLE")
            x, y = node["center"].strip("[]").split(",")
            print(f"{label}  {actions}  @{x},{y}".replace("    ", "  "))


if __name__ == "__main__":
    main()
