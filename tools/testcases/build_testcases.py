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
TD = os.path.join(ROOT, 'src/test/resources/testdata')
ACCOUNT = json.load(open(os.path.join(TD, 'account_screens.json'), encoding='utf-8'))


def read_csv(name):
    import csv
    lines = [l for l in open(os.path.join(TD, name), encoding='utf-8-sig').read().splitlines()
             if l.strip() and not l.startswith('#')]
    return list(csv.DictReader(lines))

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


AUTO = 'Tự động: CÓ. '
TODO = 'Tự động: đang xây (cần khảo sát bộ chọn ngày / bộ lọc trên máy). '
MENU_SK = 'Chạm Menu → Quản lý tài khoản → '


def statement_sections():
    def sk(tid, feat, desc, extra_steps, exp, note):
        return (tid, feat, desc, steps(LOGIN[3:], MENU_SK + feat, *extra_steps), exp, note)
    yield 'Sao kê tiền', [
        sk('TC_SKT_01', 'Sao kê tiền', 'Giá trị mặc định khi mở màn', [],
           'Tiểu khoản = tiểu khoản đang chọn; Từ ngày = Đến ngày = ngày làm việc hiện tại', AUTO.replace('CÓ', 'một phần')),
        sk('TC_SKT_02', 'Sao kê tiền', 'Hiển thị đủ thông tin tổng hợp', [],
           'Có Đầu kỳ, Phát sinh tăng, Phát sinh giảm, Cuối kỳ', AUTO + 'TC_ACC_01'),
        sk('TC_SKT_03', 'Sao kê tiền', 'Số dư khớp', [], 'Đầu kỳ + Phát sinh tăng − Phát sinh giảm = Cuối kỳ', AUTO + 'TC_ACC_01b'),
        sk('TC_SKT_04', 'Sao kê tiền', 'Tổng các dòng khớp phát sinh', ['Chọn Từ ngày lùi 1 tháng (có giao dịch)'],
           'Tổng số tiền các dòng dương = Phát sinh tăng; tổng các dòng âm = Phát sinh giảm', TODO),
        sk('TC_SKT_05', 'Sao kê tiền', 'Thông tin từng dòng giao dịch', ['Chọn Từ ngày lùi 1 tháng (có giao dịch)'],
           'Mỗi dòng có ngày (nằm trong khoảng lọc), nội dung, số tiền có dấu +/−', TODO),
        sk('TC_SKT_06', 'Sao kê tiền', 'Lọc theo khoảng ngày', ['Chọn Từ ngày = lùi 1 tháng so với Đến ngày (để có dữ liệu)'],
           'Danh sách tải lại; mọi dòng có ngày trong khoảng; số liệu tổng hợp cập nhật', TODO),
        sk('TC_SKT_07', 'Sao kê tiền', 'Từ ngày lớn hơn Đến ngày', ['Chọn Từ ngày sau Đến ngày'],
           'Báo lỗi / không cho chọn; không hiển thị dữ liệu sai', TODO),
        sk('TC_SKT_08', 'Sao kê tiền', 'Khoảng ngày vượt 6 tháng', ['Chọn Từ ngày cách Đến ngày hơn 6 tháng'],
           'Hiển thị thông báo giới hạn tối đa 6 tháng; không tra cứu', TODO),
        sk('TC_SKT_08b', 'Sao kê tiền', 'Khoảng ngày đúng 6 tháng', ['Chọn Từ ngày cách Đến ngày đúng 6 tháng'],
           'Tra cứu được, không báo lỗi', TODO),
        sk('TC_SKT_09', 'Sao kê tiền', 'Đổi tiểu khoản', ['Chọn tiểu khoản khác ở ô Tiểu khoản'],
           'Số liệu và danh sách cập nhật theo tiểu khoản mới', TODO),
        sk('TC_SKT_10', 'Sao kê tiền', 'Không có giao dịch', ['Chọn khoảng ngày không có giao dịch'],
           'Hiển thị "Không có dữ liệu"; Phát sinh tăng/giảm = 0; Đầu kỳ = Cuối kỳ', TODO),
    ]
    yield 'Sao kê chứng khoán', [
        sk('TC_SKCK_01', 'Sao kê chứng khoán', 'Giá trị mặc định', [],
           'Tiểu khoản = tiểu khoản đang chọn; khoảng ngày mặc định; Mã CK = Tất cả', TODO),
        sk('TC_SKCK_02', 'Sao kê chứng khoán', 'Hiển thị đủ trường / cột', [], 'Có Tiểu khoản, Từ ngày, Đến ngày, Mã CK',
           AUTO + 'TC_ACC_02'),
        sk('TC_SKCK_03', 'Sao kê chứng khoán', 'Lọc theo mã CK', ['Nhập/chọn Mã CK = mã đang có giao dịch'],
           'Chỉ hiển thị các dòng của mã đã chọn', TODO),
        sk('TC_SKCK_04', 'Sao kê chứng khoán', 'Thông tin từng dòng', ['Chọn khoảng ngày có giao dịch'],
           'Mỗi dòng có ngày, mã CK, loại phát sinh, KL tăng/giảm', TODO),
        sk('TC_SKCK_05', 'Sao kê chứng khoán', 'Lọc theo khoảng ngày', ['Chọn Từ ngày lùi 1 tháng'],
           'Mọi dòng có ngày trong khoảng', TODO),
        sk('TC_SKCK_06', 'Sao kê chứng khoán', 'Từ ngày lớn hơn Đến ngày', ['Chọn Từ ngày sau Đến ngày'], 'Báo lỗi / không cho chọn', TODO),
        sk('TC_SKCK_06b', 'Sao kê chứng khoán', 'Khoảng ngày vượt 6 tháng', ['Chọn Từ ngày cách Đến ngày hơn 6 tháng'],
           'Hiển thị thông báo giới hạn tối đa 6 tháng', TODO),
        sk('TC_SKCK_07', 'Sao kê chứng khoán', 'Đổi tiểu khoản', ['Chọn tiểu khoản khác'], 'Dữ liệu theo tiểu khoản mới', TODO),
        sk('TC_SKCK_08', 'Sao kê chứng khoán', 'Không có dữ liệu', ['Chọn mã / khoảng ngày không có giao dịch'],
           'Hiển thị "Không có dữ liệu"', TODO),
    ]
    yield 'Lịch sử đặt lệnh', [
        sk('TC_LSDL_01', 'Lịch sử đặt lệnh', 'Đủ cột và bộ lọc', [], 'Tiểu khoản, Từ/Đến ngày, Mã CK, Trạng thái; cột Mã CK, M/B, Giá đặt, KL đặt, Trạng thái',
           AUTO + 'TC_ACC_03'),
        sk('TC_LSDL_02', 'Lịch sử đặt lệnh', 'Lọc theo trạng thái', ['Chọn Trạng thái = Đã huỷ'], 'Chỉ còn dòng trạng thái Đã huỷ', TODO),
        sk('TC_LSDL_03', 'Lịch sử đặt lệnh', 'Lọc theo mã CK', ['Nhập Mã CK = AAA'], 'Chỉ còn dòng mã AAA', TODO),
        sk('TC_LSDL_04', 'Lịch sử đặt lệnh', 'Lọc theo khoảng ngày', ['Chọn Từ ngày lùi 1 tháng'], 'Mọi dòng trong khoảng ngày', TODO),
        sk('TC_LSDL_05', 'Lịch sử đặt lệnh', 'Chi tiết một lệnh', ['Chạm vào một dòng'], 'Hiển thị chi tiết: tiểu khoản, loại lệnh, giá, KL, giờ đặt, trạng thái', TODO),
        sk('TC_LSDL_06', 'Lịch sử đặt lệnh', 'Khớp với Sổ lệnh trong ngày', ['So sánh với Giao dịch → Sổ lệnh (hôm nay)'],
           'Lệnh trong ngày trùng mã, M/B, giá, KL, trạng thái với Sổ lệnh', AUTO.replace('CÓ', 'một phần') + 'TC_POST_02'),
        sk('TC_LSDL_07', 'Lịch sử đặt lệnh', 'Đổi tiểu khoản', ['Chọn tiểu khoản khác'], 'Chỉ hiển thị lệnh của tiểu khoản đã chọn', TODO),
    ]
    yield 'Lịch sử lệnh điều kiện', [
        sk('TC_LSDK_01', 'Lịch sử lệnh điều kiện', 'Đủ cột và bộ lọc', [], 'Tiểu khoản, Từ/Đến ngày, Mã CK, Trạng thái; cột Mã CK, Loại lệnh, KL, Trạng thái',
           AUTO + 'TC_ACC_04'),
        sk('TC_LSDK_02', 'Lịch sử lệnh điều kiện', 'Lọc theo trạng thái', ['Chọn Trạng thái = Đã huỷ'], 'Chỉ còn lệnh trạng thái đã chọn', TODO),
        sk('TC_LSDK_03', 'Lịch sử lệnh điều kiện', 'Lọc theo mã CK / khoảng ngày', ['Nhập Mã CK, chọn Từ ngày lùi 1 tháng'], 'Đúng mã, đúng khoảng ngày', TODO),
        sk('TC_LSDK_04', 'Lịch sử lệnh điều kiện', 'Ghi nhận lệnh điều kiện vừa đặt', ['Sau bước đặt lệnh điều kiện + TP/SL'],
           'Có Stop Limit, Trailing Stop, OCO, TP/SL vừa đặt với loại lệnh, KL và trạng thái đúng (Đã huỷ sau khi dọn lệnh)', TODO),
        sk('TC_LSDK_05', 'Lịch sử lệnh điều kiện', 'Chi tiết một lệnh', ['Chạm vào một dòng'],
           'Hiển thị giá kích hoạt / biên độ / giá đặt, hiệu lực, thời gian', TODO),
    ]
    yield 'Lịch sử khớp lệnh', [
        sk('TC_LSKL_01', 'Lịch sử khớp lệnh', 'Đủ cột và bộ lọc', [], 'Tiểu khoản, Từ/Đến ngày, Loại giao dịch, Mã CK; cột Ngày, Mã CK, M/B, Giá khớp, KL khớp',
           AUTO + 'TC_ACC_05'),
        sk('TC_LSKL_02', 'Lịch sử khớp lệnh', 'Lọc theo loại giao dịch', ['Chọn Loại giao dịch = Mua, rồi Bán'], 'Chỉ còn lệnh đúng loại', TODO),
        sk('TC_LSKL_03', 'Lịch sử khớp lệnh', 'Lọc theo mã CK / khoảng ngày', ['Nhập Mã CK, chọn Từ ngày lùi 1 tháng'], 'Đúng mã, đúng khoảng ngày', TODO),
        sk('TC_LSKL_04', 'Lịch sử khớp lệnh', 'Giá trị khớp', ['Chạm vào một dòng'], 'Giá trị khớp = Giá khớp × KL khớp; có phí / thuế (nếu hiển thị)', TODO),
        sk('TC_LSKL_05', 'Lịch sử khớp lệnh', 'Không có lệnh khớp', ['Chọn khoảng ngày không có lệnh khớp'], 'Hiển thị "Không có dữ liệu"', TODO),
    ]
    yield 'Lãi/lỗ thực hiện', [
        sk('TC_LLTH_01', 'Lãi/lỗ thực hiện', 'Đủ trường', [], 'Tiểu khoản, Từ/Đến ngày, Mã CK; Tổng giá trị vốn, Tổng giá trị thực hiện, Lãi/Lỗ',
           AUTO + 'TC_ACC_06'),
        sk('TC_LLTH_02', 'Lãi/lỗ thực hiện', 'Công thức lãi/lỗ', ['Chọn khoảng ngày có lệnh bán'],
           'Lãi/Lỗ = Tổng giá trị thực hiện − Tổng giá trị vốn; % = Lãi/Lỗ / Tổng giá trị vốn', TODO),
        sk('TC_LLTH_03', 'Lãi/lỗ thực hiện', 'Tổng khớp chi tiết', ['Chọn khoảng ngày có lệnh bán'], 'Các tổng = Σ các dòng chi tiết', TODO),
        sk('TC_LLTH_04', 'Lãi/lỗ thực hiện', 'Lọc theo mã CK / khoảng ngày', ['Nhập Mã CK, chọn Từ ngày lùi 1 tháng'], 'Chỉ còn dòng đúng mã, đúng khoảng', TODO),
        sk('TC_LLTH_05', 'Lãi/lỗ thực hiện', 'Không có lệnh bán', ['Chọn khoảng ngày không có lệnh bán'], 'Các tổng = 0 / "Không có dữ liệu"', TODO),
    ]
    yield 'Tài sản (Menu → Quản lý tài khoản → Tài sản)', [
        sk('TC_TS_01', 'Tài sản', 'Mở đúng tab Tài sản', [], 'Chuyển sang Giao dịch – tab Tài sản của tiểu khoản đang chọn',
           AUTO.replace('CÓ', 'một phần') + 'MenuTest TC_MENU_01 (UAT)'),
        sk('TC_TS_02', 'Tài sản', 'Thông tin tổng hợp', [], 'Giá trị tài sản ròng (NAV), Lãi/lỗ trong ngày, Trạng thái tiểu khoản, Tổng lãi/lỗ',
           AUTO + 'TC_INFO_03'),
        sk('TC_TS_03', 'Tài sản', 'Cơ cấu tài sản hiển thị đủ', [],
           'Hiển thị Tiền tại BSC, Tiền tại @BIDV, Phải thu, Chứng khoán, Phải trả, mỗi khoản có giá trị (không đối chiếu công thức NAV)',
           AUTO + 'TC_INFO_03'),
        sk('TC_TS_04', 'Tài sản', 'Kỳ hiệu suất đầu tư', ['Lần lượt chọn 1M / 3M / 6M / 1Y / YTD'],
           'Mỗi kỳ hiển thị Hiệu suất (%) và so sánh VNINDEX (%)', TODO),
        sk('TC_TS_05', 'Tài sản', 'Đổi tiểu khoản', ['Chọn tiểu khoản khác ở header'], 'Số liệu tài sản cập nhật theo tiểu khoản mới', TODO),
        sk('TC_TS_06', 'Tài sản', 'Tiểu khoản margin', ['Chọn tiểu khoản margin'], 'Có thêm Thặng dư ký quỹ / thông tin ký quỹ', AUTO + 'TC_INFO_03'),
    ]
    yield 'Dư nợ ký quỹ', [
        sk('TC_DNKQ_01', 'Dư nợ ký quỹ', 'Mở màn Nợ ký quỹ', [], 'Mở màn "Nợ ký quỹ" của tiểu khoản đang chọn', AUTO + 'TC_ACC_07'),
        sk('TC_DNKQ_02', 'Dư nợ ký quỹ', 'Thông tin dư nợ', [], 'Tổng dư nợ, nợ gốc, lãi vay; danh sách món vay (ngày vay, đến hạn, lãi suất)', TODO),
        sk('TC_DNKQ_03', 'Dư nợ ký quỹ', 'Tổng dư nợ khớp chi tiết', [], 'Tổng dư nợ = Σ (gốc + lãi) các món vay', TODO),
        sk('TC_DNKQ_04', 'Dư nợ ký quỹ', 'Không có dư nợ', ['Chọn tiểu khoản không vay'], 'Hiển thị 0 / "Không có dữ liệu"', TODO),
    ]


