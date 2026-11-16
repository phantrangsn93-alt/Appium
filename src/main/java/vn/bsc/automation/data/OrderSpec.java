package vn.bsc.automation.data;

import vn.bsc.automation.core.utils.UiNode;
import vn.bsc.automation.data.OrderRegistry.PlacedOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** What identifies an order in the book. {@code price} null = not checked (market / trailing). */
public record OrderSpec(String symbol, boolean buy, long quantity, BigDecimal price, String priceLabel,
                        boolean conditional) {

    /**
     * Normal orders are matched on their price (or market price label); conditional orders are listed with
     * their TYPE instead of a price ("AAA | MUA | Stop Limit | 100 | ● Chờ kích hoạt").
     */
    public static OrderSpec of(PlacedOrder o) {
        boolean buy = o.source().side() == OrderCase.Side.BUY;
        if (o.source().inConditionalBook()) {
            return new OrderSpec(o.source().symbol(), buy, o.quantity(), null, conditionalLabel(o.source().orderType()), true);
        }
        return new OrderSpec(o.source().symbol(), buy, o.quantity(), o.price(),
                o.source().isMarketType() ? o.source().orderType() : null, false);
    }

    public static String conditionalLabel(String type) {
        return switch (type) {
            case "STOP_LIMIT" -> "Stop Limit";
            case "TRAILING_STOP" -> "Trailing Stop";
            case "OCO" -> "OCO";
            case "TPSL" -> "TP/SL"; // confirmed on iOS production 14/11/2026: "BSI | BÁN | TP/SL | 100"
            case "LO" -> "Limit"; // LO-GTC row in the "Điều kiện" tab: "AAA | MUA | Limit | 100 | ● Chờ kích hoạt"
            default -> type;
        };
    }

    public OrderSpec withPriceAndQty(BigDecimal p, long q) {
        return new OrderSpec(symbol, buy, q, p, priceLabel, conditional);
    }

    @Override
    public String toString() {
        return (buy ? "MUA " : "BÁN ") + symbol + " x" + quantity + (price != null ? " @" + price.toPlainString() : "")
                + (priceLabel != null ? " " + priceLabel : "");
    }

    static String qtyText(long qty) {
        return NumberFormat.getIntegerInstance(Locale.US).format(qty); // 1,000
    }

    /** Lines of the row (NFC, zero-width chars removed by UiNode/Locators.nfc). */
    private static List<String> lines(UiNode row) {
        return Arrays.stream(row.desc().split("\n")).map(String::trim).collect(Collectors.toList());
    }

    /** Row matches symbol, side (M/B), quantity and - when given - price or market price label. */
    public static boolean rowMatches(UiNode row, OrderSpec o) {
        List<String> l = lines(row);
        if (l.isEmpty() || !l.get(0).equals(o.symbol())) {
            return false;
        }
        boolean side = o.buy() ? l.stream().anyMatch(x -> x.equals("M") || x.equalsIgnoreCase("Mua"))
                : l.stream().anyMatch(x -> x.equals("B") || x.equalsIgnoreCase("Bán"));
        boolean qty = l.contains(qtyText(o.quantity())) || l.contains(String.valueOf(o.quantity()));
        boolean price = true;
        if (o.price() != null || o.priceLabel() != null) {
            // ATO/ATC rows show either the type label or the price (production: "AAA | MUA | 7.46 | 100")
            price = (o.priceLabel() != null && l.contains(o.priceLabel()))
                    || (o.price() != null && priceTexts(o.price()).stream().anyMatch(l::contains));
        }
        return side && qty && price;
    }

    /** Price as it may be displayed: 7.53 / 7.5 / 7,530 (x1000) / 1,867.40. */
    public static List<String> priceTexts(BigDecimal p) {
        DecimalFormat grouped2 = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        List<String> out = new ArrayList<>(List.of(p.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                p.stripTrailingZeros().toPlainString(),
                grouped2.format(p),
                NumberFormat.getIntegerInstance(Locale.US).format(p.multiply(BigDecimal.valueOf(1000)))));
        if (p.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            // price taken from a message in VND ("giá 7,460.00") while the book shows x1000 ("7.46")
            BigDecimal k = p.divide(BigDecimal.valueOf(1000));
            out.add(k.setScale(2, RoundingMode.HALF_UP).toPlainString());
            out.add(k.stripTrailingZeros().toPlainString());
        }
        return out;
    }

    /** Status line looks like "● Chờ kích hoạt" (dot prefix): match by find(), not on the whole line. */
    public static boolean hasStatus(UiNode row, Pattern status) {
        return lines(row).stream().anyMatch(x -> status.matcher(x).find());
    }

    /** Text of the row (for reports). */
    public static String text(UiNode row) {
        return row.desc().replace("\n", " | ");
    }

}
