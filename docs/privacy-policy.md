# Chính sách Quyền riêng tư & Điều khoản (Privacy Policy & Terms) — Inkify

**Cập nhật lần cuối / Last updated:** 2026-11-21  
**Ứng dụng / Application:** Inkify (`com.eink.reader.app`)  
**Liên hệ / Contact:** ebuyami2609@gmail.com

---

## 🇻🇳 Tiếng Việt

### 1. Lưu trữ, Sao lưu, Lưu giữ & Bảo mật Dữ liệu
- Toàn bộ tệp sách bạn nhập vào ứng dụng (`EPUB`, `PDF`, `CBZ`), tiến độ đọc, dấu trang (bookmarks), ghi chú/highlight và thống kê đọc sách mặc định được **lưu trữ cục bộ hoàn toàn trên thiết bị của bạn**.
- Nếu thiết bị bật **Android Auto Backup** (sao lưu đám mây), hệ thống Android chỉ sao lưu dữ liệu cài đặt và thống kê nhẹ (`eink_settings.xml`, `eink_launcher_prefs.xml`, `eink_tutorial_prefs.xml`, `reading_stats.json`; **không** bao gồm tệp sách, ảnh bìa, trạng thái đăng nhập Drive và bộ nhớ đệm).
- Nếu bạn thực hiện **chuyển dữ liệu trực tiếp sang thiết bị mới (Device-to-Device Transfer)** qua dây cáp hoặc công cụ chuyển máy của Android, hệ thống sẽ chuyển dữ liệu ứng dụng bao gồm tệp sách (`books/`), ảnh bìa, dữ liệu thư viện và trạng thái tài khoản đồng bộ (chỉ loại trừ bộ nhớ đệm `pdf_cache/` và tệp tạm `sync_tmp/`).
- **Inkify** không vận hành máy chủ riêng để thu thập dữ liệu người dùng, không tích hợp SDK theo dõi/phân tích hành vi của bên thứ ba và không chứa quảng cáo.
- **Thời gian lưu giữ & Bảo mật truyền tải:** Dữ liệu đọc được lưu cục bộ trên máy hoặc trong thư mục ẩn `appDataFolder` trên Google Drive cá nhân của bạn cho đến khi bạn chủ động xóa hoặc gỡ cài đặt ứng dụng. Mọi kết nối mạng ra ngoài (đồng bộ Drive, tra từ/dịch, xác minh bản quyền Google Play) đều sử dụng mã hóa **HTTPS/TLS**.

### 2. Đăng nhập Google & Đồng bộ Google Drive (Tùy chọn)
- Khi bạn chủ động bật tính năng **Đồng bộ Google Drive**, ứng dụng sử dụng Google Identity Services với phạm vi quyền tối thiểu:
  - `userinfo.email`: Hiển thị địa chỉ email và tên tài khoản đang kết nối trong phần Cài đặt.
  - `https://www.googleapis.com/auth/drive.appdata`: Chỉ truy cập thư mục ẩn `appDataFolder` dành riêng cho ứng dụng Inkify trên chính tài khoản Google Drive của bạn (ứng dụng **không** có quyền xem hay truy cập bất kỳ tệp nào khác trên Google Drive của bạn).
  - Tính năng **Đồng bộ cả file sách** mặc định **tắt**; khi bật, ứng dụng kiểm tra kết nối mạng (qua `ACCESS_NETWORK_STATE`) để ưu tiên đồng bộ qua mạng không tính phí (Wi-Fi) và cảnh báo trước khi tải qua mạng di động.
- **Quyền kiểm soát, Xóa dữ liệu đám mây & Thu hồi quyền truy cập:**
  - Bạn có thể **xóa từng cuốn sách khỏi Google Drive** (ngay khi xóa sách trong Thư viện hoặc trong danh sách *Sách đã lưu trên Drive* tại phần **Cài đặt → Đồng bộ Google Drive**).
  - Bạn có thể **xóa toàn bộ dữ liệu sao lưu trên Google Drive** bất cứ lúc nào bằng nút **"Xóa dữ liệu trên Drive"** trong mục **Cài đặt → Đồng bộ Google Drive**. Thao tác này xóa dữ liệu trên Drive và đăng xuất máy hiện tại; các thiết bị khác vẫn đang đăng nhập có thể tải lại dữ liệu cục bộ của chúng nếu chưa đăng xuất.
  - **Thu hồi quyền truy cập từ bên ngoài ứng dụng (kể cả sau khi đã gỡ app):** Bạn có thể truy cập **https://myaccount.google.com/permissions** bất kỳ lúc nào để thu hồi quyền truy cập của Inkify khỏi tài khoản Google của bạn.

