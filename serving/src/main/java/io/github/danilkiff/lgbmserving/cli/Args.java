package io.github.danilkiff.lgbmserving.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/** Разбор флагов вида {@code -name value} и {@code -name=value}. */
final class Args {

    private final Map<String, String> values = new LinkedHashMap<>();

    private Args() {}

    static Args parse(String[] argv) {
        Args args = new Args();
        for (int i = 0; i < argv.length; i++) {
            String token = argv[i];
            if (!isFlag(token)) {
                throw new IllegalArgumentException("неожиданный аргумент: " + token);
            }
            String name = token.substring(1);
            int eq = name.indexOf('=');
            if (eq >= 0) {
                args.values.put(name.substring(0, eq), name.substring(eq + 1));
            } else if (i + 1 < argv.length && !isFlag(argv[i + 1])) {
                args.values.put(name, argv[++i]);
            } else {
                args.values.put(name, "true");
            }
        }
        return args;
    }

    /**
     * Отрицательное число - значение, а не имя флага: иначе {@code -threshold
     * -1000} потеряло бы значение и порог молча стал бы дефолтным.
     */
    private static boolean isFlag(String token) {
        if (token.length() < 2 || token.charAt(0) != '-') {
            return false;
        }
        char c = token.charAt(1);
        return !Character.isDigit(c) && c != '.';
    }

    String string(String name, String fallback) {
        return values.getOrDefault(name, fallback);
    }

    String require(String name, String usage) {
        String v = values.get(name);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("-%s обязателен (%s)".formatted(name, usage));
        }
        return v;
    }

    int integer(String name, int fallback) {
        String v = values.get(name);
        return v == null ? fallback : Integer.parseInt(v);
    }

    double number(String name, double fallback) {
        String v = values.get(name);
        return v == null ? fallback : Double.parseDouble(v);
    }
}
