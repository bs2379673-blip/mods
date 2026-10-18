function mikushow:internal/reset
scoreboard players set #song mikushow 3
scoreboard players operation #t mikushow = #lag mikushow
scoreboard players operation #t mikushow *= #neg mikushow
execute if score #audio mikushow matches 1 as @a at @s run playsound mikushow:show.melt record @s ~ ~ ~ 10 1 1
function mikushow:concierto/escenario
concierto iniciar melt auto false
execute if score #baile mikushow matches 1 run function mikushow:baile/melt
title @a actionbar {"text":"Melt","color":"aqua"}
