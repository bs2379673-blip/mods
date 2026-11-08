import com.starlevi.starpackport.convert.PackConverter;
import java.nio.file.*;
import java.util.zip.*;

/** Prueba afuera del juego: java Probar <client-extra.jar> <salida> <pack>... */
public class Probar {
    public static void main(String[] a) throws Exception {
        ZipFile vz = new ZipFile(a[0]);
        PackConverter.Vanilla v = p -> {
            try { ZipEntry e = vz.getEntry(p); return e == null ? null : vz.getInputStream(e).readAllBytes(); }
            catch (Exception ex) { return null; }
        };
        for (int i = 2; i < a.length; i++) {
            Path p = Paths.get(a[i]);
            String n = p.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
            boolean need = PackConverter.needsConversion(p);
            if (!need) { System.out.println("-- " + p.getFileName() + ": no hace falta"); continue; }
            long t = System.currentTimeMillis();
            var r = PackConverter.convert(p, Paths.get(a[1]).resolve(n), v, System.out::println);
            System.out.println("== " + p.getFileName() + " (" + (System.currentTimeMillis() - t) + " ms)");
            r.forEach(x -> System.out.println("   " + x));
        }
    }
}
