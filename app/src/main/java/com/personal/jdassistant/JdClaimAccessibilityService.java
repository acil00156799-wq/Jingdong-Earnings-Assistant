package com.personal.jdassistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * User-started, one-shot helper. It clicks at most one exact, visible claim
 * label in the foreground JD app. It does not solve challenges, play games,
 * interact with ads, or repeat actions.
 */
public class JdClaimAccessibilityService extends AccessibilityService {
    private static final String PREFS = "jd_claim_assistant";
    private static final Set<String> CLAIM_LABELS = new HashSet<>(Arrays.asList(
        "立即领取", "点击领取", "去领取", "领取京豆", "立即领取京豆", "领取红包", "立即领取红包", "立即收下", "收下红包", "领取奖励", "立即领取奖励"
    ));
    private static final long ATTEMPT_TIMEOUT_MS = 180_000L;
    private static final int MAX_TREE_DEPTH = 40;
    private static final int MAX_CLICKABLE_ANCESTORS = 4;
    private boolean actionAttempted = false;
    private long activeAttemptStartedAt = -1L;
    private String matchedClaimLabel = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.jingdong.app.mall".contentEquals(event.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long startedAt = prefs.getLong("attempt_started_at", 0L);
        if (startedAt != activeAttemptStartedAt) {
            activeAttemptStartedAt = startedAt;
            actionAttempted = prefs.getLong("attempt_action_generation", 0L) == startedAt;
            matchedClaimLabel = "";
            // If Android recreated this service after the one-shot lock was saved,
            // stop rather than risk repeating an action whose result is unknown.
            if (actionAttempted && prefs.getBoolean("auto_claim_enabled", false)) {
                prefs.edit().putBoolean("auto_claim_enabled", false)
                    .putString("last_result", "本次识别已处理过动作，为避免重复点击已停止")
                    .remove("last_claim_label")
                    .apply();
            }
        }
        if (!prefs.getBoolean("auto_claim_enabled", false) || actionAttempted) return;
        if (startedAt <= 0L || System.currentTimeMillis() - startedAt > ATTEMPT_TIMEOUT_MS) {
            stopForTimeout(prefs);
            return;
        }

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo target = findClaimNode(root, 0);
        if (target == null) return;

        // Lock this attempt before performing the click: rapid accessibility
        // events must never cause a second click for the same user action.
        actionAttempted = true;
        // Persist the one-shot lock before ACTION_CLICK so a service/process restart
        // cannot repeat the same click for this attempt generation.
        boolean lockSaved = prefs.edit()
            .putLong("attempt_action_generation", startedAt)
            .commit();
        if (!lockSaved) {
            prefs.edit().putBoolean("auto_claim_enabled", false)
                .putString("last_result", "无法保存单次操作保护，本次未执行点击")
                .remove("last_claim_label")
                .apply();
            return;
        }
        boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        prefs.edit()
            .putBoolean("auto_claim_enabled", false)
            .putString("last_result", clicked
                ? "已点击一个明确的领取按钮；请核对京东是否显示领取成功"
                : "已找到领取按钮，但点击未成功；本次已停止")
            .putString("last_claim_label", matchedClaimLabel)
            .putLong("last_action_time", System.currentTimeMillis())
            .apply();
    }

    private void stopForTimeout(SharedPreferences prefs) {
        actionAttempted = true;
        prefs.edit().putBoolean("auto_claim_enabled", false)
            .putString("last_result", "等待超时：180秒内没有发现符合条件的领取按钮，已自动停止")
            .remove("last_claim_label")
            .apply();
    }

    private String normalize(CharSequence value) {
        if (value == null) return "";
        return value.toString().trim().replaceAll("\\s+", "");
    }

    private AccessibilityNodeInfo findClaimNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_TREE_DEPTH
                || !node.isVisibleToUser() || !node.isEnabled()) return null;

        // Check text and accessibility description independently. Some JD
        // controls expose only one of these fields.
        String text = normalize(node.getText());
        String description = normalize(node.getContentDescription());
        if (CLAIM_LABELS.contains(text) || CLAIM_LABELS.contains(description)) {
            Rect labelBounds = new Rect();
            node.getBoundsInScreen(labelBounds);
            AccessibilityNodeInfo candidate = node;
            for (int ancestorDepth = 0;
                    candidate != null && ancestorDepth < MAX_CLICKABLE_ANCESTORS;
                    ancestorDepth++) {
                Rect candidateBounds = new Rect();
                candidate.getBoundsInScreen(candidateBounds);
                if (candidate.isVisibleToUser() && candidate.isEnabled()
                        && candidate.isClickable() && candidateBounds.contains(labelBounds)
                        && !candidateBounds.isEmpty()) {
                    matchedClaimLabel = CLAIM_LABELS.contains(text) ? text : description;
                    return candidate;
                }
                candidate = candidate.getParent();
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findClaimNode(node.getChild(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public void onInterrupt() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("auto_claim_enabled", false)
            .putString("last_result", "无障碍服务收到系统中断，本次识别已停止")
            .remove("last_claim_label")
            .apply();
        actionAttempted = true;
    }
}
