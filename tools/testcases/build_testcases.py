# Builds "Kịch bản kiểm thử tự động - BSC.xlsx" (same 6 columns as "Kịch bản kiểm thử sau khi update App.xlsx"
# + automation id + data/notes). Price board / market lists are read from testdata/market_data.json.
# Run: python3 tools/testcases/build_testcases.py
import json
import os
from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
DATA = json.load(open(os.path.join(ROOT, 'src/test/resources/testdata/market_data.json'), encoding='utf-8'))
OUT = os.path.join(ROOT, 'Kịch bản kiểm thử tự động - BSC.xlsx')

LOGIN = '1. Đăng nhập thành công (tài khoản trong file cài đặt .env)'


def steps(*s):
    return '\n'.join(f'{i}. {x}' for i, x in enumerate(s, 1))


def pb_row(sel):
    if sel.get('kind') == 'watchlist':
        items = '; '.join(f"{k} ({', '.join(v)})" if v else k for k, v in sel['watchlists'].items())
        how = 'chọn từng danh mục (và từng tab con)'
    elif sel['options']:
        items = f"{len(sel['options'])} lựa chọn: " + ', '.join(sel['options'])
        how = 'chọn lần lượt từng lựa chọn trong danh sách'
    else:
        items = 'Mở thẳng danh sách'
        how = 'xem danh sách'
    return ('TC_PB_04', f"Bảng giá – {sel['name']}", f"Bộ chọn {sel['name']} có dữ liệu",
            steps(LOGIN[3:], 'Chạm Bảng giá', f"Chạm bộ chọn {sel['name']} → {how}"),
            'Mỗi lựa chọn có ít nhất 1 dòng dữ liệu', items + '. Trống => KHÔNG ĐẠT; mỗi lựa chọn là 1 TC khi chạy')


def feature_rows():
    f = DATA['marketFeatures']
    exp = {'dialog': 'Hiện hộp "Cấp quyền" (thiết bị chưa đặt khoá màn hình) hoặc mở màn SmartOTP',
           'title': 'Mở màn "{t}"', 'priceboard': 'Chuyển sang Bảng giá, danh mục đang chọn = "{t}"',
           'sheet': 'Mở "{t}" có danh sách tài khoản nộp tiền', 'tab': 'Chuyển sang Giao dịch – tab {t}',
           'chart': 'Mở biểu đồ kỹ thuật; biểu đồ nến có dữ liệu (kiểm tra bằng phân tích ảnh)'}
    rows = []
    for q in f['quickActions']:
        k, t = q['expect'].split('|', 1)
        rows.append(('TC_MK_02', 'Chức năng nổi bật', f"Chạm \"{q['label']}\"",
                     steps(LOGIN[3:], 'Chạm Thị trường → tab Tổng quan', f"Chạm chức năng nổi bật \"{q['label']}\"",
                           'Quay lại (không lưu thay đổi)'), exp[k].format(t=t), q.get('note', '')))
    rows.append(('TC_MK_02b', 'Cài đặt chức năng nổi bật', 'Màn Cài đặt chức năng nổi bật hiển thị đủ chức năng',
                 steps(LOGIN[3:], 'Chạm Thị trường → Tổng quan', 'Chạm "Tuỳ chỉnh"', 'Quay lại (không lưu)'),
                 'Hiển thị: ' + ', '.join(f['featuredSettingsItems']), 'Chỉ xem, không lưu thay đổi'))
    rows.append(('TC_MK_03', 'Cài đặt chỉ số thị trường', 'Danh sách chỉ số trong Cài đặt chỉ số thị trường',
                 steps(LOGIN[3:], 'Chạm Thị trường → Tổng quan', 'Chạm "+" ở tiêu đề "Chỉ số thị trường"',
                       'Đóng (không bấm Xác nhận)'),
                 'Mở sheet "Chỉ số" có danh sách chỉ số và nút Xác nhận', 'Không bấm Xác nhận'))
    hm = f['heatmap']
    for flt in hm['filters']:
        rows.append(('TC_MK_04', 'Bản đồ nhiệt', f'Xem bản đồ nhiệt – lọc {flt}',
                     steps(LOGIN[3:], 'Chạm Thị trường → Tổng quan', f'Chạm "{hm["button"]}"', f'Chọn {flt}'),
                     f'Mở "{hm["title"]}"; bản đồ nhiệt có dữ liệu', 'Bản đồ vẽ đồ hoạ: kiểm tra bằng phân tích ảnh; trống => KHÔNG ĐẠT'))
    for l in f['lists']:
        tid = {'Tin tức': 'TC_MK_05', 'Phân tích': 'TC_MK_06'}.get(l['section'], 'TC_MK_07')
        for tab in (l['tabs'] or ['']):
            rows.append((tid, l['section'], f"{l['section']} > Xem thêm" + (f' – tab {tab}' if tab else ''),
                         steps(LOGIN[3:], 'Chạm Thị trường → Tổng quan', f"Cuộn đến \"{l['section']}\", chạm Xem thêm")
                         + (f'\n4. Chạm tab {tab}' if tab else ''),
                         f"Mở \"{l['title']}\"; có ít nhất 1 mục dữ liệu", 'Trống => KHÔNG ĐẠT'))
    return rows


