#!/bin/bash
# Prueba visual (NeoForge 1.21.1): servidor NeoForge de produccion con los mods + cliente de desarrollo
# (ForgeGradle) en una pantalla virtual. El servidor arma escenas y el cliente saca fotos.
set +e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
S=visrun
rm -rf $S && mkdir -p $S/mods
NEO=$(grep neo_version gradle.properties | cut -d= -f2)
curl -sSL -o $S/installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar"
( cd $S && java -jar installer.jar --installServer > install.log 2>&1 ); tail -3 $S/install.log
cp libs/playeranimator-1.jar $S/mods/
cp ../out/annisnpc-*.jar ../out/annisinteract-*.jar ../out/annisvfx-*.jar ../out/annis_compat-*.jar ../out/starfest-*.jar ../out/starsync-*.jar ../out/starspirits-*.jar ../out/starriders-*.jar libs/cinematic-0.18.30.jar libs/waterframes-1.jar libs/creativecore-1.jar $S/mods/
# StarFest: una cancion de prueba (128 BPM, parte fuerte entre 20 y 32 s) en la carpeta de musica del servidor
mkdir -p $S/config/starfest/musica && cp ci/starfest/prueba.ogg $S/config/starfest/musica/
ls $S/mods
echo "eula=true" > $S/eula.txt
# interacciones por archivo (prueba): usan las animaciones del paquete exportado
mkdir -p $S/config
cat > $S/config/annisinteract_extra.json <<'EOJ'
{"interacciones": [
  {"id": "prueba_abrazo", "nombre": "Abrazo (archivo)", "yo": "punchy:annis_abrazo", "otro": "punchy:annis_abrazo_cuello",
   "lugar": "frente", "distancia": 0.36, "segundos": 4, "activa": true},
  {"id": "prueba_brazos", "nombre": "En brazos (archivo)", "yo": "punchy:annis_cargar_brazos", "otro": "punchy:annis_en_brazos",
   "lugar": "encima", "adelante": 0.41, "arriba": 0.69, "izquierda": 0, "segundos": 0, "activa": true}
]}
EOJ
cat > $S/server.properties <<EOP
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
view-distance=5
simulation-distance=5
difficulty=peaceful
enable-rcon=true
rcon.port=25577
rcon.password=annistest
EOP
( cd $S && echo "-Xmx2G" > user_jvm_args.txt && timeout 900 bash run.sh nogui > server.log 2>&1 ) &
for i in $(seq 1 160); do grep -q 'Done (' $S/server.log 2>/dev/null && break; sleep 3; done
grep -q 'Done (' $S/server.log || {
  echo "::warning::Visual> el servidor no arranco. install: $(tail -5 $S/install.log | tr '\n' '|' | cut -c1-800)"
  echo "::warning::Visual> ls: $(ls $S | tr '\n' ' ')"
  echo "::warning::Visual servidor> $(grep -E 'Exception|ERROR|Error|Caused by|Missing|requires' $S/server.log | head -25 | tr '\n' '|' | cut -c1-6000)"
  echo "::warning::Visual servidor fin> $(tail -25 $S/server.log | tr '\n' '|' | cut -c1-5000)"
  exit 0; }

