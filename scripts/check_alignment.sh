#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  BetaClimb - audit de conformite 16 Ko (Android 15+)
#
#  Deux verifications, toutes deux necessaires :
#
#   1. ALIGNEMENT ELF : chaque segment LOAD de chaque .so doit etre aligne sur
#      2**14 (16384 octets). Un seul segment en 2**12 suffit a faire planter
#      l'application au System.loadLibrary sur un noyau 16 Ko, et a la faire
#      rejeter par Google Play.
#
#   2. PACKAGING ZIP : une .so compressee dans l'APK est extraite a
#      l'installation et perd son alignement. useLegacyPackaging=false la laisse
#      non compressee ; zipalign -P 16 verifie sa position dans l'archive.
#
#  L'exigence porte sur TOUTES les .so embarquees, y compris celles des
#  dependances tierces - pas seulement les notres. C'est pourquoi, des qu'un APK
#  est fourni, ce sont les bibliotheques REELLEMENT EMPAQUETEES qui sont
#  auditees, et non les artefacts intermediaires du build.
#
#  La preuve d'EXECUTION complementaire, c'est vision/src/androidTest.
#
#  Usage :
#    scripts/check_alignment.sh                 # audite les .so construites
#    scripts/check_alignment.sh chemin/app.apk  # audite le contenu de l'APK
# ---------------------------------------------------------------------------
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${LOCALAPPDATA:-$HOME}/Android/Sdk}}"

OBJDUMP="$(find "$SDK/ndk" -name 'llvm-objdump*' -path '*bin*' 2>/dev/null | head -1)"
if [[ -z "$OBJDUMP" ]]; then
  echo "llvm-objdump introuvable sous $SDK/ndk" >&2
  exit 1
fi

status=0
apk="${1:-}"
workdir=""

if [[ -n "$apk" ]]; then
  if [[ ! -f "$apk" ]]; then
    echo "APK introuvable : $apk" >&2
    exit 1
  fi
  workdir="$(mktemp -d)"
  trap 'rm -rf "$workdir"' EXIT
  unzip -q -o "$apk" 'lib/*/*.so' -d "$workdir" || true
  echo "Source : $apk (bibliotheques empaquetees)"
  mapfile -t libs < <(find "$workdir/lib" -name '*.so' 2>/dev/null | sort)
else
  echo "Source : artefacts de build (merged_native_libs)"
  mapfile -t libs < <(find "$ROOT" -path '*merged_native_libs*' -name '*.so' 2>/dev/null | sort -u)
fi

if [[ "${#libs[@]}" -eq 0 ]]; then
  echo "Aucune .so trouvee. Lancer d'abord : ./gradlew :app:assembleDebug" >&2
  exit 1
fi

echo
for so in "${libs[@]}"; do
  abi="$(basename "$(dirname "$so")")"
  name="$(basename "$so")"

  # awk sur le PREMIER champ : un grep sur "LOAD" matcherait aussi le chemin du
  # fichier (par exemple un projet situe dans un dossier "Downloads").
  aligns="$("$OBJDUMP" -p "$so" | awk '$1 == "LOAD" { print $NF }')"

  if [[ -z "$aligns" ]]; then
    printf '  %-12s %-30s AUCUN SEGMENT LOAD LISIBLE\n' "$abi" "$name" >&2
    status=1
    continue
  fi

  bad="$(printf '%s\n' "$aligns" | grep -cv '^2\*\*14$')"
  total="$(printf '%s\n' "$aligns" | wc -l | tr -d ' ')"

  if [[ "$bad" -eq 0 ]]; then
    printf '  %-12s %-30s %s LOAD, tous en 2**14 — CONFORME\n' "$abi" "$name" "$total"
  else
    printf '  %-12s %-30s %s/%s NON alignes — NON CONFORME\n' "$abi" "$name" "$bad" "$total" >&2
    printf '%s\n' "$aligns" | sed 's/^/      align /' >&2
    status=1
  fi
done

echo
echo "${#libs[@]} bibliotheque(s) auditee(s)."

if [[ -n "$apk" ]]; then
  zipalign="$(find "$SDK/build-tools" -name 'zipalign*' 2>/dev/null | sort -r | head -1)"
  if [[ -z "$zipalign" ]]; then
    echo "zipalign introuvable sous $SDK/build-tools" >&2
    exit 1
  fi
  echo
  if "$zipalign" -c -P 16 4 "$apk"; then
    echo "Packaging : APK aligne 16 Ko — CONFORME"
  else
    echo "Packaging : APK NON aligne 16 Ko" >&2
    status=1
  fi
fi

if [[ "$status" -eq 0 ]]; then
  echo
  echo "Conformite 16 Ko validee."
fi

exit "$status"
