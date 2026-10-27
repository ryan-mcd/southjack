#!/usr/bin/env python3
"""Map vMAJOR.MINOR.PATCH tags to monotonically ordered Android versions."""
import re
import sys


def versions(tag: str) -> tuple[str, int]:
    match = re.fullmatch(r"v(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)", tag)
    if not match:
        raise ValueError("Use vMAJOR.MINOR.PATCH, each component 0..99, without leading zeros or prerelease suffixes.")
    major, minor, patch = map(int, match.groups())
    code = major * 10_000 + minor * 100 + patch
    if code < 2:
        raise ValueError("Use v0.0.2 or higher; upstream already uses versionCode 1. Recommended first release: v0.2.0.")
    return tag[1:], code


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise ValueError("Usage: release-version.py vMAJOR.MINOR.PATCH")
        name, code = versions(sys.argv[1])
    except ValueError as error:
        print(f"Release version error: {error}", file=sys.stderr)
        sys.exit(1)
    print(f"version_name={name}")
    print(f"version_code={code}")
