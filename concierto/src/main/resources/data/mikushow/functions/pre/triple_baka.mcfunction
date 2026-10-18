function mikushow:concierto/escenario
concierto iniciar pre_triple_baka auto false
# neru aparece antes de triple_baka
schedule function mikushow:pre/triple_baka_2 3s replace
schedule function mikushow:pre/triple_baka_3 6s replace
schedule function mikushow:song/triple_baka/start 7s replace
