# Inkify — Project Memory

## Tổng quan
- **Tên ứng dụng**: `Inkify` (`applicationId = "com.eink.reader.app"`, namespace `com.example.emptyapp`) — ứng dụng Android đọc sách EPUB & PDF tối ưu cho màn hình E-Ink.
- **Ngôn ngữ giao diện**: Tiếng Việt & Tiếng Anh (`values/`, `values-vi/`, `LocaleHelper`).
- **Tech stack**:
  - Kotlin `2.3.20`, AGP `9.0.1`, Gradle `9.1.0`
  - Jetpack Compose (BOM `2026.03.01`), Navigation 3 (`1.0.1`)
  - Readium Kotlin Toolkit `3.1.1` (`readium-shared`, `readium-streamer`, `readium-navigator`, `readium-adapter-pdfium`)
  - `pdfbox-android` `2.0.27.0`, `play-services-auth` `21.3.0`, `kotlinx-serialization-json` `1.9.0`
- **SDK**: `minSdk = 24`, `compileSdk = 37`, `targetSdk = 37`, JVM 17.

## Kiến trúc & Module chính
- **DI thủ công**: `EmptyApplication` giữ `AppContainer` gồm:
  - `engine`: `ReadiumEngine`
  - `library`: `LibraryRepository` (`BookImporter`, `BookIndexStore`, `CoverExtractor`)
  - `settings`: `EInkSettingsStore`
  - `pdfBookConfigs`: `PdfBookConfigStore`
  - `annotations`: `AnnotationRepository`
  - `dictionary`: `DictionaryService`
  - `stats`: `ReadingStatsRepository` (+ `ReadingGamification`)
  - `sync`: `GoogleDriveSyncManager`
- **Điều hướng & Tích hợp hệ thống**:
  - `MainActivity` -> `Navigation.kt` / `NavigationKeys.kt` (Navigation 3) -> `LibraryScreen`, mở `ReaderActivity`.
  - `LauncherActivity`, `EInkReadingWidgetProvider`, `EInkWallpaperService`, `LockScreenReadingNotifier`, `LockScreenReadingStore`, `LockScreenNavReceiver`.
    - **Đọc trên màn hình khóa (đã cập nhật 2026-11-16)**: Bỏ `setShowWhenLocked(true)` (không còn overlay đè lên màn hình khóa), thay bằng kết hợp **Live Wallpaper** (`EInkWallpaperService` vẽ khung đọc văn bản ở vùng đáy `h * 0.61f .. h * 0.895f`) + **Custom Compact Notification `52dp`** (`notification_lockscreen_nav.xml`, nút `[‹]` / `[›]` qua `LockScreenNavReceiver`), tự động hiển thị khi bật sáng màn hình khóa qua `KeyguardManager.isKeyguardLocked` ngay cả khi chưa mở app.
- **Giao diện E-Ink (`ui/eink/`)**:
  - Bảng màu trắng-đen tương phản cao, không hiệu ứng nháy/chớp màn hình.
  - Font UI `Rubik` + `EInkText`, `EInkTheme`, `CoverBitmapCache`, `PaperGrainOverlay`, `RefreshFlashOverlay` (`RefreshFlashController`).
- **Thư viện (`ui/library/`)**:
  - `LibraryScreen` & `LibraryViewModel`: danh sách sách, `BookCover`, `LibraryBookmarks`, `LibraryHighlights`, `ReadingStatsSheet`, `TutorialOverlay` (onboarding 4 bước tương tác: xem thử tông giấy/flash/vân giấy, bật Hình nền khóa E-Ink & Thông báo điều hướng 1 chạm, đặt `eInk Launcher` qua `EInkSystemSetupHelper` kèm đánh dấu `tutorial_seen` ngay khi chọn launcher, nhập sách đầu tiên; có thanh nhắc thiết lập trên `LibraryScreen`), tab cài đặt E-Ink toàn cục, thẻ liên hệ/phản hồi.
