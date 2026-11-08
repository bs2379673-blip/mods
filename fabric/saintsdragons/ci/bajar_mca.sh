#!/bin/bash
# Trae del repo original de Saint's Dragons (rama 1.20.1, commit fijo) el arte y sonido (todos los derechos
# reservados, de Leon Saint) y los datos (MIT). El arte no se sube a este repositorio ni se modifica: se copia
# al compilar con las carpetas que usa 1.21.11 / GeckoLib 5, y los datos se pasan al formato de 1.21.11.
set -e
D=$(cd "$(dirname "$0")/.." && pwd)
T=$(mktemp -d)
git clone -q --filter=blob:none --sparse https://github.com/LilRicefield/saints-dragons "$T/sd"
cd "$T/sd"
git checkout -q 18fe61cddf96ee9769b07fadf7145c2c3c8b73d4
git sparse-checkout set common/src/main/resources common/src/generated/resources fabric/src/main/resources/data
python3 "$D/ci/adaptar.py" "$T/sd" "$D/src/main/resources" "$D/src/main/java/com/leon/saintsdragons/common/registry/ModItems.java"
A="$D/src/main/resources/assets/saintsdragons"
echo "Saint's Dragons: $(find "$A/geckolib/models" -name '*.json' | wc -l) modelos, $(find "$A/geckolib/animations" -name '*.json' | wc -l) animaciones, $(find "$D/src/main/resources/data" -name '*.json' | wc -l) datos"
rm -rf "$T"
