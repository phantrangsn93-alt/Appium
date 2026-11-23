#!/usr/bin/env node
/*
 * Side-by-side report of the same suite run on two devices (e.g. iOS and Android), one self-contained HTML file
 * with a screenshot per test and device.
 *
 * Usage (from the project root):
 *   node tools/report/compare-devices.js --ios <allure-results-dir> --android <allure-results-dir>
 *        [--title "Thị trường – Bảng giá"] [--out reports/<ts>/BSC_iOS_vs_Android.html]
 * A test case = class + method + parameters; when a case ran twice on one device the last run wins.
 * Screenshot = the last PNG attached to the test (failure screenshot, or the test's own evidence shot).
 * Images are downscaled with macOS `sips` (kept as-is where it is not available) and embedded as data URIs.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const os = require('os');
const { execFileSync } = require('child_process');

const args = process.argv.slice(2);
const opt = {};
for (let i = 0; i < args.length; i += 2) opt[args[i].replace(/^--/, '')] = args[i + 1];
if (!opt.ios || !opt.android) {
  console.error('Usage: compare-devices.js --ios <allure-results> --android <allure-results> [--title t] [--out file]');
  process.exit(2);
}
const pad = n => String(n).padStart(2, '0');
const now = new Date();
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}`;
const out = opt.out || path.join('reports', stamp, 'BSC_iOS_vs_Android.html');
const title = opt.title || 'Kết quả kiểm thử tự động – iOS và Android';

function attachments(node) {
  let list = (node.attachments || []).slice();
  (node.steps || []).forEach(s => { list = list.concat(attachments(s)); });
  return list;
}

function load(dir) {
  const cases = new Map();
  for (const f of fs.readdirSync(dir).filter(n => n.endsWith('-result.json'))) {
    const r = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
    const params = (r.parameters || []).map(p => p.value).filter(v => v !== undefined && v !== '');
    const cls = (r.labels || []).find(l => l.name === 'testClass');
    const key = `${cls ? cls.value : ''}#${r.name}#${params.join('|')}`;
    const prev = cases.get(key);
    if (prev && prev.stop > (r.stop || 0)) continue;
    const pngs = attachments(r).filter(a => a.type === 'image/png' || /\.png$/i.test(a.source));
    cases.set(key, {
      key,
      cls: cls ? cls.value.replace(/.*\./, '') : '',
      name: r.name,
      desc: (r.description || '').trim(),
      params,
      status: r.status,
      message: ((r.statusDetails && r.statusDetails.message) || '').trim(),
      start: r.start || 0,
      stop: r.stop || 0,
      shot: pngs.length ? path.join(dir, pngs[pngs.length - 1].source) : null,
    });
  }
  return cases;
}

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'bsc-cmp-'));
let n = 0;
function img(file) {
  if (!file || !fs.existsSync(file)) return null;
  const small = path.join(tmp, `s${n++}.jpg`);
  try {
    execFileSync('sips', ['-Z', '640', '-s', 'format', 'jpeg', '-s', 'formatOptions', '70', file, '--out', small], { stdio: 'ignore' });
    return 'data:image/jpeg;base64,' + fs.readFileSync(small).toString('base64');
  } catch (e) {
    return 'data:image/png;base64,' + fs.readFileSync(file).toString('base64');
  }
}

const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const LABEL = { passed: 'PASS', failed: 'FAIL', broken: 'LỖI', skipped: 'BỎ QUA', unknown: '?' };

const ios = load(opt.ios), android = load(opt.android);
const keys = [...new Set([...ios.keys(), ...android.keys()])];
const first = k => Math.min((ios.get(k) || {}).start || Infinity, (android.get(k) || {}).start || Infinity);
keys.sort((a, b) => first(a) - first(b));

function count(map) {
  const c = { total: 0, passed: 0, failed: 0, skipped: 0 };
  for (const t of map.values()) {
    c.total++;
    if (t.status === 'passed') c.passed++;
    else if (t.status === 'skipped') c.skipped++;
    else c.failed++;
  }
  return c;
}
function span(map) {
  const v = [...map.values()];
  if (!v.length) return '';
  const s = new Date(Math.min(...v.map(t => t.start))), e = new Date(Math.max(...v.map(t => t.stop)));
  const f = d => `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return `${f(s)} – ${pad(e.getHours())}:${pad(e.getMinutes())}`;
}
const ci = count(ios), ca = count(android);
const both = keys.filter(k => ios.has(k) && android.has(k));
const same = both.filter(k => ios.get(k).status === android.get(k).status).length;

function cell(t) {
  if (!t) return '<td class="dev none"><p class="muted">Không chạy trên thiết bị này</p></td>';
  const src = img(t.shot);
  return `<td class="dev"><span class="st ${t.status}">${LABEL[t.status] || t.status}</span>`
    + (t.message && t.status !== 'passed' ? `<p class="msg">${esc(t.message.split('\n').slice(0, 4).join('\n'))}</p>` : '')
    + (src ? `<a href="${src}" target="_blank"><img src="${src}" alt="Ảnh màn hình ${esc(t.name)}" loading="lazy"></a>` : '<p class="muted">Không có ảnh màn hình</p>')
    + '</td>';
}

const rows = keys.map((k, i) => {
  const a = ios.get(k), b = android.get(k), t = a || b;
  const diff = a && b && a.status !== b.status;
  const label = t.desc || t.name;
  return `<tr class="${diff ? 'diff' : ''}" data-s="${[a && a.status, b && b.status].join(' ')}">`
    + `<td class="case"><b>${i + 1}. ${esc(label)}</b><p class="muted">${esc(t.cls)}.${esc(t.name)}${t.params.length ? ' · ' + esc(t.params.join(' · ')) : ''}</p>`
    + (diff ? '<p class="flag">Khác kết quả giữa 2 thiết bị</p>' : '') + '</td>'
    + cell(a) + cell(b) + '</tr>';
}).join('\n');

const sum = (c, label, s) => `<div class="card"><h3>${label}</h3><p class="muted">${s}</p>
<div class="nums"><div><b>${c.total}</b><span>Tổng</span></div><div class="p"><b>${c.passed}</b><span>Pass</span></div>
<div class="f"><b>${c.failed}</b><span>Fail / lỗi</span></div><div class="k"><b>${c.skipped}</b><span>Bỏ qua</span></div></div></div>`;

const html = `<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title><style>
:root{--bg:#F0F1F7;--block:#fff;--line:#E6E6E6;--txt:#111;--sub:#5f6368;--acc:#2A5DAA;--up:#238619;--down:#D13338;--flat:#B37200}
@media (prefers-color-scheme:dark){:root{--bg:#111315;--block:#242729;--line:#3A3D3E;--txt:#f2f2f2;--sub:#a7abb0;--acc:#4C9BE8;--up:#3ccf4e;--down:#FF5858;--flat:#FFCC00}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--txt);font:15px/1.45 Helvetica,Arial,sans-serif}
.wrap{max-width:1280px;margin:0 auto;padding:24px 16px}h1{font-size:26px;margin:0 0 4px}h3{margin:0 0 4px;font-size:18px}
.muted{color:var(--sub);margin:2px 0;font-size:13px}.head{background:linear-gradient(135deg,#008999,#015CA2);color:#fff;border-radius:8px;padding:24px;margin-bottom:16px}
.head .muted{color:#dceff2}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px;margin-bottom:16px}
.card{background:var(--block);border:1px solid var(--line);border-radius:8px;padding:16px}.nums{display:flex;gap:18px;margin-top:10px}
.nums div{display:flex;flex-direction:column}.nums b{font-size:26px}.nums span{font-size:12px;color:var(--sub)}
.p b{color:var(--up)}.f b{color:var(--down)}.k b{color:var(--flat)}
.bar{display:flex;gap:8px;flex-wrap:wrap;margin:0 0 12px}.bar button{border:1px solid var(--line);background:var(--block);color:var(--txt);border-radius:4px;padding:6px 12px;cursor:pointer;font:inherit}
.bar button.on{background:var(--acc);color:#fff;border-color:var(--acc)}
.tbl{overflow-x:auto;background:var(--block);border:1px solid var(--line);border-radius:8px}
table{border-collapse:collapse;width:100%;min-width:760px}th,td{border-bottom:1px solid var(--line);padding:12px;vertical-align:top;text-align:left}
th{position:sticky;top:0;background:var(--block);z-index:1}td.case{width:34%}td.dev{width:33%}
td.dev img{display:block;max-width:100%;max-height:420px;margin-top:8px;border:1px solid var(--line);border-radius:6px}
.st{display:inline-block;font-weight:700;font-size:12px;padding:3px 10px;border-radius:4px;color:#fff}
.st.passed{background:#238619}.st.failed,.st.broken{background:#D13338}.st.skipped{background:#B37200}
.msg{white-space:pre-wrap;font-size:12px;color:var(--down);margin:6px 0 0;word-break:break-word}
tr.diff td.case{box-shadow:inset 4px 0 0 var(--flat)}.flag{color:var(--flat);font-size:12px;font-weight:700;margin:4px 0 0}
</style></head><body><div class="wrap">
<div class="head"><h1>${esc(title)}</h1><p class="muted">Ứng dụng BSC Smart Invest · môi trường production · chỉ đọc, không đặt lệnh · xuất lúc ${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()} ${pad(now.getHours())}:${pad(now.getMinutes())}</p></div>
<div class="cards">${sum(ci, 'iOS · iPhone 16 Pro', span(ios))}${sum(ca, 'Android · Xiaomi Mi Mix 2S', span(android))}
<div class="card"><h3>So sánh</h3><p class="muted">Test case chạy trên cả 2 máy</p><div class="nums"><div><b>${both.length}</b><span>Chạy cả 2</span></div>
<div class="p"><b>${same}</b><span>Cùng kết quả</span></div><div class="k"><b>${both.length - same}</b><span>Khác kết quả</span></div></div></div></div>
<div class="bar"><button class="on" data-f="all">Tất cả (${keys.length})</button><button data-f="failed">Có fail / lỗi</button><button data-f="diff">Khác kết quả</button><button data-f="skipped">Có bỏ qua</button></div>
<div class="tbl"><table><thead><tr><th>Test case</th><th>iOS · iPhone 16 Pro</th><th>Android · Xiaomi Mi Mix 2S</th></tr></thead><tbody>
${rows}
</tbody></table></div></div>
<script>
document.querySelectorAll('.bar button').forEach(function(b){b.onclick=function(){
document.querySelectorAll('.bar button').forEach(function(x){x.classList.remove('on')});b.classList.add('on');var f=b.dataset.f;
document.querySelectorAll('tbody tr').forEach(function(r){var s=r.dataset.s;var show=f==='all'||(f==='diff'?r.classList.contains('diff'):(f==='failed'?/failed|broken/.test(s):s.indexOf(f)>=0));r.hidden=!show;});};});
</script></body></html>`;

fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, html);
fs.rmSync(tmp, { recursive: true, force: true });
console.log(`${out}  (${keys.length} test case, iOS ${ci.passed}/${ci.total} pass, Android ${ca.passed}/${ca.total} pass, ${(fs.statSync(out).size / 1048576).toFixed(1)} MB)`);
