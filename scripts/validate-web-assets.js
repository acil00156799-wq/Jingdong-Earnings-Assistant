const fs = require('node:fs');
const html = fs.readFileSync('app/src/main/assets/index.html', 'utf8');
const scriptStart = html.indexOf('<script>');
const scriptEnd = html.indexOf('</script>', scriptStart);
if (scriptStart < 0 || scriptEnd < 0) throw new Error('index.html script block not found');
const script = html.slice(scriptStart + 8, scriptEnd);
new Function(script);

const validatorStart = script.indexOf('function isOfficialJdUrl(value)');
const addTaskStart = script.indexOf('\nfunction addTask', validatorStart);
if (validatorStart < 0 || addTaskStart < 0) throw new Error('JD URL validator not found');
const validatorSource = script.slice(validatorStart, addTaskStart);
const isOfficialJdUrl = new Function('URL', validatorSource + '; return isOfficialJdUrl;')(URL);

const cases = [
  ['https://item.jd.com/100.html', true],
  ['https://pro.m.jd.com/mall/active', true],
  ['https://passport.jd.com/new/login.aspx', true],
  ['https://jingdong.com/', true],
  ['https://www.jd.hk/', true],
  ['https://jd.com.evil.test/', false],
  ['https://eviljd.com/', false],
  ['http://jd.com/', false],
  ['https://jd.com:8443/', false],
  ['https://user@jd.com/', false],
];
for (const [url, expected] of cases) {
  const actual = isOfficialJdUrl(url);
  if (actual !== expected) throw new Error('URL validation mismatch: ' + url + ' expected ' + expected + ' got ' + actual);
}
const goTaskStart = script.indexOf('function goTask(id)');
const confirmTaskStart = script.indexOf('function confirmTask(id)', goTaskStart);
if (goTaskStart < 0 || confirmTaskStart < 0) throw new Error('Saved task execution handler not found');
const goTaskSource = script.slice(goTaskStart, confirmTaskStart);
if (!goTaskSource.includes('AndroidBridge.startOneClaimAttempt()')) throw new Error('Saved task does not start claim recognition');
if (!goTaskSource.includes('AndroidBridge.openJdUrl(t.url)')) throw new Error('Saved task does not open its configured JD link');
if (!goTaskSource.includes("localStorage.setItem('jd_active_task_id'")) throw new Error('Saved task result tracking is missing');
if (!goTaskSource.includes("localStorage.setItem('jd_active_task_account_id'")) throw new Error('Saved task account tracking is missing');
if (!script.includes("task.status='已触发一次点击，请人工核对领取结果'")) throw new Error('Claim result is not synchronized to task history');
const shortcutStart = script.indexOf('function startTaskShortcut(name)');
const startOneClaimStart = script.indexOf('function startOneClaim()', shortcutStart);
if (shortcutStart < 0 || startOneClaimStart < 0 || !script.slice(shortcutStart, startOneClaimStart).includes('goTask(task.id)')) throw new Error('Welfare shortcuts do not use the saved-task execution flow');

const shortcutUrlStart = script.indexOf('function shortcutOfficialUrl(name)');
const shortcutHandlerStart = script.indexOf('function startTaskShortcut(name)', shortcutUrlStart);
const startOneClaimForShortcutStart = script.indexOf('function startOneClaim()', shortcutHandlerStart);
if (shortcutUrlStart < 0 || shortcutHandlerStart < 0 || startOneClaimForShortcutStart < 0) throw new Error('Shortcut URL routing is missing');
const shortcutRouting = script.slice(shortcutUrlStart, shortcutHandlerStart);
if (!shortcutRouting.includes('https://my.m.jd.com/jingdou/index.html?fromori=redirect&sceneval=2')) throw new Error('Verified JD My Beans route is missing');
if (!shortcutRouting.includes("if(name==='京豆中心')")) throw new Error('Unverified shortcuts must not receive invented deep links');
if (!html.includes("startTaskShortcut('京豆中心')")) throw new Error('My Beans shortcut label is missing');

const mainActivity = fs.readFileSync('app/src/main/java/com/personal/jdassistant/MainActivity.java', 'utf8');
const accessibilityService = fs.readFileSync('app/src/main/java/com/personal/jdassistant/JdClaimAccessibilityService.java', 'utf8');
if (!mainActivity.includes('System.currentTimeMillis() - startedAt > 180_000L')) throw new Error('Native attempt timeout is not 180 seconds');
if (!accessibilityService.includes('ATTEMPT_TIMEOUT_MS = 180_000L')) throw new Error('Accessibility timeout is not 180 seconds');

if (!html.includes('onclick="refreshClaimStatus()"')) throw new Error('Manual claim status refresh control is missing');
if (!script.includes("setInterval(()=>{if(!document.hidden)refreshClaimStatus()},2000)")) throw new Error('Foreground claim status polling is missing');

if (!script.includes('function editTask(id)')) throw new Error('Task link editor is missing');
if (!script.includes('isOfficialJdUrl(url)')) throw new Error('Task link editor does not validate official JD HTTPS URLs');
if (!script.includes("edit.textContent=t.url?'修改活动链接':'设置活动链接'")) throw new Error('Task link editor is not exposed in task history');

console.log('Web asset JavaScript syntax, 10 JD URL cases, saved-task execution, verified shortcut routing, timeout, status-refresh and task-link editor checks passed.');
