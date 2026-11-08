'use strict';
/*
 * Browser version of the ISTQB Test Summary Report: <outDir>/index.html + <outDir>/attachments/.
 * Self-contained (inline CSS/JS, no network) - open index.html with a double-click.
 * Each test case row expands to its screenshots (click = full size), error message, video and page source.
 */
const fs = require('fs');
const path = require('path');

const esc = s => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const li = items => `<ul>${items.map(t => `<li>${esc(t)}</li>`).join('')}</ul>`;

module.exports = function writeHtml(d) {
  const attDir = path.join(d.outDir, 'attachments');
  fs.mkdirSync(attDir, { recursive: true });
  let n = 0;
  const copy = a => {
    if (!fs.existsSync(a.file)) return null;
    const ext = path.extname(a.file) || '';
    const name = `${String(++n).padStart(4, '0')}${ext}`;
    fs.copyFileSync(a.file, path.join(attDir, name));
    return `attachments/${name}`;
  };

  const rows = d.list.map((c, i) => {
    const atts = (c.attachments || []);
    const images = [], others = [], texts = [];
    for (const a of atts) {
      if (a.type.startsWith('image/')) { const src = copy(a); if (src) images.push({ src, name: a.name }); }
      else if (a.type.startsWith('text/plain') && fs.existsSync(a.file) && fs.statSync(a.file).size < 4000) {
        texts.push({ name: a.name, text: fs.readFileSync(a.file, 'utf8') });
      } else { const src = copy(a); if (src) others.push({ src, name: a.name, type: a.type }); }
    }
    const detail = `
      ${c.detail ? `<p><b>Dữ liệu:</b> ${esc(c.detail)}</p>` : ''}
      ${c.message ? `<p class="msg ${c.status}"><b>${c.status === 'SKIP' ? 'Lý do bỏ qua' : 'Thông báo lỗi'}:</b> ${esc(c.message)}</p>` : ''}
      ${texts.map(t => `<p><b>${esc(t.name)}:</b> ${esc(t.text)}</p>`).join('')}
      ${images.length ? `<div class="shots">${images.map(im => `<figure><a href="${im.src}" target="_blank" data-full="${im.src}"><img loading="lazy" src="${im.src}" alt="${esc(im.name)}"></a><figcaption>${esc(im.name)}</figcaption></figure>`).join('')}</div>` : '<p class="muted">Không có ảnh chụp cho test case này.</p>'}
      ${others.length ? `<p>${others.map(o => `<a class="chip" href="${o.src}" target="_blank">${o.type.startsWith('video') ? '▶ ' : '📄 '}${esc(o.name)}</a>`).join(' ')}</p>` : ''}`;
    return `<tr class="case" data-status="${c.status}" data-text="${esc((c.id + ' ' + c.name + ' ' + c.module + ' ' + c.detail).toLowerCase())}">
        <td class="num">${i + 1}</td><td><code>${esc(c.id)}</code></td>
        <td>${esc(c.name)}${c.detail && !c.name.includes(c.detail) ? `<div class="muted small">${esc(c.detail)}</div>` : ''}</td>
        <td>${esc(c.module)}</td><td><span class="badge ${c.status}">${esc(d.statusVi[c.status] || c.status)}</span></td>
        <td class="num">${c.duration.toFixed(1)}</td><td class="num">${images.length ? `📷 ${images.length}` : ''}</td></tr>
      <tr class="detail" hidden><td colspan="7">${detail}</td></tr>`;
  }).join('');

  const moduleRows = Object.entries(d.byModule).map(([m, v]) => {
    const pp = d.pct(v.pass, v.pass + v.fail);
    return `<tr><td>${esc(m)}</td><td class="num">${v.total}</td><td class="num ok">${v.pass}</td><td class="num ${v.fail ? 'ko' : ''}">${v.fail}</td>
      <td class="num ${v.skip ? 'sk' : ''}">${v.skip}</td><td><div class="bar"><span style="width:${pp}%"></span></div><span class="small">${pp}%</span></td><td class="num">${esc(d.fmtDur(v.duration))}</td></tr>`;
  }).join('');

  const failed = d.list.filter(c => c.status === 'FAIL'), skipped = d.list.filter(c => c.status === 'SKIP');
  const caseTable = (arr, label) => arr.length === 0 ? `<p class="muted">Không có.</p>` :
    `<table><thead><tr><th>Mã TC</th><th>Test case</th><th>${label}</th></tr></thead><tbody>${arr.map(c => `<tr><td><code>${esc(c.id)}</code></td><td>${esc(c.name)}</td><td>${esc(c.message || '—')}</td></tr>`).join('')}</tbody></table>`;

  const html = `<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Báo cáo kiểm thử – ${esc(d.meta.documentId)}-${esc(d.stamp)}</title>
<style>
:root{--bg:#f6f7fb;--card:#fff;--fg:#1d2433;--muted:#6b7385;--line:#dfe3ec;--head:#1f3864;--headbg:#e6ecf7;--ok:#1e7b34;--ko:#c62828;--sk:#b26a00;--okbg:#e7f4ea;--kobg:#fdeaea;--skbg:#fff3e0}
@media (prefers-color-scheme:dark){:root{--bg:#12151c;--card:#1b2029;--fg:#e6e9ef;--muted:#9aa3b5;--line:#2c3340;--head:#9db8ef;--headbg:#232b3a;--ok:#5cc27a;--ko:#ff6b6b;--sk:#ffb74d;--okbg:#17301f;--kobg:#3a1c1c;--skbg:#3a2c14}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:14px/1.5 -apple-system,Segoe UI,Roboto,Arial,sans-serif}
.wrap{max-width:1200px;margin:0 auto;padding:24px 16px 64px}h1{font-size:26px;margin:0 0 4px;color:var(--head)}
h2{font-size:19px;color:var(--head);margin:32px 0 10px;border-bottom:2px solid var(--line);padding-bottom:6px}h3{font-size:15px;margin:18px 0 8px}
.sub{color:var(--muted);margin:0 0 16px}.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:16px}
.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:12px;margin:16px 0}
.kpi{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px}.kpi b{display:block;font-size:26px}
.kpi.ok b{color:var(--ok)}.kpi.ko b{color:var(--ko)}.kpi.sk b{color:var(--sk)}
.verdict{border-radius:10px;padding:12px 16px;font-weight:600;margin:12px 0}.verdict.ok{background:var(--okbg);color:var(--ok)}.verdict.ko{background:var(--kobg);color:var(--ko)}
.tbl{overflow-x:auto}table{width:100%;border-collapse:collapse;background:var(--card);border:1px solid var(--line)}
th,td{border-bottom:1px solid var(--line);padding:7px 9px;text-align:left;vertical-align:top}th{background:var(--headbg);color:var(--head);font-weight:600;position:sticky;top:0}
td.num,th.num{text-align:right;white-space:nowrap}.ok{color:var(--ok)}.ko{color:var(--ko);font-weight:600}.sk{color:var(--sk)}
.badge{display:inline-block;border-radius:999px;padding:1px 10px;font-weight:600;font-size:12px;white-space:nowrap}
.badge.PASS{background:var(--okbg);color:var(--ok)}.badge.FAIL{background:var(--kobg);color:var(--ko)}.badge.SKIP{background:var(--skbg);color:var(--sk)}
tr.case{cursor:pointer}tr.case:hover{background:var(--headbg)}tr.detail td{background:var(--bg)}
.shots{display:flex;flex-wrap:wrap;gap:12px}.shots figure{margin:0;width:180px}.shots img{width:180px;border:1px solid var(--line);border-radius:6px;display:block}
figcaption{font-size:12px;color:var(--muted)}.msg.FAIL{color:var(--ko)}.msg.SKIP{color:var(--sk)}.muted{color:var(--muted)}.small{font-size:12px}
.bar{display:inline-block;width:90px;height:8px;background:var(--line);border-radius:4px;margin-right:6px;vertical-align:middle}.bar span{display:block;height:100%;background:var(--ok);border-radius:4px}
.filters{display:flex;flex-wrap:wrap;gap:8px;margin:8px 0 12px}.filters button,.filters input{border:1px solid var(--line);background:var(--card);color:var(--fg);border-radius:8px;padding:6px 12px;font:inherit}
.filters button.on{background:var(--head);color:var(--card);border-color:var(--head)}.filters input{flex:1;min-width:200px}
.chip{display:inline-block;border:1px solid var(--line);border-radius:999px;padding:2px 10px;margin:2px;text-decoration:none;color:var(--fg)}
dl.kv{display:grid;grid-template-columns:minmax(140px,220px) 1fr;gap:6px 16px;margin:0}dl.kv dt{font-weight:600;color:var(--muted)}dl.kv dd{margin:0}
#lb{position:fixed;inset:0;background:rgba(0,0,0,.85);display:flex;align-items:center;justify-content:center;padding:24px;cursor:zoom-out}#lb img{max-width:100%;max-height:100%}
@media print{tr.detail{display:table-row!important}.filters{display:none}}
</style></head><body><div class="wrap">
<h1>Báo cáo tổng kết kiểm thử</h1>
<p class="sub">Test Summary Report (ISTQB / IEEE 829 / ISO/IEC/IEEE 29119-3) · ${esc(d.meta.project)}${d.runTitle ? ' – ' + esc(d.runTitle) : ''}</p>
<div class="card"><dl class="kv">
<dt>Mã tài liệu</dt><dd>${esc(d.meta.documentId)}-${esc(d.stamp)}</dd><dt>Phiên bản</dt><dd>${esc(d.meta.documentVersion)}</dd>
<dt>Ngày lập</dt><dd>${esc(d.viDate(d.now))}</dd><dt>Người lập</dt><dd>${esc(d.meta.preparedBy)} – ${esc(d.meta.organization)}</dd></dl></div>

<h2>1. Tóm tắt</h2>
<div class="kpis"><div class="kpi"><span>Tổng test case</span><b>${d.total}</b></div><div class="kpi ok"><span>Đạt</span><b>${d.pass}</b></div>
<div class="kpi ko"><span>Không đạt</span><b>${d.fail}</b></div><div class="kpi sk"><span>Bỏ qua</span><b>${d.skip}</b></div>
<div class="kpi"><span>Tỷ lệ thực thi</span><b>${d.executedPct}%</b></div><div class="kpi"><span>Tỷ lệ đạt</span><b>${d.passPct}%</b></div></div>
<div class="verdict ${d.exitMet ? 'ok' : 'ko'}">Kết luận: ${esc(d.conclusion)}</div>
<p class="muted">Thực thi: ${d.suiteStart ? esc(d.viDate(d.suiteStart)) : '?'} → ${d.suiteEnd ? esc(d.viDate(d.suiteEnd)) : '?'} · tổng thời lượng test ${esc(d.fmtDur(d.totalDuration))}</p>

<h2>2. Mục tiêu kiểm thử</h2>${li(d.meta.objectives)}
<h2>3. Phạm vi kiểm thử</h2><h3>3.1 Trong phạm vi</h3>${li(d.meta.inScope)}<h3>3.2 Ngoài phạm vi</h3>${li(d.meta.outOfScope)}
<h2>4. Phương pháp kiểm thử</h2><div class="card"><dl class="kv"><dt>Cấp độ</dt><dd>${esc(d.meta.testLevel)}</dd><dt>Loại</dt><dd>${esc(d.meta.testType)}</dd></dl></div>${li(d.meta.approach)}
<h2>5. Môi trường kiểm thử</h2><div class="card"><dl class="kv">
<dt>Môi trường</dt><dd>${esc(d.environment)}</dd><dt>Ứng dụng</dt><dd>com.bsc.mobiletrading – ${esc(d.appVersion)}</dd>
<dt>Thiết bị</dt><dd>${esc(d.model)} – Android ${esc(d.android)} (udid ${esc(d.udid)})</dd>
<dt>Công cụ</dt><dd>Appium ${esc(d.appium)} + UiAutomator2, Java 17, TestNG 7.11, Allure 2.29</dd></dl></div>

<h2>6. Kết quả kiểm thử</h2><h3>6.1 Tổng hợp theo chức năng</h3>
<div class="tbl"><table><thead><tr><th>Chức năng</th><th class="num">Tổng</th><th class="num">Đạt</th><th class="num">Không đạt</th><th class="num">Bỏ qua</th><th>Tỷ lệ đạt</th><th class="num">Thời lượng</th></tr></thead><tbody>${moduleRows}</tbody></table></div>
<h3>6.2 Chi tiết từng test case <span class="muted small">(bấm vào dòng để xem ảnh chụp và bằng chứng)</span></h3>
<div class="filters"><button class="on" data-f="ALL">Tất cả (${d.total})</button><button data-f="PASS">Đạt (${d.pass})</button>
<button data-f="FAIL">Không đạt (${d.fail})</button><button data-f="SKIP">Bỏ qua (${d.skip})</button>
<button id="expand">Mở tất cả</button><input id="q" type="search" placeholder="Tìm theo mã, tên, chức năng…"></div>
<div class="tbl"><table id="cases"><thead><tr><th class="num">#</th><th>Mã TC</th><th>Test case</th><th>Chức năng</th><th>Kết quả</th><th class="num">Giây</th><th class="num">Ảnh</th></tr></thead><tbody>${rows}</tbody></table></div>

<h2>7. Sự cố và phát hiện</h2><h3>7.1 Test case không đạt</h3>${caseTable(failed, 'Mô tả lỗi')}
<h3>7.2 Test case bị bỏ qua</h3>${caseTable(skipped, 'Lý do')}
<h3>7.3 Phát hiện trên ứng dụng</h3><div class="tbl"><table><thead><tr><th>Mã</th><th>Mức độ</th><th>Mô tả</th><th>Trạng thái</th><th>Ghi chú</th></tr></thead><tbody>
${d.meta.findings.map(f => `<tr><td>${esc(f.id)}</td><td>${esc(f.severity)}</td><td>${esc(f.title)}</td><td>${esc(f.status)}</td><td>${esc(f.note)}</td></tr>`).join('')}</tbody></table></div>

<h2>8. Đánh giá theo tiêu chí kết thúc</h2><div class="tbl"><table><thead><tr><th>Tiêu chí</th><th>Yêu cầu</th><th>Thực tế</th><th>Đánh giá</th></tr></thead><tbody>
${d.exitRows.map(r => `<tr><td>${esc(r[0])}</td><td>${esc(r[1])}</td><td>${esc(r[2])}</td><td><span class="badge ${r[3] ? 'PASS' : 'FAIL'}">${r[3] ? 'Đạt' : 'Chưa đạt'}</span></td></tr>`).join('')}</tbody></table></div>
<h2>9. Rủi ro tồn đọng và hạn chế</h2>${li(d.meta.risks)}
<h2>10. Khuyến nghị</h2>${li(d.meta.recommendations)}
<h2>11. Phê duyệt</h2><div class="tbl"><table><thead><tr><th>Vai trò</th><th>Họ tên / Chữ ký</th><th>Ngày</th></tr></thead><tbody>${d.meta.approvers.map(a => `<tr><td>${esc(a)}</td><td></td><td></td></tr>`).join('')}</tbody></table></div>
</div>
<script>
(function(){
  var rows=[].slice.call(document.querySelectorAll('tr.case')), f='ALL', q='';
  function apply(){rows.forEach(function(r){var show=(f==='ALL'||r.dataset.status===f)&&(!q||r.dataset.text.indexOf(q)>=0);r.hidden=!show;if(!show)r.nextElementSibling.hidden=true;});}
  rows.forEach(function(r){r.addEventListener('click',function(){r.nextElementSibling.hidden=!r.nextElementSibling.hidden;});});
  document.querySelectorAll('.filters button[data-f]').forEach(function(b){b.addEventListener('click',function(){document.querySelectorAll('.filters button[data-f]').forEach(function(x){x.classList.remove('on');});b.classList.add('on');f=b.dataset.f;apply();});});
  document.getElementById('q').addEventListener('input',function(e){q=e.target.value.trim().toLowerCase();apply();});
  var open=false;document.getElementById('expand').addEventListener('click',function(e){open=!open;e.target.textContent=open?'Thu gọn tất cả':'Mở tất cả';rows.forEach(function(r){if(!r.hidden)r.nextElementSibling.hidden=!open;});});
  document.addEventListener('click',function(e){var a=e.target.closest('a[data-full]');if(!a)return;e.preventDefault();var lb=document.createElement('div');lb.id='lb';lb.innerHTML='<img src="'+a.dataset.full+'">';lb.onclick=function(){lb.remove();};document.body.appendChild(lb);});
})();
</script></body></html>`;
  const file = path.join(d.outDir, 'index.html');
  fs.writeFileSync(file, html);
  return file;
};
