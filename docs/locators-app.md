# Khảo sát app & locator – BSC Mobile Trading (ngoài Menu)

Bản UAT `UAT.1-master-UAT5`, Xiaomi Mi MIX 2S (Android 10), tài khoản test 002C000806, ngày 06/11/2026.
Menu phải (☰) xem [locators-menu.md](locators-menu.md). Quy tắc locator chung (NFC/NFD, zero-width, `Locators.*`)
cũng ở file đó.

## 0. Thông tin dùng cho test

| Mục | Giá trị | Cấu hình |
|---|---|---|
| Package / Activity | `com.bsc.mobiletrading` / `.MainActivity` | `config.properties` |
| Tài khoản test UAT | 002C000806 (Ngô Tấn Long) | `.env`: `BSC_USERNAME`, `BSC_PASSWORD`, `BSC_ORDER_PIN` |
| Tiểu khoản – cơ sở | `Thường - 0101042221` | `subaccount.coso=Thường` |
| Tiểu khoản – margin | `MR10+ BSC - 0101000806`, `MR90 BSC ĐB - 0101098819` | `subaccount.margin=MR10+ BSC` |
| Tiểu khoản – phái sinh | `Phaisinh - 0101042221` | `subaccount.phaisinh=Phaisinh` |
| Phương thức xác thực 2 yếu tố | Pin (có thể đổi sang SmartOTP) | Menu > Bảo mật |
| Mã phái sinh đang giao dịch | 41I1GB000 (VN30F 11/2026), 41I1GC000, 41I1H3000, 41I1H6000; VN100: 41I2GB000…; TPCP: 41B5GC000 | `orders.csv` |
| Môi trường | `ENV=uat` → `config-uat.properties` (cho phép đặt lệnh thật) | `.env` |

Data test chỉ ghi **loại** tiểu khoản (`CO_SO` / `MARGIN` / `PHAI_SINH`) – xem `SubAccount`.

## 1. Thanh điều hướng dưới (`BottomNavBar`)

ImageView, content-desc: `Thị trường`, `Bảng giá`, `Giao dịch`, `Tìm kiếm`, `Thông báo`
(có badge → desc `1\nThông báo` – dùng `descContains("Thông báo")`).

Header: `<Tên> - <Mã TK>\n<tiểu khoản>` + Button `Mở menu di chuyển`. Bấm dòng tiểu khoản (View clickable
`<tên> - <số TK>`) → bottom sheet **"Tiểu khoản"** liệt kê 4 tiểu khoản + Button `Đóng`.

## 2. Thị trường (`MarketScreen`)

| Thành phần | Locator / nội dung |
|---|---|
| Tab | `Tổng quan`, `Cổ phiếu`, `Phái sinh`, `Chứng quyền`, **`ETFs`** (tab 5, phải vuốt thanh tab sang trái) |
| Lối tắt (ImageView) | `SmartOTP` (không có dấu cách), `Mở tài khoản`, `Khuyến nghị`, `Nộp tiền`; vuốt ngang: `Lịch sự kiện`, `Danh mục`, `Đồ thị`, `Tuỳ chỉnh` |
| NAV | `Tài sản ròng (NAV)` + giá trị `------------` (bấm để hiện) |
| Thẻ chỉ số | `VN INDEX\n<điểm>\n<+/-> (<%>)\n<GT> (Tỷ VND)` → bấm mở **chi tiết chỉ số** |
| Lọc sàn | `Tất cả`, `HOSE`, `HNX`, `UPCOM` |
| Khác | `Xem bản đồ nhiệt` (ImageView), `Tin tức\nXem thêm`, `Lịch sự kiện\nXem thêm` |

| Lối tắt | Kết quả |
|---|---|
| SmartOTP | Hộp thoại "Cấp quyền" – "cài đặt mật khẩu mở khoá cho thiết bị", Button `Bỏ qua` / `Chấp nhận` (**chỉ bấm Bỏ qua**) |
| Mở tài khoản | "Mở tài khoản EKYC" – Button-like View `Bắt đầu mở tài khoản` (không tự động hoá) |
| Khuyến nghị | Như màn Bảng giá (danh mục Khuyến nghị) |
| Nộp tiền | Bottom sheet "Nộp tiền vào TKCK": chọn tài khoản `BSC<số TK>` + tên tiểu khoản |
| Lịch sự kiện | "Lịch sự kiện": lọc `Tất cả yêu thích`, `Tất cả`, từ/đến ngày `dd/MM/yy`, ô `Tìm kiếm`; cột Danh mục / Lịch sự kiện / Ngày GD KHQ |
| Danh mục | Tab Danh mục của màn Giao dịch |
| Đồ thị | Biểu đồ **WebView** (không có node native; context `WEBVIEW_chrome`) |
| Tuỳ chỉnh | "Cài đặt chức năng nổi bật" – danh sách lối tắt để bật/tắt |

