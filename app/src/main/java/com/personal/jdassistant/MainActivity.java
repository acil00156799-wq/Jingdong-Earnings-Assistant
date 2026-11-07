package com.personal.jdassistant;

import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    private static final String JD_PACKAGE = "com.jingdong.app.mall";
    private static final String PREFS = "jd_claim_assistant";

    public class AndroidBridge {
        @JavascriptInterface
        public boolean openJdApp() {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(JD_PACKAGE);
                if (launch == null) return false;
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
                return true;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public boolean openJdUrl(String value) {
            try {
                Uri uri = Uri.parse(value);
                String scheme = uri.getScheme();
                if (scheme == null || !(scheme.equals("https") || scheme.equals("http"))) return false;
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setPackage(JD_PACKAGE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(intent); return true; }
                catch (Exception appError) {
                    intent.setPackage(null);
                    startActivity(intent);
                    return true;
                }
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }

        @JavascriptInterface
        public boolean startOneClaimAttempt(String taskName) {
            if (!isAccessibilityServiceEnabled()) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("auto_claim_enabled", false)
                    .putString("claim_state", "idle")
                    .putString("last_result", "请先开启本助手的无障碍服务")
                    .apply();
                return false;
            }
            String selected = taskName == null ? "其他活动" : taskName.trim();
            if (selected.isEmpty()) selected = "其他活动";
            long now = System.currentTimeMillis();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("selected_task", selected)
                .putLong("attempt_id", now)
                .putLong("attempt_started_at", now)
                .putBoolean("auto_claim_enabled", true)
                .putString("claim_state", "waiting_page")
                .putString("last_result", selected + "：已启动识别，等待对应福利页面")
                .apply();
            return true;
        }

        private boolean isAccessibilityServiceEnabled() {
            String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String component = new ComponentName(
                MainActivity.this, JdClaimAccessibilityService.class).flattenToString();
            return enabled != null && enabled.contains(component);
        }

        @JavascriptInterface
        public void stopClaimAttempt() {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("auto_claim_enabled", false)
                .putString("claim_state", "stopped")
                .putString("last_result", "已由用户停止")
                .apply();
        }

        @JavascriptInterface
        public void markUserVerified(String unit, String incomeName) {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            String task = p.getString("selected_task", "");
            boolean matchingIncome =
                ("京豆签到".equals(task) && "beans".equals(unit))
                || (("每日红包".equals(task) || "京东红包".equals(task))
                    && ("redpacket".equals(unit) || "yuan".equals(unit)));
            if (matchingIncome && "clicked_unverified".equals(p.getString("claim_state", ""))) {
                p.edit()
                    .putString("claim_state", "user_verified")
                    .putString("last_result", task + "：用户已手动登记对应类型的实际到账")
                    .apply();
            }
        }

        @JavascriptInterface
        public String getClaimStatus() {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean waiting = p.getBoolean("auto_claim_enabled", false);
            long startedAt = p.getLong("attempt_started_at", 0L);
            if (waiting && startedAt > 0L && System.currentTimeMillis() - startedAt > 180000L) {
                p.edit()
                    .putBoolean("auto_claim_enabled", false)
                    .putString("claim_state", "timeout")
                    .putString("last_result", "识别等待超过3分钟，已自动停止；请重新打开对应京东页面后再试")
                    .apply();
                waiting = false;
            }
            return p.getString("last_result", "尚未执行") +
                (waiting ? "（正在等待领取按钮，最长3分钟）" : "");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}