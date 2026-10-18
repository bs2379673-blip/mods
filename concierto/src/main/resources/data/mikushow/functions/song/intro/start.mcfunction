function mikushow:internal/reset
scoreboard players set #song mikushow 8
scoreboard players operation #t mikushow = #lag mikushow
scoreboard players operation #t mikushow *= #neg mikushow
execute if score #audio mikushow matches 1 as @a at @s run playsound mikushow:show.intro record @s ~ ~ ~ 10 1 1
function mikushow:concierto/escenario
concierto iniciar intro auto false
title @a actionbar {"text":"Obscure Live - apertura","color":"aqua"}
