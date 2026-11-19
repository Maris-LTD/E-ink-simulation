# EPIC: Monetization & Inkify Pro (Google Play Billing)

> **Dự án:** Inkify (`com.eink.reader.app`)  
> **Trạng thái:** Proposed / Ready for Task Breakdown  
> **Phạm vi:** Chiến lược giá & chuyển đổi, Google Play Billing, Entitlement Cache (Offline-first), E-Ink Paywall UI, Soft-Gate tính năng Pro, Privacy Policy & QA.

---

## 1. Mục tiêu & Nguyên tắc bất di bất dịch
1. **Tối đa hóa tỷ lệ chuyển đổi (Conversion Rate) & Doanh thu trên mỗi người dùng (ARPU)** bằng chiến lược tâm lý giá và kích hoạt Paywall đúng **"Aha! Moment"**.
2. **Không quảng cáo (100% Ads-free) & Không Tracking SDK:** Giữ đúng cam kết tại [`docs/privacy-policy.md`](privacy-policy.md) và bảo vệ trải nghiệm đọc không nhấp nháy trên màn hình E-Ink.
3. **Serverless & Offline-first:** Không vận hành backend riêng. Toàn bộ giao dịch đi qua **Google Play Billing Library** (`com.android.billingclient:billing-ktx`), trạng thái bản quyền (`ProEntitlement`) được cache cục bộ bằng `AtomicJsonFile` để người dùng đọc offline hoàn toàn mượt mà.
4. **Không khóa tính năng đọc cơ bản:** Người dùng miễn phí vẫn đọc EPUB, PDF chuẩn, tạo bookmark, highlight, tra từ điển bình thường để xây dựng thói quen gắn bó trước khi nâng cấp.

---

## 2. Chiến lược Pricing & Tâm lý chuyển đổi (Conversion Optimization)

### 2.1. Cấu trúc 3 gói neo giá (Price Anchoring + Decoy Effect)
Cộng đồng người dùng thiết bị E-Ink (Boox, Bigme, Hisense, Kindle Android...) có đặc thù: **sẵn sàng chi tiền cho phần mềm chất lượng, nhưng rất ghét bị ép thuê bao mãi mãi**. Kết hợp cả Subscription và Lifetime sẽ tối ưu cả 2 nhóm khách hàng:

| Gói | Product ID (Play Console) | Loại | Vai trò tâm lý & Đề xuất giá tham khảo |
| :--- | :--- | :--- | :--- |
| **Inkify Pro — Tháng** | `inkify_pro_monthly` | Subscription | **Gói neo giá (Anchor):** ~$0.79 / tháng (~19.000đ). Giúp gói Năm & Trọn đời trông cực kỳ hời. |
| **Inkify Pro — Năm** *(Best Value)* | `inkify_pro_annual` | Subscription + **7-day Free Trial** | **Gói chủ lực:** ~$3.99 / năm (~79.000đ, tiết kiệm ~65%). Có **Dùng thử 7 ngày miễn phí** để xóa rào cản bấm mua lần đầu. |
| **Inkify Pro — Trọn đời** | `inkify_pro_lifetime` | One-time IAP (Non-consumable) | **Gói chốt đơn giá trị cao:** ~$8.99 (~199.000đ, chỉ bằng ~2.3 năm). Cực hút khách hàng E-Ink thích "mua đứt dùng mãi". |
| **Ủng hộ tác giả (Tip Jar)** *(Tùy chọn)* | `inkify_tip_coffee`, `inkify_tip_book` | Consumable IAP | Hiện sau khi người dùng đã lên Pro hoặc đạt cột mốc đọc cao (`ReadingGamification`), tạo thêm doanh thu tự nguyện. |

### 2.2. Các điểm kích hoạt Paywall đúng "Aha! Moment" (High-Intent Triggers)
Thay vì hiện popup ngẫu nhiên gây khó chịu, Paywall chỉ xuất hiện khi người dùng **vừa nhìn thấy giá trị vượt trội** hoặc **đang có nhu cầu cấp thiết**:

1. **Try-Before-You-Buy cho Adaptive PDF Engine (Trigger mạnh nhất):**
   - Cho phép dùng miễn phí **Adaptive PDF Reflow + Graphic Masking + Guided Region Nav** ở **5 trang đầu tiên** của mỗi tài liệu PDF.
   - Khi lật sang trang thứ 6, hiển thị thẻ nâng cấp ngay trong luồng đọc với thông điệp trực quan: *"Bạn vừa đọc mượt mà hơn trên E-Ink với PDF Reflow. Mở khóa toàn bộ tài liệu & cắt lề thông minh."*
2. **Bảo vệ dữ liệu khi bật Google Drive Sync:**
   - Khi người dùng đã có sách/highlight và bấm bật **Đồng bộ Google Drive** (`GoogleDriveSyncManager`) để sao lưu hoặc đọc trên máy thứ 2 → đây là lúc mức độ sẵn sàng chi trả cao nhất.
