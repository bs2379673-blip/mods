#!/bin/bash
# Trae del repo original de Kaleidoscope Immersive Eating (rama fabric1.21.1) los modelos, animaciones y texturas
# (CC BY-NC-ND 4.0, del Immersive_Eating Team). No se suben a este repositorio ni se modifican: se copian al
# compilar, con la carpeta que usa GeckoLib 5 (geckolib/models y geckolib/animations).
set -e
D=$(cd "$(dirname "$0")/.." && pwd)
T=$(mktemp -d)
git clone -q --filter=blob:none --sparse https://github.com/MemoryToame/ImmersiveEating "$T/ie"
cd "$T/ie"
git checkout -q eb090625b4b70270278599eb37a763c2883077fd
git sparse-checkout set src/main/resources/assets/food/geo src/main/resources/assets/food/animations src/main/resources/assets/food/textures
R=src/main/resources/assets/food
A="$D/src/main/resources/assets/food"
rm -rf "$A/geckolib" "$A/textures"
mkdir -p "$A/geckolib/models/item" "$A/geckolib/animations/item"
cp $R/geo/*.json "$A/geckolib/models/item/"
cp $R/animations/*.json "$A/geckolib/animations/item/"
cp -r $R/textures "$A/textures"
echo "Immersive Eating: $(ls "$A/geckolib/models/item" | wc -l) modelos, $(ls "$A/geckolib/animations/item" | wc -l) animaciones"
rm -rf "$T"
