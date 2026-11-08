#!/usr/bin/env node
/*
 * ISTQB Test Summary / Test Completion Report (IEEE 829 / ISO/IEC/IEEE 29119-3) generator.
 *
 * Usage (from the project root):
 *   node tools/report/generate-report.js [testng-results.xml | allure-results-dir ...] [--out reports/x.docx] [--title "Đợt A"]
 * Defaults: input = target/surefire-reports/testng-results.xml, output = reports/BSC_TestSummaryReport_<ts>.docx
 * Several result files can be given (e.g. archived runs of phase A, unit tests, phase B): they are merged,
 * the LAST execution of each test case wins (re-runs supersede earlier attempts, retried attempts are ignored).
 * Static content (objectives, scope, exit criteria, findings, risks...) comes from tools/report/report-meta.json.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell, WidthType, ShadingType,
  AlignmentType, BorderStyle, Header, Footer, PageNumber, LevelFormat, PageBreak, TableOfContents
} = require('docx');

// ------------------------------------------------------------------ args
const args = process.argv.slice(2);
const inputs = [];
let out = null, runTitle = null;
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--out') out = args[++i];
  else if (args[i] === '--title') runTitle = args[++i];
  else inputs.push(args[i]);
}
if (inputs.length === 0) inputs.push('target/surefire-reports/testng-results.xml');
const meta = JSON.parse(fs.readFileSync(path.join(__dirname, 'report-meta.json'), 'utf8'));
const now = new Date();
const pad = n => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}`;
const viDate = d => `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
const outDir = out ? path.dirname(out) : path.join('reports', stamp);
out = out || path.join(outDir, `BSC_TestSummaryReport_${stamp}.docx`);

// ------------------------------------------------------------------ TestNG results parsing (no XML lib needed)
const decode = s => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');
const attrs = s => { const o = {}; s.replace(/([\w-]+)="([^"]*)"/g, (_, k, v) => { o[k] = decode(v); }); return o; };
const cdata = s => (s || '').replace(/^\s*<!\[CDATA\[/, '').replace(/\]\]>\s*$/, '');

const MODULES = {
  LoginTest: 'Đăng nhập / Đăng xuất', ExcelLoginTest: 'Đăng nhập (kịch bản Excel)',
  NavigationTest: 'Điều hướng', PriceBoardTest: 'Bảng giá', OrderFormTest: 'Form đặt lệnh',
  ConditionalOrderFormTest: 'Lệnh điều kiện (form)', TradingInfoTest: 'Sổ lệnh / Danh mục / Tài sản',
  SearchTest: 'Tìm kiếm', StockDetailTest: 'Chi tiết mã CK', MenuTest: 'Menu',
  MenuTransactionTest: 'Giao dịch trong Menu', OrderCleanupTest: 'Công cụ dọn lệnh', OrderDryRunTest: 'Đặt lệnh – dry run (không gửi)',
  ExcelOrderTest: 'Đặt / Sửa / Huỷ lệnh thật (Excel)', LocatorsTest: 'Framework (unit)',
  OrderDataTest: 'Framework (unit)', OrderBookMatchTest: 'Framework (unit)'
};

const cases = new Map();
let suiteStart = null, suiteEnd = null;
// Allure results directory (one *-result.json per executed test: survives an interrupted run)
function readAllure(dir) {
  for (const f of fs.readdirSync(dir).filter(n => n.endsWith('-result.json'))) {
    const r = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
    const fullName = r.fullName || '';
    const cls = fullName.split('.').slice(-2, -1)[0] || (r.labels || []).find(l => l.name === 'testClass')?.value?.split('.').pop() || '?';
    const method = fullName.split('.').pop() || r.name;
    // configuration fixtures (@Before/@After in the base classes) are not test cases
    if (/^(BaseTest|LoggedInBaseTest)$/.test(cls) || /^(startSession|login|backToMainScreen|stopSession|closeSharedSessions|startRecording)$/.test(method)) continue;
    const params = (r.parameters || []).filter(x => !/Phiên hiện tại|Thời điểm/.test(x.name)).map(x => String(x.value));
    const status = { passed: 'PASS', failed: 'FAIL', broken: 'FAIL', skipped: 'SKIP' }[r.status] || 'SKIP';
    const desc = r.description || '';
    const idM = desc.match(/^(TC_[A-Z0-9_.]+)/);
    const paramId = ((params[0] || '').match(/^([A-Z0-9_]+)\s*\[/) || [])[1];
    const started = r.start ? new Date(r.start) : null, stopped = r.stop ? new Date(r.stop) : null;
    if (started && (!suiteStart || started < suiteStart)) suiteStart = started;
    if (stopped && (!suiteEnd || stopped > suiteEnd)) suiteEnd = stopped;
    const c = {
      cls, module: MODULES[cls] || cls, method, params, status,
      id: idM ? idM[1] : (paramId || `${cls}.${method}`),
      name: desc.replace(/^TC_[A-Z0-9_.]+\s*-\s*/, '').split('\n')[0] || r.name, detail: params[0] || '',
      duration: started && stopped ? (stopped - started) / 1000 : 0, started,
      message: ((r.statusDetails || {}).message || '').trim().split('\n')[0].slice(0, 300)
    };
    c.attachments = [];
    const walk = node => {
      for (const a of node.attachments || []) c.attachments.push({ name: a.name, type: a.type || '', file: path.join(dir, a.source) });
      for (const st of node.steps || []) walk(st);
    };
    walk(r);
    const key = `${cls}#${method}#${params.join('|')}`;
    const prev = cases.get(key);
    if (!prev || !prev.started || !c.started || c.started >= prev.started) cases.set(key, c);
  }
}

