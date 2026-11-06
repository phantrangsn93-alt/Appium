# Locator – Menu phải (☰) – BSC Mobile Trading

Khảo sát trên bản UAT `UAT.1-master-UAT5`, Xiaomi Mi MIX 2S (Android 10), ngày 06/11/2026.
Code tương ứng: `MenuItem`, `SheetOption`, `MenuScreen.open()`, `ActionSheet`, `MenuTargetScreen`,
`TransactionFormScreen`. Test kiểm chứng: `MenuTest` (`mvn test -Dsuite=testng-menu.xml`).

## Quy tắc chung

| Thành phần | Locator | Ghi chú |
|---|---|---|
| Nút mở Menu | `Locators.a11y("Mở menu di chuyển")` | Button ở header |
| Tiêu đề Menu | `Locators.classWithDesc("android.widget.Button", "Menu")` | |
| Nhóm / mục Menu | `Locators.a11y(<nhãn>)` | Nhóm là ImageView, mục con là View (riêng "Đổi ngôn ngữ" là ImageView) |
| Tiêu đề màn hình | `Locators.a11y(<tiêu đề>)` | Xem cột "Tiêu đề" bên dưới |
| Ô "nhãn + giá trị" (tiểu khoản, Mã CK, M/B, Trạng thái, Loại phí…) | `Locators.field(<nhãn>)` | content-desc = `nhãn\ngiá trị`; giá trị chứa **zero-width space (U+200B) giữa từng ký tự** → đọc qua `MenuTargetScreen.fieldValue()` / `Locators.nfc()` (đã tự bỏ U+200B) |
| Giá trị nằm dưới nhãn (số dư…) | `MenuTargetScreen.valueBelow(<nhãn>)` | Đọc từ 1 snapshot page source |
| Tab | `Locators.tab(<tên>)` | content-desc = `tên\nTab N trong tổng số M` |
| Ô nhập (số tiền, tìm mã) | `Locators.editText(0)` | Flutter TextField không có id |
| Nút Xác nhận | `Locators.a11y("Xác nhận")` | |

**Lưu ý khi chạy:**
- Nhãn trong app trộn NFC/NFD → luôn dùng `Locators.*`, không so sánh chuỗi trực tiếp, không dùng XPath `@content-desc='…'`.
- Mục ở sát đáy danh sách có tâm nằm dưới thanh điều hướng Android → bấm vào là bấm **HOME**.
  `MenuScreen.open()` tự cuộn để tâm mục nằm trong 85% chiều cao cửa sổ.
- Nhóm Menu là accordion: bấm lần 2 sẽ **đóng** nhóm. `MenuScreen.open()` kiểm tra `isGroupExpanded()` trước.
- Back từ màn con → quay lại Menu (nhóm vẫn mở). Riêng "Tài sản" chuyển sang tab 4 màn Giao dịch.

## Danh sách mục Menu