### 3. Tra từ điển, Dịch thuật & Hiển thị Màn hình khóa
- Khi bạn chọn một từ hoặc đoạn văn bản trong lúc đọc sách và bấm *Tra từ / Dịch*, chỉ đoạn văn bản được chọn (được **giới hạn tối đa 500 ký tự**) mới được gửi qua kết nối mã hóa HTTPS tới các dịch vụ tra cứu/dịch thuật bên thứ ba (`api.dictionaryapi.dev`, `translate.googleapis.com`, `api.mymemory.translated.net`) để trả về kết quả.
- Khi bạn bấm nút **"Dịch ngoài"**, ứng dụng sẽ mở trang `translate.google.com` trong trình duyệt web trên máy kèm đoạn văn bản được chọn để bạn tự dịch trực tiếp trên trang web của Google.
- Khi bạn bật tính năng **Đọc trên màn hình khóa** hoặc **Live Wallpaper Inkify**, tiêu đề sách và trích đoạn (snippet) đang đọc có thể hiển thị công khai trên màn hình khóa (`VISIBILITY_PUBLIC` trên thông báo điều hướng và trên hình nền động) mà không cần mở khóa thiết bị. Bạn có thể tắt tùy chọn này bất cứ lúc nào trong phần Cài đặt Reader.

### 4. Thanh toán qua Google Play Billing & Mua hàng trong ứng dụng (Inkify Pro)
- Mọi giao dịch nâng cấp **Inkify Pro** (gói tháng, gói năm có dùng thử 7 ngày nếu đủ điều kiện, gói trọn đời) và ủng hộ tác giả (Tip Jar) đều được xử lý hoàn toàn và bảo mật bởi **Google Play Billing**.
- **Inkify không thu thập, không xử lý và không lưu trữ** bất kỳ thông tin thẻ thanh toán, tài khoản ngân hàng hay dữ liệu tài chính cá nhân nào của bạn.
- Để hỗ trợ đọc sách ngoại tuyến (offline-first), ứng dụng chỉ lưu bộ nhớ đệm cục bộ được mã hóa trên thiết bị (`ProEntitlement`) ghi nhận trạng thái đã kích hoạt bản quyền Pro, mã gói (`productId`), loại gói và thời điểm xác minh gần nhất từ Google Play; khi thiết bị có kết nối mạng trở lại, ứng dụng tự động đồng bộ lại trạng thái gia hạn, hết hạn hoặc hoàn tiền thông qua Google Play.

### 5. Các quyền hệ thống ứng dụng sử dụng
- `INTERNET`: Chỉ dùng khi bạn đồng bộ Google Drive, tra từ điển/dịch trực tuyến hoặc xác minh/khôi phục giao dịch Google Play Billing (Inkify Pro).
- `ACCESS_NETWORK_STATE`: Dùng để kiểm tra trạng thái mạng (Wi-Fi không tính phí hay mạng di động) trước khi đồng bộ tệp sách hoặc kết nối mạng.
- `com.android.vending.BILLING`: Dùng cho hệ thống thanh toán và khôi phục giao dịch Google Play Billing.
- `POST_NOTIFICATIONS`: Dùng để hiển thị thanh điều hướng đọc sách trên màn hình khóa (khi bạn bật tính năng này).
- `ACCESS_NOTIFICATION_POLICY`: Chỉ dùng để bật chế độ Không làm phiền (Do Not Disturb) khi bạn chủ động bật *Focus Mode*.
- `<queries>` (`ACTION_MAIN` / `CATEGORY_LAUNCHER`): Chỉ dùng cho chế độ *eInk Home Launcher* tùy chọn nhằm liệt kê danh sách các ứng dụng có thể mở trong Ngăn ứng dụng (App Drawer) trên máy (**Inkify không khai báo và không sử dụng quyền `QUERY_ALL_PACKAGES`**).

