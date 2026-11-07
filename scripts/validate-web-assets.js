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
console.log('Web asset JavaScript syntax and 10 JD URL validation cases passed.');
