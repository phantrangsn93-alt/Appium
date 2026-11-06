package vn.bsc.automation.core.utils;

import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import vn.bsc.automation.core.driver.DriverManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** W3C Actions / mobile: gestures (TouchAction is deprecated and not used). */
public final class GestureUtils {

    private GestureUtils() {
    }

    public static void tap(int x, int y) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence tap = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        DriverManager.getDriver().perform(List.of(tap));
    }

    /** Tap the geometric centre of an element (for Flutter widgets that ignore accessibility click). */
    public static void tapCenter(WebElement el) {
        Rectangle r = el.getRect();
        tap(r.getX() + r.getWidth() / 2, r.getY() + r.getHeight() / 2);
    }

    public static void swipe(Point from, Point to, Duration duration) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), from.x, from.y))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerMove(duration, PointerInput.Origin.viewport(), to.x, to.y))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        DriverManager.getDriver().perform(List.of(swipe));
    }

    /** Scroll content up (finger moves from bottom to top). */
    public static void scrollDown() {
        Dimension s = DriverManager.getDriver().manage().window().getSize();
        swipe(new Point(s.width / 2, (int) (s.height * 0.70)), new Point(s.width / 2, (int) (s.height * 0.30)),
                Duration.ofMillis(500));
    }

    /** Small scroll: content moves by {@code fraction} of the window height (positive = down, negative = up). */
    public static void scrollBy(double fraction) {
        Dimension s = DriverManager.getDriver().manage().window().getSize();
        int from = (int) (s.height * (fraction > 0 ? 0.65 : 0.35));
        int to = (int) (from - s.height * fraction);
        swipe(new Point(s.width / 2, from), new Point(s.width / 2, to), Duration.ofMillis(600));
    }

    public static void scrollUp() {
        Dimension s = DriverManager.getDriver().manage().window().getSize();
        swipe(new Point(s.width / 2, (int) (s.height * 0.30)), new Point(s.width / 2, (int) (s.height * 0.70)),
                Duration.ofMillis(500));
    }

    /** Native UiAutomator2 swipe gesture inside an element (e.g. horizontal carousel). */
    public static void swipeIn(WebElement el, String direction, double percent) {
        DriverManager.getDriver().executeScript("mobile: swipeGesture", Map.of(
                "elementId", ((org.openqa.selenium.remote.RemoteWebElement) el).getId(),
                "direction", direction,
                "percent", percent));
    }
}