- **Trình đọc (`reader/`, `ui/reader/`)**:
  - **EPUB**: `EpubNavigatorFragment` (Readium), `EInkSettings` / `EInkPreferencesMapper` / `EInkColorFilter`, `FocusModeController`, `ReaderInput` (phím cứng âm lượng), `ReaderSearchSheet`, `DictionaryDialog`, `HighlightNoteDialog`, `ReaderContentsSheet`, `ReaderBars`, `ReaderFooterBar`, `ReaderSettingsPanel`.
  - **Adaptive PDF Engine** (tài liệu thiết kế tại `docs/adaptive-pdf-engine.md`): `PdfPagerReader`, `PdfMarginCropper`, `PdfDocumentClassifier`, `PdfPageAnalyzer`, `PdfDocumentCache` (`schemaVersion = 2`), `PdfGraphicMasker`, `PdfReflowEngine`, `PdfReflowPaginator`, `PdfRegionNavigator`, `PdfBookConfigStore`.
  - **Pre-Reflow Graphic Masking (đã triển khai 2026-11-16)**:
    - `PdfPageAnalyzer`: trích xuất `vectorSegments` và `filledRects` qua CTM (gồm cả đường viền mảnh vẽ bằng `fillPath`), tách cột `TextPosition` theo khoảng nhảy tọa độ `X` trong `writeString`.
    - `PdfGraphicMasker`: chạy *trước* `mergeLinesIntoParagraphs()` trong `PdfReflowEngine` để khoanh vùng bảng biểu (3 lớp: Vector Lines, Filled Rects kiểu Zebra-striped, Text Grid Alignment hỗ trợ hàng thưa) và ký hiệu xếp tầng (`SYMBOL_STACK`: chiều rộng hẹp `< 25%`, mật độ ký tự đặc biệt cao, >= 3 dòng cách đều theo $Y$).
    - `PdfReflowEngine` & `PdfReflowPaginator`: lọc bỏ text trong vùng mask, xuất `ReflowItem.Image` kèm `ReflowImageKind` (`ILLUSTRATION`, `TABLE`, `SYMBOL_STACK`), sắp xếp theo cột khi `columnCount >= 2`.
    - `PdfPagerReader`: giữ `SYMBOL_STACK` căn giữa gọn theo tỷ lệ gốc, còn `TABLE` / `ILLUSTRATION` dùng full chiều ngang và mở `PdfReflowImageZoomOverlay` (pinch-to-zoom, pan, double-tap) khi chạm vào.

## Trạng thái hiện tại
- `.\gradlew.bat testDebugUnitTest`: pass (`BUILD SUCCESSFUL`), hiện có 13 file unit test:
  - `BookEntryTest`, `ReadingStatsTest`, `BookIndexStoreTest`, `DictionaryServiceTest`, `RefreshFlashControllerTest`, `LockScreenTextChunkerTest`
  - `PdfBookConfigStoreTest`, `PdfDocumentClassifierTest`, `PdfGraphicMaskerTest`, `PdfReflowEngineTest`, `PdfReflowPaginatorTest`, `PdfRegionNavigatorTest`, `PdfMarginCropperTest`
- `.\gradlew.bat installDebug`: cài đặt thành công trên thiết bị `25078PC3EG - 16`.
- **Thư viện & Màn hình chính**: `List<BookEntry>.mostRecentlyReadBook()` (ưu tiên sách đang đọc có `updatedAt` mới nhất) được dùng thống nhất cho `LauncherActivity`, `EInkReadingWidgetProvider`, `EInkWallpaperService`, và tab `Đang đọc` trong `LibraryScreen` (sắp xếp theo `updatedAt` giảm dần).
- **Git**: Đã commit và push lên `origin/master` (`12d62d5` Adaptive PDF Engine + Pre-Reflow Graphic Masking; `2aa72a9` / `64abf5c` Lock-Screen Reading Panel + auto-show; `3f5c3dc` fix `mostRecentlyReadBook`; bản onboarding tương tác 4 bước).
