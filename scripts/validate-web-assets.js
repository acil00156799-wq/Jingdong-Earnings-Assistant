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
const noUrlBranchStart = goTaskSource.indexOf("if(!t.url){");
const urlBranchStart = goTaskSource.indexOf("if(!AndroidBridge.startOneClaimAttempt())", noUrlBranchStart);
if (noUrlBranchStart < 0 || urlBranchStart < 0 || goTaskSource.slice(noUrlBranchStart, urlBranchStart).includes("startOneClaimAttempt")) throw new Error('Tasks without verified deep links must not start the timeout before manual navigation');
const startRecognitionStart = script.indexOf('function startRecognitionForTask(id)');
const nextFunctionStart = script.indexOf('function confirmTask(id)', startRecognitionStart);
if (startRecognitionStart < 0 || nextFunctionStart < 0) throw new Error('Delayed task recognition action is missing');
const startRecognitionSource = script.slice(startRecognitionStart, nextFunctionStart);
if (!startRecognitionSource.includes('AndroidBridge.startOneClaimAttempt()') || !startRecognitionSource.includes('openJd()')) throw new Error('Delayed recognition must arm the one-shot attempt and return to JD');
if (!script.includes("t.status.includes('已打开京东')?'进入活动后启动识别'")) throw new Error('Task UI does not expose delayed recognition after manual navigation');
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

if (!mainActivity.includes('setWebChromeClient(new WebChromeClient()')) throw new Error('WebView JavaScript dialog handler is missing');
if (!mainActivity.includes('onJsPrompt(WebView view')) throw new Error('WebView prompt handler is missing');
if (!mainActivity.includes('onJsConfirm(WebView view')) throw new Error('WebView confirm handler is missing');

if (!mainActivity.includes('System.currentTimeMillis() - startedAt > 180_000L')) throw new Error('Native attempt timeout is not 180 seconds');
if (!accessibilityService.includes('ATTEMPT_TIMEOUT_MS = 180_000L')) throw new Error('Accessibility timeout is not 180 seconds');
const claimLabels = ['立即领取','点击领取','去领取','领取京豆','立即领取京豆','领取红包','立即领取红包','立即收下','收下红包','领取奖励','立即领取奖励'];
for (const label of claimLabels) {
  if (!accessibilityService.includes('"' + label + '"')) throw new Error('Accessibility claim label is missing: ' + label);
}
if (!accessibilityService.includes('if (!prefs.getBoolean("auto_claim_enabled", false) || actionAttempted) return;')) throw new Error('One-shot claim guard is missing');
if (!accessibilityService.includes('actionAttempted = true;')) throw new Error('Claim attempt is not locked before clicking');

if (!html.includes('onclick="refreshClaimStatus()"')) throw new Error('Manual claim status refresh control is missing');
if (!script.includes("setInterval(()=>{if(!document.hidden)refreshClaimStatus()},2000)")) throw new Error('Foreground claim status polling is missing');

if (!script.includes('function editTask(id)')) throw new Error('Task link editor is missing');
if (!script.includes('isOfficialJdUrl(url)')) throw new Error('Task link editor does not validate official JD HTTPS URLs');
if (!script.includes("edit.textContent=t.url?'修改活动链接':'设置活动链接'")) throw new Error('Task link editor is not exposed in task history');

const gradleBuild = fs.readFileSync('app/build.gradle', 'utf8');
const workflow = fs.readFileSync('.github/workflows/android-build.yml', 'utf8');
for (const secretName of ['ANDROID_SIGNING_KEYSTORE_BASE64','ANDROID_SIGNING_STORE_PASSWORD','ANDROID_SIGNING_KEY_ALIAS','ANDROID_SIGNING_KEY_PASSWORD']) {
  if (!workflow.includes('secrets.' + secretName)) throw new Error('Workflow signing secret is missing: ' + secretName);
}
for (const envName of ['ANDROID_SIGNING_STORE_FILE','ANDROID_SIGNING_STORE_PASSWORD','ANDROID_SIGNING_KEY_ALIAS','ANDROID_SIGNING_KEY_PASSWORD']) {
  if (!gradleBuild.includes(envName)) throw new Error('Gradle stable signing environment variable is missing: ' + envName);
}
if (!gradleBuild.includes('signingConfig signingConfigs.stable')) throw new Error('Stable signing is not applied to APK build types');
if (!workflow.includes('base64 --decode')) throw new Error('Workflow does not decode the private signing keystore');
if (workflow.includes('actions/cache@v4')) throw new Error('Do not cache a signing private key in a public repository');

if (!script.includes("function exportBackup()")) throw new Error('Local data backup export is missing');
if (!script.includes("format:'jd-assistant-backup'")) throw new Error('Backup export format marker is missing');
if (!script.includes("function importBackup()")) throw new Error('Local data backup import is missing');
if (!script.includes("data.format!=='jd-assistant-backup'")) throw new Error('Backup import format validation is missing');
if (!script.includes("onclick=" + '"exportBackup()"')) throw new Error('Backup export control is not visible');
if (!script.includes("onclick=" + '"importBackup()"')) throw new Error('Backup import control is not visible');
if (!mainActivity.includes('public boolean shareBackup(String backupJson)')) throw new Error('Native backup share-sheet bridge is missing');
if (!mainActivity.includes('Intent.createChooser(share, "保存或发送数据备份")')) throw new Error('Native backup share-sheet action is missing');

console.log('Web asset JavaScript syntax, 10 JD URL cases, saved-task execution, verified shortcut routing, timeout, status-refresh, task-link editor, private stable-signing configuration and local data backup/restore checks passed.');
