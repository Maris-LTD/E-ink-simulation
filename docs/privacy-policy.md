# Chính sách Quyền riêng tư (Privacy Policy) — Inkify

**Cập nhật lần cuối / Last updated:** 2026-11-19  
**Ứng dụng / Application:** Inkify (`com.eink.reader.app`)  
**Liên hệ / Contact:** letuandat556@gmail.com

---

## 🇻🇳 Tiếng Việt

### 1. Lưu trữ & Quyền sở hữu Dữ liệu
- Toàn bộ tệp sách bạn nhập vào ứng dụng (`EPUB`, `PDF`, `CBZ`), tiến độ đọc, dấu trang (bookmarks), ghi chú/highlight và thống kê đọc sách mặc định được **lưu trữ cục bộ hoàn toàn trên thiết bị của bạn**.
- Nếu thiết bị của bạn bật **Android Auto Backup** (sao lưu đám mây), hệ thống Android chỉ sao lưu dữ liệu cài đặt và thống kê nhẹ (không bao gồm tệp sách, ảnh bìa, trạng thái đăng nhập Drive và bộ nhớ đệm) theo chính sách sao lưu của Android.
- Nếu bạn thực hiện **chuyển dữ liệu trực tiếp sang thiết bị mới (Device-to-Device Transfer)** qua dây cáp hoặc công cụ chuyển máy của Android, hệ thống sẽ chuyển toàn bộ dữ liệu ứng dụng bao gồm tệp sách (`books/`), ảnh bìa, dữ liệu thư viện và trạng thái tài khoản đồng bộ (chỉ loại trừ bộ nhớ đệm `pdf_cache/` và tệp tạm `sync_tmp/`).
- **Inkify** không vận hành máy chủ riêng để thu thập dữ liệu người dùng, không tích hợp SDK theo dõi/phân tích hành vi của bên thứ ba và không chứa quảng cáo.

### 2. Đăng nhập Google & Đồng bộ Google Drive (Tùy chọn)
- Khi bạn chủ động bật tính năng **Đồng bộ Google Drive**, ứng dụng sử dụng Google Identity Services với phạm vi quyền tối thiểu:
  - `userinfo.email`: Hiển thị địa chỉ email và tên tài khoản đang kết nối trong phần Cài đặt.
  - `https://www.googleapis.com/auth/drive.appdata`: Chỉ truy cập thư mục ẩn `appDataFolder` dành riêng cho ứng dụng Inkify trên chính tài khoản Google Drive của bạn (ứng dụng **không** có quyền xem hay truy cập bất kỳ tệp nào khác trên Google Drive của bạn).
  - Tính năng **Đồng bộ cả file sách** mặc định **tắt**; khi bật, đồng bộ nền chỉ tải/đăng tệp sách qua mạng không tính phí (Wi-Fi).
- **Quyền kiểm soát và Xóa dữ liệu đám mây:**
  - Bạn có thể **xóa từng cuốn sách khỏi Google Drive** (ngay khi xóa sách trong Thư viện hoặc trong danh sách *Sách đã lưu trên Drive* tại phần Cài đặt).
  - Bạn có thể **xóa toàn bộ dữ liệu sao lưu trên Google Drive** bất cứ lúc nào bằng nút **"Xóa dữ liệu trên Drive"** trong mục *Hồ sơ & Thống kê → Cài đặt → Đồng bộ Google Drive*. Thao tác này xóa dữ liệu trên Drive và đăng xuất máy hiện tại; các thiết bị khác vẫn đang đăng nhập có thể tải lại dữ liệu cục bộ của chúng nếu chưa đăng xuất.

### 3. Tra từ điển, Dịch thuật & Hiển thị Màn hình khóa
- Khi bạn chọn một từ hoặc đoạn văn bản trong lúc đọc sách và bấm *Tra từ / Dịch*, đoạn văn bản được chọn (được **giới hạn tối đa 500 ký tự**) sẽ được gửi qua kết nối mã hóa HTTPS tới các dịch vụ tra cứu/dịch thuật bên thứ ba (`api.dictionaryapi.dev`, `translate.googleapis.com`, `api.mymemory.translated.net`) để trả về kết quả.
- Khi bạn bấm nút **"Dịch ngoài"**, ứng dụng sẽ mở trang `translate.google.com` trong trình duyệt web trên máy kèm đoạn văn bản được chọn để bạn tự dịch trực tiếp trên trang web của Google.
- Khi bạn bật tính năng **Đọc trên màn hình khóa** hoặc **Live Wallpaper Inkify**, tiêu đề sách và trích đoạn (snippet) đang đọc có thể hiển thị công khai trên màn hình khóa (`VISIBILITY_PUBLIC` trên thông báo điều hướng và trên hình nền động) mà không cần mở khóa thiết bị. Bạn có thể tắt tùy chọn này bất cứ lúc nào trong phần Cài đặt Reader.

