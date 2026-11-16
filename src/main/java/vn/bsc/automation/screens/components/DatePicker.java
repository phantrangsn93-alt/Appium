package vn.bsc.automation.screens.components;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.Point;
import vn.bsc.automation.core.base.BaseScreen;
import vn.bsc.automation.core.utils.GestureUtils;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.core.utils.WaitUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Comparator;

/**
 * Date picker dialog of the statement / history screens (surveyed on iOS 14/11/2026):
 * <pre>
 *   [ 15 ]  [ Tháng 10 ]  [ 2025 ]
 *   [ 16 ]  [ Tháng 11 ]  [ 2026 ]   &lt;- selected values = the column nodes' labels
 *   [ 17 ]  [ Tháng 12 ]  [ 2027 ]
 *        [Huỷ bỏ]   [Đồng ý]
 * </pre>
 * Each column is a wheel showing 3 rows: one step = a drag of a third of the column height
 * (drag up = next value). Values are re-read after every step, so the wheel physics cannot overshoot unnoticed.
 */
public class DatePicker extends BaseScreen {

    private final By ok = Locators.classWithDesc("android.widget.Button", "Đồng ý");
    private final By cancel = Locators.classWithDesc("android.widget.Button", "Huỷ bỏ");

    @Override
    protected By anchor() {
        return ok;
    }

    private record Column(UiNode node, int value) {
    }

    /** day, month, year columns, left to right. */
    private List<Column> columns() {
        List<UiNode> nodes = UiNode.snapshot(driver().getPageSource());
        UiNode okNode = nodes.stream().filter(n -> "Đồng ý".equals(n.desc())).findFirst()
                .orElseThrow(() -> new AssertionError("Bộ chọn ngày không mở"));
        // wheel value: content-desc on Android, the "value" attribute on iOS (label empty)
        List<UiNode> cols = nodes.stream()
                .filter(n -> n.y() < okNode.y() && n.height() > 60 && n.label().matches("(?:Tháng\\s*)?[0-9]{1,4}"))
                .sorted(Comparator.comparingInt(UiNode::x)).toList();
        if (cols.size() < 3) {
            throw new AssertionError("Bộ chọn ngày: không đọc được 3 cột Ngày/Tháng/Năm: " + cols);
        }
        return cols.subList(0, 3).stream()
                .map(n -> new Column(n, Integer.parseInt(n.label().replaceAll("[^0-9]", "")))).toList();
    }


    public LocalDate current() {
        List<Column> c = columns();
        return LocalDate.of(c.get(2).value(), c.get(1).value(), c.get(0).value());
    }

    /** Sets the wheels to {@code date} and taps "Đồng ý". */
    @Step("Chọn ngày {date}")
    public void choose(LocalDate date) {
        setColumn(2, date.getYear());
        setColumn(1, date.getMonthValue());
        setColumn(0, date.getDayOfMonth());
        LocalDate now = current();
        if (!now.equals(date)) {
            throw new AssertionError("Bộ chọn ngày đang ở " + now + ", cần " + date);
        }
        tap(ok);
        WaitUtils.waitInvisible(ok, Duration.ofSeconds(5));
    }

    @Step("Huỷ chọn ngày")
    public void cancel() {
        tap(cancel);
        WaitUtils.waitInvisible(ok, Duration.ofSeconds(5));
    }

    private void setColumn(int index, int target) {
        for (int i = 0; i < 40; i++) {
            Column c = columns().get(index);
            int diff = target - c.value();
            if (diff == 0) {
                return;
            }
            UiNode n = c.node();
            int step = n.height() / 3;
            int x = n.centerX(), y = n.centerY();
            int moves = Math.min(Math.abs(diff), 3); // up to 3 rows per drag, then re-read
            int dy = step * moves * (diff > 0 ? -1 : 1);
            GestureUtils.swipe(new Point(x, y), new Point(x, y + dy), Duration.ofMillis(350 + 120 * moves));
            WaitUtils.pause(Duration.ofMillis(500)); // wheel settles
        }
        Optional<Column> last = Optional.of(columns().get(index));
        throw new AssertionError("Bộ chọn ngày: cột " + index + " không tới được " + target + " (đang " + last.get().value() + ")");
    }
}
