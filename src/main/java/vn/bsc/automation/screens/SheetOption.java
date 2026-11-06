package vn.bsc.automation.screens;

/**
 * Options of the bottom sheets opened from the "Giao dịch tiền" menu group.
 * {@code label} = option Button content-desc, {@code formTitle} = title of the form it opens
 * ({@link TransactionFormScreen}). Every form ends with a "Xác nhận" button (then OTP/PIN - not surveyed).
 */
public enum SheetOption {

    CHUYEN_TIEN_NOI_BO(MenuItem.CHUYEN_TIEN, "Chuyển tiền nội bộ", "Chuyển tiền nội bộ"),
    CHUYEN_TIEN_RA_NGOAI(MenuItem.CHUYEN_TIEN, "Chuyển tiền ra ngoài", "Chuyển tiền ra ngoài"),
    NOP_TIEN_PHAI_SINH(MenuItem.NOP_RUT_TIEN_PHAI_SINH, "Nộp tiền vào TK phái sinh", "Nộp tiền vào TK phái sinh"),
    RUT_TIEN_PHAI_SINH(MenuItem.NOP_RUT_TIEN_PHAI_SINH, "Rút tiền từ TK phái sinh", "Rút tiền từ TK phái sinh"),
    NOP_KY_QUY_CCP(MenuItem.NOP_RUT_TIEN_CCP, "Nộp tiền ký quỹ CCP", "Nộp tiền ký quỹ CCP"),
    RUT_KY_QUY_CCP(MenuItem.NOP_RUT_TIEN_CCP, "Rút tiền ký quỹ CCP", "Rút tiền ký quỹ CCP"),
    PHONG_TOA_TIEN(MenuItem.PHONG_GIAI_TOA_TIEN_BIDV, "Phong toả tiền", "Phong toả tiền @BIDV"),
    GIAI_TOA_TIEN(MenuItem.PHONG_GIAI_TOA_TIEN_BIDV, "Giải toả tiền", "Giải toả tiền @BIDV");

    public final MenuItem menuItem;
    public final String label;
    public final String formTitle;

    SheetOption(MenuItem menuItem, String label, String formTitle) {
        this.menuItem = menuItem;
        this.label = label;
        this.formTitle = formTitle;
    }
}
