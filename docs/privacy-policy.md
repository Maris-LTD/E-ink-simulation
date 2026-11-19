# Chính sách Quyền riêng tư (Privacy Policy) — Inkify

**Cập nhật lần cuối / Last updated:** 2026-11-19  
**Ứng dụng / Application:** Inkify (`com.eink.reader.app`)  
**Liên hệ / Contact:** letuandat556@gmail.com

---

## 🇻🇳 Tiếng Việt

### 1. Lưu trữ & Quyền sở hữu Dữ liệu
- Toàn bộ tệp sách bạn nhập vào ứng dụng (`EPUB`, `PDF`, `CBZ`), tiến độ đọc, dấu trang (bookmarks), ghi chú/highlight và thống kê đọc sách mặc định được **lưu trữ cục bộ hoàn toàn trên thiết bị của bạn**.
- Nếu thiết bị của bạn bật **Android Auto Backup**, hệ thống Android có thể sao lưu tự động dữ liệu cấu hình/thư viện nhẹ (không bao gồm tệp sách và bộ nhớ đệm) vào tài khoản Google của bạn theo chính sách sao lưu của Android.
- **Inkify** không vận hành máy chủ riêng để thu thập dữ liệu người dùng, không tích hợp SDK theo dõi/phân tích hành vi của bên thứ ba và không chứa quảng cáo.

### 2. Đăng nhập Google & Đồng bộ Google Drive (Tùy chọn)
- Khi bạn chủ động bật tính năng **Đồng bộ Google Drive**, ứng dụng sử dụng Google Identity Services với phạm vi quyền tối thiểu:
  - `userinfo.email`: Hiển thị địa chỉ email và tên tài khoản đang kết nối trong phần Cài đặt.
  - `https://www.googleapis.com/auth/drive.appdata`: Chỉ truy cập thư mục ẩn `appDataFolder` dành riêng cho ứng dụng Inkify trên chính tài khoản Google Drive của bạn (ứng dụng **không** có quyền xem hay truy cập bất kỳ tệp nào khác trên Google Drive của bạn).
  - Tính năng **Đồng bộ cả file sách** mặc định **tắt**; khi bật, đồng bộ nền chỉ tải/đăng tệp sách qua mạng không tính phí (Wi-Fi).
- **Quyền kiểm soát và Xóa dữ liệu đám mây:**
  - Bạn có thể **xóa từng cuốn sách khỏi Google Drive** (ngay khi xóa sách trong Thư viện hoặc trong danh sách *Sách đã lưu trên Drive* tại phần Cài đặt).
  - Bạn có thể **xóa toàn bộ dữ liệu sao lưu trên Google Drive** bất cứ lúc nào bằng nút **"Xóa dữ liệu trên Drive"** trong mục *Hồ sơ & Thống kê → Cài đặt → Đồng bộ Google Drive*. Thao tác này xóa dữ liệu trên Drive và đăng xuất máy hiện tại; các thiết bị khác vẫn đang đăng nhập có thể tải lại dữ liệu cục bộ của chúng nếu chưa đăng xuất.

### 3. Tra từ điển & Dịch thuật
- Khi bạn chọn một từ hoặc đoạn văn bản trong lúc đọc sách và bấm *Tra từ / Dịch*, chỉ đoạn văn bản được chọn đó mới được gửi qua kết nối mã hóa HTTPS tới các dịch vụ tra cứu/dịch thuật bên thứ ba (`api.dictionaryapi.dev`, `translate.googleapis.com`, `api.mymemory.translated.net`) để trả về kết quả.

### 4. Các quyền hệ thống ứng dụng sử dụng
- `INTERNET`: Chỉ dùng khi bạn đồng bộ Google Drive hoặc tra từ điển trực tuyến.
- `POST_NOTIFICATIONS`: Dùng để hiển thị thanh điều hướng đọc sách trên màn hình khóa (khi bạn bật tính năng này).
- `ACCESS_NOTIFICATION_POLICY`: Chỉ dùng để bật chế độ Không làm phiền (Do Not Disturb) khi bạn chủ động bật *Focus Mode*.
- `<queries>` (`ACTION_MAIN` / `CATEGORY_LAUNCHER`): Chỉ dùng cho chế độ *eInk Home Launcher* tùy chọn nhằm liệt kê danh sách các ứng dụng có thể mở trong Ngăn ứng dụng (App Drawer) trên máy.

---

## 🇬🇧 English

### 1. Data Storage & Ownership
- All imported books (`EPUB`, `PDF`, `CBZ`), reading progress, bookmarks, highlights/notes, and reading statistics are **stored locally on your device by default**.
- If **Android Auto Backup** is enabled on your device, Android may back up lightweight app configuration and metadata (excluding book files and caches) to your Google account.
- **Inkify** does not operate any external tracking servers, third-party analytics SDKs, or advertising networks.

### 2. Google Sign-In & Google Drive Sync (Optional)
- If you choose to sign in with Google to enable multi-device sync, Inkify requests only:
  - `userinfo.email`: To display your connected account email/name in the Settings screen.
  - `https://www.googleapis.com/auth/drive.appdata`: To store and sync your reading data and optional book backups exclusively inside your own private Google Drive `appDataFolder` (Inkify **cannot** access any other files in your Google Drive).
  - **Sync book files** is **off by default**; when enabled, background sync transfers book files only over unmetered (Wi-Fi) networks.
- **Cloud Data Deletion & Control:**
  - You can **remove individual books from Google Drive** (either when deleting a book in the Library or from the *Books backed up on Drive* list in Settings).
  - You can **delete all Google Drive backup data** at any time by tapping **"Delete Drive data"** in *Profile & Stats → Settings → Google Drive Sync*. This removes the Drive backup and signs out this device; other devices that remain signed in may re-upload their local data unless signed out.

### 3. Dictionary & Translation
- When you select text while reading and choose *Define / Translate*, only the selected word or phrase is transmitted over HTTPS to third-party dictionary/translation services (`api.dictionaryapi.dev`, `translate.googleapis.com`, `api.mymemory.translated.net`).

### 4. Permissions Used
- `INTERNET`: Used solely for optional Google Drive Sync and online dictionary/translation lookups.
- `POST_NOTIFICATIONS`: Used for optional Lock Screen reading controls.
- `ACCESS_NOTIFICATION_POLICY`: Used only to toggle Do Not Disturb when you enable *Focus Mode*.
- `<queries>` (`ACTION_MAIN` / `CATEGORY_LAUNCHER`): Used solely by the optional *eInk Home Launcher* mode to display launchable apps installed on your device in the App Drawer.

### 5. Contact
If you have any questions regarding privacy or data protection, please contact: **letuandat556@gmail.com**
