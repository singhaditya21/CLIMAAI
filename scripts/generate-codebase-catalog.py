#!/usr/bin/env python3
"""List every versioned project file by owner, purpose, and free-launch role.

Run from anywhere in the checkout. The output is deterministic so reviewers can
spot new uncategorized files in a diff. This is an inventory, not a claim that
every optional subsystem is production-ready.
"""

import csv
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "CODEBASE_CATALOG.csv"


def classify(path: str) -> tuple[str, str, str]:
    parts = Path(path).parts
    if path.startswith("android/app/src/androidTest/") or path.startswith("android/app/src/test/"):
        return "Android phone", "Tests", "verification"
    if path.startswith("android/app/src/main/kotlin/"):
        area = parts[8] if len(parts) > 9 else "entry"
        if area in {"billing"} or "Paywall" in path or "Subscription" in path or "ProFeatures" in path or "FeatureGate" in path:
            return "Android phone", "Monetization", "deferred"
        if "AIInsights" in path:
            return "Android phone", "AI insights", "deferred"
        return "Android phone", area, "core"
    if path.startswith("android/wear/src/main/"):
        return "Wear OS", parts[8] if len(parts) > 9 else "app resources", "optional"
    if path.startswith("android/"):
        return "Android", "Build and resources", "build"
    if path.startswith("ios/ClimaAITests/"):
        return "iOS phone", "Tests", "verification"
    if path.startswith("ios/ClimaAI/Views/Subscription/") or "Subscription" in path or "Paywall" in path:
        return "iOS phone", "Monetization", "deferred"
    if path.startswith("ios/ClimaAI/Views/AI/") or "AIInsights" in path:
        return "iOS phone", "AI insights", "deferred"
    if path.startswith("ios/ClimaAI/"):
        return "iOS phone", parts[2] if len(parts) > 2 else "App", "backend-dependent"
    if path.startswith("ios/ClimaAIWatch/"):
        return "Apple Watch", "Watch app", "optional"
    if path.startswith("ios/ClimaAIWidget/"):
        return "iOS widgets", "Widgets", "optional"
    if path.startswith("ios/"):
        return "iOS", "Build and setup", "build"
    if path.startswith("backend/payment-service/"):
        return "Payment service", "Webhooks", "deferred"
    if path.startswith("backend/api/tests/"):
        return "Backend API", "Tests", "verification"
    if path.startswith("backend/api/app/routers/demo.py") or (
        path.startswith("backend/api/app/services/") and "mock" in path
    ):
        return "Backend API", "Generated demo data", "prototype"
    if path.startswith("backend/api/app/routers/ai.py") or path.startswith("backend/api/app/routers/subscriptions.py"):
        return "Backend API", "Paid/AI routes", "deferred"
    if path.startswith("backend/api/app/services/") and any(
        name in path for name in ("ai_service", "subscription_service", "receipt_validator")
    ):
        return "Backend API", "Paid/AI services", "deferred"
    if path.startswith("backend/api/app/models/") and "subscription" in path:
        return "Backend API", "Subscription model", "deferred"
    if path.startswith("backend/api/app/schemas/") and ("subscription" in path or "ai.py" in path):
        return "Backend API", "Paid/AI schemas", "deferred"
    if path.startswith("backend/api/app/"):
        return "Backend API", parts[3] if len(parts) > 3 else "App", "backend-dependent"
    if path.startswith("backend/"):
        return "Backend", "Deployment and schema", "backend-dependent"
    if path.startswith("docs/assets/") or path == "docs/index.html":
        return "GitHub Pages", "Weather website", "release-candidate"
    if path.startswith("docs/") and path.endswith(".html"):
        return "GitHub Pages", "Legal/support site", "release-candidate"
    if path == "docs/PRE_LAUNCH_AUDIT.md":
        return "Documentation", "Historical audit", "documentation"
    if path.startswith("docs/"):
        return "Documentation", "Project guidance", "documentation"
    if path.startswith(".github/"):
        return "Automation", "GitHub Actions", "verification"
    if path.startswith("scripts/tests/"):
        return "Automation", "Website tests", "verification"
    if path.startswith("scripts/"):
        return "Automation", "Developer tooling", "verification"
    return "Repository", "Overview or configuration", "documentation"


def main() -> None:
    raw = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT,
    )
    paths = sorted({
        p.decode() for p in raw.split(b"\0")
        if p and (ROOT / p.decode()).is_file()
    })
    with OUTPUT.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(("path", "system", "category", "free_launch_role"))
        for path in paths:
            if path == "docs/CODEBASE_CATALOG.csv":
                continue
            writer.writerow((path, *classify(path)))
    print(f"Catalogued {len(paths) - (OUTPUT.relative_to(ROOT).as_posix() in paths)} files in {OUTPUT}")


if __name__ == "__main__":
    main()
