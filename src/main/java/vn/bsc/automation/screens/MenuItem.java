package vn.bsc.automation.screens;

/**
 * Every entry of the right-hand (hamburger) menu, surveyed on UAT build "UAT.1-master-UAT5" (06/11/2026).
 * <p>
 * {@code label} = content-desc of the menu row, {@code title} = content-desc of the screen title that proves the
 * target opened (for {@link Kind#SHEET} the bottom-sheet title, for {@link Kind#TAB} the Trading tab name).
 * Top-level entries without children have {@code group == null}.
 */
public enum MenuItem {

    // ---- Giao dịch tiền (all except "Cho vay" open a bottom sheet -> ActionSheet / SheetOption)
    CHUYEN_TIEN(Group.GIAO_DICH_TIEN, "Chuyển tiền", "Chọn loại chuyển tiền", Kind.SHEET),
    NOP_RUT_TIEN_PHAI_SINH(Group.GIAO_DICH_TIEN, "Nộp/Rút tiền tài khoản phái sinh", "Chọn loại giao dịch", Kind.SHEET),
    NOP_RUT_TIEN_CCP(Group.GIAO_DICH_TIEN, "Nộp/Rút tiền CCP", "Chọn loại giao dịch", Kind.SHEET),
    PHONG_GIAI_TOA_TIEN_BIDV(Group.GIAO_DICH_TIEN, "Phong toả/Giải toả tiền @BIDV", "Chọn loại giao dịch", Kind.SHEET),
    CHO_VAY(Group.GIAO_DICH_TIEN, "Cho vay", "Cho vay", Kind.SCREEN),

    // ---- Giao dịch chứng khoán
    CHUYEN_CHUNG_KHOAN(Group.GIAO_DICH_CHUNG_KHOAN, "Chuyển chứng khoán", "Chuyển chứng khoán", Kind.SCREEN),
    DANG_KY_QUYEN_MUA(Group.GIAO_DICH_CHUNG_KHOAN, "Đăng ký quyền mua", "Đăng ký quyền mua", Kind.SCREEN),
    DANG_KY_MUA_CW_IPO(Group.GIAO_DICH_CHUNG_KHOAN, "Đăng ký mua chứng quyền IPO", "Đăng ký mua chứng quyền IPO", Kind.SCREEN),

    // ---- Tiện ích
    DANG_KY_VOUCHER(Group.TIEN_ICH, "Đăng ký voucher", "Voucher", Kind.SCREEN),
    XAC_NHAN_LENH_ONLINE(Group.TIEN_ICH, "Xác nhận lệnh Online", "Xác nhận lệnh Online", Kind.SCREEN),
    TRA_CUU_DANH_MUC_KY_QUY(Group.TIEN_ICH, "Tra cứu danh mục ký quỹ", "Tra cứu danh mục ký quỹ", Kind.SCREEN),
    DO_CHUNG_HUNG_QUA(Group.TIEN_ICH, "Đố chứng hứng quà", "Đố chứng hứng quà", Kind.SCREEN),
    CANH_BAO(Group.TIEN_ICH, "Cảnh báo", "Cảnh báo", Kind.SCREEN),

    // ---- Quản lý tài khoản ("Tài sản" switches to tab 4 of the Trading screen, it is not a separate screen)
    TAI_SAN(Group.QUAN_LY_TAI_KHOAN, "Tài sản", "Tài sản", Kind.TAB),
    DU_NO_KY_QUY(Group.QUAN_LY_TAI_KHOAN, "Dư nợ ký quỹ", "Nợ ký quỹ", Kind.SCREEN),
    SAO_KE_TIEN(Group.QUAN_LY_TAI_KHOAN, "Sao kê tiền", "Sao kê tiền", Kind.SCREEN),
    SAO_KE_CHUNG_KHOAN(Group.QUAN_LY_TAI_KHOAN, "Sao kê chứng khoán", "Sao kê chứng khoán", Kind.SCREEN),
    LICH_SU_DAT_LENH(Group.QUAN_LY_TAI_KHOAN, "Lịch sử đặt lệnh", "Lịch sử đặt lệnh", Kind.SCREEN),
    LICH_SU_LENH_DIEU_KIEN(Group.QUAN_LY_TAI_KHOAN, "Lịch sử lệnh điều kiện", "Lịch sử lệnh điều kiện", Kind.SCREEN),
    LICH_SU_KHOP_LENH(Group.QUAN_LY_TAI_KHOAN, "Lịch sử khớp lệnh", "Lịch sử khớp lệnh", Kind.SCREEN),
    LAI_LO_THUC_HIEN(Group.QUAN_LY_TAI_KHOAN, "Lãi/lỗ thực hiện", "Lãi/lỗ thực hiện", Kind.SCREEN),

