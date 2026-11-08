package vn.bsc.automation.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import vn.bsc.automation.core.config.ConfigReader;
import vn.bsc.automation.core.driver.DevicePool;
import vn.bsc.automation.core.driver.DriverFactory;
import vn.bsc.automation.core.driver.DriverManager;
import vn.bsc.automation.core.utils.AllureUtils;
import vn.bsc.automation.flows.LoginFlow;
import vn.bsc.automation.flows.Navigator;

/**
 * Driver lifecycle.
 * <ul>
 *   <li><b>Shared session (default, {@code session.shared=true})</b>: one Appium session per thread for the
 *       WHOLE suite - classes re-use it (no ~15 s UiAutomator2 restart per class). Before re-use the session
 *       is health-checked and transparently re-created if dead (USB drop, server crash). Closed once in
 *       {@code @AfterSuite}.</li>
 *   <li><b>Per-class session ({@code -Dsession.shared=false})</b>: new session per class - stronger isolation
 *       when debugging a class that leaves the app in a bad state.</li>
 * </ul>
 * Screen recording is per test method (attached to the report only on failure - see TestListener).
 * <p>
 * Parallel run: devices in config {@code devices=udid1:8200,udid2:8201} + {@code parallel="classes"}
 * (testng-parallel.xml): each thread borrows a device from {@link DevicePool} and keeps it (and its shared
 * session) until the end of the suite. A {@code udid} parameter in testng.xml pins a test to a device.
 */
public abstract class BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseTest.class);

    protected LoginFlow loginFlow;
    protected Navigator navigator;

    private static boolean sharedSession() {
        return ConfigReader.getBool("session.shared", true);
    }

    @BeforeClass(alwaysRun = true)
    @Parameters({"udid", "systemPort"})
    public void startSession(@Optional String udid, @Optional String systemPort) {
        boolean reusable = sharedSession() && DriverManager.hasDriver()
                && (udid == null || udid.equals(DriverManager.currentUdid()))
                && DriverManager.isAlive();
        if (reusable) {
            LOG.info("Re-using shared Appium session for {}", getClass().getSimpleName());
            DriverFactory.ensureAppInForeground();
        } else {
            if (DriverManager.hasDriver()) {
                DriverManager.quitDriver(); // dead or pinned to another device
            }
            if (udid == null) {
                // Keep the device this thread already holds; otherwise borrow one from the pool
                DevicePool.Device d = DevicePool.current() != null ? DevicePool.current() : DevicePool.acquire();
                udid = d.udid();
                systemPort = String.valueOf(d.systemPort());
            }
            DriverFactory.createDriver(udid, systemPort == null ? null : Integer.valueOf(systemPort));
        }
        loginFlow = new LoginFlow();
        navigator = new Navigator();
    }

    @BeforeMethod(alwaysRun = true)
    public void startRecording() {
        if (ConfigReader.getBool("video.on.failure", true)) {
            AllureUtils.startRecording();
        }
    }

    /** Per-class mode only: close the session and give the device back. */
    @AfterClass(alwaysRun = true)
    public void stopSession() {
        if (!sharedSession()) {
            DriverManager.quitDriver();
            DevicePool.release();
        }
    }

    /** Shared mode: close every session once, at the very end. */
    @AfterSuite(alwaysRun = true)
    public void closeSharedSessions() {
        DriverManager.quitAll();
        DevicePool.releaseAll();
    }
}
