#!/bin/bash
# Trae del repo original de Saint's Dragons (rama 1.20.1, commit fijo) el arte y sonido (todos los derechos
# reservados, de Leon Saint). No se suben a este repositorio ni se modifican: se copian al compilar, con las
# carpetas que usa GeckoLib 5 (geckolib/models y geckolib/animations).
set -e
D=$(cd "$(dirname "$0")/.." && pwd)
T=$(mktemp -d)
git clone -q --filter=blob:none --sparse https://github.com/LilRicefield/saints-dragons "$T/sd"
cd "$T/sd"
git checkout -q 18fe61cddf96ee9769b07fadf7145c2c3c8b73d4
git sparse-checkout set common/src/main/resources/assets
A="$D/src/main/resources/assets/saintsdragons"
rm -rf "$D/src/main/resources/assets"
mkdir -p "$D/src/main/resources/assets"
cp -r common/src/main/resources/assets/saintsdragons "$A"
mkdir -p "$A/geckolib"
mv "$A/geo" "$A/geckolib/models"
mv "$A/animations" "$A/geckolib/animations"
python3 "$D/ci/adaptar.py" "$A"
echo "Saint's Dragons: $(find "$A/geckolib/models" -name '*.json' | wc -l) modelos, $(find "$A/geckolib/animations" -name '*.json' | wc -l) animaciones"
rm -rf "$T"
