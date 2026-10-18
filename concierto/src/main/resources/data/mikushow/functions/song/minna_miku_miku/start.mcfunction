function mikushow:internal/reset
scoreboard players set #song mikushow 6
scoreboard players operation #t mikushow = #lag mikushow
scoreboard players operation #t mikushow *= #neg mikushow
execute if score #audio mikushow matches 1 as @a at @s run playsound mikushow:show.minna_miku_miku record @s ~ ~ ~ 10 1 1
function mikushow:concierto/escenario
concierto iniciar minna_miku_miku auto false
execute if score #baile mikushow matches 1 run function mikushow:baile/minna_miku_miku
title @a actionbar {"text":"Minna Miku Miku ni Shiteageru","color":"aqua"}
