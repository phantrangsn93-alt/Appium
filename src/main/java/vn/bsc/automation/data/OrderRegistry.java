package vn.bsc.automation.data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orders that THIS test run has placed successfully. Used to:
 * <ul>
 *   <li>find the order to modify / cancel (order_actions.csv references orders.csv ids)</li>
 *   <li>guarantee "Huỷ tất cả" only touches test orders</li>
 *   <li>clean up (cancel) anything still pending at the end of the run</li>
 * </ul>
 */
public final class OrderRegistry {

    public record PlacedOrder(OrderCase source, String subAccount, BigDecimal price, long quantity,
                              LocalDateTime placedAt, String appMessage) {
    }

    private static final Map<String, PlacedOrder> ORDERS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> CLOSED = new ConcurrentHashMap<>();

    private OrderRegistry() {
    }

    public static void register(PlacedOrder o) {
        ORDERS.put(o.source().id(), o);
        CLOSED.remove(o.source().id());
    }

    public static Optional<PlacedOrder> get(String id) {
        return Optional.ofNullable(ORDERS.get(id));
    }

    /** Marks an order as cancelled (no longer pending). */
    public static void markClosed(String id) {
        CLOSED.put(id, true);
    }

    public static void updatePrice(String id, BigDecimal newPrice) {
        PlacedOrder o = ORDERS.get(id);
        if (o != null) {
            ORDERS.put(id, new PlacedOrder(o.source(), o.subAccount(), newPrice, o.quantity(), o.placedAt(), o.appMessage()));
        }
    }

    public static List<PlacedOrder> pending() {
        List<PlacedOrder> out = new ArrayList<>();
        ORDERS.forEach((id, o) -> {
            if (!CLOSED.containsKey(id)) {
                out.add(o);
            }
        });
        return Collections.unmodifiableList(out);
    }
}
