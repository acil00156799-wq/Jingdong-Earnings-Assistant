package com.personal.jdassistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User-armed, one-shot helper. It clicks only an exact task-specific visible
 * label, then passively checks for an explicit success message for 12 seconds.
 * A page message is not proof that reward balance has been credited.
 */
public class JdClaimAccessibilityService extends AccessibilityService {
    private static final String PREFS = "jd_claim_assistant";
    private static final Set<String> BEAN_CHECKIN_LABELS = new HashSet<>(Arrays.asList(
        "签到领京豆", "领取京豆"
    ));
    private static final Set<String> SUCCESS_LABELS = new HashSet<>(Arrays.asList(
        "签到成功", "今日已签到", "已签到"
    ));
    private static final long VERIFY_WINDOW_MS = 12000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean actionAttempted = false;
    private long activeAttemptId = -1L;
    private long verificationAttemptId = -1L;
    private final Runnable verificationTimeout = () -> {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.getBoolean("verification_pending", false)) return;
        long current = p.getLong("active_task_started_at", 0L);
        if (current != verificationAttemptId) return;
        p.edit().putBoolean("verification_pending", false)
            .putString("last_result", "已点击签到按钮，但未检测到明确的签到成功提示；请到京东核对")
            .putLong("last_action_time", System.currentTimeMillis()).apply();
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.jingdong.app.mall".contentEquals(event.getPackageName())) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // Verification is passive and continues after the one permitted click.
        if (prefs.getBoolean("verification_pending", false)) {
            AccessibilityNodeInfo verifyRoot = getRootInActiveWindow();
            if (verifyRoot != null && containsExactText(verifyRoot, SUCCESS_LABELS)) {
                long current = prefs.getLong("active_task_started_at", 0L);
                if (current == verificationAttemptId) {
                    handler.removeCallbacks(verificationTimeout);
                    prefs.edit().putBoolean("verification_pending", false)
                        .putString("last_result", "京东页面出现明确的签到成功提示；京豆是否到账仍需核对账户")
                        .putLong("last_action_time", System.currentTimeMillis()).apply();
                }
            }
            return;
        }

        if (!prefs.getBoolean("auto_claim_enabled", false)) return;
        long attemptId = prefs.getLong("active_task_started_at", 0L);
        if (attemptId != activeAttemptId) { activeAttemptId = attemptId; actionAttempted = false; }
        if (actionAttempted) return;
        String taskId = prefs.getString("active_task_id", "");
        if (!"bean_checkin".equals(taskId)) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo target = findExactTaskNode(root, BEAN_CHECKIN_LABELS);
        if (target == null) return;

        actionAttempted = true;
        boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        SharedPreferences.Editor edit = prefs.edit()
            .putBoolean("auto_claim_enabled", false)
            .putString("last_task_id", taskId)
            .putLong("last_action_time", System.currentTimeMillis());
        if (clicked) {
            verificationAttemptId = attemptId;
            edit.putBoolean("verification_pending", true)
                .putString("last_result", "已点击签到按钮，正在等待京东页面反馈（尚未确认成功）");
            handler.removeCallbacks(verificationTimeout);
            handler.postDelayed(verificationTimeout, VERIFY_WINDOW_MS);
        } else {
            edit.putBoolean("verification_pending", false)
                .putString("last_result", "找到签到按钮，但点击未成功");
        }
        edit.apply();
    }

    private boolean containsExactText(AccessibilityNodeInfo node, Set<String> labels) {
        if (node == null || !node.isVisibleToUser()) return false;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        String text = t == null ? "" : t.toString().trim();
        String desc = d == null ? "" : d.toString().trim();
        if (labels.contains(text) || labels.contains(desc)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsExactText(node.getChild(i), labels)) return true;
        }
        return false;
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
        handler.removeCallbacks(verificationTimeout);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("auto_claim_enabled", false)
            .putBoolean("verification_pending", false)
            .putString("last_result", "任务辅助已中断，请在京东核对当前状态")
            .apply();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(verificationTimeout);
        super.onDestroy();
    }
}
