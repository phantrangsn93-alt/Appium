package vn.bsc.automation.core.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration access.
 * <p>
 * Resolution order (highest priority first):
 * <ol>
 *     <li>JVM system property: {@code -Ddevice.udid=xxx}</li>
 *     <li>OS environment variable or {@code .env} file: {@code DEVICE_UDID=xxx}</li>
 *     <li>{@code config/config-<env>.properties}</li>
 *     <li>{@code config/config.properties}</li>
 * </ol>
 * Secrets (password, token...) must only live in .env / CI secret store - never in properties files.
 */
public final class ConfigReader {

    private static final Properties PROPS = new Properties();
    private static final Dotenv DOTENV = Dotenv.configure().ignoreIfMissing().ignoreIfMalformed().load();

    static {
        load("config/config.properties", true);
        String env = get("env", "sit");
        load("config/config-" + env + ".properties", false);
    }

    private ConfigReader() {
    }

    private static void load(String resource, boolean required) {
        try (InputStream in = ConfigReader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                if (required) {
                    throw new IllegalStateException("Missing config resource: " + resource);
                }
                return;
            }
            Properties p = new Properties();
            p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            PROPS.putAll(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
    }

    /** device.udid -> DEVICE_UDID */
    private static String envKey(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }

    public static String get(String key, String defaultValue) {
        String v = System.getProperty(key);
        if (isBlank(v)) {
            v = DOTENV.get(envKey(key), null); // Dotenv also falls back to System.getenv
        }
        if (isBlank(v)) {
            v = PROPS.getProperty(key);
        }
        return isBlank(v) ? defaultValue : v.trim();
    }

    public static String get(String key) {
        return get(key, null);
    }

    public static String getRequired(String key) {
        String v = get(key);
        if (isBlank(v)) {
            throw new IllegalStateException("Required config '" + key + "' (env: " + envKey(key)
                    + ") is not set. Fill it in .env or pass -D" + key + "=...");
        }
        return v;
    }

    public static int getInt(String key, int defaultValue) {
        String v = get(key);
        return isBlank(v) ? defaultValue : Integer.parseInt(v);
    }

    public static boolean getBool(String key, boolean defaultValue) {
        String v = get(key);
        return isBlank(v) ? defaultValue : Boolean.parseBoolean(v);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
