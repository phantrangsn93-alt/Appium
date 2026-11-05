# BSC Mobile Trading – Appium Autotest

Framework kiểm thử tự động cho app **BSC Mobile Trading** (`com.bsc.mobiletrading`, Flutter, Android)
viết bằng **Java 17 + Appium (UiAutomator2) + TestNG + Allure**.

> ⚠️ **App giao dịch THẬT.** Framework không có hàm nào gửi lệnh thật. Nút MUA/BÁN chỉ được bấm khi
> khối lượng = 0 (để kiểm tra validate), và `OrderFormScreen.tapSubmitExpectingValidation()` sẽ ném
> `IllegalStateException` nếu khối lượng > 0 (`order.submit.enabled=false`). Không bấm "Huỷ tất cả" / "Chọn lệnh Huỷ".

---

## 1. Yêu cầu môi trường

| Thành phần | Phiên bản đã kiểm chứng |
|---|---|
| JDK | 17 |
| Maven | 3.9.x |
| Node.js | 20.x |
| Appium server | 3.9.0 (`npm i -g appium@3.9.0`) |
| UiAutomator2 driver | 8.7.3 (`appium driver install uiautomator2@8.7.3`) |
| Android SDK platform-tools | adb trong PATH, biến `ANDROID_HOME` |
| Thiết bị | Android 10 – Xiaomi Mi MIX 2S (`dabfffa4`) |

```bash
export JAVA_HOME=<jdk17>
export ANDROID_HOME=$HOME/Library/Android/sdk
adb devices            # thiết bị phải ở trạng thái "device"
```

## 2. Cấu hình

1. Copy `.env.example` → `.env` và điền tài khoản test (file `.env` đã nằm trong `.gitignore`):
   ```
   BSC_USERNAME=...
   BSC_PASSWORD=...
   ```
2. Cấu hình chung: `src/main/resources/config/config.properties` (+ `config-<env>.properties`).
   Thứ tự ưu tiên: `-Dkey=value` > biến môi trường / `.env` (`KEY_NAME`) > `config-<env>` > `config`.

| Key | Mặc định | Ý nghĩa |
|---|---|---|
| `appium.server.url` | `http://127.0.0.1:4723` | Appium server |
| `appium.server.autostart` | `true` | Tự khởi động Appium (bind 127.0.0.1) nếu chưa chạy |
| `device.udid` | `dabfffa4` | Serial thiết bị |
| `app.package` / `app.activity` | `com.bsc.mobiletrading` / `.MainActivity` | App cần test |
| `no.reset` | `true` | Giữ dữ liệu app (phiên đăng nhập) giữa các session |
| `retry.max.count` | `1` | Số lần retry test lỗi |
| `video.on.failure` | `true` | Quay màn hình, đính kèm video khi fail |
| `order.submit.enabled` | `false` | **Giữ false** – khoá an toàn chống đặt lệnh thật |

## 3. Chạy test

```bash
mvn test                                        # Smoke (testng-smoke.xml)
mvn test -Dsuite=testng-regression.xml          # Regression đầy đủ
mvn test -Dsuite=testng-postlogin.xml           # Chỉ các màn sau đăng nhập (không đăng xuất)
mvn test -Ddevice.udid=XXXX -Denv=uat           # Ghi đè cấu hình
mvn test -Dsuite=testng-unit.xml                # Unit test framework (không cần thiết bị)
mvn allure:serve                                # Xem báo cáo Allure
```

* `LoginTest` cần `.env`; nếu thiếu, suite đăng nhập được **skip** (không đăng xuất được thì không chạy).
* Các suite sau đăng nhập dùng `LoginFlow.ensureLoggedIn()`: nếu app đã đăng nhập thì dùng luôn,
  nếu chưa thì đăng nhập bằng tài khoản trong `.env`.

## 4. Cấu trúc

```
src/main/java/vn/bsc/automation
├── core
│   ├── config      ConfigReader (properties + .env + -D), AppConfig
│   ├── driver      AppiumServerManager, CapabilityBuilder, DriverFactory, DriverManager (ThreadLocal)
│   ├── base        BaseScreen (wait, tap, đọc text, retry stale)
│   └── utils       Locators, WaitUtils, GestureUtils (W3C), UiNode (snapshot), NumberUtils, AllureUtils
├── screens         Page Object cho từng màn hình + components (BottomNavBar, AppHeader, AlertDialog)
├── flows           LoginFlow (ensureLoggedIn/out, logout), Navigator
└── data            Model (IndexCard, StockRow, SelectedStock), TestDataReader (JSON)
src/test/java/vn/bsc/automation
├── base            BaseTest (vòng đời driver), LoggedInBaseTest
├── listeners       TestListener (screenshot/page source/video khi fail), RetryAnalyzer, RetryTransformer
└── tests           login / navigation / priceboard / order / search
src/test/resources/testdata   login.json, order.json, search.json
```

## 5. Danh sách test case

