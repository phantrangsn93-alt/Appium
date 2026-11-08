package vn.bsc.automation.unit;

import org.testng.annotations.Test;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.screens.OrderBookScreen;
import vn.bsc.automation.screens.OrderBookScreen.OrderSpec;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test (no device): order-book row matching and statuses (row format assumed from the column survey). */
public class OrderBookMatchTest {

    private static UiNode row(String desc) {
        return new UiNode("android.view.View", Locators.nfc(desc), "", true, 0, 0, 100, 50);
    }

    private final OrderSpec buyAaa = new OrderSpec("AAA", true, 100, new BigDecimal("7.53"), null, false);

    @Test(groups = "unit")
    public void matchesSymbolSideQtyPriceAndStatus() {
        UiNode r = row("AAA\nM\n7.53\n100\nĐã gửi");
        assertThat(OrderBookScreen.rowMatches(r, buyAaa)).isTrue();
        assertThat(OrderBookScreen.hasStatus(r, OrderBookScreen.STATUS_PLACED)).isTrue();
        assertThat(OrderBookScreen.hasStatus(r, OrderBookScreen.STATUS_CANCELLED)).isFalse();
    }

    @Test(groups = "unit")
    public void priceShownX1000AndZeroWidthCharsAreAccepted() {
        UiNode r = row("A​A​A\nMua\n7,530\n100\nCh​ờ gửi");
        assertThat(OrderBookScreen.rowMatches(r, buyAaa)).isTrue();
        assertThat(OrderBookScreen.hasStatus(r, OrderBookScreen.STATUS_PLACED)).isTrue();
    }

    @Test(groups = "unit")
    public void wrongSideQtyOrPriceDoesNotMatch() {
        assertThat(OrderBookScreen.rowMatches(row("AAA\nB\n7.53\n100\nĐã gửi"), buyAaa)).as("side").isFalse();
        assertThat(OrderBookScreen.rowMatches(row("AAA\nM\n7.53\n200\nĐã gửi"), buyAaa)).as("qty").isFalse();
        assertThat(OrderBookScreen.rowMatches(row("AAA\nM\n7.54\n100\nĐã gửi"), buyAaa)).as("price").isFalse();
        assertThat(OrderBookScreen.rowMatches(row("AAAA\nM\n7.53\n100\nĐã gửi"), buyAaa)).as("symbol").isFalse();
    }

    @Test(groups = "unit")
    public void modifiedAndCancelledStatusesBothSpellings() {
        assertThat(OrderBookScreen.hasStatus(row("AAA\nM\n7.53\n100\nĐã sửa"), OrderBookScreen.STATUS_MODIFIED)).isTrue();
        assertThat(OrderBookScreen.hasStatus(row("AAA\nM\n7.53\n100\nĐã huỷ"), OrderBookScreen.STATUS_CANCELLED)).isTrue();
        assertThat(OrderBookScreen.hasStatus(row("AAA\nM\n7.53\n100\nĐã hủy"), OrderBookScreen.STATUS_CANCELLED)).isTrue();
    }

    @Test(groups = "unit")
    public void marketOrderMatchedByPriceLabel() {
        OrderSpec ato = new OrderSpec("AAA", true, 100, null, "ATO", false);
        assertThat(OrderBookScreen.rowMatches(row("AAA\nM\nATO\n100\nChờ gửi"), ato)).isTrue();
        assertThat(OrderBookScreen.rowMatches(row("AAA\nM\nATC\n100\nChờ gửi"), ato)).isFalse();
    }

    @Test(groups = "unit")
    public void conditionalOrderExpectsWaitingForTrigger() {
        OrderSpec stop = new OrderSpec("AAA", true, 100, new BigDecimal("8.03"), null, true);
        UiNode r = row("AAA\nM\n8.03\n100\nChờ kích hoạt");
        assertThat(OrderBookScreen.placedStatus(stop)).isSameAs(OrderBookScreen.STATUS_CONDITIONAL_PLACED);
        assertThat(OrderBookScreen.rowMatches(r, stop)).isTrue();
        assertThat(OrderBookScreen.hasStatus(r, OrderBookScreen.placedStatus(stop))).isTrue();
        assertThat(OrderBookScreen.hasStatus(r, OrderBookScreen.STATUS_PLACED)).as("not a normal-order status").isFalse();
        assertThat(OrderBookScreen.placedStatus(buyAaa)).isSameAs(OrderBookScreen.STATUS_PLACED);
    }

    @Test(groups = "unit")
    public void realConditionalRowFromUat() {
        // exact row seen in "Sổ lệnh > Điều kiện" on UAT 07/11/2026
        UiNode r = row("AAA\nMUA\nStop Limit\n100\n● Chờ kích hoạt");
        OrderSpec stop = new OrderSpec("AAA", true, 100, null, "Stop Limit", true);
        assertThat(OrderBookScreen.rowMatches(r, stop)).isTrue();
        assertThat(OrderBookScreen.hasStatus(r, OrderBookScreen.placedStatus(stop))).isTrue();
        assertThat(OrderBookScreen.rowMatches(r, new OrderSpec("AAA", true, 100, null, "Trailing Stop", true))).isFalse();
    }
}
