package vn.bsc.automation.core.driver;

import io.appium.java_client.android.AndroidDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thread-safe holder for the driver of the current test thread (enables parallel execution). */
public final class DriverManager {

    private static final Logger LOG = LoggerFactory.getLogger(DriverManager.class);
    private static final ThreadLocal<AndroidDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {
    }

    public static AndroidDriver getDriver() {
        AndroidDriver d = DRIVER.get();
        if (d == null) {
            throw new IllegalStateException("Driver is not initialised for thread " + Thread.currentThread().getName());
        }
        return d;
    }

    public static boolean hasDriver() {
        return DRIVER.get() != null;
    }

    static void setDriver(AndroidDriver driver) {
        DRIVER.set(driver);
    }

    public static void quitDriver() {
        AndroidDriver d = DRIVER.get();
        if (d != null) {
            try {
                d.quit();
            } catch (Exception e) {
                LOG.warn("Error while quitting driver: {}", e.getMessage());
            } finally {
                DRIVER.remove();
            }
        }
    }
}
