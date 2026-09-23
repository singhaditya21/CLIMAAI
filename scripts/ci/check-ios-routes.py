#!/usr/bin/env python3
"""Check literal iOS API paths against FastAPI route declarations.

This catches 404s that both Swift and Python compilers miss. It intentionally
checks paths only; query and response contracts still need integration tests.
"""

import ast
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ROUTERS = ROOT / "backend/api/app/routers"
IOS = ROOT / "ios/ClimaAI"
PATH_LITERAL = re.compile(r'"(/(?:api|alerts|health|notifications|personalization)/?[^"\n]*)"')
METHODS = {"get", "post", "put", "patch", "delete"}


def normalize(path: str) -> str:
    path = path.split("?", 1)[0]
    path = re.sub(r"\\\([^)]*\)", "{}", path)
    return re.sub(r"\{[^}]*\}", "{}", path)


def routes() -> set[str]:
    result = set()
    for file in ROUTERS.glob("*.py"):
        tree = ast.parse(file.read_text(encoding="utf-8"))
        prefix = ""
        for node in tree.body:
            if not isinstance(node, ast.Assign) or not isinstance(node.value, ast.Call):
                continue
            if not any(isinstance(target, ast.Name) and target.id == "router" for target in node.targets):
                continue
            for keyword in node.value.keywords:
                if keyword.arg == "prefix" and isinstance(keyword.value, ast.Constant):
                    prefix = keyword.value.value
        for node in ast.walk(tree):
            if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                continue
            for decorator in node.decorator_list:
                if not isinstance(decorator, ast.Call) or not isinstance(decorator.func, ast.Attribute):
                    continue
                if not isinstance(decorator.func.value, ast.Name) or decorator.func.value.id != "router":
                    continue
                if decorator.func.attr not in METHODS or not decorator.args:
                    continue
                if isinstance(decorator.args[0], ast.Constant):
                    result.add(normalize(prefix + decorator.args[0].value))
    return result


def main() -> int:
    served = routes()
    failures = []
    checked = 0
    for file in IOS.rglob("*.swift"):
        if "Assets.xcassets" in file.parts:
            continue
        for line_number, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
            if line.lstrip().startswith("//"):
                continue
            for match in PATH_LITERAL.finditer(line):
                path = normalize(match.group(1))
                checked += 1
                if path not in served:
                    failures.append(f"{file.relative_to(ROOT)}:{line_number}: {path}")
    if not checked:
        print("ERROR: no iOS API paths found; the checker would verify nothing")
        return 2
    if failures:
        print("iOS paths missing from FastAPI:")
        print("\n".join(failures))
        return 1
    print(f"OK: {checked} iOS path literals match {len(served)} FastAPI routes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
