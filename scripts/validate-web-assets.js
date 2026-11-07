const fs = require('node:fs');
const html = fs.readFileSync('app/src/main/assets/index.html', 'utf8');
const match = html.match(/<script>([\\s\\S]*?)<\\/script>/i);
if (!match) throw new Error('index.html script block not found');
new Function(match[1]);

const validatorMatch = match[1].match(/function isOfficialJdUrl\\(value\\)\\{[\\s\\S]*?\\nfunction addTask/);
if (!validatorMatch) throw new Error('JD URL validator not found');
const validatorSource = validatorMatch[0].replace(/\\nfunction addTask$/, '');
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