| ID | Mô tả | Nhóm |
|---|---|---|
| TC_LOGIN_01 | Màn đăng nhập đủ thành phần, mật khẩu được che | smoke |
| TC_LOGIN_02..04 | Bỏ trống / tài khoản không tồn tại → thông báo lỗi, không vào app (data-driven) | regression |
|  | ↳ 02: "Không để trống trường tài khoản và mật khẩu" · 03: "Không để trống trường mật khẩu" · 04: app trả "Mã sự kiện không tồn tại" + mã sự kiện (**cần BA xác nhận message mong đợi**) | |
| TC_LOGIN_05 | Đăng nhập thành công, header đúng tài khoản | smoke |
| TC_LOGIN_06 | Huỷ đăng xuất giữ phiên | regression |
| TC_LOGIN_07 | Đăng xuất về màn đăng nhập, nhớ tài khoản | regression |
| TC_NAV_01..06 | Bottom nav, header, tab Thị trường / Giao dịch / Thông báo, Menu | smoke/regression |
| TC_PB_01..03 | Thẻ chỉ số, dòng mã CK (định dạng), sắp xếp, tin tức | smoke/regression |
| TC_ORD_01..08 | Form đặt lệnh: mặc định, Mua/Bán, chọn mã + Trần>TC>Sàn, bước KL/giá, validate KL=0, safety guard | smoke/regression |
| TC_INFO_01..03 | Sổ lệnh / Danh mục / Tài sản (chỉ đọc) | regression |
| TC_SEARCH_01..02 | Nhóm tìm kiếm, tìm theo mã | smoke/regression |

## 6. Ghi chú kỹ thuật (Flutter)

* App viết bằng **Flutter** → không có `resource-id`; locator dựa trên **content-desc (Semantics)**.
  Đề xuất team dev bổ sung `Semantics(identifier: ...)` / `ValueKey` cho các trường nhập, nút +/−,
  icon đăng xuất để locator ổn định hơn (hiện phải dùng thứ tự EditText / vị trí).