    // ---- Bảo mật
    DOI_MAT_KHAU(Group.BAO_MAT, "Đổi mật khẩu", "Đổi mật khẩu", Kind.SCREEN),
    CAI_DAT_SINH_TRAC(Group.BAO_MAT, "Cài đặt vân tay/khuôn mặt", "Cài đặt vân tay/khuôn mặt", Kind.SCREEN),
    CAI_DAT_XAC_THUC_2_YEU_TO(Group.BAO_MAT, "Cài đặt xác thực 2 yếu tố", "Cài đặt xác thực 2 yếu tố", Kind.SCREEN),
    THIET_BI_DA_XAC_MINH(Group.BAO_MAT, "Thiết bị đã xác minh", "Thiết bị đã xác minh", Kind.SCREEN),

    // ---- Cài đặt ("Đổi ngôn ngữ" row is an ImageView, the others are Views - locator ignores the class)
    THONG_TIN_CA_NHAN(Group.CAI_DAT, "Thông tin cá nhân", "Thông tin cá nhân", Kind.SCREEN),
    DANG_KY_DICH_VU(Group.CAI_DAT, "Đăng ký dịch vụ", "Đăng ký dịch vụ", Kind.SCREEN),
    GIAO_DIEN(Group.CAI_DAT, "Giao diện", "Thiết lập giao diện", Kind.SCREEN),
    DOI_NGON_NGU(Group.CAI_DAT, "Đổi ngôn ngữ", "Đổi ngôn ngữ", Kind.SCREEN),
    DOI_CO_CHU(Group.CAI_DAT, "Đổi cỡ chữ", "Đổi cỡ chữ", Kind.SCREEN),
    NGAN_HANG_THU_HUONG(Group.CAI_DAT, "Danh sách ngân hàng thụ hưởng", "Danh sách ngân hàng thụ hưởng", Kind.SCREEN),

    // ---- Hỗ trợ (all leave the app except "Mạng lưới", which first asks for the LOCATION permission)
    TONG_DAI(Group.HO_TRO, "Tổng đài", null, Kind.EXTERNAL),           // places a phone call - never tap in tests
    CHAT_TU_VAN_VIEN(Group.HO_TRO, "Chat với tư vấn viên", null, Kind.EXTERNAL),   // Chrome
    HUONG_DAN_SU_DUNG(Group.HO_TRO, "Hướng dẫn sử dụng", null, Kind.EXTERNAL),     // Chrome
    SO_TAY_GIAO_DICH(Group.HO_TRO, "Sổ tay giao dịch", null, Kind.EXTERNAL),       // Chrome
    MANG_LUOI(Group.HO_TRO, "Mạng lưới", null, Kind.EXTERNAL),                     // Android location permission dialog

    // ---- Top-level rows
    Y_KIEN_DONG_GOP(null, "Ý kiến đóng góp", null, Kind.EXTERNAL),   // Microsoft Forms in Chrome
    THONG_TIN_PHAN_MEM(null, "Thông tin phần mềm", "Version ", Kind.INLINE), // expands "Version <build>" row
    BSC_MTRADER(null, "BSC Mtrader", null, Kind.EXTERNAL);           // Chrome Custom Tab

    /** Expandable groups of the menu (tap = expand / collapse). */
    public enum Group {
        GIAO_DICH_TIEN("Giao dịch tiền"),
        GIAO_DICH_CHUNG_KHOAN("Giao dịch chứng khoán"),
        TIEN_ICH("Tiện ích"),
        QUAN_LY_TAI_KHOAN("Quản lý tài khoản"),
        BAO_MAT("Bảo mật"),
        CAI_DAT("Cài đặt"),
        HO_TRO("Hỗ trợ");

        public final String label;

        Group(String label) {
            this.label = label;
        }
    }

    public enum Kind {
        /** Opens an in-app screen whose title (content-desc) equals {@code title}. */
        SCREEN,
        /** Opens a bottom sheet (title + option buttons + "Đóng"). */
        SHEET,
        /** Switches a tab of the Trading screen (title = tab name). */
        TAB,
        /** Expands a row inside the menu (title = prefix of the new row). */
        INLINE,
        /** Leaves the app (browser, dialer, system permission dialog). */
        EXTERNAL
    }

    public final Group group;
    public final String label;
    public final String title;
    public final Kind kind;

    MenuItem(Group group, String label, String title, Kind kind) {
        this.group = group;
        this.label = label;
        this.title = title;
        this.kind = kind;
    }
}
