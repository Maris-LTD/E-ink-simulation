# QA Monetization & Play Console License Testing Checklist — Inkify Pro

> **Ứng dụng / App:** Inkify (`com.eink.reader.app`)  
> **Epic:** [`docs/epic-monetization.md`](epic-monetization.md)  
> **Chính sách quyền riêng tư / Privacy Policy:** [`docs/privacy-policy.md`](privacy-policy.md)

---

## 1. Play Console Product Setup Table

Tất cả các `productId` dưới đây phải được tạo ở trạng thái **Active** trong **Google Play Console → Monetize → Products** và phát hành bản build trên **Internal Testing** track trước khi kiểm thử bằng tài khoản License Tester.

| Product ID | Loại (Play Console) | Chu kỳ / Offer | Giá tham khảo | Ghi chú cấu hình & Xử lý trong App |
| :--- | :--- | :--- | :--- | :--- |
| `inkify_pro_monthly` | Subscription (`SUBS`) | 1 Month (`P1M`) | ~$2.49 / tháng (~59.000đ) | Base plan tự động gia hạn; app gọi `acknowledgePurchase` ngay khi `PURCHASED`. |
| `inkify_pro_annual` | Subscription (`SUBS`) | 1 Year (`P1Y`) + **Offer: 7-day Free Trial** (`P7D`, `priceAmountMicros = 0`) | ~$11.99 / năm (~249.000đ) | `BillingCatalogHelper.selectBestSubscriptionOffer` ưu tiên chọn offer có phase `0L` (Free Trial). Paywall phải hiển thị rõ giá sau dùng thử & cách hủy. |
| `inkify_pro_lifetime` | In-app product (`INAPP`, Non-consumable) | Mua 1 lần | ~$27.99 (~599.000đ) | `acknowledgePurchase` (không consume). Ưu tiên cao nhất trong `resolveProEntitlement` (`PurchaseType.LIFETIME`). |
| `inkify_tip_coffee` | In-app product (`INAPP`, Consumable) | Mua nhiều lần | Tùy cấu hình (ví dụ ~$1.99) | Gọi `consumeAsync` để cho phép mua lại nhiều lần; không thay đổi trạng thái `ProEntitlement`. |
| `inkify_tip_book` | In-app product (`INAPP`, Consumable) | Mua nhiều lần | Tùy cấu hình (ví dụ ~$4.99) | Gọi `consumeAsync` để cho phép mua lại nhiều lần; không thay đổi trạng thái `ProEntitlement`. |

### Yêu cầu trước khi test (Pre-requisites)
- [ ] Thêm email Google của tester vào **Play Console → Setup → License testing** (đặt License response: `RESPOND_NORMALLY` khi test bình thường).
- [ ] Upload bản build `.aab` (`bundleRelease`) hoặc `.apk` ký bằng upload/release key lên **Internal Testing** và tester đã accept opt-in link.
- [ ] Thiết bị test đăng nhập đúng tài khoản Google có trong danh sách License Testers và có kết nối Google Play Store.
- [ ] Mở `ProPaywallSheet` và xác nhận giá bản địa hóa (`formattedPrice`) load đầy đủ cho cả 3 gói Pro (không hiện placeholder lỗi).

---

## 2. End-to-End License Testing QA Checklist

### 2.1. New Purchase (Mua mới — `inkify_pro_monthly`, `inkify_pro_lifetime`, Tip Jar)
- [ ] Mở `ProPaywallSheet` từ Cài đặt hoặc một tính năng bị khóa (ví dụ: trang 6 PDF Reflow / Google Drive Sync / Lock-Screen Wallpaper / Focus Mode).
- [ ] Chọn gói `inkify_pro_monthly` (hoặc `inkify_pro_lifetime`) → chọn phương thức thanh toán **Test card, always approves**.
- [ ] Xác nhận thanh toán thành công:
  - [ ] App nhận `BillingResponseCode.OK` và gọi `acknowledgePurchase` (kiểm tra trong Play Console Order Management trạng thái không bị *Canceled/Refunded* sau 5 phút).
  - [ ] `ProEntitlement` cập nhật `isPro = true`, đúng `productId` và `purchaseType` (`SUBSCRIPTION` hoặc `LIFETIME`).
  - [ ] Toàn bộ tính năng Pro mở khóa ngay lập tức mà không cần khởi động lại app.
- [ ] *(Nếu bật Tip Jar)* Thử mua `inkify_tip_coffee` / `inkify_tip_book` → hiển thị thông báo cảm ơn (`TipSuccess`) và có thể bấm mua lại lần 2 (xác nhận `consumeAsync` hoạt động).

### 2.2. 7-Day Free Trial (`inkify_pro_annual`)
- [ ] Kiểm tra trên `ProPaywallSheet`: gói `inkify_pro_annual` hiển thị badge **Best Value** / **7 ngày dùng thử miễn phí** kèm giá gia hạn sau khi hết trial.
- [ ] Bấm mua `inkify_pro_annual` với **Test card, always approves**.
- [ ] Google Play sheet hiển thị rõ 0đ trong giai đoạn dùng thử (trên môi trường License Testing, thời gian trial được rút ngắn theo chu kỳ test của Google Play, thường ~3–5 phút).
- [ ] Sau khi xác nhận: `isPro = true`, `productId = "inkify_pro_annual"`, các tính năng Pro hoạt động bình thường.

