# EmptyApp — Project Memory

## Tổng quan
- **Tên ứng dụng**: `EmptyApp` (`com.example.emptyapp`) — ứng dụng Android đọc sách EPUB tối ưu cho màn hình E-Ink.
- **Ngôn ngữ giao diện**: Tiếng Việt.
- **Tech stack**: Kotlin `2.3.20`, AGP `9.0.1`, Gradle `9.1.0`, Jetpack Compose (BOM `2026.03.01`), Navigation 3 (`1.0.1`), Readium Kotlin Toolkit `3.1.1` (`readium-shared`, `readium-streamer`, `readium-navigator`), `kotlinx-serialization-json` `1.9.0`.
- **SDK**: `minSdk = 24`, `compileSdk = 37`, `targetSdk = 37`, JVM 17.

## Kiến trúc hiện tại
- **DI thủ công**: `EmptyApplication` giữ `AppContainer` (`ReadiumEngine`, `LibraryRepository`, `EInkSettingsStore`).
- **Điều hướng**: `MainActivity` -> `MainNavigation` (Navigation 3 `NavDisplay` với `Library`) -> mở `ReaderActivity` qua `Intent` truyền `EXTRA_BOOK_ID`.
- **Data (`data/book/`)**: `LibraryRepository` quản lý danh sách sách trong `filesDir/books` và index tại `filesDir/library.json` qua `BookIndexStore` + `BookImporter`.
- **Reader (`reader/`)**:
  - `ReadiumEngine`: mở `Publication` từ file EPUB.
  - `EInkSettings` & `EInkSettingsStore`: cấu hình tone (`PAPER`, `HIGH_CONTRAST`, `SOFT_GRAY`), font (`SERIF`, `SANS_SERIF`), cỡ chữ, giãn dòng, lề, grayscale, contrast, flash chống lưu ảnh (`flashEnabled`, `flashEveryPages`), tắt animation.
  - `EInkPreferencesMapper`: ánh xạ `EInkSettings` sang `EpubPreferences` của Readium.
  - `RefreshFlashController` + `RefreshFlashOverlay`: kích hoạt flash toàn màn hình định kỳ theo số trang lật.
  - `ReaderActivity`: hỗ trợ phím cứng `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN` để lật trang.

## Trạng thái hiện tại
- `.\gradlew.bat testDebugUnitTest`: pass (`RefreshFlashControllerTest` 5/5 tests).
- `.\gradlew.bat installDebug`: cài đặt và chạy COLD launch thành công trên thiết bị `25078PC3EG` (`BMSGKF6XEMKBKFYD`).
- Chưa có: unit test cho data/settings/mapper, tính năng Mục lục (TOC), Bookmark, tìm kiếm.
