#!/usr/bin/env bash
# One command between 'gcloud auth login' and a live backend.
#
#   ./scripts/deploy/bootstrap-gcp.sh <gcp-project-id>
#
# Takes a GCP project id, creates or reuses everything the deploy workflow
# (.github/workflows/deploy.yml) needs, and wires the two GitHub secrets that
# turn that workflow's build/deploy jobs from "skipped" into "runs":
#
#   GCP project ............ created if absent, reused if present
#   APIs ................... run, artifactregistry, secretmanager
#   Artifact Registry ...... docker repo 'climaai' in $REGION
#   deploy service account . climaai-deploy@<project>.iam.gserviceaccount.com
#                            with only run.admin, artifactregistry.writer,
#                            iam.serviceAccountUser
#   GitHub secrets ......... GCP_SA_KEY (fresh key), GCP_PROJECT_ID
#   Secret Manager ......... JWT_SECRET (generated), OPENAI_API_KEY,
#                            DATABASE_URL, REDIS_URL — empty placeholders
#                            unless passed as env vars of the same name.
#                            Empty DATABASE_URL/REDIS_URL means the API boots
#                            in degraded mode: weather/consensus work,
#                            DB-backed endpoints answer 503.
#
# Idempotent: safe to re-run after a partial failure; existing resources are
# reused and existing secret values are never overwritten (a non-empty env var
# adds a new secret version, which is additive). Nothing here deletes anything.
#
# Deliberately plain bash, compatible with the bash 3.2 that macOS ships.

set -u

REGION="${REGION:-us-central1}"          # must match REGION in deploy.yml
AR_REPO="climaai"                        # must match AR_REPO in deploy.yml
SA_NAME="climaai-deploy"

say()  { printf '\n==> %s\n' "$1"; }
note() { printf '    %s\n' "$1"; }
die()  { printf 'ERROR: %s\n' "$1" >&2; exit 1; }

# --- arguments ---------------------------------------------------------------

