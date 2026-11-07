package com.personal.jdassistant;

import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.text.TextUtils;
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
        public boolean isAccessibilityEnabled() {
            String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(enabled)) return false;
            ComponentName expected = new ComponentName(
                MainActivity.this, JdClaimAccessibilityService.class);
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabled);
            while (splitter.hasNext()) {
                if (expected.flattenToString().equalsIgnoreCase(splitter.next())) return true;
            }
            return false;
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }

        @JavascriptInterface
        public boolean startOneClaimAttempt() {
            if (!isAccessibilityEnabled()) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("auto_claim_enabled", false)
                    .putString("last_result", "尚未开启无障碍权限，请先开启后再启动")
                    .apply();
                return false;
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("attempt_started_at", System.currentTimeMillis())
                .putBoolean("auto_claim_enabled", true)
                .putString("last_result", "已启动：等待京东前台出现明确的领取按钮（最长等待90秒）")
                .apply();
            return true;
        }

        @JavascriptInterface
        public void stopClaimAttempt() {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("auto_claim_enabled", false)
                .putString("last_result", "已由用户停止")
                .apply();
        }

        @JavascriptInterface
        public String getClaimStatus() {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            return p.getString("last_result", "尚未执行") +
                (p.getBoolean("auto_claim_enabled", false) ? "（正在等待领取按钮）" : "");
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
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(
                "if(typeof refreshClaimStatus==='function')refreshClaimStatus();", null));
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}