package vn.bsc.automation.core.driver;

import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.bsc.automation.core.config.AppConfig;
import vn.bsc.automation.core.config.ConfigReader;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Starts a local Appium server when none is reachable at {@code appium.server.url}.
 * The server is always bound to 127.0.0.1 (never exposed to the network).
 */
public final class AppiumServerManager {

    private static final Logger LOG = LoggerFactory.getLogger(AppiumServerManager.class);
    private static AppiumDriverLocalService service;

    private AppiumServerManager() {
    }

    public static synchronized URL ensureServer() {
        URL url = toUrl(AppConfig.appiumServerUrl());
        if (isRunning(url)) {
            LOG.info("Using existing Appium server at {}", url);
            return url;
        }
        if (!AppConfig.appiumAutostart()) {
            throw new IllegalStateException("Appium server not reachable at " + url
                    + " and appium.server.autostart=false");
        }
        Map<String, String> env = new HashMap<>(System.getenv());
        String androidHome = ConfigReader.get("android.home", System.getenv("ANDROID_HOME"));
        if (androidHome == null) {
            androidHome = System.getProperty("user.home") + "/Library/Android/sdk";
        }
        env.put("ANDROID_HOME", androidHome);
        env.putIfAbsent("ANDROID_SDK_ROOT", androidHome);

        service = new AppiumServiceBuilder()
                .withIPAddress("127.0.0.1")
                .usingPort(url.getPort() > 0 ? url.getPort() : 4723)
                .withEnvironment(env)
                .withArgument(GeneralServerFlag.LOG_LEVEL, "warn")
                .withArgument(GeneralServerFlag.SESSION_OVERRIDE)
                .build();
        service.start();
        // Never leave an orphan server behind (it would answer /status but fail new sessions next run)
        Runtime.getRuntime().addShutdownHook(new Thread(AppiumServerManager::stopServer, "appium-shutdown"));
        LOG.info("Started local Appium server at {}", service.getUrl());
        return service.getUrl();
    }

    public static synchronized void stopServer() {
        if (service != null && service.isRunning()) {
            service.stop();
            LOG.info("Stopped local Appium server");
        }
    }

    private static boolean isRunning(URL base) {
        try {
            URL status = URI.create(base.toString().replaceAll("/$", "") + "/status").toURL();
            HttpURLConnection c = (HttpURLConnection) status.openConnection();
            c.setConnectTimeout(2000);
            c.setReadTimeout(3000);
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static URL toUrl(String s) {
        try {
            return URI.create(s).toURL();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid appium.server.url: " + s, e);
        }
    }
}