* App **trộn nhiều dạng Unicode**: NFC, NFD (`"BÁN"` = `A`+U+0301, `"Đóng"`) và tổ hợp một phần
  (`"Quên mật khẩu?"` có `ậ` = `â`+U+0323). `Locators` sinh regex chấp nhận mọi dạng tương đương chuẩn
  (có unit test `LocatorsTest`); mọi text đọc ra được chuẩn hoá NFC trước khi assert.
  Lưu ý: parser UiSelector **không un-escape `\`** → regex được dựng không dùng backslash.
* App có 3 trạng thái: **đã đăng nhập**, **khách** (header "BIDV Securities", ví dụ khi phiên hết hạn) và
  **màn đăng nhập** (2 biến thể: *nhớ tài khoản* chỉ có ô mật khẩu, và *form đầy đủ*). `LoginFlow` tự xử lý cả ba.
* Màn hình có animation/giá realtime liên tục → `waitForIdleTimeout=0`; đọc nhiều giá trị dùng
  `UiNode.snapshot(pageSource)` để tránh `StaleElementReference`.
* `enforceXPath1=true` vì engine XPath 2.0 mặc định lỗi với biểu thức `(//a/following::b)[n]`.
* Test dữ liệu thị trường chỉ kiểm tra **định dạng / quan hệ** (Trần > TC > Sàn), không so giá trị cụ thể.
* Test sai mật khẩu dùng **tài khoản không tồn tại** để tránh khoá tài khoản thật.

## 7. Chạy song song nhiều thiết bị

Mỗi `<test>` trong testng.xml khai báo `udid` và `systemPort` riêng, đặt `parallel="tests"` cho `<suite>`:

```xml
<suite name="Parallel" parallel="tests" thread-count="2">
  <test name="Device A"><parameter name="udid" value="AAA"/><parameter name="systemPort" value="8200"/>...</test>
  <test name="Device B"><parameter name="udid" value="BBB"/><parameter name="systemPort" value="8201"/>...</test>
</suite>
```

## 8. Kịch bản Excel "Kịch bản kiểm thử sau khi update App.xlsx"

| Excel | Test | Dữ liệu |
|---|---|---|
| No.1 Đăng nhập | `ExcelLoginTest` – Menu → Đăng nhập → TK khác → **người kiểm thử gõ user/pass trên điện thoại** | – |
| No.2–5 Đặt lệnh LO/ATO/MTL/ATC (+ HNX, UPCOM, lô lẻ, phái sinh, Mua/Bán) | `ExcelOrderTest.placeOrder` (data-driven) | `testdata/orders.csv` |
| No.6–8 Sửa / Huỷ / Huỷ tất cả | `ExcelOrderTest.orderAction` (data-driven) | `testdata/order_actions.csv` |
| Kiểm tra dữ liệu trước khi chạy thật | `OrderDryRunTest` – điền form từng dòng, **không gửi lệnh** | `testdata/orders.csv` |

**Thêm loại lệnh / sàn / mã = thêm 1 dòng CSV, không sửa code.** Mở CSV bằng Excel, lưu "CSV UTF-8".

Cột chính của `orders.csv`: `subAccount` (Thường_BSC, Phaisinh…) · `side` BUY/SELL · `exchange` HOSE/HNX/UPCOM/DERIVATIVE ·
`orderType` LO / ODDLOT (lô lẻ) / ATO / ATC / MTL / MOK / MAK / PLO / MARKET (UPCOM) · `price` REF/CEIL/FLOOR/số ·
`quantity` · `session` (phiên hợp lệ) · `outOfSession` REJECT/ACCEPT/ANY.

Loại lệnh thị trường theo sàn (khảo sát trên app): **HOSE** ATO, ATC, MTL · **HNX** ATC, MTL, MOK, MAK, PLO ·
**UPCOM** không có dropdown – chọn Market giá = trần · **Phái sinh** đặt bằng tiểu khoản *Phaisinh*.

**Quy tắc kết quả:** trong phiên → "đặt lệnh thành công"; **đặt sai phiên → hệ thống báo lỗi → PASS** (ghi lại thông báo).
Khung giờ phiên cấu hình tại `src/main/resources/config/sessions.properties` (kèm ngày nghỉ lễ).

```bash
mvn test -Dsuite=testng-excel.xml                            # No.1 (+ No.2-8 bị SKIP vì chưa mở khoá)
mvn test -Dsuite=testng-excel.xml -Dorder.submit.enabled=true  # chạy lệnh THẬT: người kiểm thử gõ PIN khi test dừng chờ
```

An toàn khi đặt lệnh thật:
* Chỉ chạy khi có `-Dorder.submit.enabled=true`; PIN do người kiểm thử gõ, không lưu.
* Test validate (KL = 0) **luôn** từ chối bấm MUA/BÁN khi KL > 0, bất kể cờ trên.
* **Huỷ tất cả** chỉ thực hiện khi mọi lệnh chờ đều do test tạo; có lệnh khác → dừng an toàn, không huỷ.
* Cuối class, mọi lệnh do test tạo còn chờ sẽ được tự huỷ (safety net).
* Màn xác nhận/PIN và dòng lệnh trong Sổ lệnh chưa khảo sát được (hệ thống đang batch): mỗi bước tự đính kèm
  screenshot + page source vào báo cáo để hoàn thiện locator sau lần chạy thật đầu tiên.

## 9. Tối ưu thời gian chạy

* **Một Appium session cho mỗi class**, giữ trạng thái đăng nhập (`noReset`), không cài lại app.
* Explicit wait + polling 300 ms, không `sleep`; đọc nhiều giá trị bằng 1 snapshot page source.
* **Chạy song song nhiều thiết bị** – `testng-parallel.xml` + `DevicePool`:
  ```bash
  mvn test -Dsuite=testng-parallel.xml -Ddevices=dabfffa4:8200,emulator-5554:8201
  ```
  Mỗi class mượn 1 thiết bị rảnh → N thiết bị ≈ N lần nhanh hơn. Một thiết bị chỉ chạy 1 app nên
  không thể song song trên cùng máy. `LoginTest` (đăng xuất) và `ExcelOrderTest` (1 sổ lệnh) chạy tuần tự riêng.
  Nếu dùng chung 1 tài khoản trên nhiều máy, cần xác nhận hệ thống cho đăng nhập đồng thời.
* Tắt quay video khi không cần: `-Dvideo.on.failure=false`.

## 10. Phát hiện trên app khi chạy thực tế (cần BA/Dev xem xét)

| # | Hiện tượng | Ghi chú |
|---|---|---|
| 1 | Đăng nhập tài khoản không tồn tại → "Mã sự kiện không tồn tại" + mã sự kiện | Thông báo khó hiểu với người dùng |
| 2 | Trong giờ batch, chọn mã/tiểu khoản → "Lỗi :ACCOUNTID_NOT_EXIT" | Có lỗi chính tả EXIT/EXIST; nên có thông báo "hệ thống đang xử lý cuối ngày" |
| 3 | Chuyển Limit → Market đôi khi ô "Giá" (loại lệnh) để **trống**, không có giá trị mặc định | Framework tự mở dropdown để chọn |
| 4 | Chuyển Market → Limit thì giá về 0 | Người dùng phải nhập lại giá |
| 5 | App trộn Unicode NFC/NFD/tổ hợp một phần trong nhãn | Ảnh hưởng accessibility / screen reader |
| 6 | Phiên đăng nhập hết hạn → app về chế độ khách không báo trước | |

Kiểm chứng trên thiết bị (Mi MIX 2S, 05/11/2026): dry-run 19/19 dòng orders.csv (HOSE/HNX/UPCOM, ATO/ATC/MTL/MOK/MAK/PLO,
Market UPCOM, lô lẻ 3 sàn, Bán, phái sinh) PASS; OrderFormTest 10/10; unit test 5/5.
**Chưa kiểm chứng** (cần chạy lệnh thật trong giờ giao dịch, có người gõ PIN): màn xác nhận/PIN, thông báo thành công,
dòng lệnh trong Sổ lệnh, Sửa/Huỷ/Huỷ tất cả.