def settings_sections():
    MENU_CD = 'Chạm Menu → Cài đặt → '
    MENU_BM = 'Chạm Menu → Bảo mật → '
    def cd(tid, path, feat, desc, extra, exp, note):
        return (tid, feat, desc, steps(LOGIN[3:], path + feat, *extra), exp, note)
    NOSAVE = 'Chỉ xem / kiểm tra validate – KHÔNG lưu thay đổi (cách 1). '
    yield 'Cài đặt (chỉ xem)', [
        cd('TC_CD_TTCN_01', MENU_CD, 'Thông tin cá nhân', 'Đủ trường và có giá trị', [],
           'Họ tên, số TK, Ngày sinh, Giới tính, CMND/CCCD, Ngày cấp, Nơi cấp, ĐT di động, ĐT cố định, Email đều có giá trị',
           AUTO + 'TC_SET_01; không chụp ảnh'),
        cd('TC_CD_TTCN_02', MENU_CD, 'Thông tin cá nhân', 'Che thông tin nhạy cảm', [],
           'Số điện thoại và email bị che một phần (vd +843******11)', TODO),
        cd('TC_CD_TTCN_03', MENU_CD, 'Thông tin cá nhân', 'Không sửa được trên app', ['Chạm vào từng trường'],
           'Các trường chỉ đọc, không mở bàn phím / không cho sửa', TODO),
        cd('TC_CD_DKDV_01', MENU_CD, 'Đăng ký dịch vụ', 'Danh sách dịch vụ', [],
           'Có Kết nối BIDV @Securities, Thiết lập SMS, Thiết lập Email, Giao dịch ký quỹ / phái sinh', AUTO + 'TC_SET_02'),
        cd('TC_CD_DKDV_02', MENU_CD, 'Đăng ký dịch vụ', 'Trạng thái từng dịch vụ', ['Mở từng dịch vụ'],
           'Hiển thị trạng thái đã/chưa đăng ký; đóng không lưu', NOSAVE + TODO),
        cd('TC_CD_GD_01', MENU_CD, 'Giao diện', 'Lựa chọn giao diện', [],
           'Màn "Thiết lập giao diện" có Giao diện tối / sáng, đánh dấu chế độ đang dùng', NOSAVE + TODO),
        cd('TC_CD_NN_01', MENU_CD, 'Đổi ngôn ngữ', 'Danh sách ngôn ngữ', [],
           'Có Tiếng Việt, English, 한국어, 日本語, 简体中文; đánh dấu Tiếng Việt', NOSAVE + TODO),
        cd('TC_CD_CC_01', MENU_CD, 'Đổi cỡ chữ', 'Lựa chọn cỡ chữ', [], 'Hiển thị các cỡ chữ, đánh dấu cỡ đang dùng', NOSAVE + TODO),
        cd('TC_CD_NHTH_01', MENU_CD, 'Danh sách ngân hàng thụ hưởng', 'Danh sách tài khoản thụ hưởng', [],
           'Hiển thị các tài khoản thụ hưởng (tên, số TK, ngân hàng); có nút thêm mới (không thêm)', NOSAVE + TODO + 'Có dữ liệu cá nhân'),
    ]
    yield 'Bảo mật (chỉ xem / validate, không lưu)', [
        cd('TC_BM_DMK_01', MENU_BM, 'Đổi mật khẩu', 'Hai loại mật khẩu', [],
           'Có tab "Mật khẩu đăng nhập" và "Mật khẩu đặt lệnh", nút Xác nhận', NOSAVE + TODO),
        cd('TC_BM_DMK_02', MENU_BM, 'Đổi mật khẩu', 'Bỏ trống các ô', ['Để trống, chạm Xác nhận'],
           'Báo lỗi bắt buộc nhập; không đổi mật khẩu', NOSAVE + TODO),
        cd('TC_BM_DMK_03', MENU_BM, 'Đổi mật khẩu', 'Mật khẩu mới và xác nhận không khớp',
           ['Nhập mật khẩu cũ bất kỳ, mật khẩu mới ≠ xác nhận', 'Chạm Xác nhận'],
           'Báo lỗi không khớp; không đổi mật khẩu', NOSAVE + TODO),
        cd('TC_BM_STH_01', MENU_BM, 'Cài đặt vân tay/khuôn mặt', 'Trạng thái sinh trắc học', [],
           'Hiển thị trạng thái bật/tắt; không thay đổi', NOSAVE + TODO),
        cd('TC_BM_2FA_01', MENU_BM, 'Cài đặt xác thực 2 yếu tố', 'Phương thức hiện tại', [],
           'Hiển thị phương thức hiện tại (vd Pin / SMS-OTP) và lựa chọn đổi; không bấm Thay đổi', NOSAVE + TODO),
        cd('TC_BM_TB_01', MENU_BM, 'Thiết bị đã xác minh', 'Danh sách thiết bị', [],
           'Có ít nhất 1 thiết bị (thiết bị đang dùng); không bấm Xoá thiết bị', NOSAVE + TODO),
    ]


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
    acc = []
    for sc in ACCOUNT['screens']:
        exp = ('Hiển thị đủ: ' + ', '.join(sc['expect'])) if sc['expect'] else 'Mở đúng màn, hiển thị nội dung'
        if sc.get('rows'):
            exp += '; có ít nhất 1 dòng dữ liệu'
        if sc.get('valuesRequired'):
            exp += '; mỗi trường có giá trị'
        note = 'Chỉ xem, không lưu thay đổi.'
        if sc.get('rows'):
            note += ' Trống => KHÔNG ĐẠT.'
        if sc.get('privacy'):
            note += ' Dữ liệu cá nhân: không chụp ảnh, không ghi giá trị vào báo cáo.'
        acc.append((sc['id'], 'Quản lý tài khoản / Cài đặt', sc['title'],
                    steps(LOGIN[3:], 'Chạm Menu (☰)', f"Mở mục \"{sc['title'].split(' (')[0]}\"", 'Cuộn xem hết màn'),
                    exp, note))
        if sc['id'] == 'TC_ACC_01':
            acc.append(('TC_ACC_01b', 'Quản lý tài khoản', 'Sao kê tiền: số dư khớp',
                        steps(LOGIN[3:], 'Chạm Menu → Quản lý tài khoản → Sao kê tiền'),
                        'Đầu kỳ + Phát sinh tăng − Phát sinh giảm = Cuối kỳ', 'Kiểm tra số học trên khoảng ngày mặc định'))
    yield 'Quản lý tài khoản / Tiện ích / Cài đặt – hiển thị (tự động: AccountInfoTest)', acc
    yield from statement_sections()
    yield from settings_sections()


