package org.cafeng.openapi.error;

import java.util.regex.Pattern;

/**
 * Strips SQL keywords, quoted identifiers, and column references from error messages
 * to prevent leakage of schema details in API responses.
 */
public final class ErrorMessageSanitizer {

    private static final String GENERIC_ERROR = "An internal error occurred";
    private static final Pattern QUOTED_IDENTIFIERS = Pattern.compile("'[^']*'|\\[[^\\]]*\\]|\"[^\"]*\"");
    private static final Pattern SQL_KEYWORDS = Pattern.compile(
            "\\b(SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|JOIN|INNER|OUTER|LEFT|RIGHT|ON|SET|VALUES|INTO|AND|OR|NOT|NULL|IS|IN|LIKE|BETWEEN|EXISTS|GROUP|BY|ORDER|HAVING|LIMIT|OFFSET|FETCH|NEXT|ROWS|MERGE)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN_REF = Pattern.compile("\\b(\\w+)\\.(\\w+)\\b");

    private ErrorMessageSanitizer() {}

    public static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return GENERIC_ERROR;
        }
        String sanitized = QUOTED_IDENTIFIERS.matcher(message).replaceAll("");
        sanitized = SQL_KEYWORDS.matcher(sanitized).replaceAll("");
        sanitized = COLUMN_REF.matcher(sanitized).replaceAll("");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        if (sanitized.isBlank() || sanitized.length() < 5) {
            return GENERIC_ERROR;
        }
        return sanitized;
    }

    public static String genericError() {
        return GENERIC_ERROR;
    }
}
