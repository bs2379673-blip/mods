#!/bin/bash
# Prueba visual: servidor de produccion con los mods + cliente de desarrollo (Loom) en una pantalla
# virtual. El servidor arma escenas con NPC y el jugador, y el cliente saca fotos cuando se lo piden.
set +e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
S=visrun
rm -rf $S && mkdir -p $S/mods
LOADER=$(curl -sSL https://meta.fabricmc.net/v2/versions/loader | python3 -c "import sys,json; print([v['version'] for v in json.load(sys.stdin) if v.get('stable')][0])")
INSTALLER=$(curl -sSL https://meta.fabricmc.net/v2/versions/installer | python3 -c "import sys,json; print([v['version'] for v in json.load(sys.stdin) if v.get('stable')][0])")
curl -sSL -o $S/server.jar "https://meta.fabricmc.net/v2/versions/loader/1.21.1/$LOADER/$INSTALLER/server/jar"
curl -sSL -o $S/mods/fabric-api.jar "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.104.0+1.21.1/fabric-api-0.104.0+1.21.1.jar"
cp ../annisnpc/libs/playeranimator-2.0.1+1.21.1.jar $S/mods/
cp ../out/annisnpc-*.jar ../out/annisinteract-*.jar $S/mods/
echo "eula=true" > $S/eula.txt
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
( cd $S && timeout 900 java -Xmx2G -jar server.jar nogui > server.log 2>&1 ) &
for i in $(seq 1 100); do grep -q 'Done (' $S/server.log 2>/dev/null && break; sleep 3; done
grep -q 'Done (' $S/server.log || { echo "::warning::Visual> el servidor no arranco"; exit 0; }

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
EOO
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5
( timeout 840 xvfb-run -a -s "-screen 0 1280x720x24" gradle runClient -PannisTest --no-daemon > client.log 2>&1 ) &
CP=$!
for i in $(seq 1 160); do grep -q 'joined the game' $S/server.log 2>/dev/null && break; sleep 3; done
if ! grep -q 'joined the game' $S/server.log; then
  echo "::warning::Visual> el cliente no entro: $(grep -E 'Exception|Error|error|FAILED' client.log | head -20 | tr '\n' '|' | cut -c1-3000)"
  echo "::warning::Visual cliente log> $(tail -40 run/logs/latest.log 2>/dev/null | tr '\n' '|' | cut -c1-5000)"
  pkill -f runClient; pkill -f java; exit 0
fi
sleep 25
T='tellraw @a {"text":"[TEST] '
E='"}'
python3 ../annisnpc/ci/rcon.py 25577 annistest \
  'gamerule doDaylightCycle false' 'time set 6000' 'weather clear' 'gamerule sendCommandFeedback false' \
  'tp @a 0 -60 -4 0 12' "${T}hud no${E}" \
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
  'sleep 2.5' 'kill @e[tag=s]' 'sleep 1' \
  'summon annisnpc:npc -4.4 -60 4 {Tags:["l","a5"]}' 'summon annisnpc:npc -3.6 -60 4 {Tags:["l","b5"]}' \
  'summon annisnpc:npc -0.6 -60 4.5 {Tags:["l","a6"],Rotation:[90f,0f]}' 'summon annisnpc:npc -0.6 -60 3.5 {Tags:["l","b6"]}' \
  'summon annisnpc:npc 3.6 -60 4 {Tags:["l","a7"],Rotation:[180f,0f]}' 'summon annisnpc:npc 4.6 -60 4 {Tags:["l","b7"]}' \
  'sleep 2' \
  'interaccion hacer vals @e[tag=a5,limit=1] @e[tag=b5,limit=1]' \
  'interaccion hacer estrellas @e[tag=a6,limit=1] @e[tag=b6,limit=1]' \
  'interaccion hacer sentarse @e[tag=a7,limit=1] @e[tag=b7,limit=1]' \
  'sleep 2' "${T}foto 04_vals_estrellas_sentarse${E}" 'sleep 1.5' "${T}foto 05_vals_giro${E}" \
  'kill @e[tag=l]' 'sleep 1' \
  'tp @a 0 -60 0 0 0' 'summon annisnpc:npc 0 -60 1 {Tags:["c","c1"]}' 'sleep 1.5' \
  "${T}camara frente${E}" \
  'interaccion hacer en_brazos @a[limit=1] @e[tag=c1,limit=1]' 'sleep 2' "${T}foto 06_en_brazos${E}" \
  "${T}camara atras${E}" 'sleep 0.5' "${T}foto 07_en_brazos_atras${E}" \
  'interaccion parar' 'execute as @a run interaccion parar' 'sleep 1' \
  "${T}camara frente${E}" \
  'interaccion hacer caballito @a[limit=1] @e[tag=c1,limit=1]' 'sleep 2' "${T}foto 08_caballito${E}" \
  "${T}camara atras${E}" 'sleep 0.5' "${T}foto 09_caballito_atras${E}" \
  'execute as @a run interaccion parar' 'sleep 1' \
  'interaccion hacer de_la_mano @a[limit=1] @e[tag=c1,limit=1]' 'sleep 1.5' "${T}foto 10_de_la_mano${E}" \
  'execute as @a run interaccion parar' 'sleep 1' \
  "${T}camara frente${E}" 'interaccion hacer abrazo @a[limit=1] @e[tag=c1,limit=1]' 'sleep 1.2' "${T}foto 11_abrazo_jugador${E}" \
  'sleep 4' 'interaccion caer @a[limit=1]' 'sleep 2' "${T}foto 12_caido${E}" \
  'interaccion hacer ayudar @e[tag=c1,limit=1] @a[limit=1]' 'sleep 0.9' "${T}foto 13_ayudar_a${E}" 'sleep 0.6' "${T}foto 14_ayudar_b${E}" 'sleep 0.6' "${T}foto 15_ayudar_c${E}" \
  'sleep 2' 'data get entity @a[limit=1] Health' \
  'sleep 1' 'stop' 2>&1 | tee rcon-visual.txt
sleep 8
kill $CP 2>/dev/null; pkill -f 'runClient' ; sleep 2
mkdir -p ../out/visual
cp run/screenshots/*.png ../out/visual/ 2>/dev/null
ls -la ../out/visual
echo "::notice::Visual> fotos: $(ls ../out/visual 2>/dev/null | tr '\n' ' ')"
echo "::notice::Visual> $(grep -v '^$' rcon-visual.txt | grep -v '^> ' | tr '\n' '|' | cut -c1-3000)"
echo "::warning::Visual cliente> $(grep -E 'Mixin|mixin|Exception|ERROR|Caused by' run/logs/latest.log 2>/dev/null | grep -v 'Mixin Config\|mixins.json' | head -30 | tr '\n' '|' | cut -c1-6000)"
echo "::warning::Visual servidor> $(grep -E 'Exception|ERROR|Caused by' $S/server.log | head -20 | tr '\n' '|' | cut -c1-4000)"