### 4. Thanh toán qua Google Play Billing & Mua hàng trong ứng dụng (Inkify Pro)
- Mọi giao dịch nâng cấp **Inkify Pro** (gói tháng, gói năm có dùng thử 7 ngày, gói trọn đời) và ủng hộ tác giả (Tip Jar) đều được xử lý hoàn toàn và bảo mật bởi **Google Play Billing**.
- **Inkify không thu thập, không xử lý và không lưu trữ** bất kỳ thông tin thẻ thanh toán, tài khoản ngân hàng hay dữ liệu tài chính cá nhân nào của bạn.
- Để hỗ trợ đọc sách ngoại tuyến (offline-first), ứng dụng chỉ lưu bộ nhớ đệm cục bộ trên thiết bị (`ProEntitlement`) ghi nhận trạng thái đã kích hoạt bản quyền Pro, mã gói (`productId`) và thời điểm xác minh gần nhất từ Google Play; khi thiết bị có kết nối mạng trở lại, ứng dụng tự động đồng bộ lại trạng thái gia hạn, hết hạn hoặc hoàn tiền thông qua Google Play.

### 5. Các quyền hệ thống ứng dụng sử dụng
- `INTERNET`: Chỉ dùng khi bạn đồng bộ Google Drive, tra từ điển trực tuyến hoặc xác minh/khôi phục giao dịch Google Play Billing (Inkify Pro).
- `POST_NOTIFICATIONS`: Dùng để hiển thị thanh điều hướng đọc sách trên màn hình khóa (khi bạn bật tính năng này).
- `ACCESS_NOTIFICATION_POLICY`: Chỉ dùng để bật chế độ Không làm phiền (Do Not Disturb) khi bạn chủ động bật *Focus Mode*.
- `<queries>` (`ACTION_MAIN` / `CATEGORY_LAUNCHER`): Chỉ dùng cho chế độ *eInk Home Launcher* tùy chọn nhằm liệt kê danh sách các ứng dụng có thể mở trong Ngăn ứng dụng (App Drawer) trên máy.

---

## 🇬🇧 English

### 1. Data Storage & Ownership
- All imported books (`EPUB`, `PDF`, `CBZ`), reading progress, bookmarks, highlights/notes, and reading statistics are **stored locally on your device by default**.
- If **Android Auto Backup** (cloud backup) is enabled on your device, Android only backs up lightweight app settings and reading statistics (excluding book files, covers, Drive sign-in state, and caches).
- If you perform a direct **Device-to-Device Transfer** (wired/Android device transfer to a new phone), Android transfers your full app data including book files (`books/`), covers, library index, and sync sign-in state (excluding only `pdf_cache/` and temporary `sync_tmp/` files).
- **Inkify** does not operate any external tracking servers, third-party analytics SDKs, or advertising networks.

### 2. Google Sign-In & Google Drive Sync (Optional)
- If you choose to sign in with Google to enable multi-device sync, Inkify requests only:
  - `userinfo.email`: To display your connected account email/name in the Settings screen.
  - `https://www.googleapis.com/auth/drive.appdata`: To store and sync your reading data and optional book backups exclusively inside your own private Google Drive `appDataFolder` (Inkify **cannot** access any other files in your Google Drive).
  - **Sync book files** is **off by default**; when enabled, background sync transfers book files only over unmetered (Wi-Fi) networks.
- **Cloud Data Deletion & Control:**
  - You can **remove individual books from Google Drive** (either when deleting a book in the Library or from the *Books backed up on Drive* list in Settings).
  - You can **delete all Google Drive backup data** at any time by tapping **"Delete Drive data"** in *Profile & Stats → Settings → Google Drive Sync*. This removes the Drive backup and signs out this device; other devices that remain signed in may re-upload their local data unless signed out.

### 3. Dictionary, Translation & Lock Screen Display
- When you select text while reading and choose *Define / Translate*, only the selected word or phrase (capped at **a maximum of 500 characters**) is transmitted over HTTPS to third-party dictionary/translation services (`api.dictionaryapi.dev`, `translate.googleapis.com`, `api.mymemory.translated.net`).
- When you tap the **"External Translate"** button, the app opens `translate.google.com` in your device's web browser with the selected text.
- When you enable **Lock Screen Reading** or the **Inkify Live Wallpaper**, the current book title and reading excerpt (snippet) may be displayed publicly on your lock screen (`VISIBILITY_PUBLIC` notification and live wallpaper) without unlocking your device. You can disable this at any time in Reader Settings.

### 4. Google Play Billing & In-App Purchases (Inkify Pro)
- All **Inkify Pro** purchases and subscriptions (monthly, annual with 7-day free trial, lifetime) as well as optional Tip Jar contributions are processed securely by **Google Play Billing**.
- **Inkify never collects, processes, or stores** any payment card numbers, bank account details, or personal financial information.
- To support seamless offline-first reading, the app only caches your purchase entitlement status locally on-device (`ProEntitlement`: active Pro status, `productId`, and last verification timestamp). When network connectivity is available, the app automatically re-syncs renewals, expirations, or refunds directly with Google Play.

### 5. Permissions Used
- `INTERNET`: Used solely for optional Google Drive Sync, online dictionary/translation lookups, and Google Play Billing purchase verification/restoration.
- `POST_NOTIFICATIONS`: Used for optional Lock Screen reading controls.
- `ACCESS_NOTIFICATION_POLICY`: Used only to toggle Do Not Disturb when you enable *Focus Mode*.
- `<queries>` (`ACTION_MAIN` / `CATEGORY_LAUNCHER`): Used solely by the optional *eInk Home Launcher* mode to display launchable apps installed on your device in the App Drawer.

### 6. Contact
If you have any questions regarding privacy or data protection, please contact: **letuandat556@gmail.com**
