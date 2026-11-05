package vn.bsc.automation.base;

import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.driver.DriverFactory;
import vn.bsc.automation.screens.components.AlertDialog;
import vn.bsc.automation.screens.components.BottomNavBar;

import java.time.Duration;

/** Base for suites that require an authenticated session. */
public abstract class LoggedInBaseTest extends BaseTest {

    // TestNG runs the parent's @BeforeClass (startSession) before this one.
    @BeforeClass(alwaysRun = true)
    public void login() {
        loginFlow.ensureLoggedIn();
    }

    /** Every test starts from a main screen with no dialog open (test independence). */
    @BeforeMethod(alwaysRun = true)
    public void backToMainScreen() {
        AlertDialog dialog = new AlertDialog();
        BottomNavBar nav = new BottomNavBar();
        for (int i = 0; i < 3; i++) {
            DriverFactory.ensureAppInForeground(); // a Back press may have left the app
            if (dialog.isShown(Duration.ofMillis(500))) {
                if (dialog.hasButton(AlertDialog.BTN_CLOSE)) {
                    dialog.close();
                } else if (dialog.hasButton(AlertDialog.BTN_NO)) {
                    dialog.tapButton(AlertDialog.BTN_NO);
                }
                continue;
            }
            if (nav.isDisplayed(Duration.ofSeconds(2))) {
                nav.hideKeyboard();
                return;
            }
            nav.pressBack();
        }
        DriverFactory.ensureAppInForeground();
        loginFlow.ensureLoggedIn();
    }

    /** Skips (not fails) a check that needs the account from .env. */
    protected void requireCredentials() {
        if (!AppConfig.hasCredentials()) {
            throw new SkipException("BSC_USERNAME not set in .env");
        }
    }
}
