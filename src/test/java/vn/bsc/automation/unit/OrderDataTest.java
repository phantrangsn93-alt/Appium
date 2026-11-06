package vn.bsc.automation.unit;

import org.testng.annotations.Test;
import vn.bsc.automation.core.market.TradingSession;
import vn.bsc.automation.data.OrderActionCase;
import vn.bsc.automation.data.OrderCase;
import vn.bsc.automation.data.PriceSpec;
import vn.bsc.automation.data.SubAccount;

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
            assertThat(SubAccount.typeOf(c.subAccount())).as(c.id() + " subAccount phải là CO_SO | MARGIN | PHAI_SINH").isPresent();
            boolean derivative = "DERIVATIVE".equals(c.exchange());
            assertThat(SubAccount.typeOf(c.subAccount()).get() == SubAccount.PHAI_SINH)
                    .as(c.id() + ": phái sinh <=> tiểu khoản PHAI_SINH").isEqualTo(derivative);
        }
    }

    @Test(groups = "unit")
    public void conditionalRowsHaveTheirFields() {
        for (OrderCase c : OrderCase.load("testdata/orders.csv")) {
            if (!c.price().isBlank()) {
                assertThat(PriceSpec.parse(c.price())).as(c.id() + " price").isNotNull();
            }
            switch (c.orderType()) {
                case "STOP_LIMIT" -> assertThat(PriceSpec.parse(c.triggerPrice())).as(c.id() + " triggerPrice").isNotNull();
                case "OCO" -> {
                    assertThat(PriceSpec.parse(c.price())).as(c.id() + " giá LO").isNotNull();
                    assertThat(PriceSpec.parse(c.triggerPrice())).as(c.id() + " triggerPrice").isNotNull();
                }
                case "TRAILING_STOP" -> {
                    assertThat(new java.math.BigDecimal(c.amplitude())).as(c.id() + " amplitude").isPositive();
                    assertThat(new java.math.BigDecimal(c.priceStep())).as(c.id() + " priceStep").isPositive();
                }
                default -> assertThat(c.isConditional()).isFalse();
            }
        }
    }

    @Test(groups = "unit")
    public void priceSpecSyntax() {
        assertThat(PriceSpec.parse("HINT+0.5").resolve(new java.math.BigDecimal("7.53"))).isEqualByComparingTo("8.03");
        assertThat(PriceSpec.parse("hint - 0.5").resolve(new java.math.BigDecimal("7.53"))).isEqualByComparingTo("7.03");
        assertThat(PriceSpec.parse("REF").resolve(new java.math.BigDecimal("7.53"))).isEqualByComparingTo("7.53");
        assertThat(PriceSpec.parse("7.08").resolve(null)).isEqualByComparingTo("7.08");
        assertThat(PriceSpec.parse("")).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PriceSpec.parse("ABC"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test(groups = "unit")
    public void modifyActionChangesQuantity() {
        OrderActionCase m = OrderActionCase.load("testdata/order_actions.csv").stream()
                .filter(a -> a.action() == OrderActionCase.Action.MODIFY).findFirst().orElseThrow();
        assertThat(m.newQuantity()).as("Excel: Sửa Khối lượng 200").isEqualTo(200);
    }

    @Test(groups = "unit")
    public void subAccountTypeAliases() {
        assertThat(SubAccount.typeOf("CO_SO")).contains(SubAccount.CO_SO);
        assertThat(SubAccount.typeOf("Cơ sở")).contains(SubAccount.CO_SO);
        assertThat(SubAccount.typeOf("margin")).contains(SubAccount.MARGIN);
        assertThat(SubAccount.typeOf("Phái sinh")).contains(SubAccount.PHAI_SINH);
        assertThat(SubAccount.typeOf("PHAI_SINH")).contains(SubAccount.PHAI_SINH);
        assertThat(SubAccount.typeOf("Thường")).isEmpty();
        assertThat(SubAccount.resolve("Thường")).as("literal name kept").isEqualTo("Thường");
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