### 2.3. Pending Payment (Thanh toán chờ xử lý)
- [ ] Chọn phương thức thanh toán **Slow test card, approves after a few minutes** trên Play Billing sheet.
- [ ] Xác nhận giao dịch → app nhận `Purchase.PurchaseState.PENDING`:
  - [ ] App phát `BillingMessage.PurchasePending` và hiển thị thông báo chờ thanh toán rõ ràng cho người dùng.
  - [ ] `ProEntitlement.isPending = true`, **chưa** mở khóa `isPro` (tính năng Pro vẫn bị khóa cho đến khi thanh toán hoàn tất).
- [ ] Chờ vài phút để Google Play duyệt giao dịch test → mở lại app (hoặc trigger `onResume` / `queryPurchasesAsync`):
  - [ ] `acknowledgePurchase` được gọi tự động, `isPending = false`, `isPro = true` được kích hoạt.

### 2.4. User Canceled (Người dùng hủy giữa chừng)
- [ ] Bấm nút mua bất kỳ gói nào trên `ProPaywallSheet` để mở Google Play Billing sheet.
- [ ] Bấm nút Back / chạm ra ngoài để hủy thanh toán (`BillingResponseCode.USER_CANCELED`).
- [ ] Xác nhận:
  - [ ] App xử lý êm mượt, không crash, không hiện lỗi nghiêm trọng (`BillingMessage.PurchaseCanceled`).
  - [ ] Trạng thái `ProEntitlement` giữ nguyên (không bị cấp quyền Pro nhầm).

### 2.5. Uninstall & Reinstall → Restore Purchases (Xóa app cài lại & Khôi phục giao dịch)
- [ ] Với tài khoản đã mua `inkify_pro_lifetime` hoặc subscription còn hạn, **gỡ cài đặt hoàn toàn** ứng dụng Inkify (xóa sạch dữ liệu cục bộ / `EntitlementStore`).
- [ ] Cài đặt lại ứng dụng từ Internal Testing / bản release, mở app:
  - [ ] **Tự động khôi phục:** Khi `PlayBillingManager` kết nối thành công lúc khởi động (`queryPurchasesAsync`), quyền Pro được tự động đồng bộ lại.
  - [ ] **Khôi phục thủ công:** Mở `ProPaywallSheet` (hoặc Cài đặt) → bấm **"Khôi phục giao dịch" / "Restore Purchases"** → hiển thị thông báo khôi phục thành công (`PurchasesRestored`) và mở khóa đầy đủ tính năng Pro.
- [ ] Thử bấm **"Khôi phục giao dịch"** trên tài khoản **chưa từng mua**: hiển thị thông báo không tìm thấy giao dịch (`NoPurchasesToRestore`), không crash.

### 2.6. Offline Reading After Pro Unlock (Đọc & dùng tính năng Pro khi mất mạng)
- [ ] Sau khi đã mở khóa `Inkify Pro` (trạng thái `isPro = true` đã được lưu vào `EntitlementStore` / `AtomicJsonFile`):
- [ ] Bật **Chế độ máy bay (Airplane Mode)** (tắt cả Wi-Fi và 4G/5G).
- [ ] Đóng hoàn toàn app (Force Stop / swipe khỏi Recents) rồi mở lại app ở chế độ Offline:
  - [ ] `ProEntitlement` được đọc tức thì từ cache cục bộ khi khởi động.
  - [ ] Mở file PDF bất kỳ và lật qua **trang thứ 6+** với **Adaptive PDF Reflow / Graphic Masking / Guided Region Nav** → hoạt động mượt mà, không bị chặn bởi Paywall.
  - [ ] Các tính năng offline khác của Pro (`FocusModeController`, `EInkWallpaperService`, `LauncherActivity`) vẫn hoạt động bình thường.

### 2.7. Subscription Expiration / Refund Sync (Hết hạn / Hủy / Hoàn tiền khi có mạng lại)
- [ ] Trên tài khoản test đang dùng `inkify_pro_monthly` hoặc `inkify_pro_annual`:
  - Chờ subscription test hết hạn (theo chu kỳ test của Google Play) **hoặc** thực hiện **Refund / Revoke** đơn hàng test trong **Play Console → Order Management** (hoặc chọn **Slow test card, declines after a few minutes**).
- [ ] Đảm bảo thiết bị có kết nối mạng, mở lại app (hoặc đưa app từ background ra foreground để kích hoạt `queryPurchasesAsync`):
  - [ ] `PlayBillingManager` đối chiếu với Google Play, thấy không còn purchase active.
  - [ ] `EntitlementStore` cập nhật về `ProEntitlement(isPro = false)` (soft-gate được áp dụng lại cho các tính năng Pro).
  - [ ] Dữ liệu cá nhân của người dùng (sách đã nhập, bookmark, highlight, tiến độ đọc) **vẫn được giữ nguyên 100%**, không bị xóa.
