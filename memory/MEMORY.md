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
  - Hệ thống thiết kế Editorial Monochrome: phân cấp tonal theo chuẩn E-Ink, không hiệu ứng nháy/chớp màn hình, token bo góc/viền tại `EInkTokens.kt`, bộ icon vector `Canvas` thống nhất tại `EInkIcons.kt`.
  - Cặp font `Bitter` (serif cho tiêu đề/bìa) + `Plus Jakarta Sans` (UI/body, đầy đủ dấu tiếng Việt) trong `EInkTheme`, cùng `EInkText`, `CoverBitmapCache`, `PaperGrainOverlay`, `GhostingOverlay` (`GhostingController`).
- **Thư viện (`ui/library/`, `ui/monet/`)**:
  - `LibraryScreen` & `LibraryViewModel`: giao diện Editorial Monochrome với `ContinueReadingHeroCard` (`resolveHeroBook`), `BookCover` có gáy sách & bìa dự phòng serif, tab gạch chân `EInkTabRow`, dropdown Sort dạng pill, thanh tìm kiếm + nút `+` bo tròn dưới đáy, `LibraryBookMenus` (menu `⋮` + long-press), `LibraryBookmarks` / `LibraryHighlights` (nút `TrashIcon` 48dp), `ReadingStatsSheet` (tab Stats/Settings kiểu editorial), `TutorialOverlay` & màn hình Paywall (`ProPaywallSheet` và các card).
  - **Sách mẫu & Nhập/Mở sách (`data/book/`, `MainActivity.kt`)**: `SampleBooksSeeder` tự động chèn 2 sách mẫu khi thư viện mới trống (`assets/samples/welcome_inkify.epub` và `assets/samples/adaptive_pdf_sample.pdf` 4 trang 2 cột có bảng vector), hỗ trợ `ACTION_VIEW` / `ACTION_SEND` (`ExternalBookIntentParser` + `importOrFindExisting`), chọn nhiều tệp (`OpenMultipleDocuments`) và quét cả thư mục sách (`FolderBookScanner` + `OpenDocumentTree`).
- **Trình đọc (`reader/`, `ui/reader/`)**:
  - **EPUB**: `EpubNavigatorFragment` (Readium), `EInkSettings` / `EInkPreferencesMapper` / `EInkColorFilter`, `FocusModeController`, `ReaderInput` (phím cứng âm lượng), `ReaderSearchSheet`, `DictionaryDialog`, `HighlightNoteDialog`, `ReaderContentsSheet`, `ReaderBars` (top/bottom bar 4 hàng mới với tone swatch có nhãn & thanh brightness), `ReaderFooterBar`, `ReaderSettingsControls`, `ReaderSettingsPanel` (dạng thẻ nổi 3 tab `Text`/`PDF` | `Display` | `Controls`).
  - **Adaptive PDF Engine** (tài liệu thiết kế tại `docs/adaptive-pdf-engine.md`): `PdfPagerReader`, `PdfMarginCropper`, `PdfDocumentClassifier`, `PdfPageAnalyzer`, `PdfDocumentCache` (`schemaVersion = 2`), `PdfGraphicMasker`, `PdfReflowEngine`, `PdfReflowPaginator`, `PdfRegionNavigator`, `PdfBookConfigStore`.
  - **Pre-Reflow Graphic Masking (đã triển khai 2026-11-16)**:
    - `PdfPageAnalyzer`: trích xuất `vectorSegments` và `filledRects` qua CTM (gồm cả đường viền mảnh vẽ bằng `fillPath`), tách cột `TextPosition` theo khoảng nhảy tọa độ `X` trong `writeString`.
    - `PdfGraphicMasker`: chạy *trước* `mergeLinesIntoParagraphs()` trong `PdfReflowEngine` để khoanh vùng bảng biểu (3 lớp: Vector Lines, Filled Rects kiểu Zebra-striped, Text Grid Alignment hỗ trợ hàng thưa) và ký hiệu xếp tầng (`SYMBOL_STACK`: chiều rộng hẹp `< 25%`, mật độ ký tự đặc biệt cao, >= 3 dòng cách đều theo $Y$).
    - `PdfReflowEngine` & `PdfReflowPaginator`: lọc bỏ text trong vùng mask, xuất `ReflowItem.Image` kèm `ReflowImageKind` (`ILLUSTRATION`, `TABLE`, `SYMBOL_STACK`), sắp xếp theo cột khi `columnCount >= 2`.
    - `PdfPagerReader`: giữ `SYMBOL_STACK` căn giữa gọn theo tỷ lệ gốc, còn `TABLE` / `ILLUSTRATION` dùng full chiều ngang và mở `PdfReflowImageZoomOverlay` (pinch-to-zoom, pan, double-tap) khi chạm vào.

## Trạng thái hiện tại
- Đã commit và push **Đợt 1** (`eadcc19`) và **Đợt 2 — Editorial Monochrome UI** (`3de7bac`) lên `origin/fix/audit-batch-1`.
- Đã gỡ bỏ hoàn toàn chế độ chớp làm mới E-Ink (Refresh Flash) (chưa commit):
  - Xóa `flashEnabled`/`flashEveryPages` trong `EInkSettings` và `EInkSettingsStore`, xóa các toggle/chuỗi `onboard_toggle_flash` ở `ReaderSettingsPanel`, `ReadingStatsSheet`, `TutorialOverlay` và cập nhật `adaptive_pdf_sample.pdf`.
  - Thay `RefreshFlashController` / `RefreshFlashOverlay` bằng `GhostingController` / `GhostingOverlay` và `GhostingControllerTest` (vẫn giữ tùy chọn Ghosting riêng, mặc định tắt).
- `.\gradlew.bat testDebugUnitTest lintDebug assembleRelease --console=plain`: `BUILD SUCCESSFUL` — 302/302 unit test pass (0 failed, 0 ignored), `lintDebug` & `lintVitalRelease` pass (0 errors), `assembleRelease` pass.
