package com.personal.jdassistant;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppIntegrationTest {
    @Test
    public void accessibilityServiceIsRegisteredWithRequiredBindingPermission() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ComponentName component = new ComponentName(context, JdClaimAccessibilityService.class);
        ServiceInfo service = context.getPackageManager().getServiceInfo(
                component, PackageManager.GET_META_DATA);

        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", service.permission);
        assertNotNull(service.metaData);
        assertEquals(R.xml.jd_accessibility_config,
                service.metaData.getInt("android.accessibilityservice"));
    }

    @Test
    public void mainActivityLoadsLocalWebViewUi() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View content = activity.findViewById(android.R.id.content);
                assertTrue(content instanceof ViewGroup);
                ViewGroup group = (ViewGroup) content;
                assertTrue(group.getChildCount() > 0);
                View child = group.getChildAt(0);
                assertTrue(child instanceof WebView);
                WebView webView = (WebView) child;
                assertTrue(webView.getSettings().getJavaScriptEnabled());
                assertEquals("file:///android_asset/index.html", webView.getUrl());
            });
        }
    }
}
