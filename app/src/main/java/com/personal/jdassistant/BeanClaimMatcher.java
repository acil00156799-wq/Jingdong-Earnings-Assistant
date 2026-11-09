package com.personal.jdassistant;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Pure, testable rules for the one supported task: JD bean sign-in. */
public final class BeanClaimMatcher {
    private BeanClaimMatcher() {}

    public static boolean isBeanSignInPage(String text) {
        if (text == null) return false;
        return text.contains("京豆") && (text.contains("签到") || text.contains("领豆"));
    }

    /** Exact labels only; generic “领取” is deliberately excluded. */
    public static Set<String> claimButtonLabels(String pageText) {
        if (!isBeanSignInPage(pageText)) return Collections.emptySet();
        Set<String> labels = new HashSet<>();
        Collections.addAll(labels, "签到领京豆", "领取京豆", "领京豆", "立即签到", "去签到");
        return Collections.unmodifiableSet(labels);
    }

    /**
     * Looks only for a newly visible success message after the click.
     * This is evidence of a success message, not proof of balance settlement.
     */
    public static boolean hasSuccessEvidence(String currentText, String beforeClickText) {
        if (currentText == null) return false;
        String before = beforeClickText == null ? "" : beforeClickText;
        return newlyAppeared(currentText, before, "签到成功")
                || newlyAppeared(currentText, before, "领取成功")
                || newlyAppeared(currentText, before, "签到完成")
                || (currentText.contains("获得京豆") && !before.contains("获得京豆"))
                || (currentText.contains("京豆到账") && !before.contains("京豆到账"));
    }

    private static boolean newlyAppeared(String current, String before, String marker) {
        return current.contains(marker) && !before.contains(marker);
    }
}
