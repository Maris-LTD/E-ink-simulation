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
  - `LauncherActivity`, `EInkReadingWidgetProvider`, `EInkWallpaperService`, `LockScreenReadingNotifier`.
- **Giao diện E-Ink (`ui/eink/`)**:
  - Bảng màu trắng-đen tương phản cao, không hiệu ứng nháy/chớp màn hình.
  - Font UI `Rubik` + `EInkText`, `EInkTheme`, `CoverBitmapCache`, `PaperGrainOverlay`, `RefreshFlashOverlay` (`RefreshFlashController`).
- **Thư viện (`ui/library/`)**:
  - `LibraryScreen` & `LibraryViewModel`: danh sách sách, `BookCover`, `LibraryBookmarks`, `LibraryHighlights`, `ReadingStatsSheet`, `TutorialOverlay` (onboarding tương tác), tab cài đặt E-Ink toàn cục, thẻ liên hệ/phản hồi.
- **Trình đọc (`reader/`, `ui/reader/`)**:
  - **EPUB**: `EpubNavigatorFragment` (Readium), `EInkSettings` / `EInkPreferencesMapper` / `EInkColorFilter`, `FocusModeController`, `ReaderInput` (phím cứng âm lượng), `ReaderSearchSheet`, `DictionaryDialog`, `HighlightNoteDialog`, `ReaderContentsSheet`, `ReaderBars`, `ReaderFooterBar`, `ReaderSettingsPanel`.
  - **Adaptive PDF Engine** (tài liệu thiết kế tại `docs/adaptive-pdf-engine.md`): `PdfPagerReader`, `PdfMarginCropper`, `PdfDocumentClassifier`, `PdfPageAnalyzer`, `PdfDocumentCache`, `PdfReflowEngine`, `PdfReflowPaginator`, `PdfRegionNavigator`, `PdfBookConfigStore`.
  - **Quyết định thiết kế Pre-Reflow Graphic Masking (2026-11-16)**:
    - Chèn bước `PdfGraphicMasker` chạy *trước* `mergeLinesIntoParagraphs()` trong `PdfReflowEngine` để khoanh vùng bảng biểu (3 lớp: Vector Lines, Filled Rects kiểu Zebra-striped, Text Grid Alignment không viền) và ký hiệu xếp tầng (`SYMBOL_STACK`: chiều rộng hẹp `< 25%`, mật độ ký tự đặc biệt cao, >= 3 dòng cách đều theo $Y$).
    - Lọc bỏ text nằm trong vùng mask và giữ nguyên `PdfRect` chuẩn hóa `0f..1f` để crop bitmap từ `PdfRenderer` trên RAM (không xuất PNG ra đĩa, không dùng HTML/WebView — render trực tiếp qua `ReflowItem` trong Compose để đồng bộ `EInkTheme` và tránh chớp màn hình E-Ink).
    - Với trang >= 2 cột trong Reflow: tránh trộn xen kẽ cột trái/phải khi sort theo $Y$ (phân cụm theo cột hoặc fallback Chế độ 2).

## Trạng thái hiện tại
- `.\gradlew.bat testDebugUnitTest`: pass (`BUILD SUCCESSFUL`), hiện có 10 file unit test:
  - `ReadingStatsTest`, `BookIndexStoreTest`, `DictionaryServiceTest`, `RefreshFlashControllerTest`
  - `PdfBookConfigStoreTest`, `PdfDocumentClassifierTest`, `PdfReflowEngineTest`, `PdfReflowPaginatorTest`, `PdfRegionNavigatorTest`, `PdfMarginCropperTest`
- Working tree hiện đang có phần **Adaptive PDF Engine** chưa commit (`reader/pdf/`, `PdfMarginCropper.kt`, cập nhật `ReaderScreen.kt`, `ReaderViewModel.kt`, `ReaderSettingsPanel.kt`, `ReaderBars.kt`, `docs/adaptive-pdf-engine.md`, v.v.).
