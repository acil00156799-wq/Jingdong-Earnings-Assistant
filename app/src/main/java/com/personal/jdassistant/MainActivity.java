package com.personal.jdassistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.content.Intent;
import android.content.ComponentName;
import android.text.TextUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.Locale;

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
                if (value == null || value.trim().isEmpty()) return false;
                Uri uri = Uri.parse(value.trim());
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (scheme == null || !"https".equalsIgnoreCase(scheme) || host == null) return false;
                String normalizedHost = host.toLowerCase(Locale.ROOT);
                if (!(normalizedHost.equals("jd.com") || normalizedHost.endsWith(".jd.com")
                        || normalizedHost.equals("jingdong.com") || normalizedHost.endsWith(".jingdong.com")
                        || normalizedHost.equals("jd.hk") || normalizedHost.endsWith(".jd.hk"))) return false;
                if (uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
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
        public boolean shareBackup(String backupJson) {
            try {
                if (backupJson == null || backupJson.trim().isEmpty()) return false;
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/json");
                share.putExtra(Intent.EXTRA_SUBJECT, "京东福利助手数据备份");
                share.putExtra(Intent.EXTRA_TEXT, backupJson);
                runOnUiThread(() -> {
                    try {
                        startActivity(Intent.createChooser(share, "保存或发送数据备份"));
                    } catch (Exception ignored) { }
                });
                return true;
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
                .remove("last_claim_label")
                .putString("last_result", "已启动：等待京东前台出现明确的领取按钮（最长等待180秒）")
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
            if (p.getBoolean("auto_claim_enabled", false)) {
                long startedAt = p.getLong("attempt_started_at", 0L);
                if (startedAt <= 0L || System.currentTimeMillis() - startedAt > 180_000L) {
                    p.edit().putBoolean("auto_claim_enabled", false)
                        .putString("last_result", "等待超时：180秒内没有发现符合条件的领取按钮，已自动停止")
                        .apply();
                }
            }
            String result = p.getString("last_result", "尚未执行");
            String matchedLabel = p.getString("last_claim_label", "");
            if (!matchedLabel.isEmpty()) result += "（识别按钮：" + matchedLabel + "）";
            return result + (p.getBoolean("auto_claim_enabled", false) ? "（正在等待领取按钮）" : "");
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
        // Handle the app's existing alert/confirm/prompt UI (account rename and task-link editing).
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                    .setOnCancelListener(dialog -> result.cancel())
                    .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("确定", (dialog, which) -> result.confirm())
                    .setNegativeButton("取消", (dialog, which) -> result.cancel())
                    .setOnCancelListener(dialog -> result.cancel())
                    .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, JsPromptResult result) {
                EditText input = new EditText(MainActivity.this);
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                input.setText(defaultValue == null ? "" : defaultValue);
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(message)
                    .setView(input)
                    .setPositiveButton("确定", (dialog, which) -> result.confirm(input.getText().toString()))
                    .setNegativeButton("取消", (dialog, which) -> result.cancel())
                    .setOnCancelListener(dialog -> result.cancel())
                    .show();
                return true;
            }
        });
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