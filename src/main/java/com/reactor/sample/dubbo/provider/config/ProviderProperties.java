package com.reactor.sample.dubbo.provider.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ProviderProperties {

    private static final String CLASSPATH_CONFIG = "rest-sample-dubbo-provider.properties";
    private static final Properties CLASSPATH_PROPERTIES = loadClasspathProperties();

    private ProviderProperties() {}

    public static String get(String key) {
        String value = find(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required provider property: " + key);
        }
        return value.trim();
    }

    public static String getOrDefault(String key, String defaultValue) {
        String value = find(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public static int getIntOrDefault(String key, int defaultValue) {
        String value = find(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Provider property must be an integer: " + key + "=" + value, e);
        }
    }

    private static String find(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(envKey(key));
        }
        if (value == null || value.isBlank()) {
            value = CLASSPATH_PROPERTIES.getProperty(key);
        }
        return value;
    }

    public static int getInt(String key) {
        String value = get(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Provider property must be an integer: " + key + "=" + value, e);
        }
    }

    public static long getLong(String key) {
        String value = get(key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Provider property must be a long: " + key + "=" + value, e);
        }
    }

    public static boolean getBoolean(String key) {
        String value = get(key);
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("Provider property must be a boolean: " + key + "=" + value);
    }

    private static String envKey(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }

    private static Properties loadClasspathProperties() {
        Properties properties = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = loader.getResourceAsStream(CLASSPATH_CONFIG)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + CLASSPATH_CONFIG, e);
        }
        return properties;
    }
}
