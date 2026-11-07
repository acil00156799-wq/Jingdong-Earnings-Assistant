package com.personal.jdassistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User-started, one-shot helper. A click is allowed only when both the selected
 * welfare module's page context and an exact visible claim label match.
 * It performs at most one click per explicit user-started attempt.
 */
public class JdClaimAccessibilityService extends AccessibilityService {
    private static final String PREFS = "jd_claim_assistant";
    private long lastAttemptId = -1L;
    private boolean actionAttempted = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.jingdong.app.mall".contentEquals(event.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean("auto_claim_enabled", false)) return;

        long attemptId = prefs.getLong("attempt_id", -1L);
        if (attemptId != lastAttemptId) {
            lastAttemptId = attemptId;
            actionAttempted = false;
        }
        if (actionAttempted) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        String task = prefs.getString("selected_task", "其他活动");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        String pageText = collectVisibleText(root);
        Set<String> labels = allowedLabelsFor(task, pageText);
        if (labels.isEmpty()) return;

        AccessibilityNodeInfo target = findClaimNode(root, labels);
        if (target == null) return;

        actionAttempted = true;
        boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        prefs.edit()
            .putBoolean("auto_claim_enabled", false)
            .putString("last_result", clicked
                ? task + "：已点击一个匹配的领取按钮，请核对京东页面是否显示领取成功"
                : task + "：找到匹配按钮，但点击未成功；未确认到账")
            .putLong("last_action_time", System.currentTimeMillis())
            .apply();
    }

    private Set<String> allowedLabelsFor(String task, String pageText) {
        Set<String> labels = new HashSet<>();
        if ("京豆签到".equals(task)) {
            if (pageText.contains("京豆") && (pageText.contains("签到") || pageText.contains("领豆"))) {
                labels.addAll(Arrays.asList("签到领京豆", "立即签到", "领取京豆"));
            }
        } else if ("每日红包".equals(task) || "京东红包".equals(task)) {
            if (pageText.contains("红包") && (pageText.contains("每日") || pageText.contains("签到"))) {
                labels.addAll(Arrays.asList("领取红包", "立即领取", "收下"));
            }
        } else if ("宠汪汪".equals(task)) {
            if (pageText.contains("宠汪汪")) labels.add("领取奖励");
        } else if ("游戏福利".equals(task)) {
            if (pageText.contains("游戏") && pageText.contains("福利")) labels.add("领取奖励");
        }
        // “其他活动” intentionally remains manual until its page and button
        // semantics are identified, preventing a generic button from being clicked.
        return labels;
    }

    private String collectVisibleText(AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser()) return "";
        StringBuilder out = new StringBuilder();
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null) out.append(text).append(' ');
        if (desc != null) out.append(desc).append(' ');
        for (int i = 0; i < node.getChildCount(); i++) {
            out.append(collectVisibleText(node.getChild(i))).append(' ');
        }
        return out.toString();
    }

    private AccessibilityNodeInfo findClaimNode(AccessibilityNodeInfo node, Set<String> labels) {
        if (node == null || !node.isVisibleToUser() || !node.isEnabled()) return null;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        String label = t != null ? t.toString().trim() : "";
        if (label.isEmpty() && d != null) label = d.toString().trim();

        if (labels.contains(label)) {
            AccessibilityNodeInfo candidate = node;
            while (candidate != null && !candidate.isClickable()) candidate = candidate.getParent();
            if (candidate != null && candidate.isClickable()) return candidate;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findClaimNode(node.getChild(i), labels);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public void onInterrupt() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("auto_claim_enabled", false)
            .putString("last_result", "自动领取辅助已中断")
            .apply();
    }
}
