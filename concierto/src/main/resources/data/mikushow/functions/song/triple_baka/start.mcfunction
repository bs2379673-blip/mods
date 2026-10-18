function mikushow:internal/reset
scoreboard players set #song mikushow 10
scoreboard players operation #t mikushow = #lag mikushow
scoreboard players operation #t mikushow *= #neg mikushow
execute if score #audio mikushow matches 1 as @a at @s run playsound mikushow:show.triple_baka record @s ~ ~ ~ 10 1 1
function mikushow:concierto/escenario
concierto iniciar triple_baka auto false
execute if score #baile mikushow matches 1 run function mikushow:baile/triple_baka
title @a actionbar {"text":"Triple Baka (Miku, Teto y Neru)","color":"aqua"}
