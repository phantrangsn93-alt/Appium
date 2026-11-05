package vn.bsc.automation.core.driver;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.bsc.automation.core.config.AppConfig;

import java.net.URL;

/** Creates Appium sessions and registers them in {@link DriverManager}. */
public final class DriverFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DriverFactory.class);

    private DriverFactory() {
    }

    public static AndroidDriver createAndroidDriver(String udid, Integer systemPort) {
        URL server = AppiumServerManager.ensureServer();
        UiAutomator2Options options = CapabilityBuilder.android(udid, systemPort);
        LOG.info("Creating Android session on {} with udid={}", server, options.getUdid().orElse("<any>"));
        AndroidDriver driver = new AndroidDriver(server, options);
        DriverManager.setDriver(driver);
        ensureAppInForeground();
        return driver;
    }

    /** Brings the app under test to the foreground (e.g. after a Back press left it). */
    public static void ensureAppInForeground() {
        AndroidDriver d = DriverManager.getDriver();
        String pkg = AppConfig.appPackage();
        if (!pkg.equals(d.getCurrentPackage())) {
            LOG.warn("App not in foreground (current: {}) - activating {}", d.getCurrentPackage(), pkg);
            d.activateApp(pkg);
        }
    }
}
