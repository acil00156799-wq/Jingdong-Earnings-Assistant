package com.personal.jdassistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.SharedPreferences;

/**
 * One-shot JD bean sign-in helper.
 * It clicks at most one exact, visible claim control on the bean sign-in page,
 * then observes the resulting UI for a new success message.
 */
public class JdClaimAccessibilityService extends AccessibilityService {
    private static final String PREFS = "jd_claim_assistant";
    private static final long MAX_ATTEMPT_MS = 180000L;
    private static final long VERIFY_WINDOW_MS = 20000L;

    private long lastAttemptId = -1L;
    private boolean actionAttempted = false;
    private long verificationStartedAt = 0L;
    private String textBeforeClick = "";

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
            verificationStartedAt = 0L;
            textBeforeClick = "";
        }

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        String currentText = collectVisibleText(root);

        // After the single click, observe the JD UI but never click a second time.
        if (actionAttempted) {
            if (BeanClaimMatcher.hasSuccessEvidence(currentText, textBeforeClick)) {
                prefs.edit()
                    .putBoolean("auto_claim_enabled", false)
                    .putString("claim_state", "success_message_detected")
                    .putString("last_result", "京东页面出现新的签到成功提示；请以京豆余额/明细为最终到账依据")
                    .putLong("verification_time", System.currentTimeMillis())
                    .apply();
            } else if (System.currentTimeMillis() - verificationStartedAt > VERIFY_WINDOW_MS) {
                prefs.edit()
                    .putBoolean("auto_claim_enabled", false)
                    .putString("claim_state", "clicked_unconfirmed")
                    .putString("last_result", "已点击签到按钮，但20秒内未识别到成功提示；不能确认领取成功")
                    .putLong("verification_time", System.currentTimeMillis())
                    .apply();
            }
            return;
        }

        long startedAt = prefs.getLong("attempt_started_at", 0L);
        if (startedAt > 0L && System.currentTimeMillis() - startedAt > MAX_ATTEMPT_MS) {
            prefs.edit()
                .putBoolean("auto_claim_enabled", false)
                .putString("claim_state", "timeout")
                .putString("last_result", "识别等待超过3分钟，已自动停止；未执行领取点击")
                .apply();
            return;
        }

        String task = prefs.getString("selected_task", "");
        if (!"京豆签到".equals(task) || !BeanClaimMatcher.isBeanSignInPage(currentText)) {
            updateStateIfChanged(prefs, "page_not_matched",
                "当前页面尚未识别为京豆签到页面，未执行点击；请停留在京豆签到页面");
            return;
        }

        AccessibilityNodeInfo target = findClaimNode(
            root, BeanClaimMatcher.claimButtonLabels(currentText));
        if (target == null) {
            updateStateIfChanged(prefs, "button_not_found",
                "已识别京豆签到页面，但未发现明确的签到按钮；未执行点击");
            return;
        }

        actionAttempted = true;
        boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (clicked) {
            verificationStartedAt = System.currentTimeMillis();
            textBeforeClick = currentText;
            prefs.edit()
                .putBoolean("auto_claim_enabled", true)
                .putString("claim_state", "verifying")
                .putString("last_result", "已点击签到按钮，正在观察京东是否显示成功提示（最多20秒）")
                .putLong("last_action_time", verificationStartedAt)
                .apply();
        } else {
            prefs.edit()
                .putBoolean("auto_claim_enabled", false)
                .putString("claim_state", "click_failed")
                .putString("last_result", "找到签到按钮，但系统点击未成功；未确认领取")
                .putLong("last_action_time", System.currentTimeMillis())
                .apply();
        }
    }

    private void updateStateIfChanged(SharedPreferences prefs, String state, String message) {
        if (!state.equals(prefs.getString("claim_state", ""))) {
            prefs.edit().putString("claim_state", state).putString("last_result", message).apply();
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

    private AccessibilityNodeInfo findClaimNode(AccessibilityNodeInfo node, java.util.Set<String> labels) {
        if (node == null || !node.isVisibleToUser() || !node.isEnabled()) return null;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        boolean matchesText = text != null && labels.contains(text.toString().trim());
        boolean matchesDescription = desc != null && labels.contains(desc.toString().trim());

        if (matchesText || matchesDescription) {
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
            .putString("claim_state", "interrupted")
            .putString("last_result", "自动领取辅助已中断")
            .apply();
    }
}
