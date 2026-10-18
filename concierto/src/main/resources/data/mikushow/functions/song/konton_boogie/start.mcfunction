function mikushow:internal/reset
scoreboard players set #song mikushow 2
scoreboard players operation #t mikushow = #lag mikushow
scoreboard players operation #t mikushow *= #neg mikushow
execute if score #audio mikushow matches 1 as @a at @s run playsound mikushow:show.konton_boogie record @s ~ ~ ~ 10 1 1
function mikushow:concierto/escenario
concierto iniciar konton_boogie auto false
execute if score #baile mikushow matches 1 run function mikushow:baile/konton_boogie
title @a actionbar {"text":"Konton Boogie","color":"aqua"}
