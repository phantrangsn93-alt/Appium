package vn.bsc.automation.data;

import vn.bsc.automation.core.config.ConfigReader;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * Sub-account TYPE used in test data ({@code orders.csv}, {@code order.json}) instead of the account-specific name.
 * The real name prefix shown in the app is configured per environment / account:
 * <pre>
 *   subaccount.coso      (env SUBACCOUNT_COSO)      e.g. Thường
 *   subaccount.margin    (env SUBACCOUNT_MARGIN)    e.g. MR10+ BSC
 *   subaccount.phaisinh  (env SUBACCOUNT_PHAISINH)  e.g. Phaisinh
 * </pre>
 * Accepted spellings in data files: CO_SO / COSO / "Cơ sở", MARGIN, PHAI_SINH / PHAISINH / "Phái sinh".
 * Any other value is used as a literal sub-account name (backward compatible).
 */
public enum SubAccount {
    CO_SO("subaccount.coso"),
    MARGIN("subaccount.margin"),
    PHAI_SINH("subaccount.phaisinh");

    private final String configKey;

    SubAccount(String configKey) {
        this.configKey = configKey;
    }

    /** Real sub-account name prefix for this type (fails clearly when not configured). */
    public String accountName() {
        return ConfigReader.getRequired(configKey);
    }

    /** Type written in a data file, or empty when the value is a literal sub-account name. */
    public static Optional<SubAccount> typeOf(String spec) {
        if (spec == null) {
            return Optional.empty();
        }
        String key = Normalizer.normalize(spec, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        return switch (key) {
            case "COSO" -> Optional.of(CO_SO);
            case "MARGIN" -> Optional.of(MARGIN);
            case "PHAISINH" -> Optional.of(PHAI_SINH);
            default -> Optional.empty();
        };
    }

    /** Type -> configured name; literal name -> unchanged. */
    public static String resolve(String spec) {
        return typeOf(spec).map(SubAccount::accountName).orElse(spec);
    }
}
