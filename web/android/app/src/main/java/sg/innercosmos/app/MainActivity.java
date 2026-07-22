package sg.innercosmos.app;

import android.content.Intent;

import com.getcapacitor.BridgeActivity;

/** Android-owned boundary for notification re-entry that must survive WebView lifecycle races. */
public class MainActivity extends BridgeActivity {
    private static final String LOCAL_NOTIFICATION_ID = "LocalNotificationId";
    private static final int WAKE_NOTIFICATION_OFFSET = 1_000_000;
    private int consumedNotificationId = Integer.MIN_VALUE;
    private int pendingNotificationId = Integer.MIN_VALUE;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dispatchOwnedWakeIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        dispatchOwnedWakeIntent(getIntent());
    }

    private void dispatchOwnedWakeIntent(Intent intent) {
        if (intent == null || getBridge() == null || getBridge().getWebView() == null) return;
        int notificationId = intent.getIntExtra(LOCAL_NOTIFICATION_ID, Integer.MIN_VALUE);
        if (notificationId <= WAKE_NOTIFICATION_OFFSET || notificationId == consumedNotificationId
                || notificationId == pendingNotificationId) return;
        int wakeIntentId = notificationId - WAKE_NOTIFICATION_OFFSET;
        if (wakeIntentId <= 0) return;
        pendingNotificationId = notificationId;
        String script = "window.dispatchEvent(new CustomEvent('innercosmos:wake-intent',{detail:{id:"
                + wakeIntentId + "}}));";
        // onResume can precede React's listener registration on a cold notification launch.
        // Queue one owned, idempotent delivery after WebView bootstrap; warm launches merely wait
        // an imperceptible moment and follow the same deterministic path.
        getBridge().getWebView().postDelayed(() -> {
            if (consumedNotificationId != notificationId && getBridge() != null) {
                getBridge().getWebView().evaluateJavascript(script, null);
                consumedNotificationId = notificationId;
            }
            pendingNotificationId = Integer.MIN_VALUE;
        }, 1_500);
    }
}
