# baile de cada bailarina (mod dancers) sincronizado con la cancion; las que no bailan quedan en reposo
# cancela cualquier "volver a reposo" pendiente (si no, corta el baile a los pocos segundos)
schedule clear mikushow:baile/idle
schedule clear mikushow:bailarina/reposo_miku
schedule clear mikushow:bailarina/reposo_teto
schedule clear mikushow:bailarina/reposo_neru
# el baile lo arranca el mod concierto (Dancing.danceFor) al iniciar la cancion
