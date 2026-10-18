function mikushow:internal/reset
scoreboard players set #song mikushow 4
scoreboard players operation #t mikushow = #lag mikushow
scoreboard players operation #t mikushow *= #neg mikushow
execute if score #audio mikushow matches 1 as @a at @s run playsound mikushow:show.magical_cure record @s ~ ~ ~ 10 1 1
function mikushow:concierto/escenario
concierto iniciar magical_cure auto false
execute if score #baile mikushow matches 1 run function mikushow:baile/magical_cure
title @a actionbar {"text":"Magical Cure! Love Shot!","color":"aqua"}