SUB = {'CO_SO': 'tiểu khoản thường', 'MARGIN': 'tiểu khoản margin', 'PHAI_SINH': 'tiểu khoản phái sinh'}
TYPE_NAME = {'LO': 'LO', 'ODDLOT': 'LO lô lẻ', 'ATO': 'ATO', 'ATC': 'ATC', 'MTL': 'MTL', 'MOK': 'MOK', 'MAK': 'MAK',
             'PLO': 'PLO', 'MARKET': 'lệnh thị trường (giá trần)', 'STOP_LIMIT': 'Stop Limit',
             'TRAILING_STOP': 'Trailing Stop', 'OCO': 'OCO', 'TPSL': 'TP/SL (chốt lời / cắt lỗ)'}
COND = {'STOP_LIMIT', 'TRAILING_STOP', 'OCO', 'TPSL'}


def order_row(r):
    side = 'Mua' if r['side'] == 'BUY' else 'Bán'
    gtc = r['validity'] == 'GTC'
    typ = TYPE_NAME.get(r['orderType'], r['orderType']) + (' nhiều ngày' if gtc else '')
    cond_book = r['orderType'] in COND or (r['orderType'] == 'LO' and gtc)
    if r['orderType'] == 'TPSL':
        proc = [LOGIN[3:].replace(' (tài khoản trong file cài đặt .env)', ''),
                f"Chạm Giao dịch → chọn {SUB.get(r['subAccount'], r['subAccount'])} → tab Danh mục",
                f"Chạm vào mã đang nắm giữ ({r['symbol']}, nếu không có thì mã đầu tiên) → mở sheet TP/SL",
                f"Khối lượng = {r['quantity']} (tối đa = KL đang nắm giữ)",
                f"Chốt lời = giá gợi ý ≥ … {r['price'].replace('HINT', '')}", f"Cắt lỗ = giá gợi ý ≤ … {r['triggerPrice'].replace('HINT', '')}",
                'Chạm Xác nhận → nhập PIN/OTP (nếu hỏi) → Xác nhận']
        exp = ('Thông báo đặt lệnh thành công; Sổ lệnh tab Điều kiện sinh lệnh TP/SL với trạng thái "Chờ kích hoạt" '
               '; KL khả dụng không đổi')
        return (r['id'], 'Lệnh điều kiện – TP/SL', r['description'], steps(*proc), exp,
                ('Đang bật' if r['enabled'] == 'true' else 'TẮT') + '; cần có mã trong Danh mục; tự huỷ sau khi chạy')
    proc = [LOGIN[3:].replace(' (tài khoản trong file cài đặt .env)', ''), 'Chạm Giao dịch → tab Đặt lệnh',
            f"Chọn {SUB.get(r['subAccount'], r['subAccount'])} (cấu hình order.subaccount.{r['side'].lower()}), chọn Lệnh {side.lower()}",
            f"Nhập mã {r['symbol']} ({r['exchange']}; cấu hình order.symbol.*), chọn mã gợi ý"]
    if r['orderType'] in COND:
        proc.append(f"Chọn Điều kiện → {TYPE_NAME[r['orderType']]}")
        if r['triggerPrice']:
            proc.append(f"Giá kích hoạt = {r['triggerPrice']}" + (f", Giá đặt = {r['price']}" if r['price'] else ''))
        if r['amplitude']:
            proc.append(f"Biên độ = {r['amplitude']}, Bước giá = {r['priceStep']}")
    elif r['orderType'] in ('ATO', 'ATC', 'MTL', 'MOK', 'MAK', 'PLO', 'MARKET'):
        proc.append(f"Chọn Market → {r['orderType']}")
    else:
        proc.append(f"Chọn Limit, Giá = {r['price']}")
    proc.append(f"Khối lượng = {r['quantity']}" + (', hiệu lực Nhiều ngày' if gtc else ''))
    proc.append(f"Chạm {side.upper()} → nhập PIN/OTP (nếu hỏi) → Xác nhận")
    if cond_book:
        exp = (f'Thông báo "Đặt {side.lower()} {r["symbol"]} KL {r["quantity"]} thành công"; Sổ lệnh tab Điều kiện sinh lệnh '
               f'{typ} với trạng thái "Chờ kích hoạt"; Sức mua (lệnh mua) / KL khả dụng (lệnh bán) KHÔNG ĐỔI')
    else:
        bal = 'Sức mua giảm sau khi đặt' if r['side'] == 'BUY' else 'KL khả dụng (số dư CK) giảm đúng KL đặt bán'
        exp = (f'Thông báo "Đặt {side.lower()} {r["symbol"]} … thành công"; Sổ lệnh sinh lệnh đúng thông tin với trạng thái '
               f'"Đã gửi" (đặt trong phiên) / "Chờ gửi" (đặt ngoài phiên); {bal}')
    oos = {'REJECT': 'Ngoài phiên: hệ thống từ chối (báo lỗi / khoá Xác nhận) => ĐẠT',
           'ACCEPT': 'Ngoài phiên (hệ thống đã mở cửa): vẫn nhận lệnh, chờ vào phiên => ĐẠT',
           'ANY': ''}[r['outOfSession']]
    note = '; '.join(x for x in [oos, 'Đang bật' if r['enabled'] == 'true' else 'TẮT (chưa đủ dữ liệu / số dư)',
                                 'Tự huỷ sau khi chạy'] if x)
    return (r['id'], f"Đặt lệnh – {typ}", f"{r['description']}", steps(*proc), exp, note)


