package vn.bsc.automation.screens.components;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.WaitUtils;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic Flutter modal dialog of the app, e.g.
 * <pre>
 *   [Thông báo]                                   (optional title)
 *   Số lượng không thể bỏ trống hoặc phải lớn hơn 0
 *   [Đóng]   |   [Không] [Có]
 * </pre>
 * While a modal is open Flutter hides the semantics of the page below and adds a
 * barrier node with content-desc "Bỏ qua" - used as anchor.
 */
public class AlertDialog extends BaseScreen {

    public static final String BTN_CLOSE = "Đóng";
    public static final String BTN_YES = "Có";
    public static final String BTN_NO = "Không";
    private static final String TITLE = "Thông báo";

    /** Any non-button View with text: the modal hides the semantics of the page below it. */
    private final By textNodes = By.xpath("//android.view.View[string-length(@content-desc)>0]");
    private static final String BARRIER = "Bỏ qua";
    /** Either the modal barrier or one of the standard dialog buttons (some dialogs have no barrier node). */
    private final By anyDialogMarker = Locators.descMatchesAny("Bỏ qua", BTN_CLOSE, BTN_YES, BTN_NO);

    @Override
    protected By anchor() {
        return anyDialogMarker;
    }

    private static By button(String label) {
        return Locators.classWithDesc("android.widget.Button", label);
    }

    public boolean isShown(Duration timeout) {
        return WaitUtils.isVisible(anyDialogMarker, timeout) && !findAll(By.className("android.widget.Button")).isEmpty();
    }

    /** Message text (title "Thông báo" excluded). */
    public String getMessage() {
        List<WebElement> nodes = findAll(textNodes);
        return nodes.stream()
                .map(this::getDesc)
                .filter(t -> !TITLE.equals(t) && !BARRIER.equals(t))
                .collect(Collectors.joining("\n"))
                .trim();
    }

    public boolean hasButton(String label) {
        return isVisible(button(label));
    }

    public void tapButton(String label) {
        tap(button(label));
        WaitUtils.waitInvisible(button(label), Duration.ofSeconds(5));
    }

    public void close() {
        tapButton(BTN_CLOSE);
    }
}
