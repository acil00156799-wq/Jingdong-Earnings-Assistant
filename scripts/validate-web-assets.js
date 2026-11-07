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
console.log('Web asset JavaScript syntax and 10 JD URL cases and saved-task execution checks passed.');
