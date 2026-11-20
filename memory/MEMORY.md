# Inkify — Project Memory

## Tổng quan
- **Tên ứng dụng**: `Inkify` (`applicationId = "com.eink.reader.app"`, namespace `com.example.emptyapp`) — ứng dụng Android đọc sách EPUB, PDF & CBZ tối ưu cho màn hình E-Ink và biến điện thoại Android thành thiết bị đọc tối giản.
- **Ngôn ngữ giao diện**: Tiếng Việt & Tiếng Anh (`values/`, `values-vi/`, `LocaleHelper`), `bundle.language.enableSplit = false`.
- **Liên hệ / Privacy**: `ebuyami2609@gmail.com` (`contact_email`, `docs/privacy-policy.md`).
- **Tech stack**:
  - Kotlin `2.3.20`, AGP `9.0.1`, Gradle `9.1.0`
  - Jetpack Compose (BOM `2026.03.01`), Navigation 3 (`1.0.1`)
  - Readium Kotlin Toolkit `3.1.1` (`readium-shared`, `readium-streamer`, `readium-navigator`, `readium-adapter-pdfium`)
  - `pdfbox-android` `2.0.27.0`, `play-services-auth` `21.3.0`, `billing-ktx`, `kotlinx-serialization-json` `1.9.0`
- **SDK**: `minSdk = 24`, `compileSdk = 37`, `targetSdk = 37`, JVM 17.

## Kiến trúc & Module chính
- **DI thủ công (`AppContainer` trong `EmptyApplication`)**:
  - `engine`: `Lazy<ReadiumEngine>` (khởi tạo trễ, tránh chặn main thread lúc cold start)
  - `library`: `LibraryRepository` (`BookImporter`, `BookIndexStore`, `CoverExtractor`, `FolderBookScanner`, `SampleBooksSeeder`)
  - `settings`: `EInkSettingsStore`
  - `pdfBookConfigs`: `PdfBookConfigStore` (ghi tuần tự trên IO)
  - `annotations`: `AnnotationRepository`
  - `dictionary`: `DictionaryService` (giới hạn độ dài text gửi đi)
  - `stats`: `ReadingStatsRepository` (+ `ReadingGamification`)
  - `sync`: `GoogleDriveSyncManager` (chạy trên `appScope`, có `SyncTombstoneStore`, `SyncTombstoneMerger`, `SyncSessionGuard`, các coordinator tách biệt cho download/upload/wipe)
  - `billing`: `PlayBillingManager`, `EntitlementStore` (mã hóa với `EntitlementCipher`), `ProFeatureGate`, `AppIntegrityGuard`
- **Điều hướng & Tích hợp hệ thống**:
  - `MainActivity` -> `Navigation.kt` / `NavigationKeys.kt` (Navigation 3) -> `LibraryScreen`, mở `ReaderActivity` (hỗ trợ mở file từ bên ngoài, warm-up async).
  - `LauncherActivity`, `EInkReadingWidgetProvider` (tự làm mới qua ngày mới/đổi ngôn ngữ/sau sync), `EInkWallpaperService` (cache Paint/grain off-main), `LockScreenReadingNotifier` (explicit `PendingIntent`s), `LockScreenReadingStore` (ghi tuần tự), `LockScreenNavReceiver`.
  - Các repository khởi tạo an toàn và cung cấp `awaitLoaded` cho async preload khi khởi động.
- **Giao diện E-Ink (`ui/eink/`)**:
  - Hệ thống thiết kế **Editorial Monochrome** (`3de7bac`), tương phản cao, loại bỏ E-Ink refresh flash mode (`ad6b00f`), giữ `GhostingController`, tông giấy, `PaperGrainOverlay`, `CoverBitmapCache`, font `Rubik`.
