#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  Topo - rapport d'un appareil physique
#
#  A lancer avant et apres l'installation, sur un vrai telephone. Rassemble ce
#  qu'aucun emulateur ne peut dire :
#
#   - la taille de page du noyau. C'est LA verification que tout le Sprint 0
#     attendait : jusqu'ici elle n'a tourne que sur un emulateur x86_64 ;
#   - l'ABI reelle, pour confirmer que la bibliotheque arm64 chargee est bien
#     celle auditee ;
#   - le constructeur et la surcouche, qui decident du masquage d'icone et de
#     l'agressivite du tueur de processus.
#
#  Usage : scripts/device_report.sh [serial]
# ---------------------------------------------------------------------------
set -uo pipefail

serial="${1:-}"
adb_cmd=(adb)
[[ -n "$serial" ]] && adb_cmd=(adb -s "$serial")

prop() { "${adb_cmd[@]}" shell getprop "$1" 2>/dev/null | tr -d '\r'; }

if ! "${adb_cmd[@]}" shell true >/dev/null 2>&1; then
  echo "Aucun appareil joignable. Verifier le cable, le debogage USB et" >&2
  echo "l'autorisation affichee sur le telephone." >&2
  exit 1
fi

echo "=== Appareil ==="
printf '  %-22s %s %s\n' "Modele"        "$(prop ro.product.manufacturer)" "$(prop ro.product.model)"
printf '  %-22s %s (API %s)\n' "Android"  "$(prop ro.build.version.release)" "$(prop ro.build.version.sdk)"
printf '  %-22s %s\n' "Surcouche"        "$(prop ro.build.version.incremental)"
printf '  %-22s %s\n' "SoC"              "$(prop ro.board.platform)"
printf '  %-22s %s\n' "ABI principale"   "$(prop ro.product.cpu.abi)"
printf '  %-22s %s\n' "ABI supportees"   "$(prop ro.product.cpu.abilist)"

echo
echo "=== Memoire ==="
page_size="$("${adb_cmd[@]}" shell getconf PAGE_SIZE 2>/dev/null | tr -d '\r')"
printf '  %-22s %s octets\n' "Taille de page" "$page_size"
if [[ "$page_size" == "16384" ]]; then
  echo "  -> noyau 16 Ko : l'alignement des .so est reellement exerce ici."
elif [[ "$page_size" == "4096" ]]; then
  echo "  -> noyau 4 Ko : verifie la retrocompatibilite, pas la contrainte 16 Ko."
else
  echo "  -> taille inattendue, a examiner." >&2
fi

echo
echo "=== Application ==="
if "${adb_cmd[@]}" shell pm list packages 2>/dev/null | grep -q "package:com.topo"; then
  printf '  %-22s %s\n' "Version installee" \
    "$("${adb_cmd[@]}" shell dumpsys package com.topo 2>/dev/null | grep -m1 versionName | tr -d ' \r')"
  echo "  Bibliotheques natives chargeables :"
  "${adb_cmd[@]}" shell run-as com.topo ls -l lib/ 2>/dev/null | sed 's/^/    /' || \
    echo "    (run-as indisponible : build release ou appareil non debogable)"
else
  echo "  Topo n'est pas installee."
fi