for (const file of inputs) {
  if (!fs.existsSync(file)) { console.error(`! Không thấy ${file} - bỏ qua`); continue; }
  if (fs.statSync(file).isDirectory()) { readAllure(file); continue; }
  const xml = fs.readFileSync(file, 'utf8');
  xml.replace(/<suite ([^>]*)>/g, (_, a) => {
    const s = attrs(a);
    const st = s['started-at'] ? new Date(s['started-at']) : null, fi = s['finished-at'] ? new Date(s['finished-at']) : null;
    if (st && (!suiteStart || st < suiteStart)) suiteStart = st;
    if (fi && (!suiteEnd || fi > suiteEnd)) suiteEnd = fi;
  });
  const classRe = /<class name="([^"]+)">([\s\S]*?)<\/class>/g;
  let cm;
  while ((cm = classRe.exec(xml))) {
    const cls = cm[1].split('.').pop();
    const methodRe = /<test-method ([^>]*?)(?:\/>|>([\s\S]*?)<\/test-method>)/g;
    let mm;
    while ((mm = methodRe.exec(cm[2]))) {
      const a = attrs(mm[1]);
      if (a['is-config'] === 'true' || a.retried === 'true') continue;
      const body = mm[2] || '';
      const params = [];
      body.replace(/<value(?:\s[^>]*)?>([\s\S]*?)<\/value>/g, (_, v) => { params.push(decode(cdata(v)).trim()); });
      const msgM = body.match(/<message>([\s\S]*?)<\/message>/);
      const desc = a.description || '';
      const idM = desc.match(/^(TC_[A-Z0-9_.]+)/);
      const param0 = params[0] || '';
      const paramId = (param0.match(/^([A-Z0-9_]+)\s*\[/) || [])[1];
      const key = `${cls}#${a.name}#${params.join('|')}`;
      const c = {
        cls, module: MODULES[cls] || cls, method: a.name, params, status: a.status,
        id: idM ? idM[1] : (paramId || `${cls}.${a.name}`),
        name: (desc.replace(/^TC_[A-Z0-9_.]+\s*-\s*/, '').split('\n')[0] || a.name) + (param0 && !idM ? '' : ''),
        detail: param0, duration: Number(a['duration-ms'] || 0) / 1000,
        started: a['started-at'] ? new Date(a['started-at']) : null,
        message: msgM ? decode(cdata(msgM[1])).trim().split('\n')[0].slice(0, 300) : ''
      };
      const prev = cases.get(key);
      if (!prev || !prev.started || !c.started || c.started >= prev.started) cases.set(key, c);
    }
  }
}
const list = [...cases.values()].sort((x, y) => (x.started || 0) - (y.started || 0));
if (list.length === 0) { console.error('Không có kết quả test nào để báo cáo.'); process.exit(1); }

