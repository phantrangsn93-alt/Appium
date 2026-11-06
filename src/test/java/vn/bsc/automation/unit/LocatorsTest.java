package vn.bsc.automation.unit;

import org.testng.annotations.Test;
import vn.bsc.automation.core.utils.Locators;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test (no device): regex built by Locators matches every encoding seen in the app. */
public class LocatorsTest {

    private static boolean matches(String label, String appText) {
        String sel = Locators.a11y(label).toString();
        String regex = sel.substring(sel.indexOf("descriptionMatches(\"") + 20, sel.lastIndexOf("\")"));
        return Pattern.compile(regex).matcher(appText).matches();
    }

    @Test(groups = "unit")
    public void matchesNfcNfdAndPartiallyComposed() {
        assertThat(matches("Quên mật khẩu?", "Quên mật khẩu?")).isTrue();                          // NFC
        assertThat(matches("Quên mật khẩu?", "Quên mật khẩu?")).isTrue(); // seen in app
        assertThat(matches("BÁN", "BÁN")).isTrue();                                            // NFD
        assertThat(matches("Đóng", "Đóng")).isTrue();
        assertThat(matches("Khối lượng", "Khối lượng")).isTrue();     // full NFD
        assertThat(matches("Đóng", "Dong")).isFalse();
    }

    @Test(groups = "unit")
    public void descMatchesAnyAcceptsEachLabel() {
        String sel = Locators.descMatchesAny("Bỏ qua", "Đóng", "Có", "Không").toString();
        Pattern p = Pattern.compile(sel.substring(sel.indexOf("descriptionMatches(\"") + 20, sel.lastIndexOf("\")")));
        assertThat(p.matcher("\u0110o\u0301ng").matches()).isTrue();
        assertThat(p.matcher("Không").matches()).isTrue();
        assertThat(p.matcher("Đóng cửa").matches()).isFalse();
    }

    @Test(groups = "unit")
    public void fieldMatchesExactLabelOnly() {
        String sel = Locators.field("Tiểu khoản").toString();
        Pattern p = Pattern.compile(sel.substring(sel.indexOf("descriptionMatches(\"") + 20, sel.lastIndexOf("\")")));
        // value as seen in the app: zero-width spaces between characters
        assertThat(p.matcher("Tiểu khoản\n​T​h​ư​ờ​n​g").matches()).isTrue();
        assertThat(p.matcher("Tiểu khoản chuyển\nThường - 0101042221").matches()).as("other label").isFalse();
        assertThat(p.matcher("Tiểu khoản").matches()).as("label without value").isFalse();
    }

    @Test(groups = "unit")
    public void nfcRemovesZeroWidthCharacters() {
        assertThat(Locators.nfc("​T​h​ư​ờ​n​g​ ​-​ 0101"))
                .isEqualTo("Thường - 0101");
    }
}
