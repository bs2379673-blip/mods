#!/bin/bash
# Prueba de humo: arranca un servidor dedicado con el mod, crea NPC, lanza un emote y apaga.
cd "$(dirname "$0")/.."
mkdir -p run
echo "eula=true" > run/eula.txt
cat > run/server.properties <<EOP
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
view-distance=4
simulation-distance=4
enable-rcon=true
rcon.port=25575
rcon.password=annistest
EOP
rm -f server-test.log
( timeout 840 gradle runServer --no-daemon > server-test.log 2>&1 ) &
GP=$!
for i in $(seq 1 150); do
  grep -q 'Done (' server-test.log 2>/dev/null && break
  sleep 5
done
if grep -q 'Done (' server-test.log; then
  python3 ci/rcon.py 25575 annistest \
    'annisnpc spawn steve' \
    'annisnpc spawn alex true' \
    'data get entity @e[type=annisnpc:npc,limit=1]' \
    'annisnpc emote @e[type=annisnpc:npc] wave 2' \
    'annisnpc skin @e[type=annisnpc:npc] steve true' \
    'annisnpc stationary @e[type=annisnpc:npc] true' \
    'sleep 15' \
    'execute if entity @e[type=annisnpc:npc]' \
    'stop' 2>&1 | tee rcon-out.txt
else
  echo "El servidor no llego a arrancar" | tee rcon-out.txt
fi
wait $GP
echo "== resultado =="
while IFS= read -r l; do echo "::notice::${l}"; done < rcon-out.txt
grep -E "Exception|ERROR|Mixin apply|Caused by|annisnpc|playeranimator" server-test.log | head -40 | while IFS= read -r l; do echo "::warning::${l}"; done
