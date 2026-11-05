package vn.bsc.automation.core.utils;

import io.appium.java_client.android.AndroidDriver;
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

    private static AndroidDriver driver() {
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
        return waiter(timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    public static WebElement clickable(By by) {
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