**Tab Cổ phiếu:** lọc sàn + nhóm `Khối lượng đột biến\nXem thêm`, `Khối lượng lớn nhất\nXem thêm`.
Dòng mã: `MÃ\n|\nSÀN\n<Tên>\n<Giá>\n<KL>\n<+/->\n<%>` → bấm mở **chi tiết mã**.
**Tab Phái sinh:** nhóm `Chỉ số` và `Trái phiếu chính phủ`; dòng: `41I1GB000\nVN30 Index Futures 112026\n<giá>\n<KL>\n<+/->\n<%>`.
**Tab Chứng quyền:** `Khối lượng lớn nhất` / `Tăng mạnh nhất` / `Giảm mạnh nhất` (+ Xem thêm).
**Tab ETFs:** danh sách quỹ (E1VFVN30, FUE…), cùng định dạng dòng mã.

**Chi tiết chỉ số** (VN INDEX): tiêu đề `VNINDEX\n<điểm>\n<+/-> (<%>)`, số mã tăng/đứng/giảm,
nhãn `Tổng khối lượng`, `Tổng giá trị (tỷ đồng)`, `Biến động trong ngày`, `Biến động 52 tuần`
(**giá trị có zero-width space** – đọc qua `Locators.nfc`).

## 3. Chi tiết mã (`StockDetailScreen`)

| Thành phần | Locator / nội dung |
|---|---|
| Header | ImageView `TLD\n|\nHOSE\n<tên>`; giá View `7.90\n0.00 (0.00%)` |
| Tab | `Tổng quan`, `Lịch sử`, `Tin tức`, `Phân tích`, `Lịch sự kiện` |
| Bảng giá (nhãn + giá trị là 2 View liền nhau) | Trần, Tham chiếu, Sàn, Cao nhất, Thấp nhất, Room NN, Tổng GT (Tỷ), Tổng KL, NN Mua, NN Bán, EPS, Book Value, P/E, P/B → `StockDetailScreen.value(label)` |
| Nút | Button `Mua`, Button `Bán` → màn Đặt lệnh, mã đã chọn sẵn |
| Sổ lệnh | `Dư mua` / `Dư bán` (%), cột `KL mua`, `Giá mua`, `Giá bán`, `KL bán` (3 bước giá) |
| Tab con | `Khối lượng khớp`, `Lịch sử khớp lệnh` |

Mở từ: dòng mã ở Thị trường/Bảng giá, hoặc `SearchScreen.search("TLD").openStock("TLD")`.

## 4. Bảng giá

| Thành phần | Locator / nội dung |
|---|---|
| Thêm danh mục | nút `+` – **không có content-desc** (ImageView đầu hàng, bên trái dropdown) |
| Dropdown danh mục | View `Khuyến nghị` (tên danh mục đang chọn) → sheet "Chọn danh mục": nhóm `BSC Cloud` (`Khuyến nghị`, `Danh mục nắm giữ`) và `Thiết bị` (danh mục lưu trên máy, VD `Danh mục yêu thích`) |
| Dropdown chỉ số | View `Chỉ số` → sheet Button `VNINDEX`, `VN30`, `HNXINDEX`, `HNX30`, `VN100`, `VNALL`, `Đóng` |
| CP ngành | View `CP ngành` |
| Xoay ngang | icon xoay (bảng giá ngang) – **không có content-desc** |
| Danh mục Khuyến nghị | lọc `BSC10` / `BSC30` / `BSC50`; cột `Mã`, `Khuyến nghị`, `Giá TT`, `Mục tiêu` (UAT hiện chưa có dữ liệu, chỉ có tin tức) |
| Danh sách theo chỉ số | sắp xếp `A-Z` / `Giá` / `Khối lượng`; dòng `MÃ\n|\nSÀN\n<Tên>\n<Giá>\n<KL>\n<+/->\n<%>` |

## 5. Giao dịch (`TradingScreen`)

Tab: `Đặt lệnh`, `Sổ lệnh`, `Danh mục`, `Tài sản` (`Locators.tab`).

**Đặt lệnh (`OrderFormScreen`):** `Lệnh mua` / `Lệnh bán`; thẻ mã `MÃ\n|\nSÀN\n<giá>\n<tên>\n<+/->`;
3 giá Trần / TC / Sàn (View); `Limit` / `Market` / `Điều kiện`; Giá (x1000), Khối lượng; `25%`…`100%`;
Hiệu lực `Trong ngày` / `Nhiều ngày` (**chỉ là chữ trong khối form, không có node riêng**); Button-like View `MUA` / `BÁN`.

