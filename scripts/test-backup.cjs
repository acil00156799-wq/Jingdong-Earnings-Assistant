const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const html = fs.readFileSync('app/src/main/assets/index.html', 'utf8');
const mainJava = fs.readFileSync('app/src/main/java/com/personal/jdassistant/MainActivity.java', 'utf8');
const claimService = fs.readFileSync('app/src/main/java/com/personal/jdassistant/JdClaimAccessibilityService.java', 'utf8');
assert.match(mainJava, /account\." \+ accountId \+ "\./, 'native claim preferences are account-scoped');
assert.match(mainJava, /getClaimStatus\(String accountId\)/, 'claim status requires an account id');
assert.match(mainJava, /setBlockNetworkLoads\(true\)/, 'WebView blocks network content');
assert.match(mainJava, /setAllowFileAccessFromFileURLs\(false\)/, 'file URLs cannot read other file URLs');
assert.match(mainJava, /setAllowUniversalAccessFromFileURLs\(false\)/, 'file URLs cannot access arbitrary origins');
assert.match(mainJava, /private boolean isLocalAppPage\(Uri uri\)/, 'WebView bridge is restricted to the bundled local page');
assert.match(mainJava, /return !isLocalAppPage\(request\.getUrl\(\)\)/, 'external navigations are blocked in the bridge-enabled WebView');
assert.match(mainJava, /if \(!accountId\.equals\(p\.getString\("active_attempt_account_id", ""\)\)\) return;/, 'stopping an inactive account must not overwrite its history');
assert.match(mainJava, /无障碍权限已关闭，旧任务已停止/, 'disabling accessibility clears any previous active attempt');
assert.match(mainJava, /Math\.max\(now, p\.getLong\(prefix \+ "attempt_id", 0L\) \+ 1L\)/, 'repeated attempts receive a fresh attempt id');
assert.match(claimService, /active_attempt_account_id/, 'accessibility service follows the active account attempt');
assert.match(claimService, /account\." \+ accountId \+ "\./, 'accessibility preferences are account-scoped');
assert.match(claimService, /Re-check ownership immediately before clicking/, 'service rechecks account ownership before a click');
assert.match(html, /help\.jd\.com\/user\/issue\/167-4224\.html/, 'official JD bean help source is present');
assert.match(html, /help\.jd\.com\/user\/issue\/168-4458\.html/, 'official JD red packet help source is present');
assert.match(html, /help\.jd\.com\/user\/issue\/169-335\.html/, 'official JD coupon help source is present');
assert.match(html, /function openOfficialHelp\(url\)/, 'official help links pass through the JD URL safety check');
assert.match(html, /用户已核验/, 'income history labels user-verified records');
assert.match(html, /导入记录\/待核验/, 'income history distinguishes imported unverified records');
const match = html.match(/<script>([\s\S]*?)<\/script>/i);
assert.ok(match, 'inline application script exists');
const script = match[1];
new Function(script);

const start = script.indexOf('function exportBackup()');
const end = script.indexOf('function render()', start);
assert.ok(start >= 0 && end > start, 'backup functions exist');
const chunk = script.slice(start, end);
const store = new Map();
const elements = { backupData: { value: '', focus() {}, select() {} }, backupHint: { textContent: '' } };
const alerts = [];
let failAtWrite = 0;
let writeCount = 0;
const context = {
  KEY: 'jd_assistant_accounts_v3', ACTIVE: 'jd_assistant_active_v3', ROUTES: 'jd_assistant_module_routes_v1',
  activeId: 'acct1',
  accounts: [{ id: 'acct1', name: '主账号', tasks: [{ id: 123, name: '签到任务', url: 'https://bean.jd.com/', status: '待核对', created: '2026-11-08' }], incomes: [{ name: '京豆签到', amount: 20, unit: 'beans', date: '2026-11-08', verifiedByUser: true, verificationSource: 'user_confirmed' }], created: '2026-11-01' }],
  localStorage: { getItem(k) { return store.has(k) ? store.get(k) : null; }, setItem(k, v) { writeCount += 1; if (failAtWrite === writeCount) throw new Error('simulated storage full'); store.set(k, String(v)); }, removeItem(k) { store.delete(k); } },
  document: { getElementById(id) { return elements[id]; } },
  alert(message) { alerts.push(String(message)); }, confirm() { return true; },
  moduleRoutes() { try { return JSON.parse(store.get('jd_assistant_module_routes_v1') || '{}'); } catch { return {}; } },
  isAllowedJdUrl(value) { try { const u = new URL(value), h = u.hostname.toLowerCase(); return u.protocol === 'https:' && !u.username && !u.password && (!u.port || u.port === '443') && (h === 'jd.com' || h.endsWith('.jd.com') || h === 'jingdong.com' || h.endsWith('.jingdong.com')); } catch { return false; } },
  render() {}, renderRouteHint() {}, Date, JSON, Number, Array, Object, URL
};
vm.createContext(context);
vm.runInContext(chunk, context);

