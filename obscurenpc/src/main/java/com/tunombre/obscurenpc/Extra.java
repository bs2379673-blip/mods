package com.tunombre.obscurenpc;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Conversacion general "dentro del mundo": lo que se le puede preguntar a cualquiera aunque su contexto no lo diga
 * (chistes, vida personal, cosas que no existen en la isla...). Siempre responde en personaje; nunca rompe la ficcion.
 * Se usa solo cuando ni el contexto, ni los temas, ni las intenciones basicas encontraron respuesta.
 */
final class Extra {
    private static final Random RND = new Random();

    private Extra() { }

    private static boolean has(List<String> toks, String text, String... keys) {
        for (String k : keys) {
            if (k.indexOf(' ') >= 0) { if ((" " + text + " ").contains(" " + k + " ")) return true; }
            else if (toks.contains(k)) return true;
        }
        return false;
    }

    private static String pick(String... a) { return a[RND.nextInt(a.length)]; }

    /** neutral, amable, seria, burlona, fria */
    private static String by(String per, String n, String a, String s, String b, String f) {
        String p = per == null ? "neutral" : per.toLowerCase(Locale.ROOT);
        return switch (p) { case "amable" -> a; case "seria" -> s; case "burlona" -> b; case "fria" -> f; default -> n; };
    }

    static Brain.Reply answer(Actor npc, ServerPlayer sp, UUID who, String text, List<String> t, String per) {
        Ctx.Data ctx = Ctx.get(npc.data().key());
        Brain.Reply r = answer0(npc, sp, text, t, per, ctx);
        return r;
    }