def enum_labels(java_file):
    """NAME -> first quoted label of each enum constant (Vietnamese label shown in the app)."""
    import re
    src = open(os.path.join(ROOT, 'src/main/java/vn/bsc/automation/screens', java_file), encoding='utf-8').read()
    return {m.group(1): m.group(2) for m in re.finditer(r'^\s+([A-Z_0-9]+)\((?:[^"\n]*?)"([^"]+)"', src, re.M)}


LABELS = {**enum_labels('MenuItem.java'), **enum_labels('SheetOption.java')}


def tx_expected(t):
    base = 'Thông báo giao dịch thành công; không có "Lỗi hệ thống"'
    if t['menuItem'] == 'CHUYEN_CHUNG_KHOAN':
        return base + '; KL chứng khoán khả dụng của mã: tiểu khoản chuyển GIẢM, tiểu khoản nhận TĂNG đúng số CP'
    if t['option'] == 'CHUYEN_TIEN_NOI_BO':
        return (base + '; "Số tiền tối đa được chuyển" của tiểu khoản chuyển GIẢM đúng số tiền; '
                'Sao kê tiền tiểu khoản nhận có phát sinh TĂNG đúng số tiền')
    if t['amount']:
        return base + '; tiền của tài khoản/tiểu khoản chuyển GIẢM tương ứng (ít nhất bằng số tiền, có thể kèm phí)'
    return base


