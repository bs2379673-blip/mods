package com.tunombre.obscurenpc;

import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Ordenes por chat a un NPC ("sigueme", "quedate aqui", "protegeme", "defiende esta zona"...). Se reconocen frases sueltas
 * dentro del mensaje (sin importar acentos ni mayusculas). Cada NPC decide si obedece: activado en su editor, condicion
 * opcional (afinidad, banderas) y un tope de acompañantes por jugador. El interruptor general esta en config/obscurenpc-common.toml.
 */
public final class Orders {
    private static final Random RND = new Random();

    public static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.BooleanValue ENABLED = B.comment("Permite que los jugadores den ordenes a los NPC por chat (sigueme, quedate aqui, protegeme, defiende esta zona). Cada NPC ademas debe tener activado 'Acepta ordenes' en su editor.")
            .define("ordenes_por_chat", true);
    public static final ForgeConfigSpec.IntValue MAX_FOLLOWERS = B.comment("Maximo de NPC que un mismo jugador puede llevar siguiendolo o protegiendolo a la vez")
            .defineInRange("max_acompanantes", 3, 1, 20);
    public static final ForgeConfigSpec SPEC = B.build();

    public enum Kind { SEGUIR, QUIETO, PROTEGER, ZONA, LIBRE }

    private static final String[][] PHRASES = {
            // SEGUIR
            {"sigueme", "seguime", "sigue me", "siguime", "ven conmigo", "vente conmigo", "vente para aca", "acompaname", "acompañame", "acompa ame", "quedate conmigo", "quedate a mi lado", "quedate cerca",
                    "no te separes", "ven con nosotros", "unete a mi", "unete a nosotros", "camina conmigo", "vamos juntos", "sigueme por favor", "podes seguirme", "puedes seguirme", "me sigues", "ven detras mio", "ven detras de mi"},
            // QUIETO
            {"quedate aqui", "quedate aca", "quedate ahi", "quedate alli", "quedate quieto", "quedate quieta", "espera aqui", "esperame aqui", "esperame aca", "esperame", "no te muevas", "mantente aqui", "mantente quieto",
                    "quieto aqui", "aguarda aqui", "aguarda", "permanece aqui", "detente", "alto ahi", "no te vayas", "espera"},
            // PROTEGER
            {"protegeme", "protejeme", "cuidame", "defiendeme", "cubreme", "cuida mi espalda", "cuidame las espaldas", "pelea conmigo", "ayudame a pelear", "escoltame", "protege me", "defiende me", "me proteges", "me cuidas"},
            // ZONA
            {"defiende esta zona", "defiende este lugar", "defiende esta area", "defiende el area", "defiende la zona", "protege esta zona", "protege este lugar", "protege esta area", "protege la zona", "defiende esto", "protege esto",
                    "vigila esta zona", "vigila este lugar", "vigila el area", "vigila", "guarda esta zona", "haz guardia", "hace guardia", "monta guardia", "cuida este lugar", "cuida esta zona", "defiende la base", "protege la base",
                    "defiende el campamento", "protege el campamento", "defiende el refugio", "protege el refugio", "defiende aqui", "protege aqui", "custodia esto", "custodia este lugar"},
            // LIBRE
            {"libre", "estas libre", "ya puedes irte", "puedes irte", "puedes retirarte", "vuelve a tu puesto", "vuelve a casa", "ve a tu casa", "relajate", "descansa", "a tu puesto", "olvidalo", "cancela", "ya no me sigas",
                    "deja de seguirme", "no me sigas", "separemonos", "puedes irte tranquilo", "retirate", "ya no hace falta", "deja la guardia", "baja la guardia", "puesto de descanso", "rompan filas"}
    };

    private static final String[] GROUP = {"todos", "todas", "equipo", "muchachos", "chicos", "chicas", "grupo", "escuadron", "escuadra", "compañeros", "companeros", "soldados", "gente"};

    private Orders() { }

    /** @return la orden que contiene el mensaje, o null */
    public static Kind detect(String message) {
        String t = " " + Brain.norm(message) + " ";
        // la mas larga (mas especifica) gana: "quedate conmigo" antes que "quedate"
        Kind best = null;
        int bl = 0;
        Kind[] ks = Kind.values();
        for (int k = 0; k < ks.length; k++) {
            for (String p : PHRASES[k]) {
                String n = Brain.norm(p);
                if (n.length() > bl && t.contains(" " + n + " ")) { bl = n.length(); best = ks[k]; }
            }
        }
        // "para"/"alto" sueltos como orden de detenerse (solo si es el mensaje entero)
        String solo = Brain.norm(message);
        if (best == null && (solo.equals("para") || solo.equals("alto") || solo.equals("quieto") || solo.equals("quieta"))) best = Kind.QUIETO;
        return best;
    }

