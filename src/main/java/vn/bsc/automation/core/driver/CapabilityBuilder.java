package vn.bsc.automation.core.driver;

import io.appium.java_client.android.options.UiAutomator2Options;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.config.ConfigReader;

import java.time.Duration;
import java.util.Map;

/** Builds W3C capabilities for Android / UiAutomator2 from configuration. */
public final class CapabilityBuilder {

    private CapabilityBuilder() {
    }

    /**
     * @param udid       device serial (null = config value)
     * @param systemPort UiAutomator2 server port; must be unique per device when running in parallel
     */
    public static UiAutomator2Options android(String udid, Integer systemPort) {
        UiAutomator2Options o = new UiAutomator2Options()
                .setPlatformName("Android")
                .setAutomationName("UiAutomator2")
                .setAppPackage(AppConfig.appPackage())
                .setAppActivity(AppConfig.appActivity())
                .setNoReset(ConfigReader.getBool("no.reset", true))
                .setFullReset(ConfigReader.getBool("full.reset", false))
                .setNewCommandTimeout(Duration.ofSeconds(ConfigReader.getInt("new.command.timeout", 300)))
                .setAutoGrantPermissions(true)
                .setAppWaitDuration(AppConfig.appLaunchTimeout())
                .setSystemPort(systemPort != null ? systemPort : ConfigReader.getInt("system.port", 8200))
                .setDisableWindowAnimation(true);

        String effectiveUdid = udid != null ? udid : AppConfig.udid();
        if (effectiveUdid != null) {
            o.setUdid(effectiveUdid);
        }
        String deviceName = ConfigReader.get("device.name");
        if (deviceName != null) {
            o.setDeviceName(deviceName);
        }
        String appPath = ConfigReader.get("app.path");
        if (appPath != null) {
            o.setApp(appPath);
        }
        // Flutter screens animate continuously (carousels, live prices) -> never wait for UI idle.
        // enforceXPath1: the default XPath 2.0 engine fails on expressions like "(//a/following::b)[n]".
        o.setCapability("appium:settings", Map.of("waitForIdleTimeout", 0, "enforceXPath1", true));
        return o;
    }
}
