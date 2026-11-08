package vn.bsc.automation.core.driver;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.bsc.automation.core.config.AppConfig;

import java.net.URL;

/** Creates Appium sessions and registers them in {@link DriverManager}. */
public final class DriverFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DriverFactory.class);

    private DriverFactory() {
    }

    /**
     * Creates the session for the configured platform ({@code platform.name}).
     *
     * @param port Android: UiAutomator2 systemPort; iOS: WebDriverAgent local port (unique per device)
     */
    public static AppiumDriver createDriver(String udid, Integer port) {
        return AppConfig.isIOS() ? createIOSDriver(udid, port) : createAndroidDriver(udid, port);
    }

    public static IOSDriver createIOSDriver(String udid, Integer wdaLocalPort) {
        URL server = AppiumServerManager.ensureServer();
        XCUITestOptions options = CapabilityBuilder.ios(udid, wdaLocalPort);
        LOG.info("Creating iOS session on {} with udid={} bundleId={}", server, options.getUdid().orElse("<any>"),
                AppConfig.appId());
        IOSDriver driver = new IOSDriver(server, options);
        DriverManager.setDriver(driver);
        ensureAppInForeground();
        return driver;
    }

    public static AndroidDriver createAndroidDriver(String udid, Integer systemPort) {
        URL server = AppiumServerManager.ensureServer();
        UiAutomator2Options options = CapabilityBuilder.android(udid, systemPort);
        LOG.info("Creating Android session on {} with udid={}", server, options.getUdid().orElse("<any>"));
        AndroidDriver driver;
        try {
            driver = new AndroidDriver(server, options);
        } catch (org.openqa.selenium.SessionNotCreatedException e) {
            // A previous run killed mid-way can leave the UiAutomator2 port forwarded ("local port #8200 is busy")
            if (e.getMessage() == null || !e.getMessage().contains("is busy")) {
                throw e;
            }
            LOG.warn("UiAutomator2 port busy - removing stale adb forwards and retrying once");
            releaseStalePorts(options.getUdid().orElse(null));
            driver = new AndroidDriver(server, options);
        }
        DriverManager.setDriver(driver);
        ensureAppInForeground();
        return driver;
    }

    /** adb forward --remove-all + stop the old UiAutomator2 server on the device. */
    private static void releaseStalePorts(String udid) {
        String adb = System.getenv().getOrDefault("ANDROID_HOME",
                System.getProperty("user.home") + "/Library/Android/sdk") + "/platform-tools/adb";
        String[][] cmds = {
                {adb, "-s", udid, "forward", "--remove-all"},
                {adb, "-s", udid, "shell", "am", "force-stop", "io.appium.uiautomator2.server"},
                {adb, "-s", udid, "shell", "am", "force-stop", "io.appium.uiautomator2.server.test"}};
        for (String[] c : cmds) {
            if (udid == null) {
                c = java.util.Arrays.stream(c).filter(s -> s != null && !"-s".equals(s)).toArray(String[]::new);
            }
            try {
                new ProcessBuilder(c).redirectErrorStream(true).start().waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ex) {
                LOG.warn("Could not run {}: {}", String.join(" ", c), ex.getMessage());
            }
        }
    }

    /** Brings the app under test to the foreground (e.g. after a Back press left it). */
    public static void ensureAppInForeground() {
        if (!DeviceActions.isAppInForeground()) {
            LOG.warn("App not in foreground - activating {}", AppConfig.appId());
            DeviceActions.activateApp();
        }
    }
}
