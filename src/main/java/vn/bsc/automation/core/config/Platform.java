package vn.bsc.automation.core.config;

/**
 * Platform switches in one place. Page objects describe elements with Android "roles"
 * (android.widget.Button / EditText / ImageView / View); {@link vn.bsc.automation.core.utils.Locators}
 * and {@link vn.bsc.automation.core.utils.UiNode} translate them for iOS, so screens stay platform-neutral.
 */
public final class Platform {

    private Platform() {
    }

    public static boolean ios() {
        return AppConfig.isIOS();
    }

    public static boolean android() {
        return !AppConfig.isIOS();
    }

    /** Attribute holding the Flutter Semantics label: content-desc (Android) / label (iOS). */
    public static String labelAttr() {
        return ios() ? "label" : "content-desc";
    }

    /** Checked state of radios/checkboxes: Flutter maps it to the selected trait on iOS. */
    public static String checkedAttr() {
        return ios() ? "selected" : "checked";
    }

    /** XCUIElementType(s) for an Android class name used as a role; null = any element type. */
    public static String[] iosTypes(String androidClass) {
        return switch (androidClass) {
            case "android.widget.Button" -> new String[]{"XCUIElementTypeButton"};
            case "android.widget.EditText" -> new String[]{"XCUIElementTypeTextField", "XCUIElementTypeSecureTextField"};
            case "android.widget.RadioButton", "android.widget.CheckBox", "android.widget.Switch" ->
                    new String[]{"XCUIElementTypeButton", "XCUIElementTypeSwitch", "XCUIElementTypeOther"};
            // Flutter maps tappable images / generic views to Image, Other, StaticText or Button depending on traits
            default -> null;
        };
    }

    /** Android role of an XCUIElementType (inverse of {@link #iosTypes}), used for page-source snapshots. */
    public static String androidRole(String iosType) {
        return switch (iosType) {
            case "XCUIElementTypeButton" -> "android.widget.Button";
            case "XCUIElementTypeTextField", "XCUIElementTypeSecureTextField", "XCUIElementTypeSearchField" ->
                    "android.widget.EditText";
            case "XCUIElementTypeImage" -> "android.widget.ImageView";
            case "XCUIElementTypeSwitch" -> "android.widget.Switch";
            default -> "android.view.View";
        };
    }
}