def transaction_sections():
    yield 'Bước 1 – Đăng nhập', [
        ('ExcelLoginTest', 'Đăng nhập', 'Đăng nhập thành công (Excel No.1)',
         steps('Mở app BSC Smart Invest', 'Chạm Menu → Đăng nhập → Đăng nhập tài khoản khác',
               'Xoá tài khoản cũ, nhập tài khoản / mật khẩu (UAT: file cài đặt .env; Production: người dùng tự nhập)',
               'Chạm Đăng nhập (nhập OTP nếu hỏi)'),
         'Đăng nhập thành công, header hiển thị đúng tài khoản', 'Điều kiện tiên quyết cho các bước sau')]
    orders = read_csv('orders.csv')
    groups = {}
    for r in [o for o in orders if o['orderType'] not in COND]:
        key = 'Phái sinh' if r['subAccount'] == 'PHAI_SINH' else f"Lệnh thường – {r['exchange']}"
        groups.setdefault(key, []).append(order_row(r))
    for k, rows in groups.items():
        yield f'Bước 2 – Đặt lệnh: {k}', rows
    acts = []
    for a in read_csv('order_actions.csv'):
        if a['action'] == 'MODIFY':
            change = []
            if a['priceSteps']:
                change.append(f"Giá: bấm {'+' if int(a['priceSteps']) > 0 else '−'} {abs(int(a['priceSteps']))} bước giá")
            change.append(f"Khối lượng = {a['newQuantity']}" if a['newQuantity'] else 'Giữ khối lượng')
            proc = steps('Chạm Giao dịch → Sổ lệnh → tab Chờ', f"Chạm vào lệnh {a['targetId']} (lệnh đang chờ) → Sửa",
                         *change, 'Chạm SỬA MUA/BÁN → Xác nhận')
            exp = 'Lệnh cũ chuyển "Đã sửa"; sinh lệnh mới với thông tin đã sửa'
        elif a['action'] == 'CANCEL':
            proc = steps('Chạm Giao dịch → Sổ lệnh → tab Chờ', f"Chạm vào lệnh {a['targetId']} → Huỷ", 'Xác nhận')
            exp = 'Thông báo huỷ thành công; lệnh chuyển "Đã huỷ"'
        else:
            proc = steps('Chạm Giao dịch → Sổ lệnh → tab Chờ', 'Chạm Huỷ tất cả → Xác nhận')
            exp = 'Mọi lệnh chờ chuyển "Đã huỷ"'
        acts.append((a['id'], 'Sửa / Huỷ lệnh', a['description'], proc, exp,
                     'Chỉ trên lệnh do test tạo; Huỷ tất cả dừng an toàn (FAIL) nếu có lệnh khác của khách hàng'))
    yield 'Bước 3 – Sửa / Huỷ lệnh (lô chẵn, lô lẻ, phái sinh)', acts
    yield 'Bước 4 – Đặt lệnh điều kiện (Stop Limit, Trailing Stop, OCO, TP/SL)', [order_row(r) for r in orders
                                                                                    if r['orderType'] in COND]
    prod = {r['id']: r['amount'] for r in read_csv('menu_transactions_prod.csv')}
    tx = []
    for t in read_csv('menu_transactions.csv'):
        what = LABELS.get(t['option'] or t['menuItem'], t['option'] or t['menuItem'])
        unit = 'CP' if t['menuItem'] == 'CHUYEN_CHUNG_KHOAN' else 'VND'
        amount = f"{int(t['amount']):,} {unit}" if t['amount'] else '—'
        prod_amt = f"{int(prod[t['id']]):,} {unit}" if prod.get(t['id']) else '—'
        tx.append((t['id'], 'Giao dịch trong Menu', what,
                   steps('Chạm Menu (☰)', f"Mở {LABELS.get(t['menuItem'], t['menuItem'])}"
                         + (f" → {LABELS.get(t['option'], t['option'])}" if t['option'] else ''),
                         'Chọn tiểu khoản chuyển / nhận (cấu hình menu.tx.from/to.subaccount, trống = mặc định của form); '
                         'tài khoản thụ hưởng: chọn tài khoản đầu tiên',
                         f'Nhập số tiền / khối lượng = {amount} (Production: {prod_amt})',
                         'Chạm Xác nhận → Xác nhận thông tin → nhập OTP/PIN (nếu hỏi) → Xác nhận'),
                   tx_expected(t),
                   ('Đang bật' if t['enabled'] == 'true' else 'TẮT') + (f" – {t['note']}" if t['note'] else '')))
    yield 'Bước 5 – Giao dịch tiền / chứng khoán trong Menu', tx
    yield 'Bước 6 – Đối chiếu sau giao dịch', [
        ('TC_POST_01', 'Sổ lệnh', 'Không còn lệnh test đang chờ',
         steps('Chạm Giao dịch → Sổ lệnh', 'Xem tab Chờ và Điều kiện'),
         'Không còn lệnh nào do test tạo ở trạng thái Đã gửi/Chờ gửi/Chờ kích hoạt', 'Bảo đảm không có lệnh khớp ngoài ý muốn'),
        ('TC_POST_02', 'Lịch sử đặt lệnh', 'Lịch sử ghi nhận lệnh vừa đặt',
         steps('Chạm Menu → Quản lý tài khoản → Lịch sử đặt lệnh'),
         'Có dòng cho từng lệnh thường vừa đặt (mã, M/B, KL) với trạng thái Đã sửa / Đã huỷ', ''),
        ('TC_POST_03', 'Sao kê tiền', 'Sao kê tiểu khoản chuyển ghi nhận chuyển tiền nội bộ',
         steps('Chạm Menu → Quản lý tài khoản → Sao kê tiền'),
         'Có dòng "Chuyển khoản nội bộ" giảm đúng số tiền của TX01; Đầu kỳ + tăng − giảm = Cuối kỳ', 'Chỉ khi TX01 thành công'),
        ('TC_POST_04', 'Sao kê tiền', 'Sao kê tiểu khoản NHẬN ghi nhận tiền tăng',
         steps('Chạm Menu → Quản lý tài khoản → Sao kê tiền', 'Chọn Tiểu khoản = tiểu khoản nhận của TX01'),
         'Có dòng phát sinh TĂNG đúng số tiền của TX01', 'Chỉ khi TX01 thành công'),
    ]