### 6. Quyền riêng tư của Trẻ em & Cập nhật Chính sách
- **Inkify** không hướng tới trẻ em dưới 13 tuổi (hoặc độ tuổi tối thiểu theo quy định pháp luật tại quốc gia của bạn) và không cố ý thu thập dữ liệu cá nhân từ trẻ em. Nếu phát hiện thông tin do trẻ em cung cấp ngoài ý muốn, vui lòng liên hệ qua email hỗ trợ để được xử lý và xóa kịp thời.
- Chính sách này có thể được cập nhật khi ứng dụng bổ sung tính năng hoặc yêu cầu pháp lý mới; mọi thay đổi sẽ được cập nhật trực tiếp tại trang này kèm ngày **"Cập nhật lần cuối"** mới nhất.

### 7. Điều khoản Sử dụng & Gói Đăng ký (Terms of Use)
- **Tự động gia hạn & Hủy đăng ký:** Các gói đăng ký **Inkify Pro** (theo tháng hoặc theo năm) sẽ tự động gia hạn qua tài khoản Google Play của bạn trừ khi bạn chủ động hủy trong mục *Thanh toán và gói thuê bao → Gói thuê bao* trên Google Play Store **ít nhất 24 giờ** trước khi chu kỳ hiện tại (hoặc giai đoạn dùng thử miễn phí 7 ngày, nếu có) kết thúc.
- **Hoàn tiền:** Mọi yêu cầu hoàn tiền đối với gói đăng ký, gói trọn đời hoặc Tip Jar được xem xét và xử lý theo chính sách hoàn tiền chính thức của Google Play.
- **Trách nhiệm nội dung & Bản quyền:** Inkify là công cụ hỗ trợ đọc tài liệu ngoại tuyến. Bạn chịu hoàn toàn trách nhiệm về nguồn gốc và quyền sử dụng hợp pháp đối với các tệp sách (`EPUB`, `PDF`, `CBZ`) mà bạn nhập vào ứng dụng hoặc sao lưu lên Google Drive cá nhân.
- **Giới hạn trách nhiệm:** Ứng dụng được cung cấp theo nguyên trạng (*"as is"*); chúng tôi khuyến nghị bạn sao lưu các tài liệu quan trọng và không chịu trách nhiệm đối với mất mát dữ liệu do lỗi thiết bị, xóa nhầm hoặc các sự cố ngoài tầm kiểm soát.

### 8. Liên hệ
- Mọi thắc mắc về quyền riêng tư, yêu cầu liên quan tới dữ liệu hoặc điều khoản sử dụng, vui lòng liên hệ: **ebuyami2609@gmail.com**

---

## 🇬🇧 English

### 1. Data Storage, Backup, Retention & Security
- All imported books (`EPUB`, `PDF`, `CBZ`), reading progress, bookmarks, highlights/notes, and reading statistics are **stored locally on your device by default**.
- If **Android Auto Backup** (cloud backup) is enabled on your device, Android only backs up lightweight app settings and reading statistics (`eink_settings.xml`, `eink_launcher_prefs.xml`, `eink_tutorial_prefs.xml`, `reading_stats.json`; **excluding** book files, covers, Drive sign-in state, and caches).
- If you perform a direct **Device-to-Device Transfer** (wired/Android device transfer to a new phone), Android transfers your app data including book files (`books/`), covers, library index, and sync sign-in state (excluding only `pdf_cache/` and temporary `sync_tmp/` files).
- **Inkify** does not operate any external tracking servers, third-party analytics SDKs, or advertising networks.
- **Retention & Security in Transit:** Reading data remains stored locally on your device or inside your private Google Drive `appDataFolder` until you delete it or uninstall the app. All network requests (Drive Sync, dictionary/translation lookups, Google Play Billing verification) are encrypted via **HTTPS/TLS**.

### 2. Google Sign-In & Google Drive Sync (Optional)
- If you choose to sign in with Google to enable multi-device sync, Inkify requests only:
  - `userinfo.email`: To display your connected account email/name in the Settings screen.
  - `https://www.googleapis.com/auth/drive.appdata`: To store and sync your reading data and optional book backups exclusively inside your own private Google Drive `appDataFolder` (Inkify **cannot** access any other files in your Google Drive).
  - **Sync book files** is **off by default**; when enabled, Inkify checks network state (via `ACCESS_NETWORK_STATE`) to prefer unmetered (Wi-Fi) networks and prompt before transferring book files over metered mobile data.
