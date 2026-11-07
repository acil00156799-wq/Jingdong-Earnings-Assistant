package com.personal.jdassistant;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    private static final String JD_PACKAGE = "com.jingdong.app.mall";
    private static final String PREFS = "jd_claim_assistant";

    private boolean isClaimServiceEnabled() {
        String enabled = Settings.Secure.getString(
            getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        String expected = new ComponentName(this, JdClaimAccessibilityService.class).flattenToString();
        for (String item : enabled.split(":")) {
            if (expected.equalsIgnoreCase(item)) return true;
        }
        return false;
    }

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
        public boolean isAccessibilityEnabled() {
            return isClaimServiceEnabled();
        }

        @JavascriptInterface
        public boolean startTaskModule(String taskId) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (!"bean_checkin".equals(taskId)) {
                prefs.edit().putBoolean("auto_claim_enabled", false)
                    .putString("last_result", "该任务目前为手动入口，尚无经过验证的自动执行规则")
                    .putString("active_task_id", taskId == null ? "" : taskId)
                    .apply();
                return false;
            }
            if (!isClaimServiceEnabled()) {
                prefs.edit().putBoolean("auto_claim_enabled", false)
                    .putString("last_result", "未启动：请先在系统设置中开启本助手的无障碍服务")
                    .apply();
                return false;
            }
            prefs.edit().putString("active_task_id", taskId)
                .putLong("active_task_started_at", System.currentTimeMillis())
                .putBoolean("auto_claim_enabled", true)
                .putString("last_result", "京豆签到辅助已启动：等待京东前台出现本模块指定的签到按钮")
                .apply();
            return true;
        }

        @JavascriptInterface
        public boolean startOneClaimAttempt() {
            return startTaskModule("bean_checkin");
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
            String result = p.getString("last_result", "尚未执行");
            if (!isClaimServiceEnabled()) result += "；无障碍服务未开启";
            return result + (p.getBoolean("auto_claim_enabled", false) ? "（等待领取按钮）" : "");
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
