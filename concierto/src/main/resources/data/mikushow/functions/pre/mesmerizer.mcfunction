function mikushow:concierto/escenario
concierto iniciar pre_mesmerizer auto false
# teto aparece antes de mesmerizer
schedule function mikushow:pre/mesmerizer_2 3s replace
schedule function mikushow:pre/mesmerizer_3 6s replace
schedule function mikushow:song/mesmerizer/start 7s replace
