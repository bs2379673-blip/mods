# cancela cualquier "volver a reposo" pendiente (si no, corta el baile a los pocos segundos)
schedule clear mikushow:baile/idle
schedule clear mikushow:bailarina/reposo_miku
schedule clear mikushow:bailarina/reposo_teto
schedule clear mikushow:bailarina/reposo_neru
dancer play @e[type=dancers:dancer] mikushow:reverencia
schedule function mikushow:baile/idle 3s replace
