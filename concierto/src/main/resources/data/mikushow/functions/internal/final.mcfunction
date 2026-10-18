function mikushow:concierto/escenario
concierto iniciar despedida auto false
# 1) las tres saludan con los dos brazos; cielo de fiesta con fuegos de estrellas
schedule clear mikushow:baile/idle
schedule clear mikushow:bailarina/reposo_miku
schedule clear mikushow:bailarina/reposo_teto
schedule clear mikushow:bailarina/reposo_neru
execute if score #baile mikushow matches 1 run function mikushow:baile/auto_2
schedule function mikushow:final/f2 2s replace
schedule function mikushow:final/f3 4200ms replace
schedule function mikushow:final/f4 7500ms replace
schedule function mikushow:final/f5 9s replace
schedule function mikushow:final/f6 10500ms replace
schedule function mikushow:final/f7 13s replace
scoreboard players set #full mikushow 0
