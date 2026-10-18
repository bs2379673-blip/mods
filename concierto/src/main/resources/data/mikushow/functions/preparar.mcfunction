# antes del evento: noche fija, sin lluvia, sin mensajes de comandos en el chat
time set 18000
gamerule doDaylightCycle false
weather clear 1000000
gamerule sendCommandFeedback false
gamerule logAdminCommands false
gamerule commandBlockOutput false
function mikushow:precargar
execute if score #baile mikushow matches 1 run function mikushow:baile/idle
function mikushow:baile/auto_8
tellraw @s {"text":"[MikuShow] listo. Escenario hacia el NORTE por defecto; cambialo con /function mikushow:escenario/<norte|este|sur|oeste>","color":"aqua"}
