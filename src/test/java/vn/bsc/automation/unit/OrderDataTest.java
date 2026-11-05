package vn.bsc.automation.unit;

import org.testng.annotations.Test;
import vn.bsc.automation.core.market.TradingSession;
import vn.bsc.automation.data.OrderActionCase;
import vn.bsc.automation.data.OrderCase;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests (no device): trading-session rules and the order test-data files. */
public class OrderDataTest {

    private static ZonedDateTime at(String isoLocal) { // Thursday 2026-11-05 = trading day
        return ZonedDateTime.parse(isoLocal + "+07:00[Asia/Ho_Chi_Minh]");
    }

    @Test(groups = "unit")
    public void sessionWindowsPerExchange() {
        assertThat(TradingSession.isOpen("HOSE", List.of("ATO"), at("2026-11-05T09:05"))).isTrue();
        assertThat(TradingSession.isOpen("HOSE", List.of("ATO"), at("2026-11-05T09:20"))).isFalse();
        assertThat(TradingSession.isOpen("HOSE", List.of("CONTINUOUS"), at("2026-11-05T10:00"))).isTrue();
        assertThat(TradingSession.isOpen("HOSE", List.of("CONTINUOUS"), at("2026-11-05T12:00"))).isFalse(); // lunch
        assertThat(TradingSession.isOpen("HOSE", List.of("ATC"), at("2026-11-05T14:35"))).isTrue();
        assertThat(TradingSession.isOpen("HNX", List.of("PLO"), at("2026-11-05T14:50"))).isTrue();
        assertThat(TradingSession.isOpen("HOSE", List.of("TRADING"), at("2026-11-05T21:40"))).isFalse();
        assertThat(TradingSession.isOpen("HOSE", List.of("CONTINUOUS"), at("2026-11-07T10:00"))).isFalse(); // Saturday
        assertThat(TradingSession.isOpen("HOSE", List.of("ANY"), at("2026-11-07T23:00"))).isTrue();
    }

    @Test(groups = "unit")
    public void ordersCsvIsValid() {
        List<OrderCase> rows = OrderCase.load("testdata/orders.csv");
        assertThat(rows).isNotEmpty();
        assertThat(rows.stream().map(OrderCase::id).collect(Collectors.toSet())).as("unique ids").hasSize(rows.size());
        Set<String> marketHose = Set.of("ATO", "ATC", "MTL");
        Set<String> marketHnx = Set.of("ATC", "MTL", "MOK", "MAK", "PLO");
        for (OrderCase c : rows) {
            if (c.isOddLot()) {
                assertThat(c.quantity()).as(c.id() + " lô lẻ < 100").isBetween(1L, 99L);
            }
            if (c.isMarketType() && "HOSE".equals(c.exchange())) {
                assertThat(marketHose).as(c.id()).contains(c.orderType());
            }
            if (c.isMarketType() && "HNX".equals(c.exchange())) {
                assertThat(marketHnx).as(c.id()).contains(c.orderType());
            }
            if ("DERIVATIVE".equals(c.exchange())) {
                assertThat(c.subAccount()).as(c.id() + " phái sinh dùng tiểu khoản phái sinh").isEqualTo("Phaisinh");
            }
        }
    }

    @Test(groups = "unit")
    public void actionsReferenceExistingOrders() {
        Set<String> ids = OrderCase.load("testdata/orders.csv").stream().map(OrderCase::id).collect(Collectors.toSet());
        for (OrderActionCase a : OrderActionCase.load("testdata/order_actions.csv")) {
            if (a.action() != OrderActionCase.Action.CANCEL_ALL) {
                assertThat(ids).as(a.id() + " targetId").contains(a.targetId());
            }
        }
    }
}
