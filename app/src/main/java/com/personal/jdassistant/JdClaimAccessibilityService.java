package com.personal.jdassistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User-started, single-action helper. It only clicks a small allowlist of
 * explicit claim labels in the foreground JD app. It never solves challenges,
 * plays games, browses ads, or repeats clicks.
 */
public class JdClaimAccessibilityService extends AccessibilityService {
    private static final String PREFS = "jd_claim_assistant";
    private static final Set<String> CLAIM_LABELS = new HashSet<>(Arrays.asList(
        "立即领取", "领取京豆", "领取红包", "立即收下", "收下", "领取奖励"
    ));
    private boolean actionAttempted = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.jingdong.app.mall".contentEquals(event.getPackageName())) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            actionAttempted = false;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean("auto_claim_enabled", false) || actionAttempted) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            AccessibilityNodeInfo target = findClaimNode(root);
            if (target != null) {
                actionAttempted = true;
                boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                prefs.edit()
                    .putBoolean("auto_claim_enabled", false)
                    .putString("last_result", clicked ? "已点击一个明确的领取按钮，请核对京东页面结果" : "找到领取按钮但点击未成功")
                    .putLong("last_action_time", System.currentTimeMillis())
                    .apply();
            }
        } finally {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findClaimNode(AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser() || !node.isEnabled()) return null;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String label = text != null ? text.toString().trim() : "";
        if (label.isEmpty() && desc != null) label = desc.toString().trim();

        if (CLAIM_LABELS.contains(label)) {
            AccessibilityNodeInfo clickable = node;
            while (clickable != null && !clickable.isClickable()) {
                AccessibilityNodeInfo parent = clickable.getParent();
                if (clickable != node) clickable.recycle();
                clickable = parent;
            }
            if (clickable != null && clickable.isClickable()) {
                if (clickable != node) node.recycle();
                return clickable;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findClaimNode(child);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public void onInterrupt() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("auto_claim_enabled", false)
            .putString("last_result", "自动领取已中断")
            .apply();
    }
}