**Lệnh LO nhiều ngày:** `Limit` + hiệu lực `Nhiều ngày` (RadioButton thứ 2, không có content-desc –
`OrderFormScreen.selectValidity(1)`) → hiện thêm `Đến ngày` (View `dd/MM/yyyy`, mặc định 27/12/2026 –
`getExpiryDate()`). Bấm ngày → **date picker**: 3 SeekBar (ngày / `Tháng N` / năm) + Button `Huỷ bỏ` / `Đồng ý`.
⚠ Đổi tiểu khoản khi date picker đang mở → picker **treo** (cả 2 nút không phản hồi, Back không đóng) – chỉ
khởi động lại app mới hết (và app về chế độ khách → phải đăng nhập lại).

**Lệnh Điều kiện** (`OrderFormScreen.selectConditionalType(...)`): dropdown `Kiểu lệnh` → sheet Button
`Stop Limit` / `Trailing Stop` / `OCO` / `Đóng`. Gợi ý (hint) trên form, x = giá tham chiếu (AAA: 7.53):

| Kiểu lệnh | Trường (theo thứ tự) | Hint Mua | Hint Bán |
|---|---|---|---|
| Stop Limit | Giá kích hoạt, Giá đặt, Khối lượng | kích hoạt `> x`, giá đặt `> 0.00` | kích hoạt `< x`, giá đặt `< 0.00` |
| Trailing Stop | Biên độ, Bước giá, Khối lượng | biên độ `≤ 1.04` | biên độ `≤ 1.04` |
| OCO | **LO**: Giá đặt; **Stop Limit**: Giá kích hoạt, Giá đặt; Khối lượng | LO `≤ x`, kích hoạt `> x` | LO `≥ x`, kích hoạt `< x` |

Mọi kiểu: KL `≤ <KL tối đa>`, Hiệu lực `Trong ngày` / `Nhiều ngày` + `Đến ngày`. Các ô giá/KL là View `0`
không nhãn (stepper `[- 0 +]`), xác định theo hàng. Lệnh điều kiện đã đặt xem ở Sổ lệnh (lọc `Điều kiện`) và
Menu > Quản lý tài khoản > Lịch sử lệnh điều kiện.

**TP/SL (chốt lời / cắt lỗ) từ Danh mục** (`PortfolioScreen.openTpSl("AAA")` → `TpSlSheet`): bấm dòng mã đang
nắm giữ → bottom sheet `TP/SL`: `Mã CK`, `Giá vốn`, `Khối lượng` `[- 0 +]` `≤ <KL nắm giữ>`, `Chốt lời`
`[- 0 +]` `≥ x` + `Lãi/Lỗ dự kiến`, `Cắt lỗ` `[- 0 +]` `≤ x` + `Lãi/Lỗ dự kiến`, Button `Xác nhận`
(**tạo lệnh thật**); vùng `Scrim` phía trên đóng sheet.

**Danh mục đang nắm giữ (UAT 06/11/2026):** chỉ tiểu khoản **MR10+ BSC - 0101000806** có CP: AAA 852,800 (giá vốn
8.45), DBC 112,000, MBB 250,000, MBB_WFT 62,500, PPH 51,900, PPH_WFT 2,595. Thường / MR90 / Phaisinh: trống
→ test **bán** và **TP/SL** dùng tiểu khoản `MARGIN` (`orders.csv`: SELL_LO, SELL_ODD).

**Sổ lệnh (`OrderBookScreen`):** tổng `Tổng mua`, `Tổng bán`, `Giá trị ròng (Mua - Bán)`; lọc `Tất cả`, `Chờ`,
`Khớp`, `Huỷ`, `Điều kiện`; cột `Mã CK`, `M/B`, Giá đặt, KL, Trạng thái; Button `Huỷ tất cả`, `Chọn lệnh Huỷ`
(**không bấm ngoài kịch bản**).

**Danh mục:** `Tổng giá trị vốn`, `Tổng giá trị thị trường`, `Lãi/Lỗ`; `Xem biểu đồ`; cột `Mã CK`, `Lãi/Lỗ`;
rỗng → `Không có dữ liệu`.

**Tài sản:** `Giá trị tài sản ròng`, `Lãi/lỗ trong ngày`, `Trạng thái tiểu khoản` (VD `An toàn`), `Tổng lãi/lỗ`;
`Xem hiệu suất đầu tư` (1M/3M/6M/1Y/YTD); dòng `Tiền tại @BIDV`, `Tiền tại BSC`, `Phải thu`, `Chứng khoán`,
`Phải trả`; tiểu khoản margin thêm `Thặng dư ký quỹ`, `Tỷ lệ ký quỹ hiện tại`, `Hạn mức vay tối đa`.

