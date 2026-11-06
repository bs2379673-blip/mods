package com.starlevi.starscenes.model;

import java.util.Locale;

/**
 * Expresiones simples de Molang de Blockbench (sin variables): numeros, + - * /, parentesis y
 * math.sin, math.cos (en grados, como Molang), math.abs, math.sqrt, math.pi, math.min, math.max.
 * Si la expresion usa algo que cambia con el tiempo (query.anim_time, por ejemplo), devuelve null.
 */
final class Expr {
    private final String s;
    private int i;

    private Expr(String s) {
        this.s = s.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    static Float constant(String text) {
        try {
            Expr e = new Expr(text);
            double v = e.sum();
            if (e.i != e.s.length() || Double.isNaN(v) || Double.isInfinite(v)) {
                return null;
            }
            return (float) v;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private double sum() {
        double v = product();
        while (this.i < this.s.length()) {
            char c = this.s.charAt(this.i);
            if (c == '+') {
                this.i++;
                v += product();
            } else if (c == '-') {
                this.i++;
                v -= product();
            } else {
                break;
            }
        }
        return v;
    }

    private double product() {
        double v = unary();
        while (this.i < this.s.length()) {
            char c = this.s.charAt(this.i);
            if (c == '*') {
                this.i++;
                v *= unary();
            } else if (c == '/') {
                this.i++;
                v /= unary();
            } else {
                break;
            }
        }
        return v;
    }

    private double unary() {
        if (this.i < this.s.length() && this.s.charAt(this.i) == '-') {
            this.i++;
            return -unary();
        }
        if (this.i < this.s.length() && this.s.charAt(this.i) == '+') {
            this.i++;
            return unary();
        }
        return atom();
    }

    private double atom() {
        if (this.i >= this.s.length()) {
            throw new IllegalStateException();
        }
        char c = this.s.charAt(this.i);
        if (c == '(') {
            this.i++;
            double v = sum();
            expect(')');
            return v;
        }
        if (Character.isDigit(c) || c == '.') {
            int st = this.i;
            while (this.i < this.s.length() && (Character.isDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.')) {
                this.i++;
            }
            return Double.parseDouble(this.s.substring(st, this.i));
        }
        int st = this.i;
        while (this.i < this.s.length() && (Character.isLetterOrDigit(this.s.charAt(this.i)) || this.s.charAt(this.i) == '.' || this.s.charAt(this.i) == '_')) {
            this.i++;
        }
        String name = this.s.substring(st, this.i);
        switch (name) {
            case "math.pi":
                return Math.PI;
            case "math.sin":
                return Math.sin(Math.toRadians(arg1()));
            case "math.cos":
                return Math.cos(Math.toRadians(arg1()));
            case "math.abs":
                return Math.abs(arg1());
            case "math.sqrt":
                return Math.sqrt(arg1());
            case "math.min": {
                double[] a = arg2();
                return Math.min(a[0], a[1]);
            }
            case "math.max": {
                double[] a = arg2();
                return Math.max(a[0], a[1]);
            }
            default:
                throw new IllegalStateException(name);
        }
    }

    private double arg1() {
        expect('(');
        double v = sum();
        expect(')');
        return v;
    }

    private double[] arg2() {
        expect('(');
        double a = sum();
        expect(',');
        double b = sum();
        expect(')');
        return new double[]{a, b};
    }

    private void expect(char c) {
        if (this.i >= this.s.length() || this.s.charAt(this.i) != c) {
            throw new IllegalStateException();
        }
        this.i++;
    }
}