// ------------------------------------------------------------------ metrics
const count = st => list.filter(c => c.status === st).length;
const total = list.length, pass = count('PASS'), fail = count('FAIL'), skip = count('SKIP');
const executed = pass + fail;
const pct = (a, b) => b === 0 ? 0 : Math.round((a / b) * 1000) / 10;
const executedPct = pct(executed, total), passPct = pct(pass, executed);
const totalDuration = list.reduce((s, c) => s + c.duration, 0);
const byModule = {};
for (const c of list) {
  const m = byModule[c.module] || (byModule[c.module] = { total: 0, pass: 0, fail: 0, skip: 0, duration: 0 });
  m.total++; m.duration += c.duration;
  m[c.status === 'PASS' ? 'pass' : c.status === 'FAIL' ? 'fail' : 'skip']++;
}
const crit = meta.exitCriteria;
const openCritical = meta.findings.filter(f => /cao|nghiêm trọng|critical|high/i.test(f.severity) && /mở|open/i.test(f.status)).length;
const exitRows = [
  ['Tỷ lệ test case được thực thi', `≥ ${crit.minExecutedPercent}%`, `${executedPct}% (${executed}/${total})`, executedPct >= crit.minExecutedPercent],
  ['Tỷ lệ test case đạt (trên số đã thực thi)', `≥ ${crit.minPassPercent}%`, `${passPct}% (${pass}/${executed})`, passPct >= crit.minPassPercent],
  ['Lỗi nghiêm trọng / cao còn mở', `≤ ${crit.maxOpenCriticalDefects}`, String(openCritical), openCritical <= crit.maxOpenCriticalDefects]
];
const exitMet = exitRows.every(r => r[3]);

// ------------------------------------------------------------------ environment (adb + .env, best effort)
const sh = cmd => { try { return execSync(cmd, { timeout: 8000, stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim(); } catch (e) { return ''; } };
const envFile = fs.existsSync('.env') ? fs.readFileSync('.env', 'utf8') : '';
const envVal = k => ((envFile.match(new RegExp(`^${k}=(.*)$`, 'm')) || [])[1] || '').trim();
const udid = envVal('DEVICE_UDID') || 'dabfffa4';
const adb = `${process.env.ANDROID_HOME || envVal('ANDROID_HOME') || process.env.HOME + '/Library/Android/sdk'}/platform-tools/adb -s ${udid}`;
const appVersion = (sh(`${adb} shell dumpsys package com.bsc.mobiletrading`).match(/versionName=(\S+)/) || [])[1] || 'không xác định';
const model = sh(`${adb} shell getprop ro.product.model`) || 'không xác định';
const android = sh(`${adb} shell getprop ro.build.version.release`) || '?';
const environment = (envVal('ENV') || 'sit').toUpperCase();
const appium = sh('appium --version') || '3.9.0';

// ------------------------------------------------------------------ docx helpers
const FONT = 'Arial';
const PAGE_W = 11906, MARGIN = 1134, CONTENT_W = PAGE_W - 2 * MARGIN; // A4, 2 cm margins
const COLORS = { PASS: '1E7B34', FAIL: 'C62828', SKIP: 'B26A00', head: '1F3864', headFill: 'D9E2F3', zebra: 'F5F7FB' };
const STATUS_VI = { PASS: 'Đạt', FAIL: 'Không đạt', SKIP: 'Bỏ qua' };
const border = { style: BorderStyle.SINGLE, size: 4, color: 'B4C0D6' };
const borders = { top: border, bottom: border, left: border, right: border };

const p = (text, opt = {}) => new Paragraph({
  spacing: { after: opt.after ?? 80 }, alignment: opt.align,
  children: [new TextRun({ text, bold: opt.bold, italics: opt.italics, color: opt.color, size: opt.size, font: FONT })]
});
const h1 = t => new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(t)] });
const h2 = t => new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(t)] });
const bullets = items => items.map(t => new Paragraph({ numbering: { reference: 'bullets', level: 0 }, spacing: { after: 40 }, children: [new TextRun({ text: t, font: FONT })] }));