## 6. Tìm kiếm (`SearchScreen`)

Ô nhập = `editText(0)`; nhóm `Chứng khoán` / `Tin tức` / `Chức năng`; `Tìm kiếm gần đây`.
Kết quả mã: `MÃ\n | SÀN\n<Tên>` (VD `HAH\n | HOSE\nCông ty Cổ phần Vận tải và Xếp dỡ Hải An`).
Lưu ý: vuốt màn khi bàn phím đang mở sẽ **gõ chữ bằng thao tác vuốt** (swipe typing) → ẩn bàn phím trước.

## 7. Thông báo (`NotificationScreen`)

Tab `Giao dịch`, `Khuyến nghị`, `Cảnh báo`, `Khác`; mỗi tin: `dd/MM/yyyy\nHH:mm:ss\n<tiêu đề>` + nội dung.

## 8. Đăng nhập / phiên

- **Hết phiên:** dialog `Phiên đăng nhập đã hết hạn\n<mã sự kiện>` + Button `Đóng` → về màn đăng nhập
  dạng *nhớ tài khoản*: tên + mã TK, EditText mật khẩu (`••••••`), `Quên mật khẩu?`, Button `Đăng nhập`,
  `Đăng nhập tài khoản khác`, lối tắt `Thị trường` / `Thông báo` / `Liên hệ`, `Điều khoản & Chính sách`.
- Bấm Đăng nhập khi trống mật khẩu → `Không để trống trường mật khẩu` + Button `Đóng`.
- Đăng nhập lại mất ~10 giây trước khi vào màn chính.
- Trong ngày 06/11/2026 phiên hết hạn **2 lần** khi đang thao tác (không bấm Đăng xuất).

## 9. Màn hình ngoài app / quyền hệ thống

- `Mạng lưới` (Menu > Hỗ trợ): hỏi quyền **Vị trí** (`CHỈ CHO PHÉP KHI DÙNG ỨNG DỤNG` / `TỪ CHỐI`), sau đó
  Google Map + danh sách chi nhánh (`Hội sở chính\n<địa chỉ>\n<x> km`), `Phóng to` / `Thu nhỏ`.
  Framework đặt `autoGrantPermissions=true` nên khi chạy test quyền được cấp tự động.
- Mở trình duyệt: Chat với tư vấn viên, Hướng dẫn sử dụng, Sổ tay giao dịch, Ý kiến đóng góp, BSC Mtrader.

## 10. Phát hiện cần BA/Dev xem (06/11/2026)

| # | Hiện tượng | Ở đâu |
|---|---|---|
| 1 | Bấm **Mua** ở chi tiết mã TLD → form Đặt lệnh hiện Trần/TC/Sàn **8.66 / 8.10 / 7.54**, trong khi chi tiết mã hiện **8.45 / 7.90 / 7.35** | Chi tiết mã → Đặt lệnh |
| 2 | Bấm Mua từ chi tiết mã tự **đổi tiểu khoản** sang `MR90 BSC ĐB` | Chi tiết mã → Đặt lệnh |
| 3 | Nội dung thông báo còn **biến mẫu chưa thay** `[Customer name] thân mến` | Thông báo |
| 4 | Phiên đăng nhập hết hạn 2 lần trong ngày khi đang thao tác, app không báo trước | Toàn app |
| 5 | Nhãn giá trị (tiểu khoản, số liệu chỉ số) chèn **zero-width space** giữa từng ký tự | Nhiều màn – ảnh hưởng screen reader |
| 6 | Nút `+` (thêm danh mục) và icon xoay bảng giá **không có nhãn accessibility** | Bảng giá |
| 7 | Hiệu lực `Trong ngày` / `Nhiều ngày` không có node riêng → không chọn được bằng accessibility | Đặt lệnh |
| 8 | Danh mục Khuyến nghị (BSC10/30/50) không có dữ liệu, hiển thị tin tức từ 2022 | Bảng giá / Khuyến nghị (UAT) |
| 9 | Có tin tức thử nghiệm `test news`, `111111` trên UAT | Thị trường > Tin tức |
| 10 | Date picker "Đến ngày" **treo** nếu đổi tiểu khoản khi đang mở (Huỷ bỏ/Đồng ý/Back đều không tác dụng) | Đặt lệnh – Nhiều ngày |
| 11 | Khởi động lại app → mất phiên, về chế độ khách; đăng nhập lại mất 10–30 giây | Toàn app |
