#!/usr/bin/env bash
# Reachability gate — catches code that compiles, lints and unit-tests clean but
# can never run.
#
#   ./scripts/ci/check-reachability.sh
#
# Two shapes of defect, neither visible to the compiler, because nothing is
# wrong at any call site — the call site itself is what is missing:
#
#   1. a route registered in the NavHost that nothing ever navigate()s to.
#      The screen compiles, previews, and is unreachable in the shipped app.
#   2. an initialize() nobody calls, so everything it was supposed to set up
#      stays at its default forever — a stored flag reads as false, a
#      notification channel is never created — and the failure is silent.
#
# Needs no JDK, no Android SDK and no Gradle: it reads source text, so it can
# gate a PR in seconds before any build starts.
#
# Deliberately bash, not zsh: this runs on the ubuntu-latest CI runners, which
# ship no zsh. A zsh shebang there fails as "cannot execute: required file not
# found" — an error naming neither zsh nor this script.

set -u
cd "$(dirname "$0")/../.." || exit 1

NAV=android/app/src/main/kotlin/com/climaai/app/ui/navigation/AppNavigation.kt
APP_SRC=android/app/src/main

fail=0
checked=0

err() { # <file> <line> <title> <message>
  printf '::error file=%s,line=%s,title=%s::%s\n' "$1" "$2" "$3" "$4"
  fail=1
}

gate_broken() { # <message> — the gate itself cannot run; never pass silently
  printf '::error title=Reachability gate::%s\n' "$1"
  exit 2
}

# Strip // line comments so a commented-out call never counts as a call site.
# The second expression leaves "https://" alone by requiring the character
# before // to not be a colon.
strip_comments() { sed -e 's,^[[:space:]]*//.*,,' -e 's,\([^:]\)//.*,\1,' "$1"; }

[[ -f $NAV ]] || gate_broken "$NAV not found — the navigation graph moved, so this gate is checking nothing. Update NAV in scripts/ci/check-reachability.sh."

# One whitespace-flattened copy of the app's Kotlin sources. Calls are routinely
# split over several lines:
#
#     navController.navigate(
#         Screen.Paywall.route
#     )
#
# so a line-oriented grep reports "no call sites" for a call that plainly exists.
# Flattening first is what makes the search honest.
FLAT=$(mktemp)
trap 'rm -f "$FLAT"' EXIT
while IFS= read -r f; do
  strip_comments "$f"
done < <(find "$APP_SRC" -name '*.kt' -not -path '*/build/*') \
  | tr '\n' ' ' | tr -s '[:space:]' ' ' > "$FLAT"

[[ -s $FLAT ]] || gate_broken "no Kotlin sources found under $APP_SRC"

# ---------------------------------------------------------------------------
# 1. Registered routes with no inbound navigation edge
# ---------------------------------------------------------------------------

printf '\nNavHost routes\n'

