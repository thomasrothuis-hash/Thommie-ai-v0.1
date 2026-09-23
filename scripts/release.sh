#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

message="${*:-}"
if [[ -z "$message" ]]; then
  echo 'Gebruik: scripts/release.sh "commit message"'
  exit 2
fi

branch="$(git branch --show-current)"
if [[ "$branch" != "main" ]]; then
  echo "STOP: huidige branch is '$branch', verwacht 'main'."
  exit 1
fi

echo "== MAATJE release preflight =="
git fetch origin

if ! git merge-base --is-ancestor origin/main HEAD; then
  echo "STOP: lokale main loopt achter of is gedivergeerd van origin/main."
  echo "Werk eerst bij met: git pull --ff-only"
  exit 1
fi

if [[ -z "$(git status --porcelain)" ]]; then
  echo "Geen wijzigingen om te releasen."
  exit 0
fi

git diff --check

bad_files="$(
  git status --porcelain |
    sed -E 's/^.. //' |
    grep -Ei '(\.p12|\.jks|\.pem|\.keystore|(^|/)\.env)$' || true
)"

if [[ -n "$bad_files" ]]; then
  echo "STOP: mogelijk geheim/signing-bestand in de wijzigingen:"
  echo "$bad_files"
  exit 1
fi

version="$(
  sed -nE "s/.*versionName[[:space:]]+['\"]([^'\"]+)['\"].*/\1/p" app/build.gradle |
    head -n 1
)"

if [[ -z "$version" ]]; then
  echo "STOP: versionName niet gevonden."
  exit 1
fi

if ! grep -q "MAATJE_v${version}" .github/workflows/build-apk.yml; then
  echo "STOP: workflow-artifact komt niet overeen met versionName ${version}."
  exit 1
fi

if ! grep -q "v${version}" app/src/main/java/nl/thommie/ai/MainActivity.java; then
  echo "STOP: MainActivity bevat versie v${version} niet."
  exit 1
fi

echo "Versie: v${version}"
echo "Wijzigingen:"
git status --short

git add -A
git diff --cached --check
git commit -m "$message"
git push origin main

echo
echo "KLAAR"
echo "Commit: $(git rev-parse --short HEAD)"
echo "GitHub Actions bouwt nu MAATJE v${version}."
