package vn.bsc.automation.core.utils;

import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.driver.DriverManager;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/** Explicit waits only. Thread.sleep is forbidden in this framework. */
public final class WaitUtils {

    private static final Duration POLLING = Duration.ofMillis(300);

    private WaitUtils() {
    }

    private static AppiumDriver driver() {
        return DriverManager.getDriver();
    }

    public static WebDriverWait waiter(Duration timeout) {
        WebDriverWait w = new WebDriverWait(driver(), timeout, POLLING);
        w.ignoring(StaleElementReferenceException.class);
        return w;
    }

    public static WebElement visible(By by) {
        return visible(by, AppConfig.explicitTimeout());
    }

    public static WebElement visible(By by, Duration timeout) {
        if (vn.bsc.automation.core.config.Platform.ios()) {
            return waiter(timeout).until(onScreen(by));
        }
        return waiter(timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    /**
     * iOS: XCUITest computes "visible" by hit-testing, and this Flutter app keeps a full-screen ad container
     * ("No ads") on top, so real, visible widgets report visible=false. An element counts as visible when it
     * exists and its frame lies inside the window.
     */
    private static ExpectedCondition<WebElement> onScreen(By by) {
        return d -> {
            org.openqa.selenium.Dimension w = d.manage().window().getSize();
            for (WebElement e : d.findElements(by)) {
                org.openqa.selenium.Rectangle r = e.getRect();
                if (r.getWidth() > 0 && r.getHeight() > 0 && r.getX() < w.getWidth() && r.getY() < w.getHeight()
                        && r.getX() + r.getWidth() > 0 && r.getY() + r.getHeight() > 0) {
                    return e;
                }
            }
            return null;
        };
    }

    public static WebElement clickable(By by) {
        if (vn.bsc.automation.core.config.Platform.ios()) {
            ExpectedCondition<WebElement> visible = onScreen(by);
            return waiter(AppConfig.explicitTimeout()).until(d -> {
                WebElement e = visible.apply(d);
                return e != null && e.isEnabled() ? e : null;
            });
        }
        return waiter(AppConfig.explicitTimeout()).until(ExpectedConditions.elementToBeClickable(by));
    }

    public static boolean isVisible(By by, Duration timeout) {
        try {
            visible(by, timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean waitInvisible(By by, Duration timeout) {
        if (vn.bsc.automation.core.config.Platform.ios()) {
            ExpectedCondition<WebElement> visible = onScreen(by);
            return condition(() -> visible.apply(driver()) == null, timeout);
        }
        try {
            return waiter(timeout).until(ExpectedConditions.invisibilityOfElementLocated(by));
        } catch (Exception e) {
            return false;
        }
    }

    public static List<WebElement> allPresent(By by, Duration timeout) {
        return waiter(timeout).until(ExpectedConditions.presenceOfAllElementsLocatedBy(by));
    }

    public static <T> T until(ExpectedCondition<T> condition, Duration timeout) {
        return waiter(timeout).until(condition);
    }

    /** Polls an arbitrary condition (e.g. "price value changed"). */
    public static boolean condition(Supplier<Boolean> check, Duration timeout) {
        try {
            return new FluentWait<>(check)
                    .withTimeout(timeout)
                    .pollingEvery(POLLING)
                    .ignoring(NoSuchElementException.class)
                    .ignoring(StaleElementReferenceException.class)
                    .until(c -> Boolean.TRUE.equals(c.get()));
        } catch (Exception e) {
            return false;
        }
    }
}