function table(widths, header, rows, opt = {}) {
  const scale = CONTENT_W / widths.reduce((a, b) => a + b, 0);
  const w = widths.map(x => Math.floor(x * scale));
  w[w.length - 1] += CONTENT_W - w.reduce((a, b) => a + b, 0);
  const cell = (content, i, o = {}) => new TableCell({
    width: { size: w[i], type: WidthType.DXA }, borders,
    margins: { top: 60, bottom: 60, left: 90, right: 90 },
    shading: o.fill ? { type: ShadingType.CLEAR, color: 'auto', fill: o.fill } : undefined,
    children: [new Paragraph({
      alignment: o.align, children: [new TextRun({ text: String(content ?? ''), bold: o.bold, color: o.color, size: o.size || 18, font: FONT })]
    })]
  });
  const head = new TableRow({ tableHeader: true, children: header.map((t, i) => cell(t, i, { bold: true, fill: COLORS.headFill, color: COLORS.head })) });
  const body = rows.map((r, ri) => new TableRow({
    cantSplit: true,
    children: r.map((v, i) => {
      const o = typeof v === 'object' && v !== null ? v : { text: v };
      return cell(o.text, i, { ...o, fill: o.fill || (opt.zebra && ri % 2 ? COLORS.zebra : undefined), align: o.align || (opt.align || [])[i] });
    })
  }));
  return new Table({ width: { size: CONTENT_W, type: WidthType.DXA }, columnWidths: w, rows: [head, ...body] });
}
const statusCell = st => ({ text: STATUS_VI[st] || st, color: COLORS[st], bold: true, align: AlignmentType.CENTER });
const kv = rows => table([30, 70], ['Mục', 'Nội dung'], rows.map(([k, v]) => [{ text: k, bold: true }, v]));
const fmtDur = s => s >= 60 ? `${Math.floor(s / 60)} phút ${Math.round(s % 60)} giây` : `${s.toFixed(1)} giây`;

// ------------------------------------------------------------------ content
const failed = list.filter(c => c.status === 'FAIL'), skipped = list.filter(c => c.status === 'SKIP');
const conclusion = exitMet
  ? `ĐẠT – các tiêu chí kết thúc kiểm thử được đáp ứng (thực thi ${executedPct}%, đạt ${passPct}%).`
  : `CHƯA ĐẠT – ${exitRows.filter(r => !r[3]).map(r => r[0].toLowerCase()).join('; ')} chưa đáp ứng tiêu chí.`;

