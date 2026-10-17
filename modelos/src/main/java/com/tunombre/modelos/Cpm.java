package com.tunombre.modelos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Un archivo .cpmmodel es un envoltorio: 0x53, nombre, descripcion, los datos del modelo, un "desborde" opcional, el link y el icono.
 * /cpm setskin espera solo los datos del modelo. Cuando el modelo es grande, los datos traen un LINK a una definicion
 * (guardada en el "desborde" del archivo); como el servidor no puede resolver ese link, se mete la definicion adentro.
 */
final class Cpm {
    private Cpm() { }

    private static final int HEADER = 0x53, T_END = 0, T_DEFINITION = 3, T_DEFINITION_LINK = 4;

    private static final class Rd {
        final byte[] b; int p;
        Rd(byte[] b, int p) { this.b = b; this.p = p; }
        int u8() throws IOException { if (p >= b.length) throw new IOException("fin inesperado"); return b[p++] & 0xFF; }
        int varint() throws IOException {
            int i = 0, j = 0, x;
            do { x = u8(); i |= (x & 127) << (7 * j++); if (j > 5) throw new IOException("varint"); } while ((x & 128) != 0);
            return i;
        }
        byte[] bytes(int n) throws IOException {
            if (n < 0 || p + n > b.length) throw new IOException("tamano invalido");
            byte[] r = new byte[n]; System.arraycopy(b, p, r, 0, n); p += n; return r;
        }
    }

    private static void writeVarInt(ByteArrayOutputStream o, int v) {
        while ((v & ~127) != 0) { o.write((v & 127) | 128); v >>>= 7; }
        o.write(v);
    }

    /** los datos de modelo que entiende /cpm setskin, a partir del contenido de un .cpmmodel */
    static byte[] extract(byte[] file) throws IOException {
        Rd r = new Rd(file, 0);
        if (r.u8() != HEADER) throw new IOException("no es un .cpmmodel");
        r.bytes(r.varint()); // nombre
        r.bytes(r.varint()); // descripcion
        byte[] data = r.bytes(r.varint());
        int ovfLen = r.varint();
        byte[] ovf = ovfLen > 0 ? r.bytes(ovfLen) : new byte[0];
        if (ovf.length < 4) return data;

        // los datos traen un bloque DEFINITION_LINK: se cambia por un bloque DEFINITION con el contenido del desborde
        Rd d = new Rd(data, 0);
        if (d.u8() != HEADER) throw new IOException("datos invalidos");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            int type = d.u8();
            int size = d.varint();
            byte[] payload = d.bytes(size);
            if (type == T_DEFINITION_LINK) {
                body.write(T_DEFINITION);
                writeVarInt(body, ovf.length - 3);
                body.write(ovf, 1, ovf.length - 3); // sin el 0x53 del principio ni el checksum del final
            } else {
                body.write(type);
                writeVarInt(body, size);
                body.write(payload, 0, payload.length);
            }
            if (type == T_END) break;
        }
        byte[] bd = body.toByteArray();
        int sum = 0;
        for (byte x : bd) sum = (sum + (x & 0xFF)) & 0xFFFF;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(HEADER);
        out.write(bd, 0, bd.length);
        out.write(sum >>> 8);
        out.write(sum & 0xFF);
        return out.toByteArray();
    }
}
