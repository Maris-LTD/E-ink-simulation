# EmptyApp — Project Memory

## Tổng quan
- **Tên ứng dụng**: `EmptyApp` (`com.example.emptyapp`) — ứng dụng Android đọc sách EPUB tối ưu cho màn hình E-Ink.
- **Ngôn ngữ giao diện**: Tiếng Việt.
- **Tech stack**: Kotlin `2.3.20`, AGP `9.0.1`, Gradle `9.1.0`, Jetpack Compose (BOM `2026.03.01`), Navigation 3 (`1.0.1`), Readium Kotlin Toolkit `3.1.1` (`readium-shared`, `readium-streamer`, `readium-navigator`), `kotlinx-serialization-json` `1.9.0`.
- **SDK**: `minSdk = 24`, `compileSdk = 37`, `targetSdk = 37`, JVM 17.

## Kiến trúc & Giao diện hiện tại
- **DI thủ công**: `EmptyApplication` giữ `AppContainer` (`ReadiumEngine`, `LibraryRepository`, `EInkSettingsStore`).
- **Điều hướng**: `MainActivity` -> `MainNavigation` (Navigation 3 `NavDisplay` với `Library`) -> mở `ReaderActivity` qua `Intent` truyền `EXTRA_BOOK_ID`.
- **Giao diện E-Ink**:
  - Bảng màu trắng-đen tương phản cao (`#FFFFFF` nền, `#000000` mực, `#111111` phụ), không hiệu ứng nháy/chớp màn hình.
  - Font UI: `Rubik` (`res/font/rubik.ttf`, weight `800–900`) kết hợp `EInkText` (`Stroke` + `Fill`) và đường kẻ `1.5.dp`.
  - `LibraryScreen`: TopBar (`eInk Reader`, `Tìm`, `+ Thêm sách`), TabBar đen-trắng (`Tất cả`, `Đang đọc`, `Chưa đọc`, `Đã xong`), Filter pills theo tác giả.
- **Data (`data/book/`)**: `LibraryRepository` quản lý danh sách sách trong `filesDir/books` và index tại `filesDir/library.json` qua `BookIndexStore` + `BookImporter`.
- **Reader (`reader/`)**:
  - `ReadiumEngine`: mở `Publication` từ file EPUB.
  - Font đọc: `Literata` (`assets/fonts/literata.ttf` & `literata_italic.ttf`) đăng ký qua `EpubNavigatorFragment.Configuration`, kết hợp tiêm CSS `font-weight` + `-webkit-text-stroke` nhẹ trong `ReaderScreen` (mặc định `fontWeight = 1.25`, `contrast = 1.25f`, `SETTINGS_VERSION = 5`).
  - `EInkSettings` & `EInkSettingsStore`: cấu hình tone (`HIGH_CONTRAST` mặc định, `PAPER`, `SOFT_GRAY`), font (`SERIF`, `SANS_SERIF`), cỡ chữ, độ đậm chữ, giãn dòng, lề, grayscale, contrast, tùy chọn `paperGrain` / `ghosting` (mặc định tắt), `readerFooter` (mặc định bật).
  - `ReaderActivity`: hỗ trợ phím cứng `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN` để lật trang; `ReaderScreen` có `ReaderTopBar` (`‹`, tiêu đề, `A−`, `A+`) và `ReaderFooterBar`.

## Trạng thái hiện tại
- `.\gradlew.bat testDebugUnitTest`: pass (`RefreshFlashControllerTest` 5/5 tests).
- `.\gradlew.bat installDebug`: cài đặt và chạy COLD launch thành công trên thiết bị `25078PC3EG` (`BMSGKF6XEMKBKFYD`).
- Chưa có: unit test cho data/settings/mapper, tính năng Mục lục (TOC), Bookmark.
