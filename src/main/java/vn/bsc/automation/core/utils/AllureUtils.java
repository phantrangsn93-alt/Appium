package vn.bsc.automation.core.utils;

import io.appium.java_client.android.AndroidDriver;
import io.qameta.allure.Allure;
import org.openqa.selenium.OutputType;
import vn.bsc.automation.core.driver.DriverManager;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Attachments for Allure report: screenshot, page source, video, text. */
public final class AllureUtils {

    private AllureUtils() {
    }

    public static void attachScreenshot(String name) {
        if (!DriverManager.hasDriver()) {
            return;
        }
        try {
            byte[] png = DriverManager.getDriver().getScreenshotAs(OutputType.BYTES);
            Allure.addAttachment(name, "image/png", new ByteArrayInputStream(png), ".png");
        } catch (Exception ignored) {
            // never fail a test because of reporting
        }
    }

    public static void attachPageSource(String name) {
        if (!DriverManager.hasDriver()) {
            return;
        }
        try {
            String xml = DriverManager.getDriver().getPageSource();
            Allure.addAttachment(name, "text/xml", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), ".xml");
        } catch (Exception ignored) {
        }
    }

    public static void startRecording() {
        if (!DriverManager.hasDriver()) {
            return;
        }
        try {
            DriverManager.getDriver().startRecordingScreen();
        } catch (Exception ignored) {
        }
    }

    /** Stops recording; attaches the video only when {@code attach} is true (e.g. on failure). */
    public static void stopRecording(boolean attach, String name) {
        if (!DriverManager.hasDriver()) {
            return;
        }
        try {
            AndroidDriver d = DriverManager.getDriver();
            String base64 = d.stopRecordingScreen();
            if (attach && base64 != null && !base64.isEmpty()) {
                Allure.addAttachment(name, "video/mp4", new ByteArrayInputStream(Base64.getMimeDecoder().decode(base64)), ".mp4");
            }
        } catch (Exception ignored) {
        }
    }

    public static void attachText(String name, String text) {
        Allure.addAttachment(name, "text/plain", text == null ? "" : text);
    }
}