HEAD = ['No', 'REQ', 'Feature', 'Test Case Description', 'Test Case Procedure', 'Expected Output',
        'Mã TC tự động', 'Dữ liệu / Ghi chú']
WIDTH = [6, 22, 20, 40, 48, 48, 15, 44]
thin = Side(style='thin', color='BFBFBF')
BORDER = Border(left=thin, right=thin, top=thin, bottom=thin)
WRAP = Alignment(wrap_text=True, vertical='top')


def write_sheet(ws, sections, title, rules):
    ws.append([title])
    ws['A1'].font = Font(bold=True, size=13)
    ws.append([rules])
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


MARKET_SHEET = ['Đăng nhập / Đăng xuất', 'Điều hướng', 'Thị trường – dữ liệu', 'Thị trường – tính năng (Tổng quan)',
                'Bảng giá', 'Tìm kiếm', 'Chi tiết mã']
all_ro = list(readonly_sections())
market = [x for x in all_ro if x[0] in MARKET_SHEET]
moved = [x for x in all_ro if x[0] not in MARKET_SHEET]   # tab Giao dịch + Menu phải
tx = list(transaction_sections())
post = [x for x in tx if x[0].startswith('Bước 6')]
tx = [x for x in tx if not x[0].startswith('Bước 6')]
tab = [('Bước 6 – Tab Giao dịch: ' + n, r) for n, r in moved if n.startswith('Sổ lệnh')]
menu = [('Bước 7 – Menu: ' + n, r) for n, r in moved if not n.startswith('Sổ lệnh')]
post = [('Bước 8 – Đối chiếu sau giao dịch', post[0][1])] if post else []

