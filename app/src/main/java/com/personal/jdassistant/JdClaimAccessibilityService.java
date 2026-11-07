package com.personal.jdassistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User-armed, one-shot helper. It only clicks a task-specific, exact visible
 * label in the foreground JD app. It never repeats or claims reward settlement.
 */
public class JdClaimAccessibilityService extends AccessibilityService {
    private static final String PREFS = "jd_claim_assistant";
    private static final Set<String> BEAN_CHECKIN_LABELS = new HashSet<>(Arrays.asList(
        "签到领京豆", "立即签到", "签到", "领取京豆"
    ));
    private boolean actionAttempted = false;
    private long activeAttemptId = -1L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.jingdong.app.mall".contentEquals(event.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean("auto_claim_enabled", false) || actionAttempted) return;
        long attemptId = prefs.getLong("active_task_started_at", 0L);
        if (attemptId != activeAttemptId) { activeAttemptId = attemptId; actionAttempted = false; }
        String taskId = prefs.getString("active_task_id", "");
        if (!"bean_checkin".equals(taskId)) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo target = findExactTaskNode(root, BEAN_CHECKIN_LABELS);
        if (target == null) return;

        actionAttempted = true;
        boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        prefs.edit()
            .putBoolean("auto_claim_enabled", false)
            .putString("last_result", clicked
                ? "京豆签到辅助：已点击一个可见签到按钮；请在京东页面核对签到结果和到账"
                : "京豆签到辅助：找到签到按钮，但点击未成功")
            .putString("last_task_id", taskId)
            .putLong("last_action_time", System.currentTimeMillis())
            .apply();
    }

    private AccessibilityNodeInfo findExactTaskNode(AccessibilityNodeInfo node, Set<String> allowedLabels) {
        if (node == null || !node.isVisibleToUser() || !node.isEnabled()) return null;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        String label = t != null ? t.toString().trim() : "";
        if (label.isEmpty() && d != null) label = d.toString().trim();

        if (allowedLabels.contains(label)) {
            AccessibilityNodeInfo candidate = node;
            while (candidate != null && !candidate.isClickable()) candidate = candidate.getParent();
            if (candidate != null && candidate.isClickable()) return candidate;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findExactTaskNode(node.getChild(i), allowedLabels);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public void onInterrupt() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("auto_claim_enabled", false)
            .putString("last_result", "任务辅助已中断")
            .apply();
    }
}
