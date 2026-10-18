function mikushow:internal/reset
scoreboard players set #song mikushow 7
scoreboard players operation #t mikushow = #lag mikushow
scoreboard players operation #t mikushow *= #neg mikushow
execute if score #audio mikushow matches 1 as @a at @s run playsound mikushow:show.looping_the_rooms record @s ~ ~ ~ 10 1 1
function mikushow:concierto/escenario
concierto iniciar looping_the_rooms auto false
execute if score #baile mikushow matches 1 run function mikushow:baile/looping_the_rooms
title @a actionbar {"text":"Looping the Rooms","color":"aqua"}
