package com.starlevi.starbody.client;

import com.starlevi.starbody.Traits;

import java.util.ArrayList;
import java.util.List;

/**
 * El pelo marcado convertido en tiras: cada columna de pixeles seguidos en una cara cuelga de su
 * pixel de mas arriba y se dobla hacia afuera con las fisicas.
 */
public final class HairMesh {
    /** Una tira: cara, columna (a lo ancho) y filas de arriba a abajo (dentro de la cara). */
    public record Strip(SkinLayout.Face face, int column, int top, int length, float phase) {
    }

    public final List<Strip> strips = new ArrayList<>();
    public final int pixels;

    public HairMesh(Traits t) {
        int n = 0;
        for (SkinLayout.Face f : SkinLayout.FACES) {
            for (int c = 0; c < f.width(); c++) {
                int r = 0;
                while (r < f.height()) {
                    if (!t.hair(f.u0() + c, f.v0() + r)) {
                        r++;
                        continue;
                    }
                    int start = r;
                    while (r < f.height() && t.hair(f.u0() + c, f.v0() + r)) {
                        r++;
                    }
                    this.strips.add(new Strip(f, c, start, r - start, (f.u0() + c) * 0.37f + f.side()));
                    n += r - start;
                }
            }
        }
        this.pixels = n;
    }
}
