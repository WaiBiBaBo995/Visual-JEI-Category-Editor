package com.pureneko.vjce.data;

import java.util.*;

/** Small numeric expression parser: no reflection, scripts, or access to world/player state. */
public final class Expression {
    private Expression() {}
    public static double evaluate(String source, Map<String, Double> variables) {
        if (source == null || source.length() > 4096) throw new IllegalArgumentException("Expression too long");
        var parser = new Parser(source, variables);
        double value = parser.conditional();
        parser.space();
        if (parser.pos != source.length()) throw parser.error("Unexpected token");
        if (!Double.isFinite(value)) throw parser.error("Non-finite result");
        return value;
    }

    private static final class Parser {
        final String source;
        final Map<String, Double> variables;
        int pos, depth;
        Parser(String source, Map<String, Double> variables) { this.source = source; this.variables = variables; }
        IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at " + pos + " in " + source); }
        void space() { while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) pos++; }
        boolean take(String token) { space(); if (source.startsWith(token, pos)) { pos += token.length(); return true; } return false; }
        double conditional() {
            if (++depth > 64) throw error("Expression nesting exceeds 64");
            double value = or();
            if (take("?")) { double yes = conditional(); if (!take(":")) throw error("Expected :"); double no = conditional(); value = value != 0 ? yes : no; }
            depth--;
            return value;
        }
        double or() { double a = and(); while (take("||")) { double b = and(); a = a != 0 || b != 0 ? 1 : 0; } return a; }
        double and() { double a = comparison(); while (take("&&")) { double b = comparison(); a = a != 0 && b != 0 ? 1 : 0; } return a; }
        double comparison() {
            double a = sum();
            while (true) {
                if (take(">=")) a = a >= sum() ? 1 : 0;
                else if (take("<=")) a = a <= sum() ? 1 : 0;
                else if (take("==")) a = a == sum() ? 1 : 0;
                else if (take("!=")) a = a != sum() ? 1 : 0;
                else if (take(">")) a = a > sum() ? 1 : 0;
                else if (take("<")) a = a < sum() ? 1 : 0;
                else return a;
            }
        }
        double sum() { double a = product(); while (true) { if (take("+")) a += product(); else if (take("-")) a -= product(); else return a; } }
        double product() { double a = unary(); while (true) { if (take("*")) a *= unary(); else if (take("/")) a /= unary(); else if (take("%")) a %= unary(); else return a; } }
        double unary() {
            if (++depth > 64) throw error("Expression nesting exceeds 64");
            double value;
            if (take("-")) value = -unary();
            else if (take("+")) value = unary();
            else if (take("!")) value = unary() == 0 ? 1 : 0;
            else value = atom();
            depth--;
            return value;
        }
        double atom() {
            if (take("(")) { double a = conditional(); if (!take(")")) throw error("Expected )"); return a; }
            space();
            int start = pos;
            if (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
                while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) pos++;
                if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
                    pos++;
                    if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) pos++;
                    while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
                }
                return Double.parseDouble(source.substring(start, pos));
            }
            while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) pos++;
            if (start == pos) throw error("Expected number or field");
            String name = source.substring(start, pos);
            if (take("(")) {
                var args = new ArrayList<Double>();
                if (!take(")")) { do { args.add(conditional()); } while (take(",")); if (!take(")")) throw error("Expected )"); }
                return function(name, args);
            }
            if (name.equals("true")) return 1;
            if (name.equals("false")) return 0;
            Double value = variables.get(name);
            if (value == null) throw error("Unknown field: " + name);
            return value;
        }
        double function(String name, List<Double> a) {
            int count = switch (name) { case "min", "max" -> 2; case "clamp" -> 3; case "abs", "floor", "ceil", "round", "sqrt" -> 1; default -> throw error("Unknown function: " + name); };
            if (a.size() != count) throw error("Expected " + count + " arguments for " + name);
            double v = a.getFirst();
            return switch (name) {
                case "min" -> Math.min(v, a.get(1)); case "max" -> Math.max(v, a.get(1));
                case "clamp" -> Math.max(a.get(1), Math.min(a.get(2), v)); case "abs" -> Math.abs(v);
                case "floor" -> Math.floor(v); case "ceil" -> Math.ceil(v); case "round" -> Math.round(v);
                case "sqrt" -> Math.sqrt(v); default -> throw error("Unknown function");
            };
        }
    }
}
