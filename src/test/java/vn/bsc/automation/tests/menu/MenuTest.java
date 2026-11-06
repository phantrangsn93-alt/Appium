package vn.bsc.automation.tests.menu;

import io.qameta.allure.Description;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import vn.bsc.automation.base.LoggedInBaseTest;
import vn.bsc.automation.screens.MenuItem;
import vn.bsc.automation.screens.MenuScreen;
import vn.bsc.automation.screens.MenuTargetScreen;
import vn.bsc.automation.screens.SheetOption;
import vn.bsc.automation.screens.TradingScreen;
import vn.bsc.automation.screens.TransactionFormScreen;
import vn.bsc.automation.screens.components.ActionSheet;
import vn.bsc.automation.screens.components.AppHeader;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Right-hand menu: every in-app entry opens the expected screen / bottom sheet / form (read-only - nothing is
 * submitted). Verifies the locators in {@link MenuItem}, {@link SheetOption}, {@link ActionSheet}.
 * EXTERNAL entries (browser, dialer, permission dialog) are not opened.
 */
public class MenuTest extends LoggedInBaseTest {

    @DataProvider
    public Object[][] inAppItems() {
        return Arrays.stream(MenuItem.values())
                .filter(i -> i.kind == MenuItem.Kind.SCREEN || i.kind == MenuItem.Kind.TAB)
                .map(i -> new Object[]{i})
                .toArray(Object[][]::new);
    }

    @DataProvider
    public Object[][] sheetOptions() {
        return Arrays.stream(SheetOption.values()).map(o -> new Object[]{o}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "inAppItems", groups = {"regression", "menu"})
    @Severity(SeverityLevel.NORMAL)
    @Description("TC_MENU_01 - Mục Menu mở đúng màn hình (kiểm tra tiêu đề)")
    public void menuItemOpensScreen(MenuItem item) {
        MenuScreen menu = new AppHeader().openMenu();
        menu.open(item);
        if (item.kind == MenuItem.Kind.TAB) {
            assertThat(new TradingScreen().isDisplayed()).as("Màn Giao dịch - tab " + item.title).isTrue();
        } else {
            new MenuTargetScreen(item).waitUntilLoaded();
        }
    }

    @Test(dataProvider = "sheetOptions", groups = {"regression", "menu"})
    @Severity(SeverityLevel.CRITICAL)
    @Description("TC_MENU_02 - Giao dịch tiền: bottom sheet đủ lựa chọn và mở đúng form (không bấm Xác nhận)")
    public void sheetOptionOpensForm(SheetOption option) {
        MenuScreen menu = new AppHeader().openMenu();
        menu.open(option.menuItem);
        ActionSheet sheet = new ActionSheet().waitUntilLoaded();
        assertThat(sheet.hasTitle(option.menuItem.title)).as("Tiêu đề sheet").isTrue();
        sheet.choose(option.label);
        TransactionFormScreen form = new TransactionFormScreen(option).waitUntilLoaded();
        assertThat(form.hasTextWithScroll("Xác nhận")).as("Nút Xác nhận trên form " + option.formTitle).isTrue();
    }
}
