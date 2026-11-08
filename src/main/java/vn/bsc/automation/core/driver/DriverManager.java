package vn.bsc.automation.core.driver;

import io.appium.java_client.AppiumDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe holder for the driver of the current test thread (enables parallel execution).
 * All drivers are also registered globally so a shared session can be closed once at suite end.
 */
public final class DriverManager {

    private static final Logger LOG = LoggerFactory.getLogger(DriverManager.class);
    private static final ThreadLocal<AppiumDriver> DRIVER = new ThreadLocal<>();
    private static final Map<AppiumDriver, String> ALL = new ConcurrentHashMap<>();

    private DriverManager() {
    }

    public static AppiumDriver getDriver() {
        AppiumDriver d = DRIVER.get();
        if (d == null) {
            throw new IllegalStateException("Driver is not initialised for thread " + Thread.currentThread().getName());
        }
        return d;
    }

    public static boolean hasDriver() {
        return DRIVER.get() != null;
    }

    static void setDriver(AppiumDriver driver) {
        DRIVER.set(driver);
        ALL.put(driver, Thread.currentThread().getName());
    }

    /** Cheap health check before re-using a shared session (USB drop, server crash, timeout...). */
    public static boolean isAlive() {
        AppiumDriver d = DRIVER.get();
        if (d == null || d.getSessionId() == null) {
            return false;
        }
        try {
            DeviceActions.isAppInForeground();
            return true;
        } catch (Exception e) {
            LOG.warn("Shared session is no longer usable: {}", e.getMessage());
            return false;
        }
    }

    /** udid of the current session (null if unknown). */
    public static String currentUdid() {
        AppiumDriver d = DRIVER.get();
        Object u = d == null ? null : d.getCapabilities().getCapability("appium:udid");
        if (u == null && d != null) {
            u = d.getCapabilities().getCapability("udid");
        }
        return u == null ? null : u.toString();
    }

    /** Quits the current thread's driver. */
    public static void quitDriver() {
        AppiumDriver d = DRIVER.get();
        if (d != null) {
            quit(d);
            DRIVER.remove();
        }
    }

    /** Quits every driver created during the run (shared sessions, all threads). */
    public static void quitAll() {
        ALL.keySet().forEach(DriverManager::quit);
        DRIVER.remove();
    }

    private static void quit(AppiumDriver d) {
        try {
            d.quit();
        } catch (Exception e) {
            LOG.warn("Error while quitting driver: {}", e.getMessage());
        } finally {
            ALL.remove(d);
        }
    }
}