def readonly_sections():
    pb = DATA['priceBoard']
    yield 'Đăng nhập / Đăng xuất', [
        ('TC_LOGIN_01', 'Đăng nhập', 'Màn hình đăng nhập hiển thị đủ thành phần',
         steps('Mở app ở trạng thái chưa đăng nhập', 'Chạm Menu → Đăng nhập'),
         'Hiển thị ô Tài khoản, ô Mật khẩu (ký tự được che), nút Đăng nhập', ''),
        ('TC_LOGIN_02', 'Đăng nhập', 'Bỏ trống tài khoản và mật khẩu',
         steps('Mở màn đăng nhập', 'Để trống Tài khoản và Mật khẩu', 'Chạm Đăng nhập'),
         'Thông báo "Không để trống trường tài khoản và mật khẩu"; không vào được app', 'login.json'),
        ('TC_LOGIN_03', 'Đăng nhập', 'Bỏ trống mật khẩu',
         steps('Mở màn đăng nhập', 'Nhập Tài khoản 000C000001, để trống Mật khẩu', 'Chạm Đăng nhập'),
         'Thông báo "Không để trống trường mật khẩu"; không vào được app', 'login.json'),
        ('TC_LOGIN_04', 'Đăng nhập', 'Tài khoản không tồn tại',
         steps('Mở màn đăng nhập', 'Nhập Tài khoản 000C000001, Mật khẩu sai', 'Chạm Đăng nhập'),
         'Hiển thị thông báo lỗi; không vào được app',
         'Chưa so câu thông báo: app đang báo "Mã sự kiện không tồn tại" – cần BA chốt câu mong đợi'),
        ('TC_LOGIN_05', 'Đăng nhập', 'Đăng nhập thành công bằng tài khoản hợp lệ (Excel No.1)',
         steps('Mở app BSC Smart Invest', 'Chạm Menu → Đăng nhập → Đăng nhập tài khoản khác',
               'Xoá tài khoản cũ, nhập tài khoản / mật khẩu trong file cài đặt', 'Chạm Đăng nhập'),
         'Đăng nhập thành công; header hiển thị đúng tài khoản', 'BSC_USERNAME / BSC_PASSWORD trong .env'),
        ('TC_LOGIN_06', 'Đăng xuất', 'Huỷ đăng xuất vẫn giữ phiên',
         steps(LOGIN[3:], 'Chạm Menu → Đăng xuất', 'Chọn "Không"'),
         'Vẫn ở trạng thái đăng nhập', ''),
        ('TC_LOGIN_07', 'Đăng xuất', 'Đăng xuất thành công',
         steps(LOGIN[3:], 'Chạm Menu → Đăng xuất', 'Chọn "Có"'),
         'Về màn đăng nhập, không còn phiên; màn đăng nhập nhớ tài khoản vừa dùng', ''),
    ]
    yield 'Điều hướng', [
        ('TC_NAV_01', 'Thanh điều hướng', 'Bottom navigation mở đúng 5 màn hình chính',
         steps(LOGIN[3:], 'Lần lượt chạm Thị trường, Bảng giá, Giao dịch, Tìm kiếm, Thông báo'),
         'Mỗi lần chạm mở đúng màn hình tương ứng', ''),
        ('TC_NAV_02', 'Header', 'Header hiển thị tài khoản đang đăng nhập',
         steps(LOGIN[3:], 'Quan sát header'), 'Header hiển thị Tên - Số tài khoản và tiểu khoản', ''),
        ('TC_NAV_03', 'Thị trường', '4 tab và các tiện ích nhanh',
         steps(LOGIN[3:], 'Chạm Thị trường'),
         'Có tab Tổng quan / Cổ phiếu / Phái sinh / Chứng quyền; tiện ích SmartOTP, Mở tài khoản, Khuyến nghị, Nộp tiền', ''),
        ('TC_NAV_04', 'Giao dịch', 'Chuyển qua 4 tab',
         steps(LOGIN[3:], 'Chạm Giao dịch', 'Lần lượt chạm Đặt lệnh / Sổ lệnh / Danh mục / Tài sản'),
         'Mỗi tab được chọn và hiển thị đúng nội dung', ''),
        ('TC_NAV_05', 'Thông báo', 'Chuyển qua 4 tab',
         steps(LOGIN[3:], 'Chạm Thông báo', 'Lần lượt chạm 4 tab'), 'Mỗi tab được chọn', ''),
        ('TC_NAV_06', 'Menu', 'Menu hiển thị thông tin tài khoản và đủ nhóm chức năng',
         steps(LOGIN[3:], 'Chạm biểu tượng Menu (☰)', 'Đóng Menu'),
         'Hiển thị hồ sơ tài khoản, đủ 9 nhóm chức năng; đóng được Menu', ''),
    ]
    mk = []
    for t in DATA['market']:
        for f in (t['filters'] or ['']):
            label = t['tab'] + (f' – lọc {f}' if f else '')
            if t.get('chart'):
                exp = f'Thẻ "Chỉ số thị trường" có số liệu; biểu đồ "{t["chart"]}" có cột dữ liệu (không trống)'
                note = 'Biểu đồ vẽ bằng đồ hoạ: kiểm tra bằng phân tích ảnh chụp (tỷ lệ điểm ảnh màu ≥ 0.3%)'
            elif t['sections']:
                exp = 'Mỗi mục có ít nhất 1 dòng dữ liệu: ' + ', '.join(t['sections'])
                note = 'Mục hiển thị "Không có dữ liệu" hoặc không có dòng => KHÔNG ĐẠT'
            else:
                exp = 'Danh sách có ít nhất 1 dòng dữ liệu'
                note = 'Danh sách trống => KHÔNG ĐẠT'
            mk.append(('TC_MK_01', 'Thị trường', f'Tab {label} có dữ liệu',
                       steps(LOGIN[3:], 'Chạm Thị trường', f'Chạm tab {t["tab"]}' + (f', chọn bộ lọc {f}' if f else ''),
                             'Cuộn hết nội dung tab'), exp, note))
    yield 'Thị trường – dữ liệu', mk
    yield 'Thị trường – tính năng (Tổng quan)', feature_rows()
    yield 'Bảng giá', [
        ('TC_PB_01', 'Bảng giá', 'Thẻ chỉ số VN INDEX / VN30 đúng định dạng',
         steps(LOGIN[3:], 'Chạm Bảng giá'), 'Thẻ hiển thị giá trị, thay đổi (%), GTGD "(Tỷ VND)" đúng định dạng', ''),
        ('TC_PB_02', 'Bảng giá', 'Danh sách mã có dữ liệu hợp lệ',
         steps(LOGIN[3:], 'Chạm Bảng giá'),
         'Danh sách không trống; mỗi dòng có mã CK, sàn HOSE/HNX/UPCOM, tên công ty, giá, KL, % thay đổi đúng định dạng', ''),
        ('TC_PB_03', 'Bảng giá', 'Tuỳ chọn sắp xếp và mục Tin tức',
         steps(LOGIN[3:], 'Chạm Bảng giá', 'Cuộn xuống cuối danh sách'),
         'Có sắp xếp A-Z / Giá / Khối lượng; có mục Tin tức', ''),
    ] + [pb_row(sel) for sel in pb['selectors']] + [
        ('TC_PB_05', 'Bảng giá chi tiết', 'Icon bảng giá chi tiết hiển thị bảng có dữ liệu hợp lệ',
         steps(LOGIN[3:], 'Chạm Bảng giá', 'Chạm icon bảng giá chi tiết (cạnh nút Menu)'),
         'Hiển thị bảng giá chi tiết (TC / Trần / Sàn / Bên mua / Bên bán) có dữ liệu; mỗi dòng Trần >= TC >= Sàn',
         'Bảng trống => KHÔNG ĐẠT'),
    ]
    yield 'Sổ lệnh / Danh mục / Tài sản (chỉ đọc)', [
        ('TC_INFO_01', 'Sổ lệnh', 'Tổng hợp và bộ lọc trạng thái',
         steps(LOGIN[3:], 'Chạm Giao dịch → tab Sổ lệnh'), 'Hiển thị phần tổng hợp và các bộ lọc trạng thái lệnh', ''),
        ('TC_INFO_02', 'Danh mục', 'Tổng giá trị vốn / thị trường / lãi lỗ',
         steps(LOGIN[3:], 'Chạm Giao dịch → tab Danh mục'), 'Hiển thị Tổng giá trị vốn, giá trị thị trường, lãi/lỗ', ''),
        ('TC_INFO_03', 'Tài sản', 'NAV, kỳ hiệu suất và cơ cấu tài sản',
         steps(LOGIN[3:], 'Chạm Giao dịch → tab Tài sản'),
         'Hiển thị Giá trị tài sản ròng, Trạng thái tiểu khoản; kỳ 1M/3M/6M/1Y/YTD; Tiền tại BSC, Phải thu, Chứng khoán, Phải trả',
         '"Thặng dư ký quỹ" chỉ kiểm tra khi đang ở tiểu khoản margin (MR...)'),
    ]
    sr = [('TC_SEARCH_01', 'Tìm kiếm', 'Có 3 nhóm Chứng khoán / Tin tức / Chức năng',
           steps(LOGIN[3:], 'Chạm Tìm kiếm'), 'Hiển thị 3 nhóm Chứng khoán / Tin tức / Chức năng', '')]
    for s in [('BSI', 'BSI', 'Chứng khoán BIDV'), ('AAA', 'AAA', 'An Phát')]:
        sr.append(('TC_SEARCH_02', 'Tìm kiếm', f'Tìm mã {s[0]}',
                   steps(LOGIN[3:], 'Chạm Tìm kiếm', f'Nhập "{s[0]}"'),
                   f'Kết quả có mã {s[1]}, tên công ty chứa "{s[2]}"', 'search.json'))
    for s in DATA['search']:
        sr.append(('TC_SEARCH_03', 'Tìm kiếm', f'Nhóm {s["category"]}: tìm "{s["keyword"]}"',
                   steps(LOGIN[3:], 'Chạm Tìm kiếm', f'Chọn nhóm {s["category"]}', f'Nhập "{s["keyword"]}"'),
                   f'Có ít nhất 1 kết quả; có kết quả chứa "{s["expectContains"]}"', 'Không có kết quả => KHÔNG ĐẠT'))
    yield 'Tìm kiếm', sr
    yield 'Chi tiết mã', [
        ('TC_STOCK_01', 'Chi tiết mã', 'Header, đủ tab, Trần > Tham chiếu > Sàn',
         steps(LOGIN[3:], 'Chạm Tìm kiếm, nhập mã AAA', 'Chạm kết quả AAA'),
         'Mở chi tiết mã: header, các tab; giá Trần > Tham chiếu > Sàn', ''),
        ('TC_STOCK_02', 'Chi tiết mã', 'Nút Mua mở form đặt lệnh với mã đã chọn',
         steps(LOGIN[3:], 'Mở chi tiết mã AAA', 'Chạm Mua'), 'Mở form Đặt lệnh, mã CK = AAA', 'Không gửi lệnh'),
    ]


