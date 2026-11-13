package com.example.assistant;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java math expression evaluator & unit converter for Hindi, Hinglish, and English queries.
 */
public final class MathAndUnitEvaluator {

    public static class EvalResult {
        public final boolean handled;
        public final String formattedResponse;
        public final String languageCode;

        public EvalResult(boolean handled, String formattedResponse, String languageCode) {
            this.handled = handled;
            this.formattedResponse = formattedResponse;
            this.languageCode = languageCode;
        }
    }

    private static final DecimalFormat DF = new DecimalFormat("#.####");

    private static final Pattern CONVERT_PATTERN = Pattern.compile(
            "(?i)(?:convert\\s+)?(-?\\d+(?:\\.\\d+)?)\\s*([a-zA-Z°]+)\\s+(?:to|in|into|mein)\\s+([a-zA-Z°]+)"
    );

    private static final Pattern MATH_CLEAN_PREFIX = Pattern.compile(
            "(?i)^(?:calculate|compute|solve|what is|evaluate|kitna hota hai|बताओ|हिसाब करो)\\s+"
    );

    private static final Map<String, Double> LENGTH_TO_METERS = new HashMap<>();
    private static final Map<String, Double> WEIGHT_TO_GRAMS = new HashMap<>();
    private static final Map<String, Double> TIME_TO_SECONDS = new HashMap<>();
    private static final Map<String, Double> DATA_TO_BYTES = new HashMap<>();
    private static final Map<String, Double> VOLUME_TO_LITERS = new HashMap<>();

    static {
        LENGTH_TO_METERS.put("m", 1.0);
        LENGTH_TO_METERS.put("meter", 1.0);
        LENGTH_TO_METERS.put("meters", 1.0);
        LENGTH_TO_METERS.put("km", 1000.0);
        LENGTH_TO_METERS.put("kilometer", 1000.0);
        LENGTH_TO_METERS.put("kilometers", 1000.0);
        LENGTH_TO_METERS.put("cm", 0.01);
        LENGTH_TO_METERS.put("mm", 0.001);
        LENGTH_TO_METERS.put("mile", 1609.344);
        LENGTH_TO_METERS.put("miles", 1609.344);
        LENGTH_TO_METERS.put("ft", 0.3048);
        LENGTH_TO_METERS.put("feet", 0.3048);
        LENGTH_TO_METERS.put("foot", 0.3048);
        LENGTH_TO_METERS.put("inch", 0.0254);
        LENGTH_TO_METERS.put("inches", 0.0254);

        WEIGHT_TO_GRAMS.put("g", 1.0);
        WEIGHT_TO_GRAMS.put("gram", 1.0);
        WEIGHT_TO_GRAMS.put("grams", 1.0);
        WEIGHT_TO_GRAMS.put("kg", 1000.0);
        WEIGHT_TO_GRAMS.put("kilogram", 1000.0);
        WEIGHT_TO_GRAMS.put("kilograms", 1000.0);
        WEIGHT_TO_GRAMS.put("mg", 0.001);
        WEIGHT_TO_GRAMS.put("lb", 453.59237);
        WEIGHT_TO_GRAMS.put("lbs", 453.59237);
        WEIGHT_TO_GRAMS.put("pound", 453.59237);
        WEIGHT_TO_GRAMS.put("pounds", 453.59237);
        WEIGHT_TO_GRAMS.put("oz", 28.349523125);

        TIME_TO_SECONDS.put("s", 1.0);
        TIME_TO_SECONDS.put("sec", 1.0);
        TIME_TO_SECONDS.put("second", 1.0);
        TIME_TO_SECONDS.put("seconds", 1.0);
        TIME_TO_SECONDS.put("min", 60.0);
        TIME_TO_SECONDS.put("minute", 60.0);
        TIME_TO_SECONDS.put("minutes", 60.0);
        TIME_TO_SECONDS.put("hr", 3600.0);
        TIME_TO_SECONDS.put("hrs", 3600.0);
        TIME_TO_SECONDS.put("hour", 3600.0);
        TIME_TO_SECONDS.put("hours", 3600.0);
        TIME_TO_SECONDS.put("day", 86400.0);
        TIME_TO_SECONDS.put("days", 86400.0);

        DATA_TO_BYTES.put("b", 1.0);
        DATA_TO_BYTES.put("kb", 1024.0);
        DATA_TO_BYTES.put("mb", 1024.0 * 1024.0);
        DATA_TO_BYTES.put("gb", 1024.0 * 1024.0 * 1024.0);
        DATA_TO_BYTES.put("tb", 1024.0 * 1024.0 * 1024.0 * 1024.0);

        VOLUME_TO_LITERS.put("l", 1.0);
        VOLUME_TO_LITERS.put("liter", 1.0);
        VOLUME_TO_LITERS.put("liters", 1.0);
        VOLUME_TO_LITERS.put("ml", 0.001);
        VOLUME_TO_LITERS.put("gal", 3.785411784);
        VOLUME_TO_LITERS.put("gallon", 3.785411784);
        VOLUME_TO_LITERS.put("gallons", 3.785411784);
    }

    private MathAndUnitEvaluator() {
    }

    public static EvalResult tryEvaluate(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return new EvalResult(false, "", LanguageUtils.LANG_ENGLISH);
        }
        String text = rawInput.trim();
        String lang = LanguageUtils.detectLanguage(text);

