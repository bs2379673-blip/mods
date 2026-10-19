scoreboard players set #song mikushow 0
# en el show completo no se rearman: ya se fueron en la explosion del final
execute if score #baile mikushow matches 1 unless score #full mikushow matches 1 run function mikushow:baile/rearmar
execute if score #full mikushow matches 1 run function mikushow:internal/final
execute unless score #full mikushow matches 1 run function mikushow:internal/intermedio
