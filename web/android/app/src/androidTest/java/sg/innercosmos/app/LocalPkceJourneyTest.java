package sg.innercosmos.app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.hamcrest.Matchers.containsString;
import static androidx.test.espresso.web.sugar.Web.onWebView;
import static androidx.test.espresso.web.webdriver.DriverAtoms.findElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.getText;
import static androidx.test.espresso.web.webdriver.DriverAtoms.webClick;
import static androidx.test.espresso.web.assertion.WebViewAssertions.webMatches;

import android.content.Context;
import android.Manifest;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;
import androidx.test.espresso.web.webdriver.Locator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Local-only visible-device proof for Authorization Code + PKCE. This test never reads browser
 * storage, callback URLs, tokens, or app secure storage; it interacts only with visible controls.
 */
@RunWith(AndroidJUnit4.class)
public class LocalPkceJourneyTest {
    private static final long SHORT = 15_000;
    private static final long JOURNEY = 45_000;

    private ActivityScenario<MainActivity> activity;

    @Test
    public void systemBrowserPkceReturnsToAuthenticatedAurora() throws Exception {
        // ActivityScenarioRule's automatic close is unstable after an Android notification
        // re-enters an existing Activity (AndroidX may observe a null teardown state). The
        // instrumentation process owns cleanup, while the journey explicitly owns launch.
        activity = ActivityScenario.launch(MainActivity.class);
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        // A deterministic device proof must exercise credential entry, not inherit a previous
        // Keycloak SSO cookie whose immediate callback races the freshly installed WebView.
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("pm clear com.android.chrome").close();
        UiObject2 continueButton = device.wait(Until.findObject(
                By.text("Continue with your identity provider")), SHORT);
        assertNotNull("Native PKCE gate must be visible", continueButton);
        UiObject nativeLogin = device.findObject(new UiSelector().text("Continue with your identity provider"));
        assertTrue("Visible PKCE button must accept an accessibility click",
                nativeLogin.clickAndWaitForNewWindow(20_000));

        dismissChromeFirstRun(device);

        UiObject username = device.findObject(new UiSelector().resourceId("username"));
        UiObject password = device.findObject(new UiSelector().resourceId("password"));
        // Chrome may retain the local Keycloak SSO cookie across app reinstall. In that case the
        // authorization endpoint immediately returns through the custom protocol and no credential
        // form is expected. Accept only the authenticated shell as that alternate branch.
        boolean alreadyReturned = device.wait(Until.hasObject(By.text("AURORA, WITH YOU")), 8_000);
        if (!alreadyReturned && !username.waitForExists(20_000)) {
            // The custom-protocol callback can arrive while the username wait is in progress.
            // Re-check the authenticated shell before diagnosing a missing browser form.
            alreadyReturned = device.hasObject(By.text("AURORA, WITH YOU"));
        }
        if (!alreadyReturned && !username.exists() && target.getPackageName().equals(device.getCurrentPackageName())) {
            // A retained Keycloak SSO cookie can return to the Activity before React has finished
            // exchanging and persisting the code. Give that owned callback state its full window.
            alreadyReturned = device.wait(Until.hasObject(By.text("AURORA, WITH YOU")), JOURNEY);
        }
        if (!alreadyReturned && !username.exists()) {
            UiObject diagnostic = device.findObject(new UiSelector().textStartsWith("Sign-in error:"));
            UiObject progress = device.findObject(new UiSelector().textContains("secure sign-in"));
            fail("Keycloak username field was not visible; safe app diagnostic: "
                    + (diagnostic.exists() ? diagnostic.getText() : "none")
                    + "; progress: " + (progress.exists() ? progress.getText() : "none")
                    + "; foreground package: " + device.getCurrentPackageName());
        }
        if (!alreadyReturned) {
            assertTrue("Keycloak password field must be visible in the system browser", password.waitForExists(SHORT));
            username.setText(argument("localUsername", "mobile-demo"));
            password.setText(argument("localPassword", "demo123"));

            UiObject submit = device.findObject(new UiSelector().resourceId("kc-login"));
            if (!submit.exists()) submit = device.findObject(new UiSelector().text("Sign In"));
            assertTrue("Keycloak submit button must be visible", submit.exists());
            submit.click();

            assertTrue("OIDC callback must return to the debug app",
                    waitForOwnedOidcCallback(device, target));
        }
        boolean authenticated = device.wait(Until.hasObject(By.text("AURORA, WITH YOU")), JOURNEY);
        if (!authenticated) {
            UiObject diagnostic = device.findObject(new UiSelector().textStartsWith("Sign-in error:"));
            fail("Authenticated product shell was not visible; safe app diagnostic: "
                    + (diagnostic.exists() ? diagnostic.getText() : "none")
                    + "; foreground package: " + device.getCurrentPackageName());
        }
        assertTrue("The PKCE login gate must disappear",
                device.wait(Until.gone(By.text("Continue with your identity provider")), SHORT));

        grantOwnedRuntimePermissions(target);
        exerciseCoreProductJourney(device);
    }