3. **Cá nhân hóa thiết bị E-Ink (Lock-Screen & Launcher):**
   - Cho phép xem trước giao diện **Lock-Screen Live Wallpaper** (`EInkWallpaperService`) và **eInk Home Launcher** (`LauncherActivity`) ngay trong `TutorialOverlay` hoặc Cài đặt; khi bấm **"Áp dụng chính thức"** mới mở Paywall.
4. **Reading Milestone Nudge (Không ép buộc):**
   - Khi người dùng đạt cột mốc đọc tích cực (ví dụ streak 7 ngày hoặc hoàn thành cuốn sách đầu tiên trong `ReadingGamification`), hiển thị thẻ chúc mừng tinh tế kèm nút tìm hiểu *Inkify Pro* (tối đa 1 lần/cột mốc, có nút tắt hẳn).

### 2.3. Tối ưu UI Paywall cho màn hình E-Ink (`EInkTheme`)
- **Thiết kế trắng-đen tương phản cao:** Không gradient, không animation nhấp nháy gây ghosting.
- **3 Trust Badges rõ ràng:**
  1. *100% Không quảng cáo & Không theo dõi*
  2. *Hoạt động Offline hoàn toàn*
  3. *Khôi phục giao dịch 1 chạm trên mọi máy Android*
- **Minh bạch Free Trial:** Ghi rõ ngày kết thúc dùng thử 7 ngày và cách hủy trên Google Play để tăng niềm tin, giảm tỷ lệ bỏ ngang ở màn hình xác nhận thanh toán.

---

## 3. Phân tách tính năng Free vs. Inkify Pro

| Nhóm tính năng | Free | Inkify Pro | Module / File liên quan |
| :--- | :--- | :--- | :--- |
| **Đọc sách cốt lõi** | EPUB, PDF chuẩn, Bookmark, Highlight, Mục lục, Tìm kiếm, Tra từ điển | Không giới hạn | `ReadiumEngine`, `PdfPagerReader`, `AnnotationRepository`, `DictionaryService` |
| **Adaptive PDF Engine** | Cắt lề thủ công + dùng thử Reflow **5 trang đầu/file** | Full **PDF Reflow**, **Graphic/Table Masking**, **Guided Region Nav**, Auto Margin Crop | `PdfReflowEngine`, `PdfGraphicMasker`, `PdfRegionNavigator`, `PdfMarginCropper` |
| **Đồng bộ đám mây** | Lưu trữ cục bộ + Android Backup cơ bản | Full **Google Drive Sync** (tiến độ, bookmark, highlight, file sách) | `GoogleDriveSyncManager` |
| **Hệ thống E-Ink & Điện thoại tối giản** | **Miễn phí toàn bộ:** **Lock-Screen Live Wallpaper**, **Lock-Screen Nav**, **eInk Home Launcher**, Widget | Giữ nguyên miễn phí (trải nghiệm cốt lõi của điện thoại tối giản) | `EInkWallpaperService`, `LockScreenReadingNotifier`, `LauncherActivity` |
| **Focus & Thống kê** | Thống kê tổng quan cơ bản | **Focus Mode (DND)**, chi tiết Streak & Gamification | `FocusModeController`, `ReadingStatsRepository`, `ReadingGamification` |

---

## 4. Kiến trúc kỹ thuật (Tích hợp vào `AppContainer`)

- **Package mới:**
  - `com.example.emptyapp.data.billing`
  - `com.example.emptyapp.ui.monet`
- **Các thành phần chính:**
  1. `ProFeature` (enum): `ADAPTIVE_PDF_REFLOW`, `DRIVE_SYNC`, `LOCKSCREEN_WALLPAPER`, `EINK_LAUNCHER`, `FOCUS_MODE`.
  2. `ProEntitlement` (Serializable data class): `isPro`, `productId`, `purchaseType` (`SUBSCRIPTION` / `LIFETIME`), `purchaseTimeEpochMs`, `lastVerifiedAtEpochMs`, `isPending`.
  3. `EntitlementStore`: Lưu/đọc `ProEntitlement` qua `AtomicJsonFile` (tương tự `BookIndexStore` / `EInkSettingsStore`). Hỗ trợ đọc offline tức thì khi khởi động app.
  4. `PlayBillingManager`:
     - Bọc `BillingClient` (tự động reconnect với exponential backoff).
     - `queryProductDetailsAsync` cho cả `INAPP` và `SUBS` để lấy giá bản địa hóa (`formattedPrice`).
     - `queryPurchasesAsync` khi `onResume` / mở app để đồng bộ trạng thái gia hạn, hết hạn hoặc hoàn tiền.
     - Bắt buộc gọi `acknowledgePurchase` cho giao dịch mới (nếu không Google Play sẽ tự động hoàn tiền sau 3 ngày).
     - Hỗ trợ **Restore Purchases** thủ công trên Paywall & Settings.
  5. `ProFeatureGate`: Expose `StateFlow<ProEntitlement>` và `hasAccess(feature: ProFeature, contextParam)` (ví dụ kiểm tra điều kiện trang <= 5 cho PDF Preview). Inject qua `AppContainer`.

