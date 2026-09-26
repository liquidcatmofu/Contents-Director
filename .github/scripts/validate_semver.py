#!/usr/bin/env python3
import re
import sys

SEMVER_RE = re.compile(
    r"^(0|[1-9][0-9]*)\."
    r"(0|[1-9][0-9]*)\."
    r"(0|[1-9][0-9]*)"
    r"(?:-("
        r"(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
        r"(?:\.(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*"
    r"))?"
    r"(?:\+("
        r"[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*"
    r"))?$"
)


def is_valid_semver(version: str) -> bool:
    return SEMVER_RE.fullmatch(version) is not None


def self_test() -> None:
    valid = [
        "0.0.0",
        "1.0.0",
        "1.0.0-alpha",
        "1.0.0-alpha.1",
        "1.0.0-beta.2",
        "1.0.0-rc.1",
        "1.0.0+build.01",
        "1.0.0-alpha.1+build.01",
    ]
    invalid = [
        "01.0.0",
        "1.01.0",
        "1.0.01",
        "1.0.0-alpha..1",
        "1.0.0-alpha.",
        "1.0.0-alpha.01",
        "1.0.0-01",
        "1.0.0+build..1",
        "1.0.0+build.",
    ]

    failures = []
    failures.extend(version for version in valid if not is_valid_semver(version))
    failures.extend(version for version in invalid if is_valid_semver(version))
    if failures:
        raise SystemExit(f"SemVer validation self-test failed: {failures}")


def main() -> None:
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        self_test()
        return

    if len(sys.argv) != 2:
        raise SystemExit(f"usage: {sys.argv[0]} <version> | --self-test")

    version = sys.argv[1]
    if not is_valid_semver(version):
        raise SystemExit(f"Invalid SemVer: {version}")


if __name__ == "__main__":
    main()
