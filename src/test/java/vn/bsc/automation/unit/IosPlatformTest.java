package vn.bsc.automation.unit;

import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import vn.bsc.automation.core.utils.Locators;
import vn.bsc.automation.core.utils.UiNode;

import java.util.List;
import java.util.regex.Pattern;

/** iOS locator generation and page-source parsing - no device needed. */
public class IosPlatformTest {

    private String previous;

    @BeforeClass
    public void ios() {
        previous = System.getProperty("platform.name");
        System.setProperty("platform.name", "iOS");
    }

    @AfterClass(alwaysRun = true)
    public void restore() {
        if (previous == null) {
            System.clearProperty("platform.name");
        } else {
            System.setProperty("platform.name", previous);
        }
    }

    private static String predicate(By by) {
        String s = by.toString();
        Assert.assertTrue(s.contains("iOSNsPredicateString") || s.toLowerCase().contains("predicate"), s);
        return s.substring(s.indexOf(':') + 1).trim();
    }

    @Test
    public void plainLabelIsExactPredicate() {
        Assert.assertEquals(predicate(Locators.a11y("Menu")), "label == \"Menu\"");
        Assert.assertEquals(predicate(Locators.descStartsWith("MUA")), "label BEGINSWITH \"MUA\"");
    }

    @Test
    public void classRoleTranslatesToXcuiType() {
        String p = predicate(Locators.classWithDesc("android.widget.Button", "Đăng nhập"));
        Assert.assertTrue(p.startsWith("type IN {\"XCUIElementTypeButton\"} AND label MATCHES \"(?s)"), p);
        // the ICU regex accepts both NFC and NFD forms
        String regex = p.substring(p.indexOf("MATCHES \"") + 9, p.length() - 1);
        Pattern re = Pattern.compile(regex);
        Assert.assertTrue(re.matcher(java.text.Normalizer.normalize("Đăng nhập", java.text.Normalizer.Form.NFD)).matches());
        Assert.assertTrue(re.matcher("Đăng nhập").matches());
    }

    @Test
    public void viewRoleHasNoTypeFilter() {
        String p = predicate(Locators.classWithDescContaining("android.widget.ImageView", "Thông báo"));
        Assert.assertTrue(p.startsWith("label MATCHES"), p);
        Assert.assertTrue(Pattern.compile(p.substring(15, p.length() - 1)).matcher("6\nThông báo").matches());
    }

    @Test
    public void androidXpathIsTranslated() {
        Assert.assertEquals(Locators.xpath("//android.widget.EditText[@password='true']").toString(),
                "By.xpath: //XCUIElementTypeSecureTextField");
        String x = Locators.xpath("//android.view.View[starts-with(@content-desc,'AAA\n') and @clickable='true']").toString();
        Assert.assertTrue(x.contains("//*[starts-with(@label,'AAA\n') and (@accessible='true' and not(self::XCUIElementTypeStaticText))]"), x);
    }

    @Test
    public void iosPageSourceIsParsedAsAndroidRoles() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><XCUIElementTypeApplication type=\"XCUIElementTypeApplication\""
                + " name=\"BSC\" label=\"BSC\" enabled=\"true\" visible=\"true\" accessible=\"false\" x=\"0\" y=\"0\" width=\"390\" height=\"844\">"
                + "<XCUIElementTypeButton type=\"XCUIElementTypeButton\" label=\"Xác nhận\" enabled=\"true\" visible=\"true\""
                + " accessible=\"true\" x=\"16\" y=\"780\" width=\"358\" height=\"48\"/>"
                + "<XCUIElementTypeStaticText type=\"XCUIElementTypeStaticText\" label=\"Sức mua\n0\" enabled=\"true\" visible=\"true\""
                + " accessible=\"true\" x=\"16\" y=\"100\" width=\"200\" height=\"40\"/>"
                + "<XCUIElementTypeSecureTextField type=\"XCUIElementTypeSecureTextField\" value=\"••••••\" enabled=\"true\""
                + " visible=\"true\" accessible=\"true\" x=\"16\" y=\"300\" width=\"358\" height=\"44\"/>"
                + "<XCUIElementTypeOther type=\"XCUIElementTypeOther\" label=\"off\" visible=\"false\" accessible=\"true\""
                + " x=\"0\" y=\"2000\" width=\"10\" height=\"10\"/></XCUIElementTypeApplication>";
        List<UiNode> nodes = UiNode.snapshot(xml);
        Assert.assertEquals(nodes.size(), 4, nodes.toString()); // off-screen node skipped
        UiNode btn = nodes.get(1);
        Assert.assertEquals(btn.className(), "android.widget.Button");
        Assert.assertEquals(btn.desc(), "Xác nhận");
        Assert.assertTrue(btn.clickable());
        Assert.assertTrue(nodes.get(2).clickable(), "accessible static text counts as clickable (Flutter rows)");
        Assert.assertEquals(nodes.get(3).className(), "android.widget.EditText");
        Assert.assertEquals(nodes.get(3).text().length(), 6);
    }
}