---

## 5. Danh sách Stories triển khai (theo thứ tự phụ thuộc)

### Story 1: Billing Domain & Offline-First `EntitlementStore`
- **Mô tả:** Xây dựng model `ProFeature`, `ProEntitlement`, `EntitlementStore` (dùng `AtomicJsonFile`), và `ProFeatureGate` (bao gồm logic soft-preview 5 trang đầu cho PDF Reflow).
- **Tiêu chí nghiệm thu (AC):**
  - Đọc/ghi `ProEntitlement` an toàn, không crash khi file bị hỏng hoặc chưa tồn tại.
  - `ProFeatureGate` trả về quyền truy cập chính xác cho từng `ProFeature` và điều kiện preview trang PDF.
  - Có Unit Test đầy đủ trong `app/src/test/`.

### Story 2: Tích hợp `PlayBillingManager` vào `AppContainer`
- **Mô tả:** Thêm `com.android.billingclient:billing-ktx` vào `app/build.gradle.kts`, triển khai `PlayBillingManager` và kết nối vào `AppContainer`.
- **Tiêu chí nghiệm thu (AC):**
  - Query được `ProductDetails` cho 3 gói (`monthly`, `annual` kèm offer/trial, `lifetime`) + Tip Jar (nếu bật).
  - Xử lý đầy đủ các trạng thái: `PURCHASED` (gọi `acknowledgePurchase`), `PENDING` (hiển thị trạng thái chờ thanh toán), `USER_CANCELED`, lỗi mạng.
  - Tự động `queryPurchasesAsync` khi mở app và hỗ trợ nút **Khôi phục giao dịch**.

### Story 3: E-Ink Paywall UI & Điểm vào từ Library / Settings
- **Mô tả:** Xây dựng `ProPaywallSheet` / `ProPaywallScreen` theo `EInkTheme` (song ngữ Việt/Anh), tích hợp vào `LibraryScreen` (Tab Cài đặt) và `ReadingStatsSheet`.
- **Tiêu chí nghiệm thu (AC):**
  - Hiển thị động giá tiền theo quốc gia từ Play Store, làm nổi bật gói Năm (*Best Value + 7 ngày dùng thử*) và gói Trọn đời.
  - Có 3 Trust Badges, nút *Khôi phục giao dịch*, link tới điều khoản/chính sách bảo mật.
  - Giao diện tương phản cao, không animation nhấp nháy trên E-Ink.

### Story 4: Soft-Gate các tính năng Pro & Contextual Triggers
- **Mô tả:** Gắn `ProFeatureGate` vào:
  - `PdfPagerReader` / `PdfAnalysisSession` (cho phép thử 5 trang đầu Reflow/Masking/Region Nav, trang 6 mở Paywall).
  - `GoogleDriveSyncManager` / UI Sync trong Settings.
  - `EInkWallpaperService`, `LockScreenReadingNotifier`, `LauncherActivity` (`EInkSystemSetupHelper`).
  - `FocusModeController` & `ReadingGamification` milestone nudge.
- **Tiêu chí nghiệm thu (AC):**
  - Không làm mất dữ liệu hay crash nếu người dùng chưa mua Pro.
  - Bấm vào tính năng bị khóa sẽ mở Paywall đúng ngữ cảnh (contextual header giải thích đúng tính năng vừa bấm).

### Story 5: Privacy Policy, ProGuard & Play Console QA
- **Mô tả:** Cập nhật `docs/privacy-policy.md`, kiểm tra rule ProGuard cho `release` build (`isMinifyEnabled = true`), và lập checklist kiểm thử với Google Play License Testing.
- **Tiêu chí nghiệm thu (AC):**
  - `docs/privacy-policy.md` (cả Tiếng Việt & Tiếng Anh) bổ sung mục xử lý thanh toán qua Google Play Billing.
  - Build `assembleRelease` / `bundleRelease` thành công không lỗi R8/ProGuard.
  - `.\gradlew.bat testDebugUnitTest` pass toàn bộ.
  - Checklist QA verify đủ: Mua mới, Free Trial, Pending, Hủy giao dịch, Xóa app cài lại → Restore Purchases, Dùng offline sau khi đã mua.

---

## 6. Rủi ro & Lưu ý triển khai
- **Cần tài khoản Google Play Console:** Các `productId` phải được tạo và kích hoạt (Active) trên Play Console (Internal Testing track) mới test được luồng mua thật bằng tài khoản License Tester.
- **Gia hạn & Hủy gói ở chế độ Offline:** Vì không có backend, app tin tưởng cache cục bộ khi mất mạng; khi có mạng trở lại, `queryPurchasesAsync` sẽ tự động cập nhật lại nếu subscription đã hết hạn hoặc bị hoàn tiền.
- **Kiểm tra tuân thủ chính sách Google Play:** Mọi gói Subscription bắt buộc hiển thị rõ chu kỳ thanh toán, giá sau dùng thử và hướng dẫn hủy ngay trên màn hình Paywall.
