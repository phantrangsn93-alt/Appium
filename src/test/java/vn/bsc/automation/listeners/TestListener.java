package vn.bsc.automation.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;
import vn.bsc.automation.core.config.ConfigReader;
import vn.bsc.automation.core.utils.AllureUtils;

/** Logging + evidence collection (screenshot, page source, video) for the report. */
public class TestListener implements ITestListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        LOG.info("▶ START  {}.{}", result.getTestClass().getRealClass().getSimpleName(), result.getMethod().getMethodName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        LOG.info("✔ PASS   {}", result.getMethod().getMethodName());
        if (ConfigReader.getBool("screenshot.on.pass", false)) {
            AllureUtils.attachScreenshot("Screenshot - pass");
        }
        AllureUtils.stopRecording(false, null);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        LOG.error("✘ FAIL   {} - {}", result.getMethod().getMethodName(),
                result.getThrowable() == null ? "" : result.getThrowable().getMessage());
        collectEvidence("failure");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        LOG.warn("↷ SKIP   {} - {}", result.getMethod().getMethodName(),
                result.getThrowable() == null ? "" : result.getThrowable().getMessage());
        // A retried test is reported as skipped: keep its evidence too.
        if (result.wasRetried()) {
            collectEvidence("retried attempt");
        } else {
            AllureUtils.stopRecording(false, null);
        }
    }

    private void collectEvidence(String label) {
        AllureUtils.attachScreenshot("Screenshot - " + label);
        AllureUtils.attachPageSource("Page source - " + label);
        AllureUtils.stopRecording(ConfigReader.getBool("video.on.failure", true), "Video - " + label);
    }
}
