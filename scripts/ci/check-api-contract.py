#!/usr/bin/env python3
"""Diff the Android Retrofit interface against the backend's own OpenAPI schema.

    ./scripts/ci/check-api-contract.py                       # backend on :8000
    ./scripts/ci/check-api-contract.py --schema openapi.json

A Retrofit interface is a promise about a server the compiler has never seen.
Every mismatch here type-checks, lints clean and unit-tests green, because the
unit tests mock the very interface that is wrong:

  * a path the backend does not serve      -> 404 on every call
  * a required query param never sent      -> 422 on every call
  * a param the backend does not accept    -> silently ignored, so the feature
                                              behaves as if the argument were
                                              never passed

The schema is the backend's own /openapi.json rather than a hand-written list,
so the two cannot drift apart without this failing.

Reports three kinds of mismatch and says nothing about response bodies —
Retrofit + Gson decode those leniently, and pretending to check them here would
give a false sense of coverage.
"""

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.request

DEFAULT_SCHEMA = "http://127.0.0.1:8000/openapi.json"
DEFAULT_CLIENT = "android/app/src/main/kotlin/com/climaai/app/data/ApiClient.kt"

HTTP_METHODS = ("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD")

ANNOTATION_RE = re.compile(
    r'@(' + "|".join(HTTP_METHODS) + r')\(\s*"([^"]+)"\s*\)'
)
FUN_RE = re.compile(r"\bfun\s+(\w+)")
# Captures the parameter name and everything up to the next comma or closing
# paren, which is where a `= null` default lives.
QUERY_RE = re.compile(r'@Query\(\s*"([^"]+)"\s*\)\s*(\w+)\s*:\s*([^,)]*)')


def emit(kind, path, line, title, message):
    """A GitHub annotation, so the failure lands on the diff instead of in a log."""
    print(f"::{kind} file={path},line={line},title={title}::{message}")


def gate_broken(message):
    print(f"::error title=API contract gate::{message}")
    sys.exit(2)


def load_schema(source, timeout):
    """Read the schema from a URL or a file. A URL is retried: in CI the backend
    is still booting when this job starts, and a one-shot fetch turns that into
    a flaky red build rather than a real finding."""
    if not source.startswith(("http://", "https://")):
        try:
            with open(source, encoding="utf-8") as fh:
                return json.load(fh)
        except OSError as exc:
            gate_broken(f"cannot read schema file {source}: {exc}")

    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(source, timeout=5) as resp:
                return json.load(resp)
        except (urllib.error.URLError, OSError, json.JSONDecodeError) as exc:
            last = exc
            time.sleep(2)
    gate_broken(
        f"backend schema at {source} never became available within {timeout}s "
        f"({last}). Without it this gate checks nothing, so it fails rather "
        f"than passes."
    )


def normalise(path):
    """`/alerts/{alertId}/dismiss` and `/alerts/{alert_id}/dismiss` are the same
    endpoint — Retrofit substitutes by its own @Path name, so the spelling of a
    path variable is a local matter and only its position is contractual."""
    return re.sub(r"\{[^}]*\}", "{}", path)


def index_schema(schema):
    """(method, normalised path) -> {query param name: required}"""
    index = {}
    for path, item in schema.get("paths", {}).items():
        shared = item.get("parameters", [])
        for method, operation in item.items():
            if method.upper() not in HTTP_METHODS:
                continue
            params = {}
            for param in list(shared) + list(operation.get("parameters", [])):
                if param.get("in") == "query":
                    params[param["name"]] = bool(param.get("required", False))
            index[(method.upper(), normalise(path))] = params
    return index


def parse_client(source_path):
    """Every @GET/@POST/... declaration in the Retrofit interface, with the query
    params it sends."""
    try:
        with open(source_path, encoding="utf-8") as fh:
            text = fh.read()
    except OSError as exc:
        gate_broken(f"cannot read {source_path}: {exc}")

    # Strip // comments so a commented-out endpoint is not checked as if it were
    # live. The negative lookbehind leaves "https://" alone.
    text = re.sub(r"(?<!:)//[^\n]*", "", text)

    calls = []
    matches = list(ANNOTATION_RE.finditer(text))
    for i, match in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        body = text[match.end():end]

        name = FUN_RE.search(body)
        sent, optional = [], set()
        for param, _kotlin_name, tail in QUERY_RE.findall(body):
            sent.append(param)
            if "= null" in tail:
                # Retrofit omits a null query param entirely, so this one is not
                # guaranteed to reach the server.
                optional.add(param)

        calls.append({
            "method": match.group(1),
            "path": match.group(2),
            "line": text.count("\n", 0, match.start()) + 1,
            "fun": name.group(1) if name else "<unnamed>",
            "sent": sent,
            "optional": optional,
        })
    return calls


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--schema", default=DEFAULT_SCHEMA,
                        help="URL or file path of the backend's openapi.json")
    parser.add_argument("--api-client", default=DEFAULT_CLIENT,
                        help="path to the Retrofit interface")
    parser.add_argument("--timeout", type=int, default=60,
                        help="seconds to wait for a schema URL to answer")
    args = parser.parse_args()

    schema = load_schema(args.schema, args.timeout)
    index = index_schema(schema)
    calls = parse_client(args.api_client)

    if not index:
        gate_broken(f"{args.schema} declares no paths")
    if not calls:
        gate_broken(f"{args.api_client} declares no Retrofit endpoints — the "
                    f"interface moved and this gate is checking nothing")

    known_paths = {path for _method, path in index}
    failures = 0

    print(f"\n{len(calls)} Android endpoints against {len(index)} backend "
          f"operations\n")

    for call in calls:
        key = (call["method"], normalise(call["path"]))
        label = f'{call["method"]} {call["path"]}'

        if key[1] not in known_paths:
            print(f"  MISSING  {label}")
            emit("error", args.api_client, call["line"], "Path not served by the backend",
                 f'{call["fun"]}() calls {label}, which the backend does not '
                 f'serve. Every call returns 404 and the failure surfaces as an '
                 f'empty screen, not an error.')
            failures += 1
            continue

        if key not in index:
            served = sorted(m for m, p in index if p == key[1])
            print(f"  METHOD   {label}")
            emit("error", args.api_client, call["line"], "Method not served on that path",
                 f'{call["fun"]}() sends {label}, but the backend serves only '
                 f'{", ".join(served)} on that path. Every call returns 405.')
            failures += 1
            continue

        expected = index[key]
        sent = set(call["sent"])
        always_sent = sent - call["optional"]

        missing = sorted(n for n, required in expected.items()
                         if required and n not in always_sent)
        unknown = sorted(sent - set(expected))

        if missing:
            print(f"  PARAMS   {label}  missing required: {', '.join(missing)}")
            emit("error", args.api_client, call["line"], "Required query param never sent",
                 f'{call["fun"]}() calls {label} without the required query '
                 f'param(s) {", ".join(missing)}. The backend rejects every '
                 f'request with 422 before any handler runs.')
            failures += 1

        if unknown:
            print(f"  PARAMS   {label}  not accepted: {', '.join(unknown)}")
            emit("error", args.api_client, call["line"], "Query param the backend ignores",
                 f'{call["fun"]}() sends query param(s) {", ".join(unknown)} to '
                 f'{label}, which the backend does not declare. Nothing errors — '
                 f'the values are dropped, so whatever they were meant to '
                 f'control never takes effect.')
            failures += 1

        if not missing and not unknown:
            print(f"  ok       {label}")

    if failures:
        print(f"\n{failures} contract violation(s) — see the annotations above.\n")
        return 1
    print("\nAndroid and the backend agree.\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
