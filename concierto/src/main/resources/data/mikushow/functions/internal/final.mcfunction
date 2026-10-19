concierto iniciar despedida auto false
# final: las tres ya se fueron en una explosion (cue "final" de Triple Baka); sus luces recorren el cielo y estallan arriba
schedule clear mikushow:baile/idle
schedule clear mikushow:bailarina/reposo_miku
schedule clear mikushow:bailarina/reposo_teto
schedule clear mikushow:bailarina/reposo_neru
schedule function mikushow:final/f7 20s replace
scoreboard players set #full mikushow 0
