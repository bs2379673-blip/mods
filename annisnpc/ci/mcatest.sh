#!/bin/bash
# Prueba real con MCA: servidor Fabric de produccion (sin Loom) con Fabric API, playerAnimator,
# MCA original y nuestro jar recien compilado. Comprueba que los mixins cargan y los comandos funcionan.
set +e
cd "$(dirname "$0")/.."
R=mcarun
rm -rf $R && mkdir -p $R/mods
curl -sSL -o $R/server.jar "https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.5/1.0.1/server/jar"
curl -sSL -o $R/mods/fabric-api.jar "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.104.0+1.21.1/fabric-api-0.104.0+1.21.1.jar"
cp libs/playeranimator-2.0.1+1.21.1.jar $R/mods/
cp libs/test/mca-fabric-7.7.36-beta.3+1.21.1-original.jar $R/mods/mca.jar
cp ../out/annisnpc-*.jar $R/mods/
ls -la $R/mods
echo "eula=true" > $R/eula.txt
cat > $R/server.properties <<EOP
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
view-distance=4
simulation-distance=4
enable-rcon=true
rcon.port=25576
rcon.password=annistest
EOP
cd $R
( timeout 600 java -Xmx2G -jar server.jar nogui > server.log 2>&1 ) &
GP=$!
for i in $(seq 1 100); do
  grep -q 'Done (' server.log 2>/dev/null && break
  grep -q 'Exception in thread "main"\|Incompatible mods\|Mod resolution failed' server.log 2>/dev/null && break
  sleep 3
done
if grep -q 'Done (' server.log; then
  python3 ../ci/rcon.py 25576 annistest \
    'forceload add 0 0' \
    'summon mca:female_villager 2 -60 2' \
    'summon mca:male_villager 4 -60 4' \
    'annisnpc spawn steve' \
    'sleep 3' \
    'annisnpc mca acciones' \
    'annisnpc mca accion boda tocar_tambor 8' \
    'annisnpc anim @e[type=mca:female_villager] bailar 5' \
    'annisnpc look @e[type=mca:male_villager] @e[type=annisnpc:npc,limit=1]' \
    'annisnpc mca convertir @e[type=annisnpc:npc,limit=1]' \
    'sleep 4' \
    'execute if entity @e[type=annisnpc:npc]' \
    'execute if entity @e[type=mca:male_villager]' \
    'data get entity @e[type=mca:male_villager,limit=1] fabric:attachments' \
    'annisnpc anim @e[type=mca:male_villager] stop' \
    'sleep 3' \
    'stop' 2>&1 | tee rcon-out.txt
else
  echo "El servidor con MCA no llego a arrancar" | tee rcon-out.txt
fi
wait $GP
echo "::notice::MCA> $(tr '\n' '|' < rcon-out.txt | cut -c1-4000)"
echo "::warning::MCA log> $(grep -E "Mixin apply|mixin.*annisnpc|annisnpc.*(ERROR|Exception)|InvalidInjection|Caused by|Exception|ERROR" server.log | head -30 | tr '\n' '|' | cut -c1-6000)"
grep -q "Integracion con Minecraft Comes Alive activada" server.log && echo "::notice::MCA> integracion activada"
