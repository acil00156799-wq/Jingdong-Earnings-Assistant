package com.personal.jdassistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User-started, one-shot page-context recognizer.
 * It reads visible text from the JD app only to identify the selected welfare
 * page. It never clicks, submits, claims, or otherwise operates JD controls.
 * The user must perform all actions manually inside the official JD app.
 */
public class JdClaimAccessibilityService extends AccessibilityService {
    private static final String PREFS = "jd_claim_assistant";
    private static final long MAX_ATTEMPT_MS = 180000L;
    private long lastAttemptId = -1L;
    private String lastAccountId = "";
    private boolean actionAttempted = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.jingdong.app.mall".contentEquals(event.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String accountId = prefs.getString("active_attempt_account_id", "");
        if (accountId.isEmpty()) return;
        String prefix = "account." + accountId + ".";
        if (!prefs.getBoolean(prefix + "auto_claim_enabled", false)) return;

        long startedAt = prefs.getLong(prefix + "attempt_started_at", 0L);
        if (startedAt > 0L && System.currentTimeMillis() - startedAt > MAX_ATTEMPT_MS) {
            SharedPreferences.Editor edit = prefs.edit().putBoolean(prefix + "auto_claim_enabled", false)
                .putString(prefix + "claim_state", "timeout")
                .putString(prefix + "last_result", "识别等待超过3分钟，已自动停止；未执行领取点击");
            if (accountId.equals(prefs.getString("active_attempt_account_id", ""))) edit.remove("active_attempt_account_id");
            edit.apply();
            return;
        }

        long attemptId = prefs.getLong(prefix + "attempt_id", -1L);
        if (!accountId.equals(lastAccountId) || attemptId != lastAttemptId) {
            lastAccountId = accountId;
            lastAttemptId = attemptId;
            actionAttempted = false;
        }
        if (actionAttempted) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        String task = prefs.getString(prefix + "selected_task", "其他活动");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        String pageText = collectVisibleText(root);
        boolean pageMatched = isTargetPage(task, pageText);
        if (!pageMatched) {
            updateStateIfChanged(prefs, prefix, "page_not_matched",
                task + "：当前页面尚未识别为对应福利页面，未执行点击；请停留在该模块页面");
            return;
        }

        // Platform rules prohibit unapproved software/robot activity for
        // claiming beans, coupons, and other benefits. This helper therefore
        // stops at page recognition and leaves the actual claim action to the user.
        actionAttempted = true;
        SharedPreferences.Editor resultEdit = prefs.edit()
            .putBoolean(prefix + "auto_claim_enabled", false)
            .putString(prefix + "claim_state", "manual_action_required")
            .putString(prefix + "last_result", task + "：已识别到对应页面。请你在京东官方 App 内自行查看并点击；本助手不会代替领取，也不会判断到账。")
            .putLong(prefix + "last_action_time", System.currentTimeMillis());
        if (accountId.equals(prefs.getString("active_attempt_account_id", ""))) resultEdit.remove("active_attempt_account_id");
        resultEdit.apply();
    }

    private boolean isTargetPage(String task, String pageText) {
        if ("京豆签到".equals(task)) {
            return pageText.contains("京豆") && (pageText.contains("签到") || pageText.contains("领豆"));
        }
        if ("每日红包".equals(task) || "京东红包".equals(task)) {
            return pageText.contains("红包") && (pageText.contains("每日") || pageText.contains("签到"));
        }
        if ("宠汪汪".equals(task)) return pageText.contains("宠汪汪");
        if ("游戏福利".equals(task)) return pageText.contains("游戏") && pageText.contains("福利");
        return false;
    }

    private void updateStateIfChanged(SharedPreferences prefs, String prefix, String state, String message) {
        if (!state.equals(prefs.getString(prefix + "claim_state", ""))) {
            prefs.edit().putString(prefix + "claim_state", state).putString(prefix + "last_result", message).apply();
        }
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
            // Only allow a nearby clickable control. Walking to an arbitrary
            // distant clickable ancestor could click an entire card or page.
            AccessibilityNodeInfo candidate = node;
            for (int depth = 0; candidate != null && depth <= 2; depth++) {
                if (candidate.isVisibleToUser() && candidate.isEnabled() && candidate.isClickable()) {
                    return candidate;
                }
                candidate = candidate.getParent();
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findClaimNode(node.getChild(i), labels);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public void onInterrupt() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String accountId = prefs.getString("active_attempt_account_id", "");
        if (accountId.isEmpty()) return;
        String prefix = "account." + accountId + ".";
        prefs.edit().putBoolean(prefix + "auto_claim_enabled", false)
            .putString(prefix + "last_result", "自动领取辅助已中断")
            .remove("active_attempt_account_id").apply();
    }
}