    private static void grantOwnedRuntimePermissions(Context target) {
        // This journey proves the product response to granted OS capabilities. Permission-denial
        // rendering stays in deterministic component tests; granting here avoids Android 16
        // permission-controller copy/resource drift turning PKCE and notification evidence flaky.
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.grantRuntimePermission(target.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        automation.grantRuntimePermission(target.getPackageName(), Manifest.permission.RECORD_AUDIO);
    }

    private void exerciseCoreProductJourney(UiDevice device) throws Exception {
        // Espresso-Web's webdriver bridge reliably injects ASCII on all emulator IMEs; the
        // localized Chinese rendering is covered by screenshot and browser regression suites.
        String message = "I feel nervous today and want to understand it.";
        onWebView().forceJavascriptEnabled();
        setReactTextarea(message);
        clickWeb(".composer button.send", JOURNEY);
        assertWebText(".message.user p", message, JOURNEY);
        assertWebText(".message.aurora p", "", JOURNEY);

        clickWeb(".mobile-presence .mobile-actions button:first-child", JOURNEY);
        allowSystemPermission(device);
        assertAppResumed(device);

        // Prove one real OS delivery. Postpone/cancel are deterministic hook + Playwright
        // contracts; repeating them here introduces unrelated UI timing into the native gate.
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "cmd appops set " + InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName()
                        + " SCHEDULE_EXACT_ALARM allow").close();
        setReactInput(".return-negotiate input", "1 分钟后");
        clickWeb(".return-negotiate button", JOURNEY);
        assertWebText(".return-card", "1 分钟后", JOURNEY);

        clickWeb(".space-tabs button:nth-child(2)", JOURNEY);
        assertWebText(".product-space:not([hidden])", "记忆", JOURNEY);
        clickWeb(".space-tabs button:first-child", JOURNEY);
        assertWebText(".message.user p", message, JOURNEY);

        clickWeb(".mobile-presence .mobile-actions button:nth-child(2)", JOURNEY);
        allowSystemPermission(device);
        assertAppResumed(device);
        // The settings action performs a short owned getUserMedia probe and releases the stream.
        // Wait for that probe to finish before starting the real recorder to avoid concurrent
        // capture requests on slower emulator audio devices.
        assertWebText(".global-state", "麦克风已准备好", JOURNEY);
        Thread.sleep(1_000);
        clickWeb(".composer button.voice", JOURNEY);
        assertVoiceRecorderStarted();
        clickWeb(".voice-cancel", SHORT);
        waitForWebElementGone(".voice-cancel", SHORT);

        device.pressHome();
        device.openNotification();
        UiObject2 notification = device.wait(Until.findObject(By.text("Aurora")), 90_000);
        assertNotNull("A due WakeIntent must surface as an Android system notification", notification);
        UiObject2 notificationRow = notification;
        while (!notificationRow.isClickable() && notificationRow.getParent() != null) {
            notificationRow = notificationRow.getParent();
        }
        assertTrue("The Aurora notification must expose a clickable system row", notificationRow.isClickable());
        notificationRow.click();
        assertAppResumed(device);
        assertWebText(".message.user p", message, JOURNEY);
    }

