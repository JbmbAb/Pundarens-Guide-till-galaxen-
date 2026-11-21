#!/usr/bin/env bash
# Kontroll av den byggda APK:n: behörigheter (ingen INTERNET), inga Google-tjänster i dex-koden,
# inbyggda bibliotek, storlek och SHA-256. Utskriften läggs i etapp2-testutdata.
set -euo pipefail
APK="${1:?ange sökväg till APK}"
BT="${ANDROID_HOME:-/opt/android-sdk}/build-tools/36.0.0"
echo "# APK: $APK"
echo "# storlek: $(stat -c %s "$APK") byte"
echo "# sha256: $(sha256sum "$APK" | cut -d' ' -f1)"
echo "== badging"; "$BT/aapt2" dump badging "$APK" | grep -E "^package|sdkVersion|targetSdk|native-code|application-label:"
echo "== behörigheter"; "$BT/aapt2" dump permissions "$APK"
# INTERNET finns sedan etapp 6 och får bara användas av Convoy (beslutsloggen, avsnitt 12).
if "$BT/aapt2" dump permissions "$APK" | grep -q "android.permission.INTERNET"; then echo "OK: INTERNET finns (Convoy)"; else echo "FEL: INTERNET saknas, Convoy kan inte nå reläet"; exit 1; fi
echo "== inbyggda bibliotek"; unzip -l "$APK" | grep -E "\.so$" | awk '{print $1, $4}'
echo "== tillgångar"; unzip -l "$APK" | grep -E "assets/" | awk '{print $1, $4}'
T=$(mktemp -d); unzip -q -o "$APK" 'classes*.dex' -d "$T"
echo "== dex-klasser per förbjudet prefix"
fel=0
for p in "Lcom/google/android/gms/" "Lcom/google/firebase/" "Lcom/google/crypto/tink/" "Lcom/google/android/libraries/maps/" "Lcom/google/maps/" "Lcom/google/android/play/"; do
  n=$(for d in "$T"/classes*.dex; do "$BT/dexdump" "$d" 2>/dev/null | grep -E "^  Class descriptor  : '$p" || true; done | wc -l)
  echo "$p $n"; [ "$n" -eq 0 ] || fel=1
done
echo "== övriga com.google-klasser (granskade undantag förväntas: gson, listenablefuture)"
for d in "$T"/classes*.dex; do "$BT/dexdump" "$d" 2>/dev/null | grep -oE "^  Class descriptor  : 'Lcom/google/[a-z]+/[a-z]+" || true; done | sed "s/.*'L//" | sort | uniq -c
rm -rf "$T"
[ $fel -eq 0 ] && echo "OK: inga förbjudna Google-klasser i dex" || { echo "FEL: förbjudna Google-klasser"; exit 1; }