HEAD = ['No', 'REQ', 'Feature', 'Test Case Description', 'Test Case Procedure', 'Expected Output',
        'Mã TC tự động', 'Dữ liệu / Ghi chú']
WIDTH = [6, 22, 20, 40, 48, 48, 15, 44]
thin = Side(style='thin', color='BFBFBF')
BORDER = Border(left=thin, right=thin, top=thin, bottom=thin)
WRAP = Alignment(wrap_text=True, vertical='top')


def write_sheet(ws, sections, title):
    ws.append([title])
    ws['A1'].font = Font(bold=True, size=13)
    ws.append(['Quy tắc: lỗi hệ thống (E_SYSTEM_ERROR / "Lỗi hệ thống") luôn KHÔNG ĐẠT; dữ liệu trống ở tab/mục '
               'được liệt kê => KHÔNG ĐẠT. Cột "Mã TC tự động" = tên test trong code.'])
    ws.append(HEAD)
    for c in ws[3]:
        c.font = Font(bold=True, color='FFFFFF')
        c.fill = PatternFill('solid', fgColor='1F4E78')
        c.alignment = Alignment(wrap_text=True, vertical='center', horizontal='center')
        c.border = BORDER
    no = 0
    for req, rows in sections:
        ws.append(['', req])
        for c in ws[ws.max_row]:
            c.font = Font(bold=True)
            c.fill = PatternFill('solid', fgColor='DDEBF7')
            c.border = BORDER
        for tid, feat, desc, proc, exp, note in rows:
            no += 1
            ws.append([no, '', feat, desc, proc, exp, tid, note])
            for c in ws[ws.max_row]:
                c.alignment = WRAP
                c.border = BORDER
    for i, w in enumerate(WIDTH):
        ws.column_dimensions[chr(65 + i)].width = w
    ws.freeze_panes = 'A4'
    return no


wb = Workbook()
ws = wb.active
ws.title = 'Chỉ đọc'
n = write_sheet(ws, list(readonly_sections()), 'BSC Mobile Trading – Test case CHỈ ĐỌC (không giao dịch)')
wb.save(OUT)
print(OUT, n, 'test cases')
