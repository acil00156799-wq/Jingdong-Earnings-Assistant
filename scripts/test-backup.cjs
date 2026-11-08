const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const html = fs.readFileSync('app/src/main/assets/index.html', 'utf8');
const match = html.match(/<script>([\\s\\S]*?)<\\/script>/i);
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
const context = {
  KEY: 'jd_assistant_accounts_v3', ACTIVE: 'jd_assistant_active_v3', ROUTES: 'jd_assistant_module_routes_v1',
  activeId: 'acct1',
  accounts: [{ id: 'acct1', name: '主账号', tasks: [{ id: 123, name: '签到任务', url: 'https://bean.jd.com/', status: '待核对', created: '2026-11-08' }], incomes: [{ name: '京豆签到', amount: 20, unit: 'beans', date: '2026-11-08', verifiedByUser: true, verificationSource: 'user_confirmed' }], created: '2026-11-01' }],
  localStorage: { getItem(k) { return store.has(k) ? store.get(k) : null; }, setItem(k, v) { store.set(k, String(v)); }, removeItem(k) { store.delete(k); } },
  document: { getElementById(id) { return elements[id]; } },
  alert(message) { alerts.push(String(message)); }, confirm() { return true; },
  moduleRoutes() { try { return JSON.parse(store.get('jd_assistant_module_routes_v1') || '{}'); } catch { return {}; } },
  isAllowedJdUrl(value) { try { const u = new URL(value), h = u.hostname.toLowerCase(); return u.protocol === 'https:' && (h === 'jd.com' || h.endsWith('.jd.com') || h === 'jingdong.com' || h.endsWith('.jingdong.com')); } catch { return false; } },
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

const beforeId = context.accounts[0].id;
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

console.log('PASS: JavaScript syntax, backup export/restore, module routes, external URL rejection, malformed JSON, oversized backup');
