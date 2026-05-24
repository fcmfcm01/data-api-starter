package org.cafeng.openapi.definition;

/**
 * Response shape declared in an API YAML file.
 *
 * <p>The {@code yamlValue} matches the string used in YAML
 * ({@code page}, {@code list}, {@code single}).</p>
 */
public enum ResponseType {
    PAGE("page"), LIST("list"), SINGLE("single");

    private final String yamlValue;

    ResponseType(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    public String yamlValue() {
        return yamlValue;
    }

    public static ResponseType fromYamlValue(String value) {
        for (ResponseType rt : values()) {
            if (rt.yamlValue.equalsIgnoreCase(value)) {
                return rt;
            }
        }
        throw new IllegalArgumentException("Invalid response type: " + value);
    }
}