| Nhóm | Mục (`MenuItem`) | Loại | Tiêu đề / đích |
|---|---|---|---|
| Giao dịch tiền | Chuyển tiền (`CHUYEN_TIEN`) | Bottom sheet | "Chọn loại chuyển tiền" |
| | Nộp/Rút tiền tài khoản phái sinh | Bottom sheet | "Chọn loại giao dịch" |
| | Nộp/Rút tiền CCP | Bottom sheet | "Chọn loại giao dịch" |
| | Phong toả/Giải toả tiền @BIDV | Bottom sheet | "Chọn loại giao dịch" |
| | Cho vay | Màn hình | "Cho vay" – tab "Chuyển tiền cho vay" / "Đăng ký HĐ khung", nút "Tạo mới" |
| Giao dịch chứng khoán | Chuyển chứng khoán | Form | "Chuyển chứng khoán" |
| | Đăng ký quyền mua | Màn hình | "Đăng ký quyền mua" – cột Mã CK / KL mua tối đa / Ngày hết hạn |
| | Đăng ký mua chứng quyền IPO | Màn hình | tab "Danh sách IPO" / "IPO đã đăng ký" |
| Tiện ích | Đăng ký voucher | Màn hình | "Voucher" – tab "Còn hiệu lực" / "Nhận ưu đãi" |
| | Xác nhận lệnh Online | Màn hình | Lọc Tiểu khoản, Từ/Đến ngày, M/B; nút "Xác nhận tất cả", "Chọn lệnh" |
| | Tra cứu danh mục ký quỹ | Màn hình | Cột Rổ CK / Mã CK / Tỷ lệ vay / Room còn lại |
| | Đố chứng hứng quà | Màn hình | |
| | Cảnh báo | Màn hình | |
| Quản lý tài khoản | Tài sản | Tab | Tab 4 "Tài sản" của màn Giao dịch |
| | Dư nợ ký quỹ | Màn hình | **"Nợ ký quỹ"** (khác nhãn menu) |
| | Sao kê tiền | Màn hình | Tiểu khoản, Từ/Đến ngày, Đầu kỳ / Phát sinh tăng / Phát sinh giảm / Cuối kỳ, danh sách giao dịch |
| | Sao kê chứng khoán | Màn hình | Tiểu khoản, Từ/Đến ngày, `field("Mã CK")` |
| | Lịch sử đặt lệnh | Màn hình | Tiểu khoản, Từ/Đến ngày, ô mã (EditText "Tất cả"), `field("Trạng thái")`; cột Mã CK / M/B / Giá đặt / KL đặt / Trạng thái |
| | Lịch sử lệnh điều kiện | Màn hình | như trên; cột Mã CK / Loại lệnh / KL / Trạng thái |
| | Lịch sử khớp lệnh | Màn hình | như trên + `field("Loại giao dịch")`; cột Ngày / Mã CK / M/B / Giá khớp / KL khớp |
| | Lãi/lỗ thực hiện | Màn hình | Tiểu khoản, Từ/Đến ngày, `field("Mã CK")`; Tổng giá trị vốn / Tổng giá trị thực hiện / Lãi/Lỗ |
| Bảo mật | Đổi mật khẩu | Màn hình | tab "Mật khẩu đăng nhập" / "Mật khẩu đặt lệnh", nút "Xác nhận" |
| | Cài đặt vân tay/khuôn mặt | Màn hình | |
| | Cài đặt xác thực 2 yếu tố | Màn hình | Phương thức hiện tại ("Pin"), `field("Đổi phương thức xác thực mới")`, nút "Thay đổi" |
| | Thiết bị đã xác minh | Màn hình | Mỗi thiết bị có nút "Xoá thiết bị" – **không bấm** |
| Cài đặt | Thông tin cá nhân | Màn hình | Chỉ đọc (có dữ liệu cá nhân – không ghi vào báo cáo) |
| | Đăng ký dịch vụ | Màn hình | Kết nối BIDV @Securities, Thiết lập SMS, Thiết lập Email, Giao dịch ký quỹ/phái sinh |
| | Giao diện | Màn hình | **"Thiết lập giao diện"** – Giao diện tối / sáng |
| | Đổi ngôn ngữ | Màn hình | Tiếng Việt, English, 한국어, 日本語, 简体中文 |
| | Đổi cỡ chữ | Màn hình | SeekBar "Cỡ chữ" |
| | Danh sách ngân hàng thụ hưởng | Màn hình | nút "Thêm", "Xoá" |
| Hỗ trợ | Tổng đài | Ra ngoài | **Gọi điện thật – không tự động hoá** |
| | Chat với tư vấn viên / Hướng dẫn sử dụng / Sổ tay giao dịch | Ra ngoài | Mở Chrome |
| | Mạng lưới | Ra ngoài | Hộp thoại xin quyền Vị trí của Android |
| — | Ý kiến đóng góp | Ra ngoài | Microsoft Forms trên Chrome |
| — | Thông tin phần mềm | Mở rộng tại chỗ | Dòng "Version <bản build>" |
| — | BSC Mtrader | Ra ngoài | Chrome Custom Tab |

## Form giao dịch tiền (`SheetOption` → `TransactionFormScreen`)

| Lựa chọn trên bottom sheet | Tiêu đề form | Ô tiểu khoản (`field`) | Nhãn số dư (`valueBelow`) | Ô số tiền (EditText, content-desc) |
|---|---|---|---|---|
| Chuyển tiền nội bộ | Chuyển tiền nội bộ | Tiểu khoản chuyển, Tiểu khoản nhận | Số tiền tối đa được chuyển | "Chuyển tiền" |
| Chuyển tiền ra ngoài | Chuyển tiền ra ngoài | Tiểu khoản, Số tài khoản ngân hàng, Loại phí | Số tiền tối đa được chuyển | "Chuyển tiền" (+ Ngân hàng, Người thụ hưởng, Tỉnh/Thành phố, Chi nhánh) |
| Nộp tiền vào TK phái sinh | Nộp tiền vào TK phái sinh | Tiểu khoản nhận, Tiểu khoản chuyển | Số dư tiền | "Nộp tiền vào TK phái sinh" |
| Rút tiền từ TK phái sinh | Rút tiền từ TK phái sinh | Tài khoản giao dịch, Tiểu khoản nhận | Tiền mặt khả dụng | "Rút tiền từ TK phái sinh" |
| Nộp tiền ký quỹ CCP | Nộp tiền ký quỹ CCP | Tiểu khoản chuyển, Loại phí | Số dư khả dụng | "Yêu cầu nộp ký quỹ bằng tiền" |
| Rút tiền ký quỹ CCP | Rút tiền ký quỹ CCP | Tiểu khoản nhận, Loại phí | Số dư khả dụng | "Yêu cầu rút ký quỹ bằng tiền" |
| Phong toả tiền | Phong toả tiền @BIDV | Tiểu khoản | Số dư phong toả, Số dư khả dụng | (không thấy EditText trên màn đầu) |
| Giải toả tiền | Giải toả tiền @BIDV | Tiểu khoản | Số dư phong toả, Số dư khả dụng, Số tiền có thể giải toả | (không thấy EditText trên màn đầu) |

Form **Chuyển chứng khoán**: Tiểu khoản chuyển, Tiểu khoản nhận, Mã CK, Tổng khối lượng, Khối lượng có thể chuyển, "Xác nhận".

**Chưa khảo sát:** nội dung danh sách khi mở dropdown tiểu khoản / Loại phí / ngân hàng, và bước OTP/PIN sau
"Xác nhận" (khảo sát chỉ mở form, không gửi giao dịch).