const children = [
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 1800, after: 200 }, children: [new TextRun({ text: 'BÁO CÁO TỔNG KẾT KIỂM THỬ', bold: true, size: 44, font: FONT, color: COLORS.head })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 120 }, children: [new TextRun({ text: 'Test Summary Report (ISTQB / IEEE 829 / ISO/IEC/IEEE 29119-3)', italics: true, size: 24, font: FONT })] }),
  new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new TextRun({ text: meta.project + (runTitle ? ` – ${runTitle}` : ''), bold: true, size: 28, font: FONT })] }),
  kv([
    ['Mã tài liệu', `${meta.documentId}-${stamp}`], ['Phiên bản', meta.documentVersion], ['Ngày lập', viDate(now)],
    ['Người lập', meta.preparedBy], ['Đơn vị', meta.organization],
    ['Kết luận', conclusion]
  ]),
  new Paragraph({ children: [new PageBreak()] }),
  h1('Mục lục'),
  new TableOfContents('Mục lục', { hyperlink: true, headingStyleRange: '1-2' }),
  p('(Mở bằng Microsoft Word: nhấn chuột phải vào mục lục → Update Field để cập nhật số trang.)', { italics: true, size: 16 }),
  new Paragraph({ children: [new PageBreak()] }),

  h1('1. Tóm tắt'),
  p(`Báo cáo tổng kết kết quả thực thi kiểm thử tự động ứng dụng BSC Mobile Trading trên môi trường ${environment}, phiên bản ứng dụng ${appVersion}.`),
  table([40, 15, 15, 15, 15], ['Chỉ số', 'Tổng', 'Đạt', 'Không đạt', 'Bỏ qua'], [
    [{ text: 'Số test case', bold: true }, { text: total, align: AlignmentType.CENTER }, { text: pass, color: COLORS.PASS, bold: true, align: AlignmentType.CENTER },
      { text: fail, color: COLORS.FAIL, bold: true, align: AlignmentType.CENTER }, { text: skip, color: COLORS.SKIP, bold: true, align: AlignmentType.CENTER }]
  ]),
  p(''),
  kv([
    ['Tỷ lệ thực thi', `${executedPct}% (${executed}/${total})`], ['Tỷ lệ đạt', `${passPct}% (${pass}/${executed})`],
    ['Thời gian thực thi', `${suiteStart ? viDate(suiteStart) : '?'} → ${suiteEnd ? viDate(suiteEnd) : '?'} (tổng thời lượng test: ${fmtDur(totalDuration)})`],
    ['Kết luận', conclusion]
  ]),

  h1('2. Mục tiêu kiểm thử'), ...bullets(meta.objectives),

  h1('3. Phạm vi kiểm thử'),
  h2('3.1 Trong phạm vi'), ...bullets(meta.inScope),
  h2('3.2 Ngoài phạm vi'), ...bullets(meta.outOfScope),

  h1('4. Phương pháp kiểm thử'),
  kv([['Cấp độ kiểm thử', meta.testLevel], ['Loại kiểm thử', meta.testType]]),
  p(''), ...bullets(meta.approach),

  h1('5. Môi trường kiểm thử'),
  kv([
    ['Môi trường', environment], ['Ứng dụng', `com.bsc.mobiletrading – phiên bản ${appVersion}`],
    ['Thiết bị', `${model} – Android ${android} (udid ${udid})`],
    ['Công cụ', `Appium ${appium} + UiAutomator2, Java 17, TestNG 7.11, Allure 2.29`],
    ['Dữ liệu kiểm thử', 'testdata/orders.csv, order_actions.csv, order.json, login.json, search.json']
  ]),

  h1('6. Kết quả kiểm thử'),
  h2('6.1 Tổng hợp theo chức năng'),
  table([34, 10, 10, 12, 10, 12, 12], ['Chức năng', 'Tổng', 'Đạt', 'Không đạt', 'Bỏ qua', 'Tỷ lệ đạt', 'Thời lượng'],
    Object.entries(byModule).map(([m, v]) => [
      { text: m, bold: true }, v.total, { text: v.pass, color: COLORS.PASS }, { text: v.fail, color: v.fail ? COLORS.FAIL : undefined, bold: v.fail > 0 },
      { text: v.skip, color: v.skip ? COLORS.SKIP : undefined }, `${pct(v.pass, v.pass + v.fail)}%`, fmtDur(v.duration)
    ]), { zebra: true, align: [undefined, AlignmentType.CENTER, AlignmentType.CENTER, AlignmentType.CENTER, AlignmentType.CENTER, AlignmentType.CENTER, AlignmentType.RIGHT] }),
  h2('6.2 Chi tiết từng test case'),
  table([5, 15, 33, 17, 10, 8, 22], ['#', 'Mã TC', 'Test case', 'Chức năng', 'Kết quả', 'Giây', 'Ghi chú'],
    list.map((c, i) => [
      { text: i + 1, align: AlignmentType.CENTER }, c.id, c.name + (c.detail && !c.name.includes(c.detail) ? ` – ${c.detail}` : ''), c.module,
      statusCell(c.status), { text: c.duration.toFixed(1), align: AlignmentType.RIGHT }, c.message
    ]), { zebra: true }),

  h1('7. Sự cố và phát hiện'),
  h2('7.1 Test case không đạt'),
  ...(failed.length === 0 ? [p('Không có test case không đạt.')] :
    [table([15, 35, 50], ['Mã TC', 'Test case', 'Mô tả lỗi'], failed.map(c => [c.id, c.name, c.message]))]),
  h2('7.2 Test case bị bỏ qua'),
  ...(skipped.length === 0 ? [p('Không có test case bị bỏ qua.')] :
    [table([15, 35, 50], ['Mã TC', 'Test case', 'Lý do'], skipped.map(c => [c.id, c.name, c.message || 'Bỏ qua do điều kiện tiên quyết không thoả']))]),
  h2('7.3 Phát hiện trên ứng dụng (khuyến nghị theo dõi như defect)'),
  table([8, 14, 40, 10, 28], ['Mã', 'Mức độ', 'Mô tả', 'Trạng thái', 'Ghi chú'],
    meta.findings.map(f => [f.id, f.severity, f.title, f.status, f.note])),

  h1('8. Đánh giá theo tiêu chí kết thúc'),
  table([40, 18, 24, 18], ['Tiêu chí', 'Yêu cầu', 'Thực tế', 'Đánh giá'],
    exitRows.map(r => [r[0], r[1], r[2], { text: r[3] ? 'Đạt' : 'Chưa đạt', bold: true, color: r[3] ? COLORS.PASS : COLORS.FAIL, align: AlignmentType.CENTER }])),
  p(''), p(`Kết luận: ${conclusion}`, { bold: true }),

  h1('9. Rủi ro tồn đọng và hạn chế'), ...bullets(meta.risks),
  h1('10. Khuyến nghị'), ...bullets(meta.recommendations),

  h1('11. Phê duyệt'),
  table([40, 30, 30], ['Vai trò', 'Họ tên / Chữ ký', 'Ngày'], meta.approvers.map(a => [a, '', '']))
];