wb = Workbook()
ws = wb.active
ws.title = 'Thị trường – Bảng giá'
n1 = write_sheet(ws, market, 'BSC Mobile Trading – THỊ TRƯỜNG – BẢNG GIÁ (suite testng-market.xml)',
                 'Không đặt lệnh, không chuyển tiền, không lưu thay đổi. Lỗi hệ thống (E_SYSTEM_ERROR / "Lỗi hệ thống") luôn '
                 'KHÔNG ĐẠT; dữ liệu trống ở tab/mục được liệt kê => KHÔNG ĐẠT. Cột "Mã TC tự động" = tên test trong code.')
ws2 = wb.create_sheet('Giao dịch')
n2 = write_sheet(ws2, tx + tab + menu + post, 'BSC Mobile Trading – GIAO DỊCH (suite testng-transaction.xml)',
                 'Tham số cấu hình được (trống = mặc định): order.subaccount.buy/sell, order.symbol.*, menu.tx.amount, '
                 'menu.tx.stock.qty, menu.tx.from/to.subaccount (config.properties / .env). GIAO DỊCH THẬT, chạy theo thứ tự: '
                 'Đăng nhập → Lệnh thường → Sửa/Huỷ → Lệnh điều kiện + TP/SL → Giao dịch tiền/chứng khoán trong Menu → '
                 'Tab Giao dịch → Menu (Quản lý tài khoản, Tiện ích, Cài đặt, Bảo mật – chỉ xem/validate) → Đối chiếu. '
                 'Lỗi hệ thống luôn KHÔNG ĐẠT. Lệnh do test tạo được tự huỷ. Production chỉ chạy khi chủ tài khoản cho phép '
                 '(env=prod, tự nhập OTP, tiền 10,000 VND).')
wb.save(OUT)
print(OUT, n1, 'Thị trường – Bảng giá +', n2, 'Giao dịch')
