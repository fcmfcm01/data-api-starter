package org.cafeng.openapi.engine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Defense-in-depth SQL injection protection.
 * Validates parameter values before they are bound to PreparedStatement.
 *
 * Note: PreparedStatement with proper ? binding already prevents SQL injection.
 * This guard provides an additional safety net for defense-in-depth.
 */
public class SqlInjectionGuard {

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("--", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/\\*.*?\\*/", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile(";\\s*(?:SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|EXEC|EXECUTE|UNION)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bUNION\\b.*\\bSELECT\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("'\\s*(?:OR|AND)\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:WAITFOR|DELAY|BENCHMARK|SLEEP)\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:LOAD_FILE|INTO\\s+OUTFILE|INTO\\s+DUMPFILE)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("0x[0-9a-fA-F]{6,}")
    );

    private final Set<String> safeParamsCache = ConcurrentHashMap.newKeySet();

    /**
     * Validates all parameter values against SQL injection patterns.
     * @throws IllegalArgumentException if a potentially dangerous value is detected
     */
    public void validate(Map<String, Object> parameters) {
        if (parameters == null) return;

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String stringValue) {
                checkValue(entry.getKey(), stringValue);
            }
        }
    }

    private void checkValue(String paramName, String value) {
        if (safeParamsCache.contains(value)) {
            return;
        }
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                throw new IllegalArgumentException(
                    "Potential SQL injection detected in parameter '" + paramName + "': value contains forbidden pattern");
            }
        }
        safeParamsCache.add(value);
    }
}