    public static boolean isGroup(String message) {
        String t = " " + Brain.norm(message) + " ";
        for (String g : GROUP) if (t.contains(" " + Brain.norm(g) + " ")) return true;
        return false;
    }

    // ------------------------------------------------------------------ aplicar ----
    public static Brain.Reply apply(Actor npc, ServerPlayer p, Kind kind) {
        Ctx.Data ctx = Ctx.get(npc.data().key());
        String per = Ctx.personality(ctx, npc);
        NpcData d = npc.data();
        if (!ENABLED.get() || !d.acepta_ordenes) return new Brain.Reply(line(ctx, "o_rechazo", per, "no_acepta"), null);
        if (d.ordenes_si != null && !d.ordenes_si.isBlank() && !Fmt.test(npc, p, new JsonPrimitive(d.ordenes_si.trim())))
            return new Brain.Reply(line(ctx, "o_condicion", per, "condicion"), "shake_head");
        if ((kind == Kind.SEGUIR || kind == Kind.PROTEGER) && !p.getUUID().equals(npc.ordenDueno)) {
            int n = 0;
            for (Actor o : npc.level().getEntitiesOfClass(Actor.class, npc.getBoundingBox().inflate(80)))
                if (o != npc && p.getUUID().equals(o.ordenDueno) && (o.orden.equals("seguir") || o.orden.equals("proteger"))) n++;
            if (n >= MAX_FOLLOWERS.get()) return new Brain.Reply(line(ctx, "o_lleno", per, "lleno"), "shrug");
        }
        switch (kind) {
            case SEGUIR -> { npc.orden = "seguir"; npc.ordenDueno = p.getUUID(); npc.ordenPos = null; return new Brain.Reply(line(ctx, "o_seguir", per, "seguir"), "nod"); }
            case PROTEGER -> { npc.orden = "proteger"; npc.ordenDueno = p.getUUID(); npc.ordenPos = null; return new Brain.Reply(line(ctx, "o_proteger", per, "proteger"), "salute"); }
            case QUIETO -> { npc.orden = "quieto"; npc.ordenDueno = p.getUUID(); npc.ordenPos = npc.blockPosition(); return new Brain.Reply(line(ctx, "o_quieto", per, "quieto"), "stop"); }
            case ZONA -> { npc.orden = "zona"; npc.ordenDueno = p.getUUID(); npc.ordenPos = npc.blockPosition(); return new Brain.Reply(line(ctx, "o_zona", per, "zona"), "salute"); }
            default -> {
                npc.orden = "";
                npc.ordenDueno = null;
                BlockPos h = npc.home();
                npc.getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 1.0D);
                return new Brain.Reply(line(ctx, "o_libre", per, "libre"), "nod");
            }
        }
    }

    private static String line(Ctx.Data ctx, String key, String per, String def) {
        String c = Ctx.line(ctx, key, null);
        if (c != null) return c;
        String[] l = defaults(per, def);
        return l[RND.nextInt(l.length)];
    }

    private static String[] defaults(String per, String k) {
        String p = per == null ? "neutral" : per.toLowerCase(Locale.ROOT);
        return switch (k) {
            case "seguir" -> switch (p) {
                case "amable" -> new String[]{"¡Claro, voy contigo, {jugador}!", "Te sigo. No te alejes demasiado, ¿sí?", "Vamos juntos, entonces."};
                case "seria" -> new String[]{"Entendido. Detrás de ti.", "A tus órdenes. Mantén el paso.", "Te sigo."};
                case "burlona" -> new String[]{"¿De niñera? Está bien, voy detrás. Pero tú guías, valiente.", "Claro, te sigo. Si algo te muerde, grita fuerte.", "Vamos, vamos. Yo te cubro la retaguardia."};
                case "fria" -> new String[]{"...Está bien. Camina.", "Voy detrás. No hables de más.", "Mm. Vamos."};
                default -> new String[]{"De acuerdo, te sigo.", "Voy contigo.", "Vamos, {jugador}."};
            };
            case "quieto" -> switch (p) {
                case "amable" -> new String[]{"Aquí me quedo. Vuelve pronto, ¿sí?", "Te espero aquí, {jugador}.", "Tranquilo, no me muevo de este lugar."};
                case "seria" -> new String[]{"Permaneceré en posición.", "Aquí me quedo. No tardes.", "Entendido. Sin moverme."};
                case "burlona" -> new String[]{"Me quedo aquí, como una estatua. Una muy guapa.", "Claro, te espero. No es que tenga otros planes.", "Quieto. Como un buen perro. Es broma."};
                case "fria" -> new String[]{"Aquí estaré.", "Mm. Aquí.", "No me muevo."};
                default -> new String[]{"Me quedo aquí.", "De acuerdo, te espero.", "Aquí estaré, {jugador}."};
            };
            case "proteger" -> switch (p) {
                case "amable" -> new String[]{"¡Cuenta conmigo! No dejaré que te pase nada.", "Te cuido, {jugador}. Quédate cerca de mí.", "Yo te protejo. Para eso estoy."};
                case "seria" -> new String[]{"Protegeré tu posición. Procede.", "Entendido. Nada se te acercará.", "A tu lado. Atento a los flancos."};
                case "burlona" -> new String[]{"¿Guardaespaldas? Me subes el sueldo después, ¿eh?", "Hecho. Nadie toca a mi cliente favorito.", "Yo te cubro. Tú intenta no morirte por tu cuenta."};
                case "fria" -> new String[]{"Lo haré. No me lo agradezcas.", "Te cubro. Mantente vivo.", "Entendido."};
                default -> new String[]{"Entendido, te protejo.", "Te cubro la espalda.", "Cuenta conmigo, {jugador}."};
            };
            case "zona" -> switch (p) {
                case "amable" -> new String[]{"Yo me encargo de este lugar. Nadie va a pasar.", "Vigilaré esta zona, {jugador}. Descuida.", "Quedó a mi cuidado."};
                case "seria" -> new String[]{"Zona asegurada. Tomo la guardia.", "Entendido. Defenderé esta posición.", "Puesto cubierto. Procede con lo tuyo."};
                case "burlona" -> new String[]{"Guardia de turno, qué emoción. Nadie pasa. Bueno, nadie que muerda.", "Se hará. Si algo entra, lo recibo con una sonrisa y algo de plomo.", "Este rincón ahora es mío. Que nadie pise el felpudo."};
                case "fria" -> new String[]{"Nadie pasará.", "Lo defiendo.", "Hecho."};
                default -> new String[]{"Defenderé esta zona.", "Yo vigilo aquí.", "Entendido, me quedo de guardia."};
            };
            case "libre" -> switch (p) {
                case "amable" -> new String[]{"Está bien. Cuídate mucho, {jugador}.", "De acuerdo. Si me necesitas, aquí estoy.", "Entonces vuelvo a lo mío. ¡Suerte!"};
                case "seria" -> new String[]{"Entendido. Vuelvo a mi puesto.", "Me retiro. Mucha suerte.", "A la orden. Fin de la escolta."};
                case "burlona" -> new String[]{"¡Libre al fin! Aunque lo disfrutaba. Un poco.", "Bien, bien. Si te asustas, ya sabes dónde encontrarme.", "Despedido con honores. Adiós, jefe."};
                case "fria" -> new String[]{"Bien.", "Me voy.", "Como quieras."};
                default -> new String[]{"De acuerdo, vuelvo a lo mío.", "Entendido. Cuídate.", "Hasta luego, {jugador}."};
            };
            case "lleno" -> new String[]{"Ya vamos demasiados contigo, {jugador}. Con tanta gente haremos ruido y nos van a oír.", "Somos muchos ya. Suelta a alguno y entonces voy."};
            case "condicion" -> switch (p) {
                case "amable" -> new String[]{"Todavía no nos conocemos lo suficiente, {jugador}. Dame tiempo.", "Me gustaría ayudarte, pero aún no confío del todo."};
                case "burlona" -> new String[]{"¿Órdenes? Primero ganas mi confianza, campeón.", "Ni nos hemos tomado un café juntos y ya me das órdenes."};
                case "fria" -> new String[]{"No te conozco. No.", "No recibo órdenes de desconocidos."};
                default -> new String[]{"No te conozco lo suficiente como para seguirte.", "Primero tienes que ganarte mi confianza."};
            };
            default /* no_acepta */ -> switch (p) {
                case "amable" -> new String[]{"Ay, lo siento, {jugador}, pero yo no puedo salir de aquí.", "Quisiera ayudarte, pero mi lugar está aquí."};
                case "seria" -> new String[]{"No recibo órdenes de civiles.", "Tengo mis propias órdenes. No puedo."};
                case "burlona" -> new String[]{"¿Yo, hacerte caso? Qué ocurrencia más graciosa.", "Me encantaría, pero soy de esos que no obedecen. Intenta con otro."};
                case "fria" -> new String[]{"No.", "No soy tu perro."};
                default -> new String[]{"No puedo acompañarte, tengo cosas que hacer aquí.", "Lo siento, {jugador}, eso no me corresponde."};
            };
        };
    }

    /** NPCs a los que va dirigida una orden de grupo ("¡todos, síganme!") */
    public static List<Actor> group(ServerPlayer p) {
        return p.level().getEntitiesOfClass(Actor.class, p.getBoundingBox().inflate(14), a -> a.data().acepta_ordenes && a.hasLineOfSight(p));
    }
}
