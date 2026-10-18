scoreboard players set #song mikushow 0
execute if score #baile mikushow matches 1 run function mikushow:baile/rearmar
execute if score #full mikushow matches 1 run function mikushow:internal/intermedio
execute if score #full mikushow matches 1 run schedule function mikushow:song/triple_baka/start_full 5s replace
execute unless score #full mikushow matches 1 run function mikushow:internal/intermedio