if [ $# -ne 1 ] || [ -z "${1:-}" ]; then
  printf 'Usage: %s <gcp-project-id>\n' "$0" >&2
  printf 'Example: %s climaai-prod\n' "$0" >&2
  exit 1
fi
PROJECT_ID="$1"

# --- preconditions -----------------------------------------------------------

if ! command -v gcloud >/dev/null 2>&1; then
  printf 'ERROR: gcloud is not installed.\n' >&2
  printf 'Install it, then authenticate:\n' >&2
  printf '  brew install --cask google-cloud-sdk   # or https://cloud.google.com/sdk/docs/install\n' >&2
  printf '  gcloud auth login\n' >&2
  exit 1
fi

ACTIVE_ACCOUNT="$(gcloud auth list --filter=status:ACTIVE --format='value(account)' 2>/dev/null || true)"
if [ -z "$ACTIVE_ACCOUNT" ]; then
  printf 'ERROR: gcloud is installed but not authenticated.\n' >&2
  printf 'Run:\n  gcloud auth login\n' >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  printf 'ERROR: gh (GitHub CLI) is not installed; it is needed to set the repo secrets.\n' >&2
  printf 'Install and authenticate:\n  brew install gh\n  gh auth login\n' >&2
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  printf 'ERROR: gh is installed but not authenticated.\nRun:\n  gh auth login\n' >&2
  exit 1
fi

# gh infers the repository from the git remote, so run from the repo root.
cd "$(dirname "$0")/../.." || die "could not cd to the repository root"

say "Bootstrapping GCP project '$PROJECT_ID' (region $REGION) as $ACTIVE_ACCOUNT"
note "BILLING: Cloud Run requires billing to be enabled on the project."
note "That step is unavoidably yours, in the console:"
note "  https://console.cloud.google.com/billing/linkedaccount?project=$PROJECT_ID"

# --- project -----------------------------------------------------------------

say "Project"
if gcloud projects describe "$PROJECT_ID" >/dev/null 2>&1; then
  note "exists — reusing"
else
  gcloud projects create "$PROJECT_ID" || die "could not create project '$PROJECT_ID' (id taken globally? pick another)"
  note "created"
fi

# --- APIs --------------------------------------------------------------------

say "Enabling APIs (run, artifactregistry, secretmanager)"
if ! gcloud services enable \
    run.googleapis.com \
    artifactregistry.googleapis.com \
    secretmanager.googleapis.com \
    --project "$PROJECT_ID"; then
  die "enabling APIs failed — the usual cause is billing not yet enabled on '$PROJECT_ID'. Enable billing in the console, then re-run this script."
fi
note "enabled (no-op when already enabled)"

# --- Artifact Registry repo --------------------------------------------------

say "Artifact Registry repo '$AR_REPO' in $REGION"
if gcloud artifacts repositories describe "$AR_REPO" \
    --location "$REGION" --project "$PROJECT_ID" >/dev/null 2>&1; then
  note "exists — reusing"
else
  gcloud artifacts repositories create "$AR_REPO" \
    --repository-format docker \
    --location "$REGION" \
    --description "ClimaAI backend images" \
    --project "$PROJECT_ID" || die "could not create Artifact Registry repo"
  note "created"
fi

# --- deploy service account --------------------------------------------------

SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
say "Deploy service account $SA_EMAIL"
if gcloud iam service-accounts describe "$SA_EMAIL" --project "$PROJECT_ID" >/dev/null 2>&1; then
  note "exists — reusing"
else
  gcloud iam service-accounts create "$SA_NAME" \
    --display-name "ClimaAI GitHub Actions deploy" \
    --project "$PROJECT_ID" || die "could not create service account"
  note "created"
fi

# The minimum the workflow needs: deploy Cloud Run revisions, push images,
# and act as the service's runtime identity. add-iam-policy-binding is
# idempotent — re-running adds nothing new.
say "Granting roles to the deploy service account"
for role in roles/run.admin roles/artifactregistry.writer roles/iam.serviceAccountUser; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member "serviceAccount:$SA_EMAIL" \
    --role "$role" \
    --condition None \
    --quiet >/dev/null || die "could not grant $role"
  note "$role"
done

# Cloud Run mounts the Secret Manager secrets as env vars at deploy time, and
# it is the *runtime* identity (the default compute SA) that reads them — the
# first deploy fails with a permission error without this grant.
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
RUNTIME_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
say "Granting secretmanager.secretAccessor to the runtime account $RUNTIME_SA"
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:$RUNTIME_SA" \
  --role roles/secretmanager.secretAccessor \
  --condition None \
  --quiet >/dev/null || die "could not grant secretAccessor to the runtime account"
note "granted"

# --- GitHub secrets ----------------------------------------------------------

say "Minting a key and setting the GitHub secrets"
KEY_FILE="$(mktemp)" || die "mktemp failed"
# Remove the key material from disk no matter how the script exits.
trap 'rm -f "$KEY_FILE"' EXIT

gcloud iam service-accounts keys create "$KEY_FILE" \
  --iam-account "$SA_EMAIL" \
  --project "$PROJECT_ID" || die "could not create a service account key (10-key limit reached? prune old ones in the console)"

gh secret set GCP_SA_KEY < "$KEY_FILE" || die "gh secret set GCP_SA_KEY failed"
gh secret set GCP_PROJECT_ID --body "$PROJECT_ID" || die "gh secret set GCP_PROJECT_ID failed"
note "GCP_SA_KEY and GCP_PROJECT_ID set on the repository"
note "(each run mints a fresh key; prune old ones occasionally — SAs cap at 10)"

# --- Secret Manager secrets --------------------------------------------------

# ensure_secret <name> <value>: create if missing; if it exists and a non-empty
# value was passed, add a new version (additive); otherwise leave it untouched.
ensure_secret() {
  name="$1"
  value="$2"
  if gcloud secrets describe "$name" --project "$PROJECT_ID" >/dev/null 2>&1; then
    if [ -n "$value" ]; then
      printf '%s' "$value" | gcloud secrets versions add "$name" \
        --data-file - --project "$PROJECT_ID" >/dev/null || die "could not add a version to secret $name"
      note "$name: exists — added a new version from the env var"
    else
      note "$name: exists — left untouched"
    fi
  else
    printf '%s' "$value" | gcloud secrets create "$name" \
      --replication-policy automatic \
      --data-file - --project "$PROJECT_ID" >/dev/null || die "could not create secret $name"
    if [ -n "$value" ]; then
      note "$name: created from the env var"
    else
      note "$name: created EMPTY (placeholder)"
    fi
  fi
}

say "Secret Manager secrets"
# JWT_SECRET must never ship empty — tokens signed with "" are forgeable.
if [ -z "${JWT_SECRET:-}" ]; then
  JWT_SECRET="$(openssl rand -hex 32)" || die "openssl rand failed"
fi
ensure_secret JWT_SECRET "$JWT_SECRET"
ensure_secret OPENAI_API_KEY "${OPENAI_API_KEY:-}"
ensure_secret DATABASE_URL "${DATABASE_URL:-}"
ensure_secret REDIS_URL "${REDIS_URL:-}"
note ""
note "Empty DATABASE_URL/REDIS_URL is fine for a first deploy: the API boots"
note "in degraded mode — weather and consensus endpoints work, DB-backed"
note "endpoints (auth, locations, subscriptions, personalization) answer 503,"
note "and /health reports per-component status. Fill them in later with e.g.:"
note "  printf '%s' 'postgresql+asyncpg://...' | gcloud secrets versions add DATABASE_URL --data-file - --project $PROJECT_ID"

# --- done --------------------------------------------------------------------

say "Done. What happens next:"
cat <<EOF

  1. Trigger a deploy: push a backend change to main, or run
       gh workflow run 'Deploy to Production'

  2. Watch the run: the 'Deploy to Cloud Run' job prints the service URL
     (https://...run.app) in its 'Show URL' step.

  3. Point the Android app at it: put that URL into gradle.properties as
       climaaiApiBaseUrl=<url>

  Reminder: if the deploy fails with a billing error, enable billing for
  '$PROJECT_ID' in the console (unavoidably a manual step) and re-run the
  workflow — this script does not need to be run again.
EOF
