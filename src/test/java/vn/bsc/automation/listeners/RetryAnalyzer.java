package vn.bsc.automation.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import vn.bsc.automation.core.config.AppConfig;

/**
 * Retries a failed test up to {@code retry.max.count} times (default 1).
 * Retries are logged and visible in the report - they reduce noise, they must not hide real bugs.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(RetryAnalyzer.class);
    private int attempt = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (attempt < AppConfig.retryMaxCount()) {
            attempt++;
            LOG.warn("Retrying {} (attempt {}/{})", result.getMethod().getMethodName(), attempt, AppConfig.retryMaxCount());
            return true;
        }
        return false;
    }
}
