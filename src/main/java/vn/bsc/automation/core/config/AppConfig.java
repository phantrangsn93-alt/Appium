package vn.bsc.automation.core.config;

import java.time.Duration;

/** Typed accessors for commonly used configuration values. */
public final class AppConfig {

    private AppConfig() {
    }

    public static String appiumServerUrl() {
        return ConfigReader.get("appium.server.url", "http://127.0.0.1:4723");
    }

    public static boolean appiumAutostart() {
        return ConfigReader.getBool("appium.server.autostart", true);
    }

    public static String udid() {
        return ConfigReader.get("device.udid");
    }

    public static String appPackage() {
        return ConfigReader.getRequired("app.package");
    }

    public static String appActivity() {
        return ConfigReader.getRequired("app.activity");
    }

    public static Duration explicitTimeout() {
        return Duration.ofSeconds(ConfigReader.getInt("timeout.explicit", 20));
    }

    public static Duration shortTimeout() {
        return Duration.ofSeconds(ConfigReader.getInt("timeout.short", 5));
    }

    public static Duration appLaunchTimeout() {
        return Duration.ofSeconds(ConfigReader.getInt("timeout.app.launch", 40));
    }

    public static int retryMaxCount() {
        return ConfigReader.getInt("retry.max.count", 1);
    }

    public static boolean orderSubmitEnabled() {
        return ConfigReader.getBool("order.submit.enabled", false);
    }

    public static String username() {
        return ConfigReader.getRequired("bsc.username");
    }

    public static String password() {
        return ConfigReader.getRequired("bsc.password");
    }

    public static boolean hasCredentials() {
        return ConfigReader.get("bsc.username") != null && ConfigReader.get("bsc.password") != null;
    }
}
