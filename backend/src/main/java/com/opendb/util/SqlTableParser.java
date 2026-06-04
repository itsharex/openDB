package com.opendb.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlTableParser {

    private static final Pattern FROM_TABLE = Pattern.compile(
            "(?is)\\bfrom\\s+((?:`([^`]+)`\\.`([^`]+)`)|(?:`([^`]+)`)|(?:([a-zA-Z0-9_]+)(?:\\.([a-zA-Z0-9_]+))?))"
    );

    private SqlTableParser() {
    }

    public record ParsedTable(String database, String table) {
    }

    public static Optional<ParsedTable> parseSingleTableSelect(String sql) {
        if (sql == null || sql.isBlank()) {
            return Optional.empty();
        }
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            return Optional.empty();
        }
        if (containsKeyword(upper, " JOIN ") || containsKeyword(upper, " UNION ")
                || containsKeyword(upper, " INTERSECT ") || containsKeyword(upper, " EXCEPT ")) {
            return Optional.empty();
        }

        Matcher matcher = FROM_TABLE.matcher(trimmed);
        if (!matcher.find()) {
            return Optional.empty();
        }

        if (matcher.group(2) != null && matcher.group(3) != null) {
            return Optional.of(new ParsedTable(matcher.group(2), matcher.group(3)));
        }
        if (matcher.group(4) != null) {
            return Optional.of(new ParsedTable(null, matcher.group(4)));
        }
        if (matcher.group(5) != null) {
            String first = matcher.group(5);
            String second = matcher.group(6);
            if (second != null) {
                return Optional.of(new ParsedTable(first, second));
            }
            return Optional.of(new ParsedTable(null, first));
        }
        return Optional.empty();
    }

    private static boolean containsKeyword(String upperSql, String keyword) {
        return upperSql.contains(keyword.trim());
    }
}
