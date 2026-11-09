package com.personal.jdassistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class BeanClaimMatcherTest {
    @Test public void recognizesBeanSignInPageOnlyWithBeanContext() {
        assertTrue(BeanClaimMatcher.isBeanSignInPage("我的京豆 今日签到"));
        assertTrue(BeanClaimMatcher.isBeanSignInPage("京豆 领豆任务"));
        assertFalse(BeanClaimMatcher.isBeanSignInPage("每日签到"));
        assertFalse(BeanClaimMatcher.isBeanSignInPage("京东首页"));
    }

    @Test public void exposesOnlySpecificButtonsOnBeanPage() {
        assertTrue(BeanClaimMatcher.claimButtonLabels("京豆签到").contains("签到领京豆"));
        assertTrue(BeanClaimMatcher.claimButtonLabels("京豆签到").contains("立即签到"));
        assertFalse(BeanClaimMatcher.claimButtonLabels("京豆签到").contains("领取"));
        assertTrue(BeanClaimMatcher.claimButtonLabels("每日签到").isEmpty());
    }

    @Test public void successMustAppearAfterTheClick() {
        assertTrue(BeanClaimMatcher.hasSuccessEvidence("签到成功，获得京豆", "京豆签到"));
        assertFalse(BeanClaimMatcher.hasSuccessEvidence("签到成功，获得京豆", "京豆签到 签到成功 获得京豆"));
        assertFalse(BeanClaimMatcher.hasSuccessEvidence("领取按钮 京豆签到", "京豆签到"));
        assertTrue(BeanClaimMatcher.hasSuccessEvidence("京豆到账 +5", "京豆签到"));
    }
}
