/*
 * 福利任务模块注册表
 * 这里记录每个模块的独立身份、入口说明和当前能力状态。
 * 未经验证的京东活动链接不写入；manual_only 表示当前仅打开京东供用户手动操作。
 */
window.JD_TASK_MODULES = [
  { id: "bean_checkin", name: "京豆签到", icon: "🫘", capability: "manual_only",
    guidance: "请在京东内找到签到/京豆活动并自行确认签到结果；当前未接入自动签到。" },
  { id: "daily_redpacket", name: "每日红包", icon: "🧧", capability: "manual_only",
    guidance: "请在京东内打开每日红包活动并按页面提示操作；当前未接入自动领取。" },
  { id: "pet_dog", name: "宠汪汪", icon: "🐶", capability: "manual_only",
    guidance: "请在京东内打开宠汪汪；当前不执行喂养、互动或游戏步骤。" },
  { id: "jd_redpacket", name: "京东红包", icon: "🎁", capability: "manual_only",
    guidance: "请在京东内查看红包活动；当前未接入活动发现和自动领取。" },
  { id: "game_rewards", name: "游戏福利", icon: "🎮", capability: "manual_only",
    guidance: "请在京东内选择具体游戏活动；当前不自动玩游戏或完成任务。" },
  { id: "other_events", name: "其他活动", icon: "⭐", capability: "manual_only",
    guidance: "请在京东内自行选择活动；当前没有通用活动自动执行能力。" }
];