    private static Brain.Reply answer0(Actor npc, ServerPlayer sp, String text, List<String> t, String per, Ctx.Data ctx) {
        // ---- chistes ----
        if (has(t, text, "chiste", "chistes", "haz reir", "hazme reir", "algo gracioso", "cuentame algo gracioso")) {
            String c = Ctx.line(ctx, "chistes", null);
            if (c == null) c = by(per,
                    pick("¿Un chiste? Mi vida entera lo es, pero no hace gracia.", "¿Sabes qué es lo más gracioso de esta isla? Que algunos aún creen que van a salir."),
                    pick("Te cuento uno: un soldado, un científico y un infectado entran a un bar. Salió uno solo. Perdona, el humor se me murió con el mundo.", "¿Quieres reír? Mira cómo corre un infectado detrás de un conejo. Es lo mejor que tenemos."),
                    "No es momento para chistes.",
                    pick("¿Por qué el infectado cruzó el puente? Porque lo perseguía alguien más rápido. Tú, por ejemplo.", "Un chiste: tu puntería. Gracias, gracias, estoy aquí toda la semana."),
                    "No estoy para eso.");
            return new Brain.Reply(c, by(per, "shrug", "nod", "shake_head", "shrug", "shake_head"));
        }
        // ---- "eres una IA / un bot / real" ----
        if (has(t, text, "eres una ia", "eres un bot", "eres un robot", "eres real", "eres humano", "eres un programa", "eres artificial", "eres un npc", "eres un personaje", "eres inteligencia artificial", "chatgpt", "eres un modelo", "te programaron", "estas programado")) {
            return new Brain.Reply(by(per,
                    pick("¿Una qué? No sé qué dices. Sangro, como todos, creo.", "Qué cosas preguntas. Aquí todos somos de carne... y a veces de algo peor."),
                    "Soy tan real como el resto. Quizá no tanto como quisiera, con lo que pasa en esta isla.",
                    "Soy una persona, no un aparato. No me hagas repetirlo.",
                    pick("Sí, soy un robot. Tengo un botón en la espalda y todo. ¿Quieres apretarlo?", "Claro, y tú eres un sueño febril de la flor."),
                    "Qué pregunta más estúpida."), "shake_head");
        }
        if (has(t, text, "quien te creo", "quien te hizo", "quien te programo", "quien es tu creador", "quien te inventó", "quien te invento", "tu creador", "tu dueño", "tu dueno")) {
            return new Brain.Reply(by(per,
                    "Mis padres, supongo. Como todos. ¿A qué viene eso?",
                    "Mi madre me dio la vida, y lo demás me lo fui ganando yo. ¿Por qué lo preguntas?",
                    "Mis datos son míos. Eso no se pregunta.",
                    "Un poeta tragicómico, seguramente. Con poco presupuesto.",
                    "Nadie que te importe."), null);
        }
        // ---- cosas que no existen en este mundo ----
        if (has(t, text, "internet", "google", "youtube", "celular", "telefono", "whatsapp", "instagram", "tiktok", "facebook", "minecraft", "discord", "mod", "mods", "plugin", "servidor", "programar", "codigo", "python", "java",
                "bitcoin", "netflix", "pelicula", "videojuego", "videojuegos", "twitter", "wifi", "computadora", "ordenador", "inteligencia artificial", "openai", "claude", "anthropic", "gpt", "windows", "play", "playstation", "xbox", "steam")) {
            return new Brain.Reply(by(per,
                    pick("No sé de qué hablas. Suena a algo de antes... de otro mundo.", "Eso no existe aquí, {jugador}. Aquí tenemos lo que tenemos."),
                    pick("Ay, qué palabras raras usas... no las conozco. ¿Estás bien, {jugador}?", "Eso no me suena de nada. ¿Seguro que no te golpeaste la cabeza?"),
                    "No conozco eso. Hable de cosas que importen aquí.",
                    pick("Wow, hablas como de otra dimensión. Yo solo entiendo de sobrevivir y de ser guapo.", "¿Eso se come? ¿Se dispara? Si no, no me interesa."),
                    "No sé qué es eso. Ni me importa."), "shrug");
        }
        // ---- edad, origen, familia ----
        if (has(t, text, "cuantos anos tienes", "que edad tienes", "tu edad", "cuantos anos")) {
            return new Brain.Reply(by(per, "Los suficientes para saber que no quiero más.", "Ya perdí la cuenta de los días, imagínate los años.", "Eso no es relevante.", "Los que me quedaron después de este lío. Pocos y mal vividos.", "Los suficientes."), "shrug");
        }
        if (has(t, text, "de donde eres", "de donde vienes", "donde naciste", "eres de aqui")) {
            return new Brain.Reply(by(per, "De lejos. Antes de que todo esto empezara. No me gusta acordarme.", "De un sitio mejor, supongo. Ya casi no lo recuerdo.", "De donde me tocó. Es información personal.", "De por ahí, de donde no hay flores raras. Qué tiempos.", "Da igual."), null);
        }
        if (has(t, text, "tienes familia", "tienes hijos", "tienes esposa", "tienes esposo", "tienes pareja", "tienes novia", "tienes novio", "tus padres", "tienes hermanos", "hermano", "hermana")) {
            return new Brain.Reply(by(per, "Tuve. No quiero hablar de eso ahora.", "Los extraño más de lo que puedo decir... Prefiero no seguir, ¿sí?", "Es privado.", "Mi familia soy yo y mi mal humor. Muy unidos.", "No."), "think");
        }
        // ---- amor / piropos ----
        if (has(t, text, "te amo", "te quiero", "casate conmigo", "eres guapo", "eres guapa", "eres lindo", "eres linda", "eres hermoso", "eres hermosa", "salimos", "novia", "novio", "beso", "besame", "enamorado", "enamorada")) {
            return new Brain.Reply(by(per, "Ah... eh... gracias, supongo. No es el momento, {jugador}.", "Qué detalle, {jugador}... me sacaste los colores. Pero con todo lo que pasa, no sé.", "Esto es inapropiado. Hablemos de otra cosa.",
                    pick("Ay, para, que me sonrojo... Mentira. Pero guárdalo para cuando haya menos infectados.", "Si sobrevivimos a esto, lo hablamos. Es una promesa, no un sí."), "...No."), "shrug");
        }
        // ---- comida / sueno ----
        if (has(t, text, "tienes hambre", "que comes", "que comiste", "comida", "tienes sed", "tienes sueno", "estas cansado", "estas cansada", "dormiste")) {
            return new Brain.Reply(by(per, "Apenas como y duermo mal. Pero aquí seguimos.", "Siempre algo de hambre, siempre algo de sueño. Ya me acostumbré.", "Eso no importa. Hay que aguantar.", "Tengo hambre de pizza y sueño de tres semanas. Pero ya ves, glamour.", "Aguanto."), null);
        }
        // ---- el jugador ----
        if (has(t, text, "como me llamo", "quien soy", "sabes quien soy", "sabes mi nombre", "me conoces", "te acuerdas de mi", "te acuerdas de mi")) {
            String n = sp != null ? sp.getGameProfile().getName() : "desconocido";
            int v = sp != null ? npc.counter(sp.getUUID(), "visitas") : 0;
            return new Brain.Reply(by(per,
                    v > 1 ? "Claro, eres " + n + ". Ya hemos hablado antes." : "Te llamas " + n + ", ¿no?",
                    v > 1 ? "¡Claro que sí! Eres " + n + ". Me alegra volver a verte." : "Eres " + n + ", me parece. Mucho gusto.",
                    "Eres " + n + ". Lo tengo anotado.",
                    "El de siempre, " + n + ". Famoso en toda la isla por tropezarse.",
                    n + ". Qué más da."), null);
        }
        if (has(t, text, "que opinas de mi", "que piensas de mi", "confias en mi", "te caigo bien", "te caigo mal", "soy buena persona", "soy de fiar")) {
            int af = sp != null ? npc.counter(sp.getUUID(), "afinidad") : 0;
            String s = af >= 4 ? by(per, "Confío en ti. No es poca cosa por aquí.", "Eres de lo mejor que me ha pasado en esta isla, de verdad.", "Eres de confianza. Mantenlo así.", "Me caes bien, y odio decirlo. Que no se te suba.", "No estás tan mal.")
                    : af < 0 ? by(per, "Me das mala espina, {jugador}.", "Quisiera confiar en ti, pero me lo has puesto difícil...", "No. Todavía no.", "Tienes un talento natural para caerme mal.", "No.")
                    : by(per, "Todavía no te conozco lo suficiente. Dame tiempo.", "Pareces buena gente, pero aquí hay que ir despacio.", "Aún te estoy evaluando.", "Pareces inofensivo. Eso o muy buen actor.", "Aún no.");
            return new Brain.Reply(s, null);
        }
        if (has(t, text, "puedo confiar en ti", "eres de fiar", "me mientes", "mientes", "eres sincero", "eres sincera", "me ocultas algo", "ocultas algo", "escondes algo", "me estas ocultando")) {
            return new Brain.Reply(by(per, "Todos ocultamos algo aquí. Pero no soy tu enemigo, eso te lo juro.", "Te diría todo si pudiera, {jugador}. A veces saber demasiado es peligroso.", "Hay cosas que no puedo decir. No insistas.", "¿Yo? Soy un libro abierto. Con varios capítulos arrancados.", "Todos mienten. Yo, menos."), "think");
        }
        // ---- supervivencia general ----
        if (has(t, text, "como sobrevivo", "como sobrevivir", "consejos", "consejo", "tips", "que me recomiendas", "recomiendas", "algun truco", "trucos")) {
            return new Brain.Reply(by(per,
                    pick("Haz ruido lo mínimo, no salgas de noche y no te alejes de la luz.", "Mantente acompañado, lleva comida y duerme cuando puedas."),
                    pick("Cuídate, {jugador}: la luz es tu amiga, la oscuridad no. Y no andes solo si puedes evitarlo.", "Ten siempre una salida cerca y alguien que te cubra."),
                    pick("Rutas conocidas, refugio antes del anochecer y nunca solo.", "Si algo no se mueve como debe, no te acerques."),
                    pick("Consejo gratis: no mueras. Parece obvio, pero mira a tu alrededor.", "Corre rápido, esconde bien, y si puedes, que el otro sea más lento que tú."),
                    "No mueras. Lo demás es detalle."), "nod");
        }
        // ---- peleas / retos ----
        if (has(t, text, "peleamos", "te reto", "pelea conmigo", "te voy a matar", "te mato", "vas a morir", "te voy a pegar")) {
            return new Brain.Reply(by(per, "No quieres hacer eso, {jugador}.", "Por favor, no... los dos perdemos si peleamos.", "Una amenaza más y me defiendo.", "Ohh, qué miedo. Ven, intenta.", "Inténtalo."), "guard");
        }
        // ---- muerte ----
        if (has(t, text, "que pasa si muero", "que pasa cuando mueres", "morir", "la muerte", "tienes miedo a morir")) {
            return new Brain.Reply(by(per, "Hay cosas peores que morir en esta isla. Ya lo verás.", "No quiero pensarlo, la verdad.", "La muerte es un riesgo. Se gestiona.", "Dicen que duele. Yo prefiero no comprobarlo.", "Todos morimos."), "think");
        }
        // ---- que sabes ----
        if (has(t, text, "que sabes", "de que puedes hablar", "que me puedes contar", "que puedes decirme", "de que hablamos", "que puedes contarme", "preguntame", "que te puedo preguntar")) {
            return new Brain.Reply(by(per,
                    "Puedes preguntarme por este sitio, por la gente de aquí, o por lo que me haya tocado vivir.",
                    "Pregúntame lo que quieras, {jugador}. Si lo sé, te lo cuento con gusto.",
                    "Sobre lo que importa aquí. Elige tema.",
                    "De todo un poco. Menos de lo que no quiero hablar, que es casi todo.",
                    "Tú pregunta. Yo decido."), "talk_a");
        }
        // ---- opinion generica ----
        if (text.startsWith("que opinas") || text.startsWith("que piensas") || text.startsWith("que te parece") || text.startsWith("tu opinion")) {
            return new Brain.Reply(by(per, "No tengo una opinión formada. Aquí lo único seguro es que mañana puede ser peor.", "Mmm... no sé, {jugador}. Cuéntame tú qué piensas.", "Prefiero reservarme esa opinión.", "Mi opinión es cara, y tú vienes sin cambio.", "No opino."), "think");
        }
        return null;
    }
}
