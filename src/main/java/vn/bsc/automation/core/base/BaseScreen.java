package vn.bsc.automation.core.base;

import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Allure;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.driver.DriverManager;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.WaitUtils;

import java.time.Duration;
import java.util.List;

/**
 * Base for every screen / component object.
 * Wraps raw Appium calls with explicit waits, stale-element retry and logging so that
 * screen objects contain only locators and business actions.
 */
public abstract class BaseScreen {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected AndroidDriver driver() {
        return DriverManager.getDriver();
    }

    /** Unique element proving this screen is displayed. */
    protected abstract By anchor();

    public boolean isDisplayed() {
        return isDisplayed(AppConfig.explicitTimeout());
    }

    public boolean isDisplayed(Duration timeout) {
        return WaitUtils.isVisible(anchor(), timeout);
    }

    /** Waits until screen is loaded; fails with a clear message otherwise. */
    @SuppressWarnings("unchecked")
    public <T extends BaseScreen> T waitUntilLoaded() {
        if (!isDisplayed()) {
            throw new AssertionError(getClass().getSimpleName() + " is not displayed (anchor: " + anchor() + ")");
        }
        return (T) this;
    }

    // ---------------------------------------------------------------- element access

    protected WebElement find(By by) {
        return WaitUtils.visible(by);
    }

    protected List<WebElement> findAll(By by) {
        return driver().findElements(by);
    }

    protected boolean isVisible(By by) {
        return WaitUtils.isVisible(by, AppConfig.shortTimeout());
    }

    protected boolean isVisible(By by, Duration timeout) {
        return WaitUtils.isVisible(by, timeout);
    }

    /**
     * Scrolls the screen until the element is visible (small screens push content below the fold).
     * Device-independent: swipes are proportional to the window size.
     */
    protected WebElement scrollTo(By by) {
        for (int i = 0; i <= 5; i++) {
            if (WaitUtils.isVisible(by, Duration.ofSeconds(i == 0 ? 2 : 1))) {
                return find(by);
            }
            GestureUtils.scrollDown();
        }
        throw new AssertionError("Element not found after scrolling: " + by);
    }

    protected boolean isVisibleWithScroll(By by) {
        try {
            scrollTo(by);
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- actions

    protected void tap(By by) {
        step("Tap " + by, () -> retryOnStale(() -> WaitUtils.clickable(by).click()));
    }

    /** Coordinate tap on the element centre - for Flutter widgets without accessibility action. */
    protected void tapCenter(By by) {
        step("Tap centre of " + by, () -> retryOnStale(() -> GestureUtils.tapCenter(find(by))));
    }

    protected void type(By by, String text) {
        step("Type into " + by, () -> retryOnStale(() -> {
            WebElement el = WaitUtils.clickable(by);
            el.click();
            el.clear();
            el.sendKeys(text);
        }));
    }

    /** Returned text is NFC-normalised (app mixes NFC/NFD Vietnamese). */
    protected String getText(By by) {
        return Locators.nfc(retryOnStaleGet(() -> find(by).getText()));
    }

    /** Flutter puts visible text into content-desc rather than text. NFC-normalised. */
    protected String getDesc(By by) {
        return Locators.nfc(retryOnStaleGet(() -> find(by).getAttribute("content-desc")));
    }

    protected String getDesc(WebElement el) {
        return Locators.nfc(el.getAttribute("content-desc"));
    }

    protected boolean isSelected(By by) {
        return Boolean.parseBoolean(find(by).getAttribute("selected"));
    }

    /**
     * Taps a Flutter tab and waits until it reports selected=true
     * (the page transition animation finishes after the tap returns).
     */
    protected void tapTab(By tab) {
        tap(tab);
        WaitUtils.condition(() -> isSelected(tab), Duration.ofSeconds(5));
    }

    protected boolean isChecked(By by) {
        return Boolean.parseBoolean(find(by).getAttribute("checked"));
    }

    public void hideKeyboard() {
        try {
            if (driver().isKeyboardShown()) {
                driver().hideKeyboard();
            }
        } catch (Exception ignored) {
            // keyboard may already be hidden
        }
    }

    public void pressBack() {
        driver().navigate().back();
    }

    // ---------------------------------------------------------------- helpers

    protected void step(String name, Runnable action) {
        log.info(name);
        Allure.step(name, action::run);
    }

    private void retryOnStale(Runnable r) {
        for (int i = 0; ; i++) {
            try {
                r.run();
                return;
            } catch (StaleElementReferenceException e) {
                if (i >= 2) {
                    throw e;
                }
            }
        }
    }

    private <T> T retryOnStaleGet(java.util.function.Supplier<T> s) {
        for (int i = 0; ; i++) {
            try {
                return s.get();
            } catch (StaleElementReferenceException e) {
                if (i >= 2) {
                    throw e;
                }
            }
        }
    }
}
