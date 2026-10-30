package com.annis.npc.client.punchy;

import java.util.HashMap;
import java.util.Map;

/**
 * Formulas Molang sencillas de las animaciones Bedrock: numeros, + - * /, parentesis, menos unario,
 * comparaciones y "? :", query.anim_time / query.life_time y las funciones math.* mas usadas
 * (sin y cos en grados, como en Bedrock). Lo que no conoce vale 0.
 */
public final class Molang {
    public interface Expr {
        float eval(float animTime, float lifeTime);
    }

    private static final Map<String, Expr> CACHE = new HashMap<>();

    private final String s;
    private int i;

    private Molang(String s) {
        this.s = s.toLowerCase(java.util.Locale.ROOT);
    }

    public static synchronized Expr parse(String text) {
        return CACHE.computeIfAbsent(text, k -> {
            try {
                Molang m = new Molang(k);
                Expr e = m.ternary();
                return e;
            } catch (RuntimeException ex) {
                return (a, l) -> 0;
            }
        });
    }

    public static Expr constant(float v) {
        return (a, l) -> v;
    }

    private void ws() {
        while (this.i < this.s.length() && Character.isWhitespace(this.s.charAt(this.i))) {
            this.i++;
        }
    }

    private boolean eat(String tok) {
        ws();
        if (this.s.startsWith(tok, this.i)) {
            this.i += tok.length();
            return true;
        }
        return false;
    }

    private Expr ternary() {
        Expr c = compare();
        if (eat("?")) {
            Expr a = ternary();
            Expr b = eat(":") ? ternary() : constant(0);
            return (x, l) -> c.eval(x, l) != 0 ? a.eval(x, l) : b.eval(x, l);
        }
        return c;
    }

    private Expr compare() {
        Expr a = sum();
        while (true) {
            if (eat("<=")) {
                Expr l = a, r = sum();
                a = (x, t) -> l.eval(x, t) <= r.eval(x, t) ? 1 : 0;
            } else if (eat(">=")) {
                Expr l = a, r = sum();
                a = (x, t) -> l.eval(x, t) >= r.eval(x, t) ? 1 : 0;
            } else if (eat("<")) {
                Expr l = a, r = sum();
                a = (x, t) -> l.eval(x, t) < r.eval(x, t) ? 1 : 0;
            } else if (eat(">")) {
                Expr l = a, r = sum();
                a = (x, t) -> l.eval(x, t) > r.eval(x, t) ? 1 : 0;
            } else {
                return a;
            }
        }
    }

    private Expr sum() {
        Expr a = product();
        while (true) {
            if (eat("+")) {
                Expr l = a, r = product();
                a = (x, t) -> l.eval(x, t) + r.eval(x, t);
            } else if (eat("-")) {
                Expr l = a, r = product();
                a = (x, t) -> l.eval(x, t) - r.eval(x, t);
            } else {
                return a;
            }
        }
    }

    private Expr product() {
        Expr a = unary();
        while (true) {
            if (eat("*")) {
                Expr l = a, r = unary();
                a = (x, t) -> l.eval(x, t) * r.eval(x, t);
            } else if (eat("/")) {
                Expr l = a, r = unary();
                a = (x, t) -> {
                    float d = r.eval(x, t);
                    return d == 0 ? 0 : l.eval(x, t) / d;
                };
            } else {
                return a;
            }
        }
    }

    private Expr unary() {
        if (eat("-")) {
            Expr e = unary();
            return (x, t) -> -e.eval(x, t);
        }
        if (eat("+")) {
            return unary();
        }
        if (eat("!")) {
            Expr e = unary();
            return (x, t) -> e.eval(x, t) == 0 ? 1 : 0;
        }
        return atom();
    }

    private Expr[] args() {
        java.util.List<Expr> list = new java.util.ArrayList<>();
        if (!eat("(")) {
            return new Expr[0];
        }
        if (eat(")")) {
            return new Expr[0];
        }
        do {
            list.add(ternary());
        } while (eat(","));
        eat(")");
        return list.toArray(new Expr[0]);
    }

    private static float deg(float d) {
        return (float) Math.toRadians(d);
    }

    private Expr atom() {
        ws();
        if (eat("(")) {
            Expr e = ternary();
            eat(")");
            return e;
        }
        int start = this.i;
        if (this.i < this.s.length() && (Character.isDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.')) {
            while (this.i < this.s.length() && (Character.isDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.')) {
                this.i++;
            }
            float v = Float.parseFloat(this.s.substring(start, this.i));
            return constant(v);
        }
        while (this.i < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '_'
                || this.s.charAt(this.i) == '.')) {
            this.i++;
        }
        String id = this.s.substring(start, this.i);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Molang: " + this.s);
        }
        if (id.startsWith("q.")) {
            id = "query." + id.substring(2);
        } else if (id.startsWith("v.")) {
            id = "variable." + id.substring(2);
        }
        switch (id) {
            case "query.anim_time":
                return (x, t) -> x;
            case "query.life_time":
                return (x, t) -> t;
            case "math.pi":
                return constant((float) Math.PI);
            case "true":
                return constant(1);
            case "false":
                return constant(0);
            default:
                break;
        }
        if (id.startsWith("math.")) {
            Expr[] a = args();
            Expr a0 = a.length > 0 ? a[0] : constant(0);
            Expr a1 = a.length > 1 ? a[1] : constant(0);
            Expr a2 = a.length > 2 ? a[2] : constant(0);
            return switch (id) {
                case "math.sin" -> (x, t) -> (float) Math.sin(deg(a0.eval(x, t)));
                case "math.cos" -> (x, t) -> (float) Math.cos(deg(a0.eval(x, t)));
                case "math.abs" -> (x, t) -> Math.abs(a0.eval(x, t));
                case "math.sqrt" -> (x, t) -> (float) Math.sqrt(Math.max(0, a0.eval(x, t)));
                case "math.floor" -> (x, t) -> (float) Math.floor(a0.eval(x, t));
                case "math.ceil" -> (x, t) -> (float) Math.ceil(a0.eval(x, t));
                case "math.round" -> (x, t) -> Math.round(a0.eval(x, t));
                case "math.min" -> (x, t) -> Math.min(a0.eval(x, t), a1.eval(x, t));
                case "math.max" -> (x, t) -> Math.max(a0.eval(x, t), a1.eval(x, t));
                case "math.mod" -> (x, t) -> {
                    float d = a1.eval(x, t);
                    return d == 0 ? 0 : a0.eval(x, t) % d;
                };
                case "math.pow" -> (x, t) -> (float) Math.pow(a0.eval(x, t), a1.eval(x, t));
                case "math.clamp" -> (x, t) -> Math.max(a1.eval(x, t), Math.min(a2.eval(x, t), a0.eval(x, t)));
                case "math.lerp" -> (x, t) -> {
                    float k = a2.eval(x, t);
                    return a0.eval(x, t) + (a1.eval(x, t) - a0.eval(x, t)) * k;
                };
                case "math.exp" -> (x, t) -> (float) Math.exp(a0.eval(x, t));
                case "math.atan2" -> (x, t) -> (float) Math.toDegrees(Math.atan2(a0.eval(x, t), a1.eval(x, t)));
                default -> constant(0);
            };
        }
        // variables y consultas que no conocemos: 0 (y si traen argumentos, se consumen)
        if (this.i < this.s.length() && this.s.charAt(this.i) == '(') {
            args();
        }
        return constant(0);
    }
}
