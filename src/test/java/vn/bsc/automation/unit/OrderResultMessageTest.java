package vn.bsc.automation.unit;

import org.testng.annotations.Test;
import vn.bsc.automation.data.OrderCase;
import vn.bsc.automation.flows.OrderFlow;
import vn.bsc.automation.flows.OrderResultReader;

import static org.assertj.core.api.Assertions.assertThat;
import java.text.Normalizer;

/** Unit test (no device): the app's result messages are classified correctly (success vs failure). */
public class OrderResultMessageTest {

    @Test(groups = "unit")
    public void realSuccessMessagesAreSuccess() {
        assertThat(OrderResultReader.isSuccessMessage("Đặt mua AAA KL 100 giá 6.82 thành công!")).isTrue();
        assertThat(OrderResultReader.isSuccessMessage("Sửa Mua AAA KL 200 giá 6.83 thành công!")).isTrue();
        assertThat(OrderResultReader.isSuccessMessage("BSC Smart Invest\nGửi yêu cầu huỷ lệnh thành công\nĐóng")).isTrue();
        assertThat(OrderResultReader.isSuccessMessage("BSC Smart Invest\nĐặt lệnh TP/SL thành công\nĐóng")).isTrue();
    }

    @Test(groups = "unit")
    public void failureWordingContainingThanhCongIsNotSuccess() {
        assertThat(OrderResultReader.isSuccessMessage("Huỷ lệnh không thành công")).isFalse();
        assertThat(OrderResultReader.isSuccessMessage("Hủy lệnh không thành công")).isFalse();
        assertThat(OrderResultReader.isSuccessMessage("Đặt lệnh chưa thành công, vui lòng thử lại")).isFalse();
        assertThat(OrderResultReader.isSuccessMessage("Giao dịch thất bại")).isFalse();
        assertThat(OrderResultReader.isSuccessMessage("E_SYSTEM_ERROR")).isFalse();
        assertThat(OrderResultReader.isSuccessMessage("")).isFalse();
        assertThat(OrderResultReader.isSuccessMessage(null)).isFalse();
    }

    @Test(groups = "unit")
    public void decomposedVietnameseIsHandled() {
        String nfd = Normalizer.normalize("Huỷ lệnh không thành công", Normalizer.Form.NFD);
        assertThat(OrderResultReader.isSuccessMessage(nfd)).isFalse();
        String ok = Normalizer.normalize("Đặt mua AAA thành công", Normalizer.Form.NFD);
        assertThat(OrderResultReader.isSuccessMessage(ok)).isTrue();
    }

    @Test(groups = "unit")
    public void sideSuccessRejectsFailures() {
        assertThat(OrderResultReader.isSideSuccessMessage(OrderCase.Side.BUY, "Đặt mua AAA KL 100 thành công")).isTrue();
        assertThat(OrderResultReader.isSideSuccessMessage(OrderCase.Side.SELL, "Đặt mua AAA KL 100 thành công")).isFalse();
        assertThat(OrderResultReader.isSideSuccessMessage(OrderCase.Side.BUY, "Đặt mua không thành công")).isFalse();
        assertThat(OrderResultReader.isSideSuccessMessage(OrderCase.Side.SELL, "Đặt lệnh TP/SL thành công")).isTrue();
        assertThat(OrderResultReader.isSideSuccessMessage(OrderCase.Side.SELL, "Đặt lệnh TP/SL không thành công")).isFalse();
    }
}
