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

    /** Target platform: {@code platform.name} = Android (default) | iOS. Override with -Dplatform.name=iOS or PLATFORM_NAME. */
    public static boolean isIOS() {
        return "ios".equalsIgnoreCase(ConfigReader.get("platform.name", "Android"));
    }

    /** Device udid of the selected platform (Android: device.udid, iOS: ios.udid). */
    public static String udid() {
        return isIOS() ? ConfigReader.get("ios.udid") : ConfigReader.get("device.udid");
    }

    /** Application id of the selected platform (Android package or iOS bundle id). */
    public static String appId() {
        return isIOS() ? ConfigReader.getRequired("ios.bundle.id") : appPackage();
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

    /** Order PIN (BSC_ORDER_PIN in .env). Null = tester types the PIN on the phone. Never log it. */
    public static String orderPin() {
        return credentialsDisabled() ? null : ConfigReader.get("bsc.order.pin");
    }

    public static boolean hasCredentials() {
        return !credentialsDisabled()
                && ConfigReader.get("bsc.username") != null && ConfigReader.get("bsc.password") != null;
    }

    /**
     * credentials.disabled=true (config-prod): ignore .env account/PIN entirely - the tester types PIN/OTP on the
     * phone. A UAT password/PIN typed into the production app could lock a real account.
     */
    public static boolean credentialsDisabled() {
        return ConfigReader.getBool("credentials.disabled", false);
    }
}