# opciones del cliente: sin pantallas de bienvenida, sin sonido
mkdir -p run
cat > run/options.txt <<EOO
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
tutorialStep:none
pauseOnLostFocus:false
renderDistance:5
simulationDistance:5
guiScale:2
soundCategory_master:0.0
renderClouds:"false"
narrator:0
resourcePacks:["vanilla","file/Annis_Animaciones.zip","file/Loco_Prueba.zip"]
EOO
# paquete de animaciones exportado (lo lee Annis: asi la prueba usa las animaciones del paquete)
mkdir -p run/resourcepacks run/mods
rm -f run/mods/*.jar; cp libs/playeranimator-1.jar libs/punchymoves-1.jar libs/punchystudio-1.jar run/mods/ 2>/dev/null
# clips de prueba con los nombres de Punchy / Fluid & Foolish (idle, normal_forward, sneaking) para la caminata de los NPC
(cd ci/locoprueba && zip -qr ../../run/resourcepacks/Loco_Prueba.zip .)
# Cinematic Interactions (MIT) para probar Annis Compat
cp libs/cinematic-0.18.30.jar run/mods/
cp libs/waterframes-1.jar libs/watermedia-1.jar libs/creativecore-1.jar run/mods/ 2>/dev/null
# Figura + avatar de prueba para Annis Compat
cp libs/figura-1.jar run/mods/ 2>/dev/null
rm -rf run/figura/avatars/prueba; mkdir -p run/figura/avatars; cp -r ci/figura/prueba run/figura/avatars/
cp ../out/Annis_Animaciones.zip run/resourcepacks/ 2>/dev/null || true
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5
( timeout 840 xvfb-run -a -s "-screen 0 1280x720x24" gradle :annisinteract:runClient -PannisTest --no-daemon > client.log 2>&1 ) &
CP=$!
for i in $(seq 1 160); do grep -q 'joined the game' $S/server.log 2>/dev/null && break; sleep 3; done
if ! grep -q 'joined the game' $S/server.log; then
  echo "::warning::Visual> el cliente no entro: $(grep -E 'Exception|Error|error|FAILED' client.log | head -30 | tr '\n' '|' | cut -c1-4000)"
  echo "::warning::Visual cliente errores> $(grep -B2 -A12 -E 'Exception|Caused by' run/logs/latest.log 2>/dev/null | grep -v 'Narrator\|flite\|text2speech' | grep -E 'Exception|Caused|at com.annis|at net.minecraft|annis' | head -40 | tr '\n' '|' | cut -c1-6000)"
  echo "::warning::Visual cliente log> $(tail -40 run/logs/latest.log 2>/dev/null | tr '\n' '|' | cut -c1-5000)"
  mkdir -p ../out; for p in $(pgrep -f 'java.*(neoforge|DevLaunch|bootstrap)'); do jstack $p >> ../out/jstack.txt 2>&1; done
  pkill -f runClient; pkill -f java; exit 0
fi
sleep 25
T='tellraw @a {"text":"[TEST] '
E='"}'
# --- StarSync: dos pantallas de WaterFrames vinculadas (se cambia una y la otra la sigue) ---
IMG="https://raw.githubusercontent.com/bs2379673-blip/mods/forge-1.20.1/neoforge/starfest/src/main/resources/assets/starfest/textures/cielo/galaxia.png"
SYNC=('time set 6000' "${T}hud no${E}" 'fill 78 -60 84 84 -57 84 minecraft:white_concrete' 'setblock 79 -59 83 waterframes:frame' 'setblock 83 -59 83 waterframes:frame'
  'starsync vincular sala 79 -59 83' 'starsync vincular sala 83 -59 83' 'starsync grupos'
  "data merge block 79 -59 83 {url:\"$IMG\",active:1b}" 'sleep 2' 'data get block 83 -59 83 url' 'data get block 83 -59 83 active'
  'tp @a 81 -59 77 0 5' 'sleep 6' "${T}foto w01_pantallas_vinculadas${E}"
  "data merge block 83 -59 83 {url:\"x\"}" 'sleep 2' 'data get block 79 -59 83 url' 'sleep 1')
python3 ci/rcon.py 25577 annistest "${SYNC[@]}" 2>&1 | tee rcon-starsync.txt
# --- StarSpirits: punto de extraccion, caravana, maceta y los 8 espiritus en fila ---
SPI=('time set 6000' 'weather clear' "${T}hud no${E}" "${T}camara primera${E}" 'fill 132 -60 136 152 -54 156 air'
  'setblock 140 -60 146 starspirits:punto_extraccion' 'setblock 136 -60 146 starspirits:maceta'
  'tp @a 140 -60 141.5 0 14' 'sleep 2.5' "${T}foto p01_punto_listo${E}"
  'starspirits extraer 140 -60 146 @r luminon' 'sleep 0.8' "${T}foto p02_extrae_a${E}" 'sleep 1.0' "${T}foto p03_extrae_b${E}"
  'sleep 0.9' "${T}foto p04_extrae_c${E}" 'sleep 0.6' "${T}foto p05_extrae_d${E}" 'sleep 1.6' "${T}foto p06_salio${E}")
for s in champi margarita brotecito campanita rosita loto bonsai; do SPI+=("starspirits dar @a $s"); done
SPI+=('tp @a 140 -60 141 180 10' 'sleep 2.5' "${T}camara frente${E}" 'sleep 1' "${T}foto p07_caravana_frente${E}"
  "${T}caminar si${E}" 'sleep 2.5' "${T}foto p08_caravana_corre${E}" 'sleep 0.6' "${T}foto p09_caravana_corre_b${E}" "${T}caminar no${E}"
  'sleep 2.5' "${T}foto p10_caravana_quieta${E}"
  'tp @a 136 -60 143.6 0 28' "${T}camara primera${E}" 'starspirits plantar 136 -60 146 @r 2' 'sleep 2' "${T}foto p11_maceta${E}"
  'starspirits quitar @r todos' 'kill @e[type=starspirits:espiritu]')
X=141; for s in champi margarita brotecito campanita luminon rosita loto bonsai; do SPI+=("summon starspirits:espiritu $X -60 151 {tipo:\"$s\",NoAI:1b,Rotation:[180f,0f]}"); X=$((X+1)); done
SPI+=('tp @a 144.5 -60 147.6 0 18' 'sleep 2.5' "${T}foto p12_fila_dia${E}" 'time set 18000' 'sleep 1.5' "${T}foto p13_fila_noche${E}"
  'time set 6000' 'kill @e[type=starspirits:espiritu]' 'sleep 0.5')
python3 ci/rcon.py 25577 annistest "${SPI[@]}" 2>&1 | tee rcon-starspirits.txt
# --- StarRiders: fenix salvaje, llamado con el silbato, montado y volando ---
FEN=('time set 6000' 'weather clear' "${T}hud no${E}" "${T}camara primera${E}" 'fill 150 -60 150 180 -40 200 air'
  'starriders salvaje 160 -60 172' 'tp @a 160 -60 162 0 0' 'sleep 3' "${T}foto r01_salvaje${E}" 'sleep 4' "${T}foto r02_salvaje_b${E}"
  'kill @e[type=starriders:fenix]' 'starriders dar @r' 'tp @a 160 -60 160 0 5' 'sleep 1' 'starriders llamar @r' 'sleep 0.6' "${T}foto r03_llamado${E}"
  'sleep 2.5' "${T}foto r04_posado${E}" 'tp @a 167 -60 163.5 90 8' 'sleep 1.5' "${T}foto r05_costado${E}"
  'tp @a 160 -60 159 0 5' 'sleep 0.5' 'ride @r mount @e[type=starriders:fenix,limit=1]' "${T}camara atras${E}" 'sleep 1.5' "${T}foto r06_montado${E}"
  "${T}mirar 0 -25${E}" "${T}saltar si${E}" 'sleep 0.8' "${T}saltar no${E}" "${T}caminar si${E}" 'sleep 2.5' "${T}foto r07_vuela_atras${E}"
  "${T}camara frente${E}" 'sleep 0.8' "${T}foto r08_vuela_frente${E}" "${T}camara atras${E}" "${T}mirar 70 -5${E}" 'sleep 0.5' "${T}foto r09_curva${E}"
  "${T}mirar 70 10${E}" "${T}caminar no${E}" 'sleep 2' "${T}foto r10_planea${E}" 'sleep 4' "${T}foto r11_aterriza${E}"
  'ride @r dismount' "${T}camara primera${E}" 'kill @e[type=starriders:fenix]' 'time set 18000' 'tp @a 160 -60 160 0 5' 'sleep 1' 'starriders llamar @r'
  'sleep 3' "${T}foto r12_noche${E}" 'kill @e[type=starriders:fenix]' 'starriders quitar @r todos' 'time set 6000' 'sleep 0.5')
GUA=('time set 6000' 'weather clear' 'kill @e[type=starriders:fenix]' 'fill 150 -60 150 180 -40 200 air'
  'starriders salvaje 160 -60 172 guardian' 'tp @a 160 -60 163 0 5' 'sleep 3' "${T}foto g01_guardian_salvaje${E}"
  'tp @a 167 -60 172 90 5' 'sleep 1.5' "${T}foto g02_guardian_costado${E}" 'kill @e[type=starriders:guardian]'
  'starriders dar @r guardian' 'tp @a 160 -60 160 0 5' 'sleep 1' 'starriders llamar @r' 'sleep 0.5' "${T}foto g03_guardian_rugido${E}"
  'sleep 3' "${T}foto g04_guardian_quieto${E}" 'tp @a 160 -60 159 0 5' 'sleep 0.5' 'ride @r mount @e[type=starriders:guardian,limit=1]'
  "${T}camara atras${E}" 'sleep 1.5' "${T}foto g05_guardian_montado${E}" "${T}caminar si${E}" 'sleep 1.5' "${T}foto g06_guardian_camina${E}"
  "${T}camara frente${E}" 'sleep 0.6' "${T}foto g07_guardian_camina_frente${E}" "${T}camara atras${E}" "${T}saltar si${E}" 'sleep 0.35'
  "${T}foto g08_guardian_salto${E}" "${T}saltar no${E}" "${T}caminar no${E}" 'sleep 1' 'ride @r dismount' "${T}camara primera${E}"
  'kill @e[type=starriders:guardian]' 'starriders quitar @r todos' 'sleep 0.5')
FEN+=("${GUA[@]}")
python3 ci/rcon.py 25577 annistest "${FEN[@]}" 2>&1 | tee rcon-starriders.txt
echo "::notice::Visual StarRiders> $(grep -v '^$' rcon-starriders.txt | grep -v '^> ' | tr '\n' '|' | cut -c1-2000)"
echo "::notice::Visual StarSpirits> $(grep -v '^$' rcon-starspirits.txt | grep -v '^> ' | tr '\n' '|' | cut -c1-2500) || $(grep -E 'StarSpirits' run/logs/latest.log 2>/dev/null | head -12 | tr '\n' '|' | cut -c1-2500)"
# --- StarFest: boliche de prueba de noche, con la cancion del servidor ---
STAR=('gamerule doDaylightCycle false' 'time set 18000' 'weather clear' 'gamerule sendCommandFeedback false' "${T}hud no${E}"
  'fill 54 -61 50 66 -61 62 minecraft:black_concrete' 'fill 56 -61 54 64 -61 60 starfest:piso'
  'setblock 56 -60 52 starfest:foco[facing=up]' 'setblock 58 -60 52 starfest:foco[facing=up]' 'setblock 60 -60 51 starfest:foco[facing=up]'
  'setblock 62 -60 52 starfest:foco[facing=up]' 'setblock 64 -60 52 starfest:foco[facing=up]'
  'setblock 54 -60 50 starfest:reflector[facing=up]' 'setblock 66 -60 50 starfest:reflector[facing=up]'
  'setblock 60 -60 53 starfest:cabina[facing=south]' 'setblock 55 -60 53 starfest:lanzador[facing=up]' 'setblock 65 -60 53 starfest:lanzador[facing=up]'
  'tp @a 60 -60 68 180 -8' 'sleep 2' "${T}foto s00_sin_musica${E}"
  'execute positioned 60 -60 56 run starfest musica tocar prueba 40 1' 'sleep 5' "${T}foto s01_musica_a${E}" 'sleep 1.3' "${T}foto s02_musica_b${E}"
  'sleep 1.7' "${T}foto s03_musica_c${E}" 'sleep 13' "${T}foto s04_parte_fuerte_a${E}" 'sleep 0.6' "${T}foto s05_parte_fuerte_b${E}"
  'sleep 1.1' "${T}foto s06_parte_fuerte_c${E}" 'execute positioned 60 -60 56 run starfest fuegos' 'sleep 1.6' "${T}foto s07_fuegos${E}"
  'tp @a 60 -60 61 180 50' 'sleep 1' "${T}foto s08_piso${E}" 'tp @a 70 -60 57 90 -10' 'sleep 1' "${T}foto s09_costado${E}"
  'starfest musica parar' 'playsound minecraft:music_disc.cat record @a 60 -60 56' 'sleep 6' "${T}foto s10_tocadiscos${E}"
  'stopsound @a record' 'sleep 1'
  # cielos de concierto (al aire libre) con musica, y NPC que bailan
  'summon annisnpc:npc 58 -60 58 {Tags:["sfb"],NoAI:1b,Protected:0b,Rotation:[0f,0f]}' 'summon annisnpc:npc 62 -60 58 {Tags:["sfb"],NoAI:1b,Protected:0b,Rotation:[0f,0f]}'
  'starfest bailarines agregar @e[tag=sfb] bailar' 'tp @a 60 -60 68 180 -8' 'sleep 1' "${T}foto s11_bailarines_sin_musica${E}"
  'execute positioned 60 -60 56 run starfest cielo noche_neon 200' 'sleep 2' "${T}foto c01_neon_sin_musica${E}"
  'execute positioned 60 -60 56 run starfest musica tocar prueba 40 1' 'sleep 3' "${T}foto s12_bailarines_con_musica${E}"
  'tp @a 60 -60 68 180 -40' 'sleep 2' "${T}foto c02_neon_a${E}" 'tp @a 60 -60 68 180 -85' 'sleep 1.2' "${T}foto c03_neon_arriba${E}"
  'execute positioned 60 -60 56 run starfest cielo galaxia 200' 'tp @a 60 -60 68 180 -30' 'sleep 2.5' "${T}foto c04_galaxia${E}" 'tp @a 60 -60 68 180 -85' 'sleep 1' "${T}foto c05_galaxia_arriba${E}"
  'execute positioned 60 -60 56 run starfest cielo aurora 200' 'tp @a 60 -60 68 90 -15' 'sleep 2.5' "${T}foto c06_aurora${E}"
  'execute positioned 60 -60 56 run starfest cielo fiesta 200' 'tp @a 60 -60 68 180 -50' 'sleep 2.5' "${T}foto c07_fiesta${E}" 'tp @a 60 -60 68 180 -85' 'sleep 1' "${T}foto c08_fiesta_arriba${E}"
  'tp @a 60 -60 68 180 -8' 'starfest musica parar' 'sleep 1.5' "${T}foto s13_bailarines_paran${E}"
  'starfest cielo parar' 'starfest bailarines limpiar' 'kill @e[tag=sfb]' 'time set 6000' 'sleep 1')
python3 ci/rcon.py 25577 annistest "${STAR[@]}"
# --- caminata de los NPC con los clips de Punchy (cintura incluida) ---
LOCO=('gamerule doDaylightCycle false' 'time set 6000' 'weather clear' 'gamerule sendCommandFeedback false'
  'tp @a 30 -60 26 0 10' "${T}hud no${E}"
  'summon annisnpc:npc 28.5 -60 30 {Tags:["lq"],NoAI:1b,Protected:0b,Rotation:[180f,0f]}'
  'summon annisnpc:npc 30 -60 30 {Tags:["lw"],NoAI:1b,Protected:0b,Rotation:[-90f,0f]}' 'sleep 2')
for i in $(seq 1 14); do LOCO+=('execute as @e[tag=lw] at @s run tp @s ~0.25 ~ ~ -90 0' 'sleep 0.1'); [ $i = 9 ] && LOCO+=("${T}foto l01_camina_a${E}"); [ $i = 12 ] && LOCO+=("${T}foto l02_camina_b${E}"); done
LOCO+=('sleep 1.5' "${T}foto l03_quietos${E}" 'kill @e[tag=lq]' 'kill @e[tag=lw]' 'sleep 1')
# bailar (boda en MCA): los hombros siguen al torso cuando gira
LOCO+=('summon annisnpc:npc 30 -60 30 {Tags:["bd"],NoAI:1b,Protected:0b,Rotation:[180f,0f]}' 'sleep 1.5' 'annisnpc anim @e[tag=bd] bailar'
  'sleep 0.5' "${T}foto b01_bailar_giro_a${E}" 'sleep 1.05' "${T}foto b02_bailar_giro_b${E}" 'kill @e[tag=bd]' 'sleep 0.5')
# Annis Compat: jugador "en la fogata" de Cinematic (sin abrir la escena), visto de frente y de costado
LOCO+=('tp @a 40 -60 40 0 0' "${T}camara frente${E}" "${T}estacion CAMPFIRE${E}" 'sleep 1.2' "${T}foto f01_fogata_frente${E}"
  "${T}cuerpo 90${E}" 'sleep 0.6' "${T}foto f02_fogata_costado${E}" "${T}estacion ANVIL${E}" 'sleep 0.8' "${T}foto f03_yunque_costado${E}"
  "${T}estacion ninguna${E}" 'sleep 0.8' "${T}foto f04_fuera_costado${E}" "${T}cuerpo libre${E}" "${T}camara primera${E}")
python3 ci/rcon.py 25577 annistest "${LOCO[@]}"
# Annis Compat + Figura: el avatar sigue a Punchy; con una animacion propia del cuerpo, Punchy se pausa
W='tp @a 100 -60 100 0 0'
FIG=('gamerule doDaylightCycle false' 'time set 6000' "$W" "${T}hud no${E}" "${T}camara frente${E}" "${T}cuerpo 90${E}" 'sleep 1.5'
  "${T}caminar derecha${E}" 'sleep 1.5' "${T}pose g01_normal_camina${E}" "${T}foto g01_normal_camina${E}" 'sleep 1.6' "${T}caminar no${E}" 'sleep 0.5'
  "${T}figura cargar prueba${E}" "$W" 'sleep 5' "${T}pose g02_figura_quieto${E}" "${T}foto g02_figura_quieto${E}" 'sleep 1.6'
  "${T}caminar derecha${E}" 'sleep 1.5' "${T}pose g03_figura_camina${E}" "${T}foto g03_figura_camina${E}" 'sleep 1.6' 'sleep 0.3' "${T}foto g03b_figura_camina${E}" "${T}caminar no${E}" 'sleep 0.5'
  "$W" "${T}figura anim cola si${E}" "${T}caminar derecha${E}" 'sleep 1.5' "${T}pose g04_cola_camina${E}" "${T}foto g04_cola_camina${E}" 'sleep 1.6' "${T}caminar no${E}" "${T}figura anim cola no${E}" 'sleep 0.5'
  "$W" "${T}figura anim saludo si${E}" "${T}caminar derecha${E}" 'sleep 1.5' "${T}pose g05_saludo_camina${E}" "${T}foto g05_saludo_camina${E}" 'sleep 1.6' "${T}caminar no${E}" 'sleep 1.2' "${T}pose g06_saludo_quieto${E}" "${T}foto g06_saludo_quieto${E}" 'sleep 1.6'
  "${T}figura anim saludo no${E}" 'sleep 1.2' "${T}pose g07_sin_saludo${E}" "${T}foto g07_sin_saludo${E}" 'sleep 1.6' "${T}figura quitar${E}" "${T}cuerpo libre${E}" "${T}camara primera${E}" 'sleep 1')
python3 ci/rcon.py 25577 annistest "${FIG[@]}"
echo "::notice::Visual Figura> $(grep -E '\[TEST\] (figura|pose)|Annis Compat|figura.*(ERROR|Exception)' run/logs/latest.log 2>/dev/null | head -20 | tr '\n' '|' | cut -c1-3000)"
python3 ci/rcon.py 25577 annistest \
  'gamerule doDaylightCycle false' 'time set 6000' 'weather clear' 'gamerule sendCommandFeedback false' \
  'tp @a 0 -60 -4 0 12' "${T}hud no${E}" \
  'tp @a 0 -60 -6 0 8' 'summon annisnpc:npc 0 -60 2 {Tags:["v1"],Protected:0b,Rotation:[180f,0f]}' 'sleep 2' \
  'vfx arbol 0 -60 2 @e[tag=v1,limit=1]' 'sleep 0.2' "${T}foto 00a_vfx_inicio${E}" 'sleep 3' "${T}foto 00b_vfx_hojas${E}" \
  'sleep 3' "${T}foto 00c_vfx_toca${E}" 'sleep 1.2' "${T}foto 00d_vfx_brillo${E}" 'sleep 2' "${T}foto 00e_vfx_flores${E}" \
  'sleep 1.4' "${T}foto 00f_vfx_columna${E}" 'sleep 1.2' "${T}foto 00g_vfx_aparece${E}" 'sleep 2.5' "${T}foto 00h_vfx_final${E}" \
  'vfx parar' 'kill @e[tag=v1]' 'sleep 1' \
  "${T}camara frente${E}" 'sleep 1' "${T}golpe${E}" 'sleep 0.05' "${T}foto 00i_golpe_a${E}" 'sleep 1' "${T}golpe${E}" 'sleep 0.12' "${T}foto 00j_golpe_b${E}" 'sleep 1' "${T}golpe${E}" 'sleep 0.2' "${T}foto 00j_golpe_c${E}" \
  'sleep 1' "${T}foto 00k_sin_golpe${E}" "${T}camara primera${E}" 'sleep 0.5' \
  'summon annisnpc:npc -5.3 -60 4 {Tags:["s","a1"]}' 'summon annisnpc:npc -4.5 -60 4 {Tags:["s","b1"]}' \
  'summon annisnpc:npc -1.9 -60 4 {Tags:["s","a2"]}' 'summon annisnpc:npc -1.1 -60 4 {Tags:["s","b2"]}' \
  'summon annisnpc:npc 1.0 -60 4 {Tags:["s","a3"]}' 'summon annisnpc:npc 2.0 -60 4 {Tags:["s","b3"]}' \
  'summon annisnpc:npc 4.4 -60 4 {Tags:["s","a4"]}' 'summon annisnpc:npc 5.4 -60 4 {Tags:["s","b4"]}' \
  'sleep 3' \
  'interaccion hacer abrazo @e[tag=a1,limit=1] @e[tag=b1,limit=1]' \
  'interaccion hacer beso @e[tag=a2,limit=1] @e[tag=b2,limit=1]' \
  'interaccion hacer chocar_cinco @e[tag=a3,limit=1] @e[tag=b3,limit=1]' \
  'interaccion hacer apreton @e[tag=a4,limit=1] @e[tag=b4,limit=1]' \
  'sleep 0.7' "${T}foto 01_escenas_a${E}" 'sleep 0.6' "${T}foto 02_escenas_b${E}" 'sleep 1.2' "${T}foto 03_escenas_c${E}" \
  'sleep 2.5' 'execute as @e[type=annisnpc:npc] run data merge entity @s {Protected:0b}' 'kill @e[type=annisnpc:npc]' 'sleep 1.5' \
  'summon annisnpc:npc -4.4 -60 4 {Tags:["l","a5"],Protected:0b}' 'summon annisnpc:npc -3.6 -60 4 {Tags:["l","b5"],Protected:0b}' \
  'summon annisnpc:npc -0.6 -60 4.5 {Tags:["l","a6"],Rotation:[90f,0f],Protected:0b}' 'summon annisnpc:npc -0.6 -60 3.5 {Tags:["l","b6"],Protected:0b}' \
  'summon annisnpc:npc 3.6 -60 4 {Tags:["l","a7"],Rotation:[180f,0f],Protected:0b}' 'summon annisnpc:npc 4.6 -60 4 {Tags:["l","b7"],Protected:0b}' \
  'sleep 2' \
  'interaccion hacer vals @e[tag=a5,limit=1] @e[tag=b5,limit=1]' \
  'interaccion hacer estrellas @e[tag=a6,limit=1] @e[tag=b6,limit=1]' \
  'interaccion hacer sentarse @e[tag=a7,limit=1] @e[tag=b7,limit=1]' \
  'sleep 2' "${T}foto 04_vals_estrellas_sentarse${E}" 'sleep 1.5' "${T}foto 05_vals_giro${E}" \
  'kill @e[type=annisnpc:npc]' 'sleep 1.5' \
  'summon annisnpc:npc -3.4 -60 4 {Tags:["p","p1"],Protected:0b}' 'summon annisnpc:npc -2.6 -60 4 {Tags:["p","p2"],Protected:0b}' \
  'summon annisnpc:npc 2.6 -60 4 {Tags:["p","p3"],Protected:0b}' 'summon annisnpc:npc 3.4 -60 4 {Tags:["p","p4"],Protected:0b}' \
  'sleep 2' \
  'interaccion hacer propuesta @e[tag=p1,limit=1] @e[tag=p2,limit=1]' \
  'interaccion hacer baile_lento @e[tag=p3,limit=1] @e[tag=p4,limit=1]' \
  'sleep 2' "${T}foto 05b_propuesta_lento${E}" 'sleep 1.4' "${T}foto 05c_propuesta_sorpresa${E}" 'sleep 1.6' "${T}foto 05d_propuesta_mano${E}" \
  'kill @e[type=annisnpc:npc]' 'sleep 1.5' \
  'summon annisnpc:npc -4.2 -60 3 {Tags:["n","n1"],Rotation:[180f,0f],Protected:0b}' 'summon annisnpc:npc -3.2 -60 3 {Tags:["n","n2"],Rotation:[180f,0f],Protected:0b}' \
  'summon annisnpc:npc -0.4 -60 2.2 {Tags:["n","n3"],Protected:0b}' 'summon annisnpc:npc 0.4 -60 2.2 {Tags:["n","n4"],Protected:0b}' \
  'summon annisnpc:npc 2.8 -60 3 {Tags:["n","n5"],Protected:0b}' 'summon annisnpc:npc 3.8 -60 3 {Tags:["n","n6"],Protected:0b}' \
  'sleep 2' \
  'interaccion hacer hombro @e[tag=n2,limit=1] @e[tag=n1,limit=1]' \
  'interaccion hacer abrazo @e[tag=n3,limit=1] @e[tag=n4,limit=1]' \
  'interaccion hacer besar_mano @e[tag=n5,limit=1] @e[tag=n6,limit=1]' \
  'sleep 1.9' "${T}foto 05e_hombro_abrazo_besomano${E}" 'sleep 0.8' "${T}foto 05f_hombro_abrazo_besomano${E}" \
  'kill @e[type=annisnpc:npc]' 'sleep 1.5' \
  'summon annisnpc:npc -1.6 -60 3 {Tags:["x","x1"],Protected:0b}' 'summon annisnpc:npc -0.8 -60 3 {Tags:["x","x2"],Protected:0b}' \
  'summon annisnpc:npc 1.5 -60 3 {Tags:["x","x3"],Rotation:[90f,0f],Protected:0b}' 'summon annisnpc:npc 1.5 -60 4 {Tags:["x","x4"],Protected:0b}' \
  'sleep 2' 'interaccion hacer prueba_abrazo @e[tag=x1,limit=1] @e[tag=x2,limit=1]' 'interaccion hacer prueba_brazos @e[tag=x3,limit=1] @e[tag=x4,limit=1]' \
  'sleep 1.8' "${T}foto 05j_interacciones_archivo${E}" \
  'kill @e[type=annisnpc:npc]' 'sleep 1.5' \
  'summon annisnpc:npc -4.4 -60 4 {Tags:["d","d1"],Protected:0b}' 'summon annisnpc:npc -3.6 -60 4 {Tags:["d","d2"],Protected:0b}' \
  'summon annisnpc:npc -0.4 -60 3 {Tags:["d","d3"],Protected:0b}' 'summon annisnpc:npc 0.4 -60 3 {Tags:["d","d4"],Protected:0b}' \
  'summon annisnpc:npc 3.6 -60 4 {Tags:["d","d5"],Protected:0b}' 'summon annisnpc:npc 4.4 -60 4 {Tags:["d","d6"],Protected:0b}' \
  'sleep 2' \
  'interaccion hacer swing @e[tag=d1,limit=1] @e[tag=d2,limit=1]' \
  'interaccion hacer bachata+sigo @e[tag=d3,limit=1] @e[tag=d4,limit=1]' \
  'interaccion hacer tango @e[tag=d5,limit=1] @e[tag=d6,limit=1]' \
  'sleep 2' "${T}foto 05g_bailes${E}" 'sleep 1.5' "${T}foto 05h_bailes${E}" 'sleep 1.2' "${T}foto 05i_bailes${E}" \
  'kill @e[type=annisnpc:npc]' 'sleep 1.5' \
  'summon annisnpc:npc -3 -60 4 {Tags:["k","k1"],Rotation:[90f,0f],Protected:0b}' 'summon annisnpc:npc -3 -60 5 {Tags:["k","k2"],Protected:0b}' \
  'summon annisnpc:npc 0.5 -60 4 {Tags:["k","k3"],Rotation:[90f,0f],Protected:0b}' 'summon annisnpc:npc 0.5 -60 5 {Tags:["k","k4"],Protected:0b}' \
  'summon annisnpc:npc 4 -60 4 {Tags:["k","k5"],Rotation:[90f,0f],Protected:0b}' 'summon annisnpc:npc 4 -60 6 {Tags:["k","k6"],Protected:0b}' \
  'sleep 2' \
  'interaccion hacer en_brazos @e[tag=k1,limit=1] @e[tag=k2,limit=1]' \
  'interaccion hacer caballito @e[tag=k3,limit=1] @e[tag=k4,limit=1]' \
  'interaccion caer @e[tag=k5,limit=1]' \
  'sleep 0.3' "${T}foto 06a_cayendo${E}" 'sleep 0.3' "${T}foto 06b_cayendo${E}" \
  'sleep 1.4' "${T}foto 06_brazos_caballito_caido${E}" \
  'data get entity @e[tag=k1,limit=1] Pos' 'data get entity @e[tag=k2,limit=1] Pos' 'data get entity @e[tag=k3,limit=1] Pos' 'data get entity @e[tag=k4,limit=1] Pos' \
  'interaccion hacer ayudar @e[tag=k6,limit=1] @e[tag=k5,limit=1]' \
  'sleep 0.8' "${T}foto 07_ayudar_a${E}" 'sleep 0.6' "${T}foto 08_ayudar_b${E}" 'sleep 0.6' "${T}foto 09_ayudar_c${E}" \
  'sleep 2' 'data get entity @e[tag=k5,limit=1] Health' \
  'kill @e[type=annisnpc:npc]' 'sleep 1.5' \
  'tp @a 0 -60 0 0 0' 'summon annisnpc:npc 0 -60 1 {Tags:["c","c1"],Protected:0b}' 'sleep 1.5' \
  'interaccion hacer abrazo @a[limit=1] @e[tag=c1,limit=1]' 'sleep 0.3' 'execute as @a at @s run tp @s ~ ~ ~ 90 15' \
  "${T}camara atras${E}" 'sleep 1' "${T}foto 10_abrazo_jugador${E}" \
  'sleep 4' 'interaccion hacer chocar_cinco @a[limit=1] @e[tag=c1,limit=1]' 'sleep 0.3' 'execute as @a at @s run tp @s ~ ~ ~ 90 15' \
  'sleep 0.4' "${T}foto 11_cinco_jugador${E}" 'sleep 2' \
  'interaccion hacer en_brazos @a[limit=1] @e[tag=c1,limit=1]' 'sleep 1.5' \
  'data get entity @a[limit=1] Pos' 'data get entity @e[tag=c1,limit=1] Pos' "${T}foto 12_jugador_en_brazos${E}" \
  'execute as @a run interaccion parar' 'sleep 1' \
  'interaccion caer @a[limit=1]' 'sleep 0.5' 'execute as @a at @s run tp @s ~ ~ ~ ~ 35' 'sleep 1.5' "${T}foto 13_jugador_caido${E}" \
  'interaccion hacer ayudar @e[tag=c1,limit=1] @a[limit=1]' 'sleep 0.9' "${T}foto 14_jugador_ayudado${E}" 'sleep 1.2' "${T}foto 15_jugador_ayudado_b${E}" \
  'sleep 2' 'data get entity @a[limit=1] Health' \
  'sleep 2' 'tp @a 0 -60 0 0 0' 'tp @e[tag=c1] 0 -60 2' 'sleep 1' \
  'interaccion hacer pedir_brazos @a[limit=1] @e[tag=c1,limit=1]' 'sleep 1.5' "${T}camara frente${E}" 'sleep 0.5' "${T}foto 16_me_llevan${E}" \
  'execute as @a run interaccion parar' 'sleep 1.5' \
  'tp @a 0 -60 0 0 0' 'tp @e[tag=c1] -1.1 -60 0' 'sleep 1' \
  'interaccion hacer de_la_mano @a[limit=1] @e[tag=c1,limit=1]' 'sleep 1' "${T}camara atras${E}" \
  'execute as @a at @s run tp @s ~ ~ ~0.25' 'sleep 0.1' 'execute as @a at @s run tp @s ~ ~ ~0.25' 'sleep 0.1' \
  'execute as @a at @s run tp @s ~ ~ ~0.25' 'sleep 0.1' 'execute as @a at @s run tp @s ~ ~ ~0.25' 'sleep 0.1' \
  'execute as @a at @s run tp @s ~ ~ ~0.25' 'sleep 0.1' 'execute as @a at @s run tp @s ~ ~ ~0.25' 'sleep 0.1' \
  "${T}foto 17_de_la_mano_caminando${E}" 'sleep 1' "${T}foto 18_de_la_mano_quietos${E}" \
  'execute as @a run interaccion parar' 'sleep 1' \
  'tp @a 0 -60 0 0 0' 'tp @e[tag=c1] 0 -60 1.2' 'sleep 1' \
  'interaccion hacer mimos @a[limit=1] @e[tag=c1,limit=1]' 'sleep 1.5' "${T}foto 21_mimos_inicio${E}" \
  "${T}tocar 1 0.5 -8.6 -1${E}" 'sleep 1.2' "${T}foto 22_mimos_pelo_1p${E}" \
  "${T}tocar 9 0 -3.4 -4.6${E}" 'sleep 1.2' "${T}foto 23_mimos_nariz_1p${E}" \
  "${T}tocar 3 -3 -1 -8${E}" 'sleep 1.2' "${T}foto 24_mimos_mano_1p${E}" \
  "${T}vista costado${E}" "${T}tocar 12 -4.6 -3 0${E}" 'sleep 1.2' "${T}foto 25_mimos_oreja${E}" \
  "${T}tocar 14 1 4 2.4${E}" 'sleep 1.2' "${T}foto 26_mimos_espalda${E}" \
  "${T}tocar 15 0 9 2.4${E}" 'sleep 1.0' "${T}foto 27_mimos_cosquillas${E}" \
  "${T}vista primera${E}" \
  "${T}soltar${E}" 'sleep 0.5' 'execute as @a run interaccion parar' 'sleep 1' "${T}camara primera${E}" \
  'tp @a 0 -60 0 0 0' 'tp @e[tag=c1] 0 -60 1.5' 'sleep 1' \
  'interaccion hacer en_brazos @e[tag=c1,limit=1] @a[limit=1]' 'sleep 1.2' "${T}camara atras${E}" \
  'execute as @a at @s run tp @s ~ ~ ~0.25' 'sleep 0.1' 'execute as @a at @s run tp @s ~ ~ ~0.25' 'sleep 0.1' \
  'execute as @a at @s run tp @s ~ ~ ~0.25' 'sleep 0.1' 'execute as @a at @s run tp @s ~ ~ ~0.25' 'sleep 0.1' \
  "${T}foto 20_npc_carga_caminando${E}" 'sleep 0.5' \
  'execute as @a run interaccion parar' 'sleep 1' \
  'tp @a 0 -60 0 0 0' 'tp @e[tag=c1] 0 -60 1.2' 'sleep 1' \
  'interaccion hacer en_brazos @a[limit=1] @e[tag=c1,limit=1]' 'sleep 1.5' "${T}camara frente${E}" 'sleep 0.5' "${T}foto 28_brazos_quieto${E}" \
  'execute as @a at @s run tp @s ~ ~ ~ ~60 ~' 'sleep 0.05' "${T}foto 29_brazos_girando${E}" 'sleep 1' "${T}foto 30_brazos_girado${E}" \
  'execute as @a run interaccion parar' 'sleep 1' "${T}camara primera${E}" \
  'setblock 3 -60 0 minecraft:red_bed[facing=south,part=foot]' 'setblock 3 -60 1 minecraft:red_bed[facing=south,part=head]' \
  'tp @a 2 -60 -1 0 0' 'tp @e[tag=c1] 2 -60 0' 'sleep 1' \
  'interaccion hacer cama @a[limit=1] @e[tag=c1,limit=1]' 'sleep 2' "${T}camara frente${E}" 'sleep 0.5' "${T}foto 31_cama${E}" \
  'execute as @a at @s run tp @s ~ ~ ~ ~90 ~' 'sleep 0.5' "${T}foto 32_cama_b${E}" \
  'execute as @a run interaccion parar' 'sleep 1' "${T}camara primera${E}" 'tp @a 0 -60 0 0 0' 'tp @e[tag=c1] 0 -60 1.2' 'sleep 1' \
  'interaccion hacer mimos @a[limit=1] @e[tag=c1,limit=1]' 'sleep 1.5' \
  "${T}tocar 2 -3 -2 -4.6${E}" 'sleep 1.2' "${T}foto 33_mejilla_1p${E}" \
  "${T}tocar 9 0 -2.6 -4.6${E}" 'sleep 1.2' "${T}foto 34_nariz_1p${E}" \
  "${T}soltar${E}" 'sleep 0.5' 'execute as @a run interaccion parar' 'sleep 1' \
  'gamerule doImmediateRespawn true' 'interaccion caer @a[limit=1]' 'sleep 1.5' 'kill @a' 'sleep 3' "${T}camara frente${E}" 'sleep 1' "${T}foto 19_tras_reaparecer${E}" \
  'sleep 1' 'stop' 2>&1 | tee rcon-visual.txt
sleep 8
kill $CP 2>/dev/null; pkill -f 'runClient'; pkill -f 'run.sh' ; sleep 2
mkdir -p ../out/visual
cp run/screenshots/*.png ../out/visual/ 2>/dev/null
ls -la ../out/visual
echo "::notice::Visual> $(grep -h 'Animaciones de paquetes' run/logs/latest.log 2>/dev/null | tail -1)"
echo "::notice::Visual> fotos: $(ls ../out/visual 2>/dev/null | tr '\n' ' ')"
echo "::notice::Visual> $(grep -v '^$' rcon-visual.txt | grep -v '^> ' | tr '\n' '|' | cut -c1-3000)"
echo "::warning::Visual cliente> $(grep -E 'Mixin|mixin|Exception|ERROR|Caused by' run/logs/latest.log 2>/dev/null | grep -v 'Mixin Config\|mixins.json' | head -30 | tr '\n' '|' | cut -c1-6000)"
echo "::warning::Visual servidor> $(grep -E 'Exception|ERROR|Caused by' $S/server.log | head -20 | tr '\n' '|' | cut -c1-4000)"
