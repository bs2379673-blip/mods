package com.starlevi.starscenes.emf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Las formulas de las animaciones de EMF / OptiFine CEM (por ejemplo
 * "sin(limb_swing * 0.6662) * limb_speed * 1.4"), leidas una vez y evaluadas cada cuadro.
 * Interprete propio de StarScenes. Los verdaderos valen 1 y los falsos 0.
 */
public abstract class Expr {
    /** De donde salen los valores con nombre (variables del personaje, var.*, head.rx...). */
    public interface Scope {
        float get(String name);

        /** Numero al azar estable por personaje (para random(semilla)). */
        default float random(float seed) {
            long h = Double.doubleToLongBits(seed) * 0x9E3779B97F4A7C15L;
            return ((h >>> 11) & 0xFFFFFF) / (float) 0x1000000;
        }
    }

    public abstract float eval(Scope s);

    /** Lo que no entendio (funciones desconocidas), para avisar. */
    public static final List<String> UNKNOWN = new ArrayList<>();

    // ---------------------------------------------------------------- lectura

    public static Expr parse(String text) {
        Parser p = new Parser(text);
        Expr e = p.or();
        p.ws();
        if (p.i < p.s.length()) {
            throw new IllegalArgumentException("sobra '" + p.s.substring(p.i) + "' en: " + text);
        }
        return e;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) {
            this.s = s;
        }

        void ws() {
            while (this.i < this.s.length() && Character.isWhitespace(this.s.charAt(this.i))) {
                this.i++;
            }
        }

        boolean eat(String t) {
            ws();
            if (this.s.startsWith(t, this.i)) {
                // que "=" no se coma el principio de "==" ni "<" el de "<="
                this.i += t.length();
                return true;
            }
            return false;
        }

        boolean peek(String t) {
            ws();
            return this.s.startsWith(t, this.i);
        }

        Expr or() {
            Expr a = and();
            while (eat("||")) {
                Expr l = a, r = and();
                a = new Expr() {
                    public float eval(Scope sc) {
                        return l.eval(sc) != 0 || r.eval(sc) != 0 ? 1 : 0;
                    }
                };
            }
            return a;
        }

        Expr and() {
            Expr a = eq();
            while (eat("&&")) {
                Expr l = a, r = eq();
                a = new Expr() {
                    public float eval(Scope sc) {
                        return l.eval(sc) != 0 && r.eval(sc) != 0 ? 1 : 0;
                    }
                };
            }
            return a;
        }

        Expr eq() {
            Expr a = cmp();
            while (true) {
                if (eat("==")) {
                    Expr l = a, r = cmp();
                    a = bin(l, r, 0);
                } else if (eat("!=")) {
                    Expr l = a, r = cmp();
                    a = bin(l, r, 1);
                } else {
                    return a;
                }
            }
        }

        Expr cmp() {
            Expr a = add();
            while (true) {
                int op;
                if (eat("<=")) {
                    op = 2;
                } else if (eat(">=")) {
                    op = 3;
                } else if (eat("<")) {
                    op = 4;
                } else if (eat(">")) {
                    op = 5;
                } else {
                    return a;
                }
                a = bin(a, add(), op);
            }
        }

        Expr add() {
            Expr a = mul();
            while (true) {
                if (eat("+")) {
                    a = bin(a, mul(), 6);
                } else if (peek("-")) {
                    this.i++;
                    a = bin(a, mul(), 7);
                } else {
                    return a;
                }
            }
        }

        Expr mul() {
            Expr a = unary();
            while (true) {
                if (eat("*")) {
                    a = bin(a, unary(), 8);
                } else if (eat("/")) {
                    a = bin(a, unary(), 9);
                } else if (eat("%")) {
                    a = bin(a, unary(), 10);
                } else {
                    return a;
                }
            }
        }

        Expr unary() {
            if (eat("-")) {
                Expr x = unary();
                return new Expr() {
                    public float eval(Scope sc) {
                        return -x.eval(sc);
                    }
                };
            }
            if (eat("+")) {
                return unary();
            }
            if (peek("!") && !peek("!=")) {
                this.i++;
                Expr x = unary();
                return new Expr() {
                    public float eval(Scope sc) {
                        return x.eval(sc) == 0 ? 1 : 0;
                    }
                };
            }
            return atom();
        }

        Expr atom() {
            ws();
            if (eat("(")) {
                Expr e = or();
                if (!eat(")")) {
                    throw new IllegalArgumentException("falta ')' en: " + this.s);
                }
                return e;
            }
            int st = this.i;
            if (this.i < this.s.length() && (Character.isDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.')) {
                while (this.i < this.s.length() && (Character.isDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.')) {
                    this.i++;
                }
                if (this.i < this.s.length() && (this.s.charAt(this.i) == 'e' || this.s.charAt(this.i) == 'E') && this.i + 1 < this.s.length()
                        && (Character.isDigit(this.s.charAt(this.i + 1)) || this.s.charAt(this.i + 1) == '-')) {
                    this.i += 2;
                    while (this.i < this.s.length() && Character.isDigit(this.s.charAt(this.i))) {
                        this.i++;
                    }
                }
                float v = Float.parseFloat(this.s.substring(st, this.i));
                return constant(v);
            }
            while (this.i < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '_' || this.s.charAt(this.i) == '.'
                    || this.s.charAt(this.i) == ':')) {
                this.i++;
            }
            if (st == this.i) {
                throw new IllegalArgumentException("no entiendo '" + this.s.substring(this.i) + "' en: " + this.s);
            }
            String name = this.s.substring(st, this.i);
            if (eat("(")) {
                List<Expr> args = new ArrayList<>();
                if (!eat(")")) {
                    do {
                        args.add(or());
                    } while (eat(","));
                    if (!eat(")")) {
                        throw new IllegalArgumentException("falta ')' en: " + this.s);
                    }
                }
                return Funcs.call(name.toLowerCase(Locale.ROOT), args);
            }
            switch (name.toLowerCase(Locale.ROOT)) {
                case "pi":
                    return constant((float) Math.PI);
                case "e":
                    return constant((float) Math.E);
                case "true":
                    return constant(1);
                case "false":
                    return constant(0);
                default:
                    return new Expr() {
                        public float eval(Scope sc) {
                            return sc.get(name);
                        }

                        @Override
                        public String toString() {
                            return name;
                        }
                    };
            }
        }

        static Expr bin(Expr l, Expr r, int op) {
            return new Expr() {
                public float eval(Scope sc) {
                    float a = l.eval(sc), b = r.eval(sc);
                    return switch (op) {
                        case 0 -> a == b ? 1 : 0;
                        case 1 -> a != b ? 1 : 0;
                        case 2 -> a <= b ? 1 : 0;
                        case 3 -> a >= b ? 1 : 0;
                        case 4 -> a < b ? 1 : 0;
                        case 5 -> a > b ? 1 : 0;
                        case 6 -> a + b;
                        case 7 -> a - b;
                        case 8 -> a * b;
                        case 9 -> b == 0 ? 0 : a / b;
                        default -> b == 0 ? 0 : a % b;
                    };
                }
            };
        }
    }

    public static Expr constant(float v) {
        return new Expr() {
            public float eval(Scope sc) {
                return v;
            }
        };
    }
}
