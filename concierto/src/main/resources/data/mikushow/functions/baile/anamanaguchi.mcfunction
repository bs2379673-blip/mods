# baile de cada bailarina (mod dancers) sincronizado con la cancion; las que no bailan quedan en reposo
# cancela cualquier "volver a reposo" pendiente (si no, corta el baile a los pocos segundos)
schedule clear mikushow:baile/idle
schedule clear mikushow:bailarina/reposo_miku
schedule clear mikushow:bailarina/reposo_teto
schedule clear mikushow:bailarina/reposo_neru
dancer play @e[type=dancers:dancer,tag=!mks_teto,tag=!mks_neru] mikushow:anamanaguchi_miku
dancer play @e[type=dancers:dancer,tag=mks_teto] mikushow:idle true
dancer play @e[type=dancers:dancer,tag=mks_neru] mikushow:idle true
dancer travel @e[type=dancers:dancer] 1
