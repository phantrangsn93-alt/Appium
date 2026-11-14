package vn.bsc.automation.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Menu money / securities transactions that succeeded in this run, with the receiving sub-account of internal
 * transfers, so the post-transaction checks can find them in the statements of both sub-accounts.
 */
public final class TransactionLog {

    /** @param receiver receiving sub-account ("MR90 BSC ĐB - 0101098819"), null when not an internal transfer */
    public record Entry(MenuTransactionCase c, String receiver) {
    }

    private static final Map<String, Entry> DONE = Collections.synchronizedMap(new LinkedHashMap<>());

    private TransactionLog() {
    }

    public static void succeeded(MenuTransactionCase c, String receiver) {
        DONE.put(c.id(), new Entry(c, receiver));
    }

    public static Map<String, Entry> all() {
        synchronized (DONE) {
            return new LinkedHashMap<>(DONE);
        }
    }
}