const doc = new Document({
  creator: meta.preparedBy, title: 'Báo cáo tổng kết kiểm thử', description: meta.project,
  styles: {
    default: { document: { run: { font: FONT, size: 20 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: FONT, size: 28, bold: true, color: COLORS.head }, paragraph: { spacing: { before: 280, after: 120 }, keepNext: true, outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { font: FONT, size: 24, bold: true, color: COLORS.head }, paragraph: { spacing: { before: 200, after: 100 }, keepNext: true, outlineLevel: 1 } }
    ]
  },
  numbering: { config: [{ reference: 'bullets', levels: [{ level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
    style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] }] },
  features: { updateFields: true },
  sections: [{
    properties: { page: { size: { width: PAGE_W, height: 16838 }, margin: { top: MARGIN, bottom: MARGIN, left: MARGIN, right: MARGIN } } },
    headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: 'B4C0D6', space: 4 } },
      children: [new TextRun({ text: `${meta.documentId} – Báo cáo tổng kết kiểm thử`, size: 16, color: '666666', font: FONT })] })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: 'Trang ', size: 16, font: FONT }), new TextRun({ children: [PageNumber.CURRENT], size: 16, font: FONT }),
        new TextRun({ text: ' / ', size: 16, font: FONT }), new TextRun({ children: [PageNumber.TOTAL_PAGES], size: 16, font: FONT })] })] }) },
    children
  }]
});

fs.mkdirSync(path.dirname(out), { recursive: true });
const html = require('./html-report')({
  meta, list, byModule, total, pass, fail, skip, executed, executedPct, passPct, totalDuration, suiteStart, suiteEnd,
  exitRows, exitMet, conclusion, environment, appVersion, model, android, udid, appium, stamp, runTitle, outDir,
  statusVi: STATUS_VI, pct, fmtDur, viDate, now
});
Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(out, buf);
  console.log(`Báo cáo Word: ${out}`);
  console.log(`Báo cáo HTML: ${html}`);
  console.log(`Tổng ${total} | Đạt ${pass} | Không đạt ${fail} | Bỏ qua ${skip} | Thực thi ${executedPct}% | Đạt ${passPct}% | ${exitMet ? 'ĐẠT' : 'CHƯA ĐẠT'} tiêu chí kết thúc`);
});
