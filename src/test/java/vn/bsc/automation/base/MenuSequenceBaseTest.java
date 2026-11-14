package vn.bsc.automation.base;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import vn.bsc.automation.screens.MenuScreen;
import vn.bsc.automation.screens.components.AlertDialog;
import vn.bsc.automation.screens.components.AppHeader;
import vn.bsc.automation.screens.components.BottomNavBar;

import java.time.Duration;

/**
 * Menu test cases run one after another FROM THE OPEN MENU: the menu is opened once; after each screen a single
 * Back returns to the menu (groups stay expanded) - no round trip through the main screen.
 */
public abstract class MenuSequenceBaseTest extends LoggedInBaseTest {

    @Override
    protected boolean keepScreenBetweenTests() {
        return true;
    }

    /** Menu open: from a sub-screen go back (max 3), from the main screen open it once. */
    @BeforeMethod(alwaysRun = true)
    public void toMenu() {
        MenuScreen menu = new MenuScreen();
        AlertDialog dialog = new AlertDialog();
        for (int i = 0; i < 4 && !menu.isDisplayed(Duration.ofSeconds(1)); i++) {
            if (dialog.isShown(Duration.ofMillis(300)) && dialog.hasButton(AlertDialog.BTN_CLOSE)) {
                dialog.close();
                continue;
            }
            if (new BottomNavBar().isDisplayed(Duration.ofSeconds(1))) {
                new AppHeader().openMenu();
                return;
            }
            menu.pressBack();
        }
    }

    /** One Back from the screen just tested: back to the menu, ready for the next item. */
    @AfterMethod(alwaysRun = true)
    public void backToMenu() {
        if (!new MenuScreen().isDisplayed(Duration.ofMillis(500))) {
            new MenuScreen().pressBack();
        }
    }
}