- **Cloud Data Deletion & Revocation Control:**
  - You can **remove individual books from Google Drive** (either when deleting a book in the Library or from *Books backed up on Drive* in **Settings → Google Drive Sync**).
  - You can **delete all Google Drive backup data** at any time by tapping **"Delete Drive data"** in **Settings → Google Drive Sync**. This removes the Drive backup and signs out this device; other devices that remain signed in may re-upload their local data unless signed out.
  - **Revoke access outside the app (including after uninstalling):** You can revoke Inkify's access to your Google account at any time via **https://myaccount.google.com/permissions**.

### 3. Dictionary, Translation & Lock Screen Display
- When you select text while reading and choose *Define / Translate*, only the selected word or phrase (capped at **a maximum of 500 characters**) is transmitted over HTTPS to third-party dictionary/translation services (`api.dictionaryapi.dev`, `translate.googleapis.com`, `api.mymemory.translated.net`).
- When you tap the **"External Translate"** button, the app opens `translate.google.com` in your device's web browser with the selected text.
- When you enable **Lock Screen Reading** or the **Inkify Live Wallpaper**, the current book title and reading excerpt (snippet) may be displayed publicly on your lock screen (`VISIBILITY_PUBLIC` notification and live wallpaper) without unlocking your device. You can disable this at any time in Reader Settings.

### 4. Google Play Billing & In-App Purchases (Inkify Pro)
- All **Inkify Pro** purchases and subscriptions (monthly, annual with a 7-day free trial where eligible, lifetime) as well as optional Tip Jar contributions are processed securely by **Google Play Billing**.
- **Inkify never collects, processes, or stores** any payment card numbers, bank account details, or personal financial information.
- To support seamless offline-first reading, the app only caches an encrypted purchase entitlement on-device (`ProEntitlement`: active Pro status, `productId`, purchase type/time, and last verification timestamp). When network connectivity is available, the app automatically re-syncs renewals, expirations, or refunds directly with Google Play.

### 5. Permissions Used
- `INTERNET`: Used solely for optional Google Drive Sync, online dictionary/translation lookups, and Google Play Billing purchase verification/restoration.
- `ACCESS_NETWORK_STATE`: Used to check whether the device is on an unmetered (Wi-Fi) or metered network before syncing book files or performing network actions.
- `com.android.vending.BILLING`: Used for processing and restoring Google Play Billing purchases.
- `POST_NOTIFICATIONS`: Used for optional Lock Screen reading controls.
- `ACCESS_NOTIFICATION_POLICY`: Used only to toggle Do Not Disturb when you enable *Focus Mode*.
- `<queries>` (`ACTION_MAIN` / `CATEGORY_LAUNCHER`): Used solely by the optional *eInk Home Launcher* mode to display launchable apps installed on your device in the App Drawer (**Inkify does not request or use `QUERY_ALL_PACKAGES`**).

### 6. Children's Privacy & Policy Updates
- **Inkify** is not directed to children under 13 years of age (or the applicable minimum age in your jurisdiction) and does not knowingly collect personal information from children. If you believe a child has provided personal data unintentionally, please contact us so we can address and remove it promptly.
- We may update this policy as new features or legal requirements are introduced. Any revisions will be posted directly on this page with an updated **"Last updated"** date.

### 7. Terms of Use & Subscription Terms
- **Auto-Renewal & Cancellation:** **Inkify Pro** subscriptions (monthly or annual) automatically renew through your Google Play account unless canceled in *Google Play Store → Payments & subscriptions → Subscriptions* **at least 24 hours** before the end of the current billing period (or the 7-day free trial period, where applicable).
- **Refunds:** All refund requests for subscriptions, lifetime purchases, or Tip Jar contributions are governed by Google Play's standard refund policy.
- **User Content & Copyright Responsibility:** Inkify is an offline document reader. You are solely responsible for ensuring you have the legal right and authorization to import, read, or back up the book files (`EPUB`, `PDF`, `CBZ`) you add to the app.
- **Disclaimer:** The application is provided on an *"as is"* basis. While care is taken to protect local data, we recommend keeping backups of important files and accept no liability for data loss outside our reasonable control.

### 8. Contact
If you have any questions regarding privacy, data protection, or these terms, please contact: **ebuyami2609@gmail.com**
