package vn.bsc.automation.core.driver;

import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.options.XCUITestOptions;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.config.ConfigReader;

import java.time.Duration;
import java.util.Map;

/** Builds W3C capabilities for Android (UiAutomator2) and iOS (XCUITest) from configuration. */
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

    /**
     * iOS real device / simulator. Real devices need WebDriverAgent signed with the team in
     * {@code ios.xcode.org.id} (IOS_XCODE_ORG_ID in .env) and a unique {@code ios.wda.bundle.id}.
     *
     * @param udid         device udid (null = ios.udid)
     * @param wdaLocalPort WebDriverAgent port; must be unique per device when running in parallel
     */
    public static XCUITestOptions ios(String udid, Integer wdaLocalPort) {
        XCUITestOptions o = new XCUITestOptions()
                .setPlatformName("iOS")
                .setAutomationName("XCUITest")
                .setBundleId(AppConfig.appId())
                .setNoReset(ConfigReader.getBool("no.reset", true))
                .setFullReset(ConfigReader.getBool("full.reset", false))
                .setNewCommandTimeout(Duration.ofSeconds(ConfigReader.getInt("new.command.timeout", 300)))
                .setWdaLocalPort(wdaLocalPort != null ? wdaLocalPort : ConfigReader.getInt("ios.wda.port", 8100))
                .setWdaLaunchTimeout(Duration.ofSeconds(ConfigReader.getInt("ios.wda.launch.timeout", 180)))
                .setAutoAcceptAlerts(false)
                .setShowXcodeLog(ConfigReader.getBool("ios.show.xcode.log", false));
        o.setCapability("appium:autoGrantPermissions", true);

        String effectiveUdid = udid != null ? udid : AppConfig.udid();
        if (effectiveUdid != null && !effectiveUdid.isBlank()) {
            o.setUdid(effectiveUdid);
        }
        set(o, "appium:deviceName", ConfigReader.get("ios.device.name", "iPhone"));
        set(o, "appium:platformVersion", ConfigReader.get("ios.platform.version"));
        set(o, "appium:app", ConfigReader.get("ios.app.path"));
        set(o, "appium:xcodeOrgId", ConfigReader.get("ios.xcode.org.id"));
        set(o, "appium:xcodeSigningId", ConfigReader.get("ios.xcode.signing.id", "Apple Development"));
        set(o, "appium:updatedWDABundleId", ConfigReader.get("ios.wda.bundle.id"));
        if (ConfigReader.getBool("ios.use.prebuilt.wda", false)) {
            o.setCapability("appium:usePrebuiltWDA", true);
        }
        // WebDriverAgent already running on the device (started by xcodebuild / Xcode): attach instead of building it
        set(o, "appium:webDriverAgentUrl", ConfigReader.get("ios.wda.url"));
        set(o, "appium:derivedDataPath", ConfigReader.get("ios.derived.data.path"));
        // Same rationale as Android: Flutter animates continuously -> do not wait for idle / animations.
        // snapshotMaxDepth: Flutter semantics trees are deep (default 50 truncates bottom sheets).
        o.setCapability("appium:settings", Map.of(
                "waitForIdleTimeout", 0,
                "animationCoolOffTimeout", 0,
                "snapshotMaxDepth", ConfigReader.getInt("ios.snapshot.max.depth", 62)));
        return o;
    }

    private static void set(XCUITestOptions o, String cap, String value) {
        if (value != null && !value.isBlank()) {
            o.setCapability(cap, value);
        }
    }
}