    private void setReactTextarea(String value) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        String script = "(() => { const e=document.querySelector('textarea');"
                + "if(!e)return false; const s=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set;"
                + "s.call(e," + JSONObject.quote(value) + ");e.dispatchEvent(new Event('input',{bubbles:true}));return true;})()";
        activity.onActivity(screen -> screen.getBridge().getWebView()
                .evaluateJavascript(script, ignored -> complete.countDown()));
        assertTrue("React textarea update must complete", complete.await(10, TimeUnit.SECONDS));
    }

    private void setReactInput(String selector, String value) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        String script = "(() => { const e=document.querySelector(" + JSONObject.quote(selector) + ");"
                + "if(!e)return false; const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                + "s.call(e," + JSONObject.quote(value) + ");e.dispatchEvent(new Event('input',{bubbles:true}));return true;})()";
        activity.onActivity(screen -> screen.getBridge().getWebView()
                .evaluateJavascript(script, ignored -> complete.countDown()));
        assertTrue("React input update must complete", complete.await(10, TimeUnit.SECONDS));
    }

    private void assertVoiceRecorderStarted() throws Exception {
        long deadline = System.currentTimeMillis() + SHORT;
        String state = "pending";
        do {
            state = evaluateWeb("(() => { const c=document.querySelector('.voice-cancel');"
                    + "if(c)return 'ready';const e=document.querySelector('.voice-error');"
                    + "return e?'error:'+String(e.dataset.error||'unknown'):'pending';})()");
            if (state.contains("ready")) return;
            if (state.contains("error:")) fail("Native recorder startup failed safely: " + state);
            Thread.sleep(500);
        } while (System.currentTimeMillis() < deadline);
        fail("Native recorder did not become ready: " + state);
    }

    private String evaluateWeb(String script) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>("null");
        activity.onActivity(screen -> screen.getBridge().getWebView()
                .evaluateJavascript(script, result -> { value.set(result); complete.countDown(); }));
        assertTrue("Web diagnostic must complete", complete.await(10, TimeUnit.SECONDS));
        return value.get();
    }

    private static void clickWeb(String selector, long timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        Throwable last = null;
        do {
            try {
                onWebView().withElement(findElement(Locator.CSS_SELECTOR, selector)).perform(webClick());
                return;
            } catch (Throwable failure) { last = failure; Thread.sleep(500); }
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Web control was not ready: " + selector, last);
    }

    private static void assertWebText(String selector, String expected, long timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        Throwable last = null;
        do {
            try {
                onWebView().withElement(findElement(Locator.CSS_SELECTOR, selector))
                        .check(webMatches(getText(), containsString(expected)));
                return;
            } catch (Throwable failure) { last = failure; Thread.sleep(500); }
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Web element did not reach expected state: " + selector, last);
    }

    private static void waitForWebElementGone(String selector, long timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        do {
            try {
                onWebView().withElement(findElement(Locator.CSS_SELECTOR, selector)).check(webMatches(getText(), containsString("")));
            } catch (Throwable gone) { return; }
            Thread.sleep(500);
        } while (System.currentTimeMillis() < deadline);
        fail("Web element remained visible after its owned operation: " + selector);
    }

    private static void allowSystemPermission(UiDevice device) throws Exception {
        // Permission-controller resource IDs are stable across locale changes and cover the
        // notification, microphone and one-time variants used by current Android images.
        for (String id : new String[]{
                "com.android.permissioncontroller:id/permission_allow_button",
                "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
                "com.android.permissioncontroller:id/permission_allow_one_time_button"
        }) {
            UiObject button = device.findObject(new UiSelector().resourceId(id));
            if (button.waitForExists(2_000)) {
                button.click();
                device.waitForIdle(1_000);
                return;
            }
        }
        // A permission granted by an earlier journey has no system surface and is valid.
        if (!"com.google.android.permissioncontroller".equals(device.getCurrentPackageName())) return;
        fail("Android permission controller was visible but no allow action was available");
    }

    private static void assertAppResumed(UiDevice device) {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("The product Activity must resume after the owned permission decision",
                device.wait(Until.hasObject(By.pkg(target.getPackageName())), SHORT));
    }

    private static void dismissChromeFirstRun(UiDevice device) throws Exception {
        // A clean CI/AVD image may show Chrome's own first-run surfaces before navigating to
        // Keycloak. These are browser setup controls, not application credentials.
        String[] ids = {
                "com.android.chrome:id/signin_fre_dismiss_button",
                "com.android.chrome:id/terms_accept",
                "com.android.chrome:id/negative_button"
        };
        String[] labels = {
                "Use without an account", "Accept & continue", "Agree & continue", "No thanks",
                "Continue without an account"
        };
        long deadline = System.currentTimeMillis() + SHORT;
        while (System.currentTimeMillis() < deadline) {
            if (device.findObject(new UiSelector().resourceId("username")).exists()) return;
            // Browser startup is asynchronous; the first iterations may still observe the app.
            if (!"com.android.chrome".equals(device.getCurrentPackageName())) {
                Thread.sleep(500);
                continue;
            }
            boolean clicked = false;
            for (String id : ids) {
                UiObject control = device.findObject(new UiSelector().resourceId(id));
                if (control.exists()) { control.click(); clicked = true; break; }
            }
            if (!clicked) for (String label : labels) {
                UiObject control = device.findObject(new UiSelector().text(label));
                if (control.exists()) { control.click(); clicked = true; break; }
            }
            if (clicked) device.waitForIdle(2_000);
            Thread.sleep(500);
        }
    }

    private static boolean waitForOwnedOidcCallback(UiDevice device, Context target) throws Exception {
        long deadline = System.currentTimeMillis() + JOURNEY;
        while (System.currentTimeMillis() < deadline) {
            if (target.getPackageName().equals(device.getCurrentPackageName())) return true;
            UiObject open = device.findObject(new UiSelector().resourceId("android:id/button1"));
            if (!open.exists()) open = device.findObject(new UiSelector().textMatches("(?i)open|continue"));
            if (open.exists()) {
                open.click();
                device.waitForIdle(1_000);
            }
            Thread.sleep(500);
        }
        return false;
    }

    private static String argument(String name, String fallback) {
        String value = InstrumentationRegistry.getArguments().getString(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