# Destinations reached through a table instead of by name.
#
#     private val mainTabs = listOf(MainTab(Screen.Radar, ...), ...)
#     mainTabs.forEach { tab -> navController.navigate(tab.screen.route) }
#
# navigate(Screen.Radar.route) is then nowhere in the source, so the literal
# search below calls a bottom-bar tab unreachable — the exact opposite of the
# truth. Credit the Screens a table holds, but only when something actually
# iterates that table and navigates in the loop body: a list nobody walks is
# still a dead end, and crediting it would make this gate a rubber stamp.
TABLE_REACHABLE=$(awk '
  BEGIN { RS = "\001" }
  {
    s = $0

    # Tables: val NAME = listOf( ... ), paren-balanced so nested calls
    # (MainTab(Screen.Radar, ...)) do not end the list early.
    rest = s
    while (match(rest, /(val|var) [A-Za-z0-9_]+ *= *(listOf|listOfNotNull|arrayOf|setOf) *\(/)) {
      name = substr(rest, RSTART, RLENGTH)
      sub(/^(val|var) /, "", name)
      sub(/ *=.*$/, "", name)
      start = RSTART + RLENGTH
      depth = 1; i = start; n = length(rest)
      while (i <= n && depth > 0) {
        c = substr(rest, i, 1)
        if (c == "(") depth++
        else if (c == ")") depth--
        i++
      }
      tbl[name] = tbl[name] " " substr(rest, start, i - start - 1)
      rest = substr(rest, start)
    }

    # Iterations whose body navigates, brace-balanced for the same reason.
    rest = s
    while (match(rest, /[A-Za-z0-9_]+ *\. *(forEach|forEachIndexed|map|mapIndexed) *\{/)) {
      name = substr(rest, RSTART, RLENGTH)
      sub(/ *\..*$/, "", name)
      start = RSTART + RLENGTH
      depth = 1; i = start; n = length(rest)
      while (i <= n && depth > 0) {
        c = substr(rest, i, 1)
        if (c == "{") depth++
        else if (c == "}") depth--
        i++
      }
      body = substr(rest, start, i - start - 1)
      if (body ~ /navigate *\(/ && (name in tbl)) walked[name] = 1
      rest = substr(rest, start)
    }

    for (name in walked) {
      b = tbl[name]
      while (match(b, /Screen\.[A-Za-z0-9_]+/)) {
        print substr(b, RSTART + 7, RLENGTH - 7)
        b = substr(b, RSTART + RLENGTH)
      }
    }
  }
' "$FLAT" | sort -u)

# Everything above the first composable() registration is the code that decides
# where the graph starts, so any Screen named up there is a legitimate entry
# point rather than a route that needs an inbound edge.
first_composable=$(grep -n 'composable(' "$NAV" | head -1 | cut -d: -f1)
[[ -n $first_composable ]] || gate_broken "$NAV registers no composable() routes"

START_DESTS=$(head -n "$((first_composable - 1))" "$NAV" \
  | grep -o 'Screen\.[A-Za-z0-9_]*\.route' \
  | sed -e 's/^Screen\.//' -e 's/\.route$//' | sort -u)

while IFS= read -r hit; do
  line=${hit%%:*}
  name=$(printf '%s' "$hit" | sed -n 's/.*composable(Screen\.\([A-Za-z0-9_]*\)\.route.*/\1/p')
  [[ -n $name ]] || continue
  checked=$((checked + 1))

  if printf '%s\n' "$START_DESTS" | grep -qx "$name"; then
    printf '  %-14s start destination\n' "$name"
    continue
  fi

  # Count both spellings of an inbound edge: through the sealed-class constant
  # and through the raw route string, which is just as valid a way to reach it.
  n=$(grep -oE "navigate\([[:space:]]*Screen\.$name\.route" "$FLAT" | wc -l | tr -d ' ')
  route=$(sed -n "s/.*object[[:space:]]\{1,\}$name[[:space:]]*:[[:space:]]*Screen(\"\([^\"]*\)\").*/\1/p" "$NAV" | head -1)
  if [[ -n $route ]]; then
    n=$((n + $(grep -oF "navigate(\"$route\"" "$FLAT" | wc -l | tr -d ' ')))
  fi

  if [[ $n -eq 0 ]] && printf '%s\n' "$TABLE_REACHABLE" | grep -qx "$name"; then
    printf '  %-14s from a destination table\n' "$name"
    continue
  fi

  if [[ $n -eq 0 ]]; then
    printf '  %-14s UNREACHABLE\n' "$name"
    err "$NAV" "$line" "Unreachable route" \
      "Screen.$name is registered in the NavHost but no navigate() anywhere in $APP_SRC targets it, and it is not a start destination. The screen ships in the APK and no user can ever open it. Either give it an inbound edge or drop the registration."
  else
    printf '  %-14s %d inbound\n' "$name" "$n"
  fi
done < <(grep -n 'composable(Screen\.[A-Za-z0-9_]*\.route' "$NAV")

# ---------------------------------------------------------------------------
# 2. initialize() with no call sites
# ---------------------------------------------------------------------------

printf '\ninitialize() call sites\n'

while IFS= read -r hit; do
  f=${hit%%:*}
  rest=${hit#*:}
  ln=${rest%%:*}

  # The enclosing type is the last top-level class/object declared above it.
  owner=$(head -n "$ln" "$f" \
    | grep -E '^[a-z ]*(class|object) [A-Za-z0-9_]+' | tail -1 \
    | sed -E 's/^[a-z ]*(class|object) ([A-Za-z0-9_]+).*/\2/')
  [[ -n $owner ]] || continue
  checked=$((checked + 1))

  # Receivers a call could be written through: the type itself (object/companion
  # call), the conventional instance name, and every val/var actually bound to
  # the type. Resolving the last of those is what keeps `notificationService.
  # initialize()` from being reported as missing just because the variable is
  # not named after its class.
  head=$(printf '%s' "${owner:0:1}" | tr 'A-Z' 'a-z')
  recvs=$(
    {
      printf '%s\n%s\n' "$owner" "$head${owner:1}"
      grep -oE "(val|var)[[:space:]]+[A-Za-z0-9_]+[[:space:]]*[:=][[:space:]]*$owner[^A-Za-z0-9_]" "$FLAT" \
        | sed -E 's/(val|var)[[:space:]]+([A-Za-z0-9_]+).*/\2/'
    } | sort -u
  )

  # The optional `( ... )` covers construct-and-call-in-one-expression:
  #
  #     NotificationService(this).initialize()
  #
  # which is the ordinary way to call a setup method on a throwaway instance.
  # Without it the receiver has to be a bare name and this reads as no call site
  # at all — the gate then demands a fix for code that is already correct.
  # One level of nesting is tolerated so NotificationService(getApplication())
  # still matches.
  calls=0
  for r in $recvs; do
    calls=$((calls + $(grep -oE "[^A-Za-z0-9_.]$r[[:space:]]*(\((\([^)]*\)|[^()])*\))?[[:space:]]*\.[[:space:]]*initialize[[:space:]]*\(" "$FLAT" | wc -l | tr -d ' ')))
  done

  # A call from inside the declaring file counts too (init { initialize() }).
  # Every `fun initialize(` also matches the bare-call pattern, so subtract the
  # declarations back out rather than trying to exclude them in one regex.
  bare=$(strip_comments "$f" | tr '\n' ' ' \
    | grep -oE '[^A-Za-z0-9_.]initialize[[:space:]]*\(' | wc -l | tr -d ' ')
  decl=$(strip_comments "$f" | tr '\n' ' ' \
    | grep -oE 'fun[[:space:]]+initialize[[:space:]]*\(' | wc -l | tr -d ' ')
  calls=$((calls + bare - decl))

  if [[ $calls -le 0 ]]; then
    printf '  %-24s NEVER CALLED\n' "$owner.initialize"
    err "$f" "$ln" "initialize() is never called" \
      "$owner.initialize() has no call site in $APP_SRC. Everything it sets up stays at its default and nothing reports an error — the app just behaves as if the setup never happened. Call it, or delete it. (Receivers searched: $(printf '%s' "$recvs" | tr '\n' ' ' | sed 's/ $//'). If it is called through some other name, bind that variable to the type so this gate can see it.)"
  else
    printf '  %-24s %d call site(s)\n' "$owner.initialize" "$calls"
  fi
done < <(grep -rn 'fun initialize(' "$APP_SRC" --include='*.kt')

printf '\n%d symbols checked. ' "$checked"
if [[ $fail -eq 0 ]]; then
  printf 'All reachable.\n\n'
else
  printf 'Unreachable code found — see the annotations above.\n\n'
fi
exit $fail
