/*
 * Task registry. assisted_click only means a narrowly-scoped, user-armed
 * visible-button click after the user opens the corresponding JD activity.
 * It does not claim to discover activities or verify reward settlement.
 */
window.JD_TASK_MODULES = [
  { id: "bean_checkin", name: "京豆签到", icon: "🫘", capability: "assisted_click",
    labels: ["签到领京豆", "领取京豆"],
    guidance: "已进入京豆签到辅助流程：请确认京东打开的是签到页面；助手只会尝试点击“签到领京豆”或“领取京豆”两个明确按钮。" },
  { id: "daily_redpacket", name: "每日红包", icon: "🧧", capability: "manual_only",
    guidance: "当前仅打开京东。请自行找到每日红包活动并操作；自动领取规则尚未验证。" },
  { id: "pet_dog", name: "宠汪汪", icon: "🐶", capability: "manual_only",
    guidance: "当前仅打开京东宠汪汪入口；不自动喂养、互动、游戏或完成多步骤任务。" },
  { id: "jd_redpacket", name: "京东红包", icon: "🎁", capability: "manual_only",
    guidance: "当前仅打开京东；红包活动入口和领取规则尚未验证。" },
  { id: "game_rewards", name: "游戏福利", icon: "🎮", capability: "manual_only",
    guidance: "当前仅打开京东；不自动玩游戏、看广告或完成游戏任务。" },
  { id: "other_events", name: "其他活动", icon: "⭐", capability: "manual_only",
    guidance: "当前仅打开京东；其他活动需逐项验证后再添加自动执行规则。" }
];
