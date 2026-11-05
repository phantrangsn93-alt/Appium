package vn.bsc.automation.base;

import org.testng.annotations.AfterClass;
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
 * Driver lifecycle: one Appium session per test class (fast, keeps app state such as login),
 * screen recording per test method (attached to report only on failure - see TestListener).
 * <p>
 * Parallel run: list devices in config {@code devices=udid1:8200,udid2:8201} and use
 * {@code parallel="classes"} (testng-parallel.xml) - each class borrows a free device from {@link DevicePool}.
 * A {@code udid} parameter in testng.xml pins a class/test to a specific device instead.
 */
public abstract class BaseTest {

    protected LoginFlow loginFlow;
    protected Navigator navigator;

    @BeforeClass(alwaysRun = true)
    @Parameters({"udid", "systemPort"})
    public void startSession(@Optional String udid, @Optional String systemPort) {
        if (udid == null) {
            // Parallel mode: borrow a free device from the pool (config "devices=udid:port,...")
            DevicePool.Device d = DevicePool.acquire();
            udid = d.udid();
            systemPort = String.valueOf(d.systemPort());
        }
        DriverFactory.createAndroidDriver(udid, systemPort == null ? null : Integer.valueOf(systemPort));
        loginFlow = new LoginFlow();
        navigator = new Navigator();
    }

    @BeforeMethod(alwaysRun = true)
    public void startRecording() {
        if (ConfigReader.getBool("video.on.failure", true)) {
            AllureUtils.startRecording();
        }
    }

    @AfterClass(alwaysRun = true)
    public void stopSession() {
        DriverManager.quitDriver();
        DevicePool.release();
    }
}