context.localStorage.setItem(context.ROUTES, JSON.stringify({ '京豆签到': 'https://bean.jd.com/' }));
context.exportBackup();
const validBackup = elements.backupData.value;
assert.match(validBackup, /acct1/);
context.accounts = [{ id: 'other', name: '其他', tasks: [], incomes: [] }];
context.activeId = 'other';
context.importBackup();
assert.equal(context.accounts[0].id, 'acct1');
assert.equal(context.accounts[0].tasks.length, 1);
assert.equal(context.accounts[0].incomes[0].amount, 20);
assert.equal(JSON.parse(context.localStorage.getItem(context.ROUTES))['京豆签到'], 'https://bean.jd.com/');

// A failed multi-key restore must roll back both persisted data and in-memory state.
context.accounts = [{ id: 'before', name: 'Before', tasks: [], incomes: [] }];
context.activeId = 'before';
store.set(context.KEY, JSON.stringify(context.accounts));
store.set(context.ACTIVE, 'before');
elements.backupData.value = validBackup;
writeCount = 0;
failAtWrite = 2;
context.importBackup();
failAtWrite = 0;
assert.equal(context.accounts[0].id, 'before', 'failed restore must restore in-memory accounts');
assert.equal(context.activeId, 'before', 'failed restore must restore in-memory active account');
assert.equal(JSON.parse(store.get(context.KEY))[0].id, 'before', 'failed restore must roll back persisted accounts');

assert.equal(context.isAllowedJdUrl('https://user:pass@bean.jd.com/'), false, 'credential-bearing URLs must be rejected');
assert.equal(context.isAllowedJdUrl('https://bean.jd.com:8443/'), false, 'non-standard ports must be rejected');
assert.equal(context.isAllowedJdUrl('https://bean.jd.com/'), true, 'normal JD HTTPS URLs remain allowed');

const beforeId = context.accounts[0].id;
const duplicateAccounts = JSON.parse(validBackup);
duplicateAccounts.accounts.push({ ...duplicateAccounts.accounts[0], name: '重复 ID' });
elements.backupData.value = JSON.stringify(duplicateAccounts);
context.importBackup();
assert.equal(context.accounts[0].id, beforeId, 'duplicate account IDs must be rejected');
assert.ok(alerts.some(x => x.includes('账号 ID 不可重复')));

const duplicateTasks = JSON.parse(validBackup);
duplicateTasks.accounts[0].tasks.push({ ...duplicateTasks.accounts[0].tasks[0], name: '重复任务 ID' });
elements.backupData.value = JSON.stringify(duplicateTasks);
context.importBackup();
assert.equal(context.accounts[0].id, beforeId, 'duplicate task IDs must be rejected');
assert.ok(alerts.some(x => x.includes('任务编号重复')));

const malformedRoutes = JSON.parse(validBackup);
malformedRoutes.moduleRoutes = [];
elements.backupData.value = JSON.stringify(malformedRoutes);
context.importBackup();
assert.equal(context.accounts[0].id, beforeId, 'non-object module routes must be rejected');

const poisonedRoutes = JSON.parse(validBackup);
poisonedRoutes.moduleRoutes = JSON.parse('{"__proto__":"https://bean.jd.com/"}');
elements.backupData.value = JSON.stringify(poisonedRoutes);
context.importBackup();
assert.equal(context.accounts[0].id, beforeId, 'unknown module route names must be rejected');

const unsafe = JSON.parse(validBackup);
unsafe.accounts[0].tasks[0].url = 'https://evil.example/';
elements.backupData.value = JSON.stringify(unsafe);
context.importBackup();
assert.equal(context.accounts[0].id, beforeId, 'unsafe external URL must not change saved data');
assert.ok(alerts.some(x => x.includes('非京东官方 HTTPS')));

elements.backupData.value = '{broken json';
context.importBackup();
assert.equal(context.accounts[0].id, beforeId, 'malformed JSON must not change saved data');

elements.backupData.value = 'x'.repeat(2000001);
context.importBackup();
assert.equal(context.accounts[0].id, beforeId, 'oversized backup must not change saved data');

console.log('PASS: JavaScript syntax, account-scoped claim state, pre-click ownership guard, backup export/restore and rollback, duplicate ID rejection, module route allowlist/shape, HTTPS host, credential and port validation, malformed JSON, oversized backup');
