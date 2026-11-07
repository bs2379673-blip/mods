#!/bin/bash
# Trae de Minecraft Comes Alive (rama 1.21.11, licencia GPL-3.0) las texturas de piel, caras, pelo y
# ropa, los mapas de color y las listas de nombres. No se suben al repositorio: se copian al compilar.
set -e
D=$(cd "$(dirname "$0")/.." && pwd)
T=$(mktemp -d)
git clone -q --depth 1 --filter=blob:none --sparse -b 1.21.11 https://github.com/Luke100000/minecraft-comes-alive "$T/mca"
cd "$T/mca"
git sparse-checkout set common/src/main/resources/assets/mca/skins common/src/main/resources/assets/mca/textures/colormap \
    common/src/main/resources/data/mca/skins common/src/main/resources/data/mca/mca_names
R=common/src/main/resources
A="$D/src/main/resources/assets/starvillagers/mca"
rm -rf "$A" "$D/src/main/resources/starvillagers_mca"
mkdir -p "$A/skins" "$A/colormap" "$D/src/main/resources/starvillagers_mca"
cp -r $R/assets/mca/skins/skin $R/assets/mca/skins/hair "$A/skins/"
mkdir -p "$A/skins/face" "$A/skins/clothing"
cp -r $R/assets/mca/skins/face/normal "$A/skins/face/"
cp -r $R/assets/mca/skins/clothing/normal "$A/skins/clothing/"
cp $R/assets/mca/textures/colormap/villager_skin.png $R/assets/mca/textures/colormap/villager_hair.png "$A/colormap/"
cp -r $R/data/mca/skins "$D/src/main/resources/starvillagers_mca/listas"
cp -r $R/data/mca/mca_names "$D/src/main/resources/starvillagers_mca/nombres"
cp LICENSE "$D/src/main/resources/starvillagers_mca/LICENSE-MCA-GPL3.txt"
# indice de archivos (el juego no puede listar carpetas dentro del jar)
(cd "$D/src/main/resources/assets/starvillagers/mca/skins" && find . -name "*.png" | sed 's#^\./##' | sort) > "$D/src/main/resources/starvillagers_mca/texturas.txt"
(cd "$D/src/main/resources/starvillagers_mca/nombres" && find . -name "*.json" | sed 's#^\./##' | sort) > "$D/src/main/resources/starvillagers_mca/nombres.txt"
echo "MCA: $(wc -l < "$D/src/main/resources/starvillagers_mca/texturas.txt") texturas"
rm -rf "$T"
