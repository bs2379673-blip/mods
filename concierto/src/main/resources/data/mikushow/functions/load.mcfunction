scoreboard objectives add mikushow dummy
execute unless score #lag mikushow matches -100.. run scoreboard players set #lag mikushow 2
execute unless score #audio mikushow matches 0.. run scoreboard players set #audio mikushow 1
execute unless score #dir mikushow matches 0.. run scoreboard players set #dir mikushow 0
execute unless score #flash mikushow matches 0.. run scoreboard players set #flash mikushow 1
execute unless score #baile mikushow matches 0.. run scoreboard players set #baile mikushow 1
scoreboard players set #neg mikushow -1
execute unless score #song mikushow matches 0.. run scoreboard players set #song mikushow 0
