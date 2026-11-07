package com.starlevi.starscenes.emf;

import java.util.List;

/** Las funciones del lenguaje de animaciones de EMF / OptiFine (sin, clamp, if, lerp, suavizados...). */
final class Funcs {
    private Funcs() {
    }

    interface F1 {
        float f(float a);
    }

    interface F2 {
        float f(float a, float b);
    }

    interface F3 {
        float f(float a, float b, float c);
    }

    private static Expr f1(List<Expr> a, F1 f) {
        Expr x = arg(a, 0);
        return new Expr() {
            public float eval(Scope s) {
                return f.f(x.eval(s));
            }
        };
    }

    private static Expr f2(List<Expr> a, F2 f) {
        Expr x = arg(a, 0), y = arg(a, 1);
        return new Expr() {
            public float eval(Scope s) {
                return f.f(x.eval(s), y.eval(s));
            }
        };
    }

    private static Expr f3(List<Expr> a, F3 f) {
        Expr x = arg(a, 0), y = arg(a, 1), z = arg(a, 2);
        return new Expr() {
            public float eval(Scope s) {
                return f.f(x.eval(s), y.eval(s), z.eval(s));
            }
        };
    }

    private static Expr arg(List<Expr> a, int i) {
        return i < a.size() ? a.get(i) : Expr.constant(0);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static float wrap(float v, float half) {
        float full = half * 2;
        v = (v + half) % full;
        if (v < 0) {
            v += full;
        }
        return v - half;
    }

    /** Suavizados (t de 0 a 1). */
    private static float ease(String n, float t) {
        double p = Math.PI;
        return (float) switch (n) {
            case "easeinsine", "easein" -> 1 - Math.cos(t * p / 2);
            case "easeoutsine", "easeout" -> Math.sin(t * p / 2);
            case "easeinoutsine", "easeinout" -> -(Math.cos(p * t) - 1) / 2;
            case "easeinquad" -> t * t;
            case "easeoutquad" -> 1 - (1 - t) * (1 - t);
            case "easeinoutquad" -> t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            case "easeincubic", "cubiceasein" -> t * t * t;
            case "easeoutcubic", "cubiceaseout" -> 1 - Math.pow(1 - t, 3);
            case "easeinoutcubic", "cubiceaseinout" -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            case "easeinquart" -> Math.pow(t, 4);
            case "easeoutquart" -> 1 - Math.pow(1 - t, 4);
            case "easeinoutquart" -> t < 0.5 ? 8 * Math.pow(t, 4) : 1 - Math.pow(-2 * t + 2, 4) / 2;
            case "easeinquint" -> Math.pow(t, 5);
            case "easeoutquint" -> 1 - Math.pow(1 - t, 5);
            case "easeinoutquint" -> t < 0.5 ? 16 * Math.pow(t, 5) : 1 - Math.pow(-2 * t + 2, 5) / 2;
            case "easeinexpo" -> t == 0 ? 0 : Math.pow(2, 10 * t - 10);
            case "easeoutexpo" -> t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
            case "easeinoutexpo" -> t == 0 ? 0 : t == 1 ? 1 : t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
            case "easeincirc" -> 1 - Math.sqrt(1 - t * t);
            case "easeoutcirc" -> Math.sqrt(1 - Math.pow(t - 1, 2));
            case "easeinoutcirc" -> t < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
            case "easeinback" -> 2.70158 * t * t * t - 1.70158 * t * t;
            case "easeoutback" -> 1 + 2.70158 * Math.pow(t - 1, 3) + 1.70158 * Math.pow(t - 1, 2);
            case "easeinoutback" -> t < 0.5 ? (Math.pow(2 * t, 2) * ((2.5949095 + 1) * 2 * t - 2.5949095)) / 2
                    : (Math.pow(2 * t - 2, 2) * ((2.5949095 + 1) * (t * 2 - 2) + 2.5949095) + 2) / 2;
            case "easeinelastic" -> t == 0 ? 0 : t == 1 ? 1 : -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (2 * p / 3));
            case "easeoutelastic" -> t == 0 ? 0 : t == 1 ? 1 : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * p / 3)) + 1;
            case "easeinoutelastic" -> t == 0 ? 0 : t == 1 ? 1 : t < 0.5 ? -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * (2 * p / 4.5))) / 2
                    : (Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * (2 * p / 4.5))) / 2 + 1;
            case "easeoutbounce" -> bounce(t);
            case "easeinbounce" -> 1 - bounce(1 - t);
            case "easeinoutbounce" -> t < 0.5 ? (1 - bounce(1 - 2 * t)) / 2 : (1 + bounce(2 * t - 1)) / 2;
            default -> t;
        };
    }

    private static double bounce(double t) {
        double n = 7.5625, d = 2.75;
        if (t < 1 / d) {
            return n * t * t;
        } else if (t < 2 / d) {
            return n * (t -= 1.5 / d) * t + 0.75;
        } else if (t < 2.5 / d) {
            return n * (t -= 2.25 / d) * t + 0.9375;
        }
        return n * (t -= 2.625 / d) * t + 0.984375;
    }

    private static float frame(Expr[] xs, int i, int k, boolean loop, Expr.Scope s) {
        int j = loop ? Math.floorMod(i, k) : Math.max(0, Math.min(k - 1, i));
        return xs[1 + j].eval(s);
    }

    static Expr call(String n, List<Expr> a) {
        switch (n) {
            case "sin":
                return f1(a, x -> (float) Math.sin(x));
            case "cos":
                return f1(a, x -> (float) Math.cos(x));
            case "tan":
                return f1(a, x -> (float) Math.tan(x));
            case "asin":
                return f1(a, x -> (float) Math.asin(x));
            case "acos":
                return f1(a, x -> (float) Math.acos(x));
            case "atan":
                return f1(a, x -> (float) Math.atan(x));
            case "atan2":
                return f2(a, (y, x) -> (float) Math.atan2(y, x));
            case "torad":
                return f1(a, x -> (float) Math.toRadians(x));
            case "todeg":
                return f1(a, x -> (float) Math.toDegrees(x));
            case "abs":
                return f1(a, Math::abs);
            case "floor":
                return f1(a, x -> (float) Math.floor(x));
            case "ceil":
                return f1(a, x -> (float) Math.ceil(x));
            case "round":
                return f1(a, x -> (float) Math.round(x));
            case "frac":
                return f1(a, x -> x - (float) Math.floor(x));
            case "sqrt":
                return f1(a, x -> (float) Math.sqrt(Math.max(0, x)));
            case "exp":
                return f1(a, x -> (float) Math.exp(x));
            case "log":
                return f1(a, x -> x <= 0 ? 0 : (float) Math.log(x));
            case "signum":
                return f1(a, Math::signum);
            case "pow":
                return f2(a, (x, y) -> (float) Math.pow(x, y));
            case "fmod":
                return f2(a, (x, y) -> y == 0 ? 0 : x - y * (float) Math.floor(x / y));
            case "wrapdeg":
                return f1(a, x -> wrap(x, 180));
            case "wraprad":
                return f1(a, x -> wrap(x, (float) Math.PI));
            case "degdiff":
                return f2(a, (x, y) -> Math.abs(wrap(y - x, 180)));
            case "raddiff":
                return f2(a, (x, y) -> Math.abs(wrap(y - x, (float) Math.PI)));
            case "clamp":
                return f3(a, Funcs::clamp);
            case "between":
                return f3(a, (x, lo, hi) -> x >= lo && x <= hi ? 1 : 0);
            case "lerp":
                return f3(a, (t, x, y) -> x + (y - x) * t);
            case "equals":
                return f3(a, (x, y, eps) -> Math.abs(x - y) <= eps ? 1 : 0);
            case "catch":
                return arg(a, 0);
            case "nbt":
                return Expr.constant(0);
            case "print":
            case "printb":
                return arg(a, a.size() - 1);
            case "random":
            case "randomb": {
                Expr seed = a.isEmpty() ? null : a.get(0);
                boolean b = n.equals("randomb");
                return new Expr() {
                    public float eval(Scope s) {
                        float r = seed == null ? (float) Math.random() : s.random(seed.eval(s));
                        return b ? (r >= 0.5f ? 1 : 0) : r;
                    }
                };
            }
            case "min":
            case "max": {
                boolean max = n.equals("max");
                Expr[] xs = a.toArray(new Expr[0]);
                return new Expr() {
                    public float eval(Scope s) {
                        float r = xs.length == 0 ? 0 : xs[0].eval(s);
                        for (int i = 1; i < xs.length; i++) {
                            float v = xs[i].eval(s);
                            r = max ? Math.max(r, v) : Math.min(r, v);
                        }
                        return r;
                    }
                };
            }
            case "in": {
                Expr[] xs = a.toArray(new Expr[0]);
                return new Expr() {
                    public float eval(Scope s) {
                        float v = xs.length == 0 ? 0 : xs[0].eval(s);
                        for (int i = 1; i < xs.length; i++) {
                            if (xs[i].eval(s) == v) {
                                return 1;
                            }
                        }
                        return 0;
                    }
                };
            }
            case "if":
            case "ifb": {
                // if(cond1, valor1, cond2, valor2, ..., sino)
                Expr[] xs = a.toArray(new Expr[0]);
                return new Expr() {
                    public float eval(Scope s) {
                        int i = 0;
                        for (; i + 1 < xs.length; i += 2) {
                            if (xs[i].eval(s) != 0) {
                                return xs[i + 1].eval(s);
                            }
                        }
                        return i < xs.length ? xs[i].eval(s) : 0;
                    }
                };
            }
            case "keyframe":
            case "keyframeloop": {
                // keyframe(t, v0, v1, v2, ...): de un valor al siguiente segun t (de a 1), con curva suave
                boolean loop = n.equals("keyframeloop");
                Expr[] xs = a.toArray(new Expr[0]);
                return new Expr() {
                    public float eval(Scope s) {
                        int k = xs.length - 1;
                        if (k <= 0) {
                            return 0;
                        }
                        float t = xs[0].eval(s);
                        int end = k - 1;
                        if (loop) {
                            t = t % k;
                            if (t < 0) {
                                t += k;
                            }
                        }
                        int f = (int) Math.floor(t);
                        if (!loop && f >= end) {
                            return xs[1 + end].eval(s);
                        }
                        if (!loop && f <= 0 && t <= 0) {
                            return xs[1].eval(s);
                        }
                        float u = t - f;
                        float p0 = frame(xs, f - 1, k, loop, s), p1 = frame(xs, f, k, loop, s), p2 = frame(xs, f + 1, k, loop, s), p3 = frame(xs, f + 2, k, loop, s);
                        return 0.5f * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
                    }
                };
            }
            case "catmullrom": {
                Expr t = arg(a, 0), p0 = arg(a, 1), p1 = arg(a, 2), p2 = arg(a, 3), p3 = arg(a, 4);
                return new Expr() {
                    public float eval(Scope s) {
                        float u = t.eval(s), a0 = p0.eval(s), a1 = p1.eval(s), a2 = p2.eval(s), a3 = p3.eval(s);
                        return 0.5f * (2 * a1 + (-a0 + a2) * u + (2 * a0 - 5 * a1 + 4 * a2 - a3) * u * u + (-a0 + 3 * a1 - 3 * a2 + a3) * u * u * u);
                    }
                };
            }
            case "quadbezier": {
                Expr t = arg(a, 0), p0 = arg(a, 1), p1 = arg(a, 2), p2 = arg(a, 3);
                return new Expr() {
                    public float eval(Scope s) {
                        float u = t.eval(s), w = 1 - u;
                        return w * w * p0.eval(s) + 2 * w * u * p1.eval(s) + u * u * p2.eval(s);
                    }
                };
            }
            case "cubicbezier": {
                Expr t = arg(a, 0), p0 = arg(a, 1), p1 = arg(a, 2), p2 = arg(a, 3), p3 = arg(a, 4);
                return new Expr() {
                    public float eval(Scope s) {
                        float u = t.eval(s), w = 1 - u;
                        return w * w * w * p0.eval(s) + 3 * w * w * u * p1.eval(s) + 3 * w * u * u * p2.eval(s) + u * u * u * p3.eval(s);
                    }
                };
            }
            case "hermite": {
                Expr t = arg(a, 0), p0 = arg(a, 1), p1 = arg(a, 2), m0 = arg(a, 3), m1 = arg(a, 4);
                return new Expr() {
                    public float eval(Scope s) {
                        float u = t.eval(s), u2 = u * u, u3 = u2 * u;
                        return (2 * u3 - 3 * u2 + 1) * p0.eval(s) + (u3 - 2 * u2 + u) * m0.eval(s) + (-2 * u3 + 3 * u2) * p1.eval(s)
                                + (u3 - u2) * m1.eval(s);
                    }
                };
            }
            default:
                if (n.startsWith("ease") || n.startsWith("cubicease")) {
                    String name = n;
                    if (a.size() >= 3) {
                        // easeX(t, desde, hasta)
                        Expr t = a.get(0), from = a.get(1), to = a.get(2);
                        return new Expr() {
                            public float eval(Scope s) {
                                float f = from.eval(s);
                                return f + (to.eval(s) - f) * ease(name, t.eval(s));
                            }
                        };
                    }
                    return f1(a, t -> ease(name, t));
                }
                synchronized (Expr.UNKNOWN) {
                    if (!Expr.UNKNOWN.contains(n)) {
                        Expr.UNKNOWN.add(n);
                    }
                }
                return Expr.constant(0);
        }
    }
}
