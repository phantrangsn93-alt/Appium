package vn.bsc.automation.data;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One row of {@code testdata/order_actions.csv} - actions on orders placed by {@link OrderCase} rows.
 *
 * <pre>
 * id          unique key
 * enabled     true/false
 * excelNo     row number in the Excel scenario file
 * action      MODIFY | CANCEL | CANCEL_ALL
 * targetId    id of the order in orders.csv (MODIFY / CANCEL). Empty for CANCEL_ALL.
 * priceSteps  MODIFY only: number of taps on the price "+" (negative = "-")
 * </pre>
 */
public record OrderActionCase(String id, boolean enabled, String excelNo, String description, Action action,
                              String targetId, int priceSteps) {

    public enum Action { MODIFY, CANCEL, CANCEL_ALL }

    public static OrderActionCase from(Map<String, String> r) {
        String steps = r.getOrDefault("priceSteps", "");
        return new OrderActionCase(
                r.get("id"),
                !"false".equalsIgnoreCase(r.getOrDefault("enabled", "true")),
                r.getOrDefault("excelNo", ""),
                r.getOrDefault("description", ""),
                Action.valueOf(r.get("action").toUpperCase(Locale.ROOT)),
                r.getOrDefault("targetId", ""),
                steps.isBlank() ? 0 : Integer.parseInt(steps));
    }

    public static List<OrderActionCase> load(String resource) {
        return CsvReader.read(resource).stream().map(OrderActionCase::from).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return id + " [" + action + (targetId.isBlank() ? "" : " " + targetId) + "]";
    }
}
