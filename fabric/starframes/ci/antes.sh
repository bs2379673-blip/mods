#!/bin/bash
# Genera el video y la imagen de prueba (con marcas de ARRIBA e IZQUIERDA para ver la orientacion).
set -e
D="$1/build/prueba"
mkdir -p "$D"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "testsrc2=size=640x360:rate=30:duration=20" -f lavfi -i "sine=frequency=440:duration=20" \
  -vf "drawbox=x=0:y=0:w=640:h=60:color=red@1:t=fill,drawbox=x=0:y=0:w=60:h=360:color=yellow@1:t=fill" \
  -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest "$D/video.mp4"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=0x2050c0:s=400x400" \
  -vf "drawbox=x=0:y=0:w=400:h=60:color=red@1:t=fill,drawbox=x=0:y=0:w=60:h=400:color=yellow@1:t=fill,drawbox=x=170:y=170:w=60:h=60:color=white@1:t=fill" \
  -frames:v 1 "$D/imagen.png"
ls -la "$D"