        // 1. Unit Conversion
        Matcher convMatcher = CONVERT_PATTERN.matcher(text);
        if (convMatcher.find()) {
            try {
                double val = Double.parseDouble(convMatcher.group(1));
                String fromUnit = convMatcher.group(2).toLowerCase(Locale.ROOT);
                String toUnit = convMatcher.group(3).toLowerCase(Locale.ROOT);
                Double converted = convertUnits(val, fromUnit, toUnit);
                if (converted != null) {
                    String msg = formatConversion(lang, val, fromUnit, converted, toUnit);
                    return new EvalResult(true, msg, lang);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // 2. Arithmetic / Math expression
        String candidate = MATH_CLEAN_PREFIX.matcher(text).replaceFirst("").trim();
        candidate = candidate.replaceAll("(?i)\\bplus\\b", "+")
                .replaceAll("(?i)\\bminus\\b", "-")
                .replaceAll("(?i)\\btimes\\b|\\bmultiplied by\\b|\\binto\\b", "*")
                .replaceAll("(?i)\\bdivided by\\b", "/")
                .replace("×", "*")
                .replace("÷", "/")
                .replace("?", "")
                .trim();

        if (looksLikeMathExpression(candidate)) {
            try {
                double value = evaluateExpression(candidate);
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    String formatted = DF.format(value);
                    String msg;
                    if (LanguageUtils.LANG_HINDI.equals(lang)) {
                        msg = candidate + " का उत्तर " + formatted + " है।";
                    } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
                        msg = candidate + " ka jawab " + formatted + " hota hai.";
                    } else {
                        msg = candidate + " equals " + formatted + ".";
                    }
                    return new EvalResult(true, msg, lang);
                }
            } catch (Exception ignored) {
            }
        }

        return new EvalResult(false, "", lang);
    }

    private static boolean looksLikeMathExpression(String expr) {
        if (expr.isEmpty()) return false;
        boolean hasDigit = false;
        boolean hasOp = false;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '%') {
                hasOp = true;
            } else if (c != ' ' && c != '.' && c != '(' && c != ')' && !Character.isLetter(c)) {
                return false;
            }
        }
        return hasDigit && (hasOp || expr.toLowerCase(Locale.ROOT).startsWith("sqrt("));
    }

    private static Double convertUnits(double value, String from, String to) {
        // Temperature special cases
        if ((from.equals("c") || from.equals("°c") || from.equals("celsius"))
                && (to.equals("f") || to.equals("°f") || to.equals("fahrenheit"))) {
            return (value * 9.0 / 5.0) + 32.0;
        }
        if ((from.equals("f") || from.equals("°f") || from.equals("fahrenheit"))
                && (to.equals("c") || to.equals("°c") || to.equals("celsius"))) {
            return (value - 32.0) * 5.0 / 9.0;
        }
        if ((from.equals("c") || from.equals("celsius")) && (to.equals("k") || to.equals("kelvin"))) {
            return value + 273.15;
        }

        Double linear = convertFromMap(value, from, to, LENGTH_TO_METERS);
        if (linear != null) return linear;
        linear = convertFromMap(value, from, to, WEIGHT_TO_GRAMS);
        if (linear != null) return linear;
        linear = convertFromMap(value, from, to, TIME_TO_SECONDS);
        if (linear != null) return linear;
        linear = convertFromMap(value, from, to, DATA_TO_BYTES);
        if (linear != null) return linear;
        return convertFromMap(value, from, to, VOLUME_TO_LITERS);
    }

    private static Double convertFromMap(double value, String from, String to, Map<String, Double> map) {
        Double fromFactor = map.get(from);
        Double toFactor = map.get(to);
        if (fromFactor == null || toFactor == null) {
            return null;
        }
        return (value * fromFactor) / toFactor;
    }

    private static String formatConversion(String lang, double inVal, String from, double outVal, String to) {
        String inStr = DF.format(inVal);
        String outStr = DF.format(outVal);
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return inStr + " " + from + " लगभग " + outStr + " " + to + " के बराबर है।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return inStr + " " + from + " ka value " + outStr + " " + to + " hota hai.";
        }
        return inStr + " " + from + " is " + outStr + " " + to + ".";
    }

    // Recursive descent expression parser
    public static double evaluateExpression(final String str) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else if (eat('%')) x %= parseFactor();
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return +parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    if (!eat(')')) throw new RuntimeException("Missing ')'");
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if (ch >= 'a' && ch <= 'z') {
                    while (ch >= 'a' && ch <= 'z') nextChar();
                    String func = str.substring(startPos, this.pos);
                    if (eat('(')) {
                        x = parseExpression();
                        if (!eat(')')) throw new RuntimeException("Missing ')' after " + func);
                    } else {
                        x = parseFactor();
                    }
                    switch (func) {
                        case "sqrt": x = Math.sqrt(x); break;
                        case "sin": x = Math.sin(Math.toRadians(x)); break;
                        case "cos": x = Math.cos(Math.toRadians(x)); break;
                        case "tan": x = Math.tan(Math.toRadians(x)); break;
                        case "log": x = Math.log10(x); break;
                        default: throw new RuntimeException("Unknown function: " + func);
                    }
                } else {
                    throw new RuntimeException("Unexpected char: " + (char) ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor());
                return x;
            }
        }.parse();
    }
}