- **Thư viện & Onboarding (`ui/library/`)**:
  - `LibraryScreen` & `LibraryViewModel`: nhập sách đơn lẻ, nhập hàng loạt theo thư mục (`FolderBookScanner`), sách mẫu có sẵn (`SampleBooksSeeder`), sắp xếp theo `Collator`, tab `Tất cả`/`Đang đọc`/`Chưa đọc`/`Đã xong`, `ReadingStatsSheet`, `TutorialOverlay` (chặn chạm bằng `pointerInput`, ẩn semantics nền dưới cho TalkBack).
  - `List<BookEntry>.mostRecentlyReadBook()` dùng thống nhất cho `LauncherActivity`, `EInkReadingWidgetProvider`, `EInkWallpaperService` và tab `Đang đọc`.
- **Monetization — Inkify Pro (`data/billing/`, `ui/billing/`)**:
  - Tài liệu: `docs/epic-monetization.md`, `docs/qa-monetization-checklist.md`.
  - Launcher và Lock-Screen Reading được mở **miễn phí**; `ProPaywallSheet` + soft-gate theo ngữ cảnh (ví dụ PDF Reflow sau trang 5, Focus Mode, Google Drive Sync); R8 obfuscation & `AppIntegrityGuard` chống giả mạo entitlement.
- **Trình đọc (`reader/`, `ui/reader/`)**:
  - **EPUB**: `EpubNavigatorFragment`, `EInkSettings` / `EInkPreferencesMapper` / `EInkColorFilter`, `FocusModeController`, `ReaderInput` (phím âm lượng, chặn khi overlay mở), khớp bookmark theo `href` + cửa sổ progression trang, `expectRelayout`/`expectJump` one-shot cho đếm lật trang.
  - **Adaptive PDF Engine** (`docs/adaptive-pdf-engine.md`): `PdfPagerReader`, `PdfMarginCropper`, `PdfDocumentClassifier`, `PdfPageAnalyzer` (`BatchLineStripper` single-pass), `PdfAnalysisSession` (1 `PDDocument` mỗi phiên + cache phân tích trang), `PdfDocumentCache` **v4** (chunked atomic, stratified vector/rect capping, cache snippet màn khóa theo trang), `PdfGraphicMasker`, `PdfReflowEngine`, `PdfReflowPaginator` (giữ anchor đọc khi relayout), `PdfRegionNavigator`, viewport tile rendering khi zoom sâu.

## Release & Kiểm thử
- **Signing**: `app/inkify-release.jks` + `keystore.properties` đã lưu trong repo (bỏ khỏi `.gitignore`) để build đa máy; `app/build.gradle.kts` hỗ trợ fallback đọc `keystore.properties`.
- **Unit tests (`app/src/test`)**: 57 file unit test bao phủ Library/Import/SampleBooks, Billing/Entitlement/ProFeatureGate, Sync/AtomicJson/Tombstone/Drive coordinators, PDF Engine/Cache/Reflow/Zoom, Reader/Bookmarks/Ghosting/LockScreen, Launcher/Widget/Wallpaper/L10n/A11y.
- **Tài liệu audit & fix**: `docs/audit-2026-11-18.md`, `docs/audit-2026-11-19.md`, `docs/fix-plan.md` (đã hoàn tất audit batch 1–5 tới `bf9cf69`).

## Các mốc Git gần nhất
- `786e663` .. `bf9cf69`: Fix audit batch 1–5 (sync resilience, AtomicJsonFile, PDF single-session & chunked cache v4, viewport tile zoom, async cold start, lint gate, a11y, l10n, widget/wallpaper).
- `cee184f` .. `f44ec0a`: Triển khai Inkify Pro monetization, `ProPaywallSheet`, soft-gates, `EntitlementStore` mã hóa, `AppIntegrityGuard`, làm Launcher & Lock-screen miễn phí (`3349ad3`), `docs/qa-monetization-checklist.md`.
- `eadcc19`: Thêm sách mẫu, mở file từ ngoài, nhập hàng loạt/thư mục, sửa onboarding & copy.
- `3de7bac`: Nâng cấp Editorial Monochrome design system.
- `ad6b00f`: Gỡ bỏ chế độ E-Ink refresh flash.
- `e1e1634`: Thêm keystore release (`app/inkify-release.jks`, `keystore.properties`), cập nhật email liên hệ `ebuyami2609@gmail.com`.
