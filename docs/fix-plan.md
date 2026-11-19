# Kế hoạch sửa lỗi (nguồn: `docs/audit-2026-11-18.md`)

Quy trình: sửa lần lượt từng mục, mỗi mục xong thì ghi log vào phần **Nhật ký** bên dưới (đã sửa gì, file nào, cách kiểm tra, bước tiếp theo) rồi mới sang mục sau.

Trạng thái: `[ ]` chưa làm · `[~]` đang làm · `[x]` xong · `[-]` bỏ qua/hoãn

## Đợt 1 — Mất dữ liệu / treo app / chặn phát hành
- [x] 1. D1 — Sync ghi đè cloud khi đọc remote thất bại (+ phân trang list file)
- [x] 2. D2 — Ghi index/annotation/stats/pdf-config không atomic, race, đọc lỗi = rỗng
- [x] 3. P1 — Debounce lưu tiến độ khi lật trang
- [x] 4. P2 — PDF reflow/paginate ra khỏi main thread, chỉ tính khi ở chế độ reflow
- [x] 5. P3 — LRU bitmap PDF theo byte, giới hạn kích thước render 2×
- [x] 6. D6 — Reflow PDF nhiều cột giữ đúng thứ tự
- [x] 7. D7 — Tone Dark không đảo màu EPUB 2 lần
- [x] 8. P4 — `onPause` reader: debounce sync, cache snippet
- [x] 9. P5 — Kéo độ sáng không ghi settings/submit preferences mỗi event
- [x] 10. D10 — Release: R8 + shrinkResources + signing config; backup rules

## Đợt 2 — Sync & dữ liệu
- [x] 11. D3 — Giữ annotation của sách không có trên máy khi upload
- [x] 12. D4 — Tombstone cho xoá bookmark/highlight/sách; giữ tombstone khi sign out; sửa "xoá toàn bộ cloud"
- [x] 13. D5 — Timeout upload JSON, lỗi upload 1 sách không chặn cả sync, merge `dailyGoalMinutes`
- [x] 14. D8 — Vị trí đọc: recreate activity, locator notification, reflow đổi font, bookmark toggle, settings ≠ lật trang
- [x] 15. D9 — Import trùng, cover xám, locale, import crash, widget sách đã xoá, PdfRenderer close race, wallpaper crash

## Đợt 3 — Hiệu năng còn lại
- [x] 16. P6 — Cold start lazy/async, widget `updatePeriodMillis=0`
- [x] 17. P7 — PDF: phân tích lười, classifier dùng chung, prefetch, cache vector, region cache
- [x] 18. P8 — Reader EPUB: ghosting PixelCopy, lock-screen IO, JS inject, bookmark parse, recomposition
- [x] 19. P9 — Library/launcher: cover cache theo byte, remember stats, clock loop, wallpaper check, backfill cover, app list
- [x] 20. P10 — Wallpaper cache Paint/grain, sync WorkManager + backoff, upload sách chỉ Wi-Fi

## Đợt 4 — UX
- [x] 21. U1 — Giữ state thư viện khi mở Thống kê
- [x] 22. U2 + U3 — PDF zoom không chớp trắng, tap-zoom, lật trang khi zoom, placeholder/retry
- [x] 23. U4 — Thông báo lỗi sync localized, trạng thái "kết nối lại"
- [x] 24. U5 — Quyền thông báo, notification màn khoá, focus mode, lỗi intent bị nuốt
- [x] 25. U6 — Dialog ghi chú, checkbox xoá Drive mặc định tắt + undo, tutorial chặn touch, lỗi mở sách
- [x] 26. U7 — Accessibility (contentDescription, 48dp, semantics)
- [x] 27. U8 + U9 — Launcher pins, empty state, sort, cạnh trái ẩn, phím âm lượng, đồng hồ 12h
- [x] 28. U10 + U11 — Chuỗi cứng, plurals, privacy policy

## Đợt 5 — Sửa theo re-audit (nguồn: `docs/audit-2026-11-19.md`)
Mã trong ngoặc (H/M/L) trỏ tới mục tương ứng trong báo cáo re-audit — xem ở đó để biết file/dòng, kịch bản lỗi và cách sửa chi tiết.

### 5A — Chặn đọc sách / sai vị trí / build gate
- [x] 29. H1 — PDF Reflow kẹt spinner: fallback trang gốc (hoặc lỗi + nút "Về trang gốc") khi phân tích rỗng, tap giữa mở toolbar trên 2 màn spinner, `analyzeAllPages` bắt lỗi theo từng trang
- [x] 30. H3 — Bookmark EPUB so theo `href` + khoảng progression của trang hiện tại (dùng `PaginationListener`), chỉ so `position` cho PDF/CBZ/fixed-layout
- [x] 31. M1 + M2 + L3 — Notification màn khoá: `deleteIntent`/PREV/NEXT dùng intent tường minh; snippet rỗng không giữ session cũ; lưu `capturedAt` làm `EXTRA_LOCATOR_CREATED_AT`; `stepSnippet` trả `null` khi không đổi; `LockScreenReadingStore` ghi tuần tự (`limitedParallelism(1)`, `_state.update`)
- [x] 32. Lint gate — sửa 24 `LocalContextGetResourceValueCall` + `MissingPermission` (`areNotificationsEnabled`); `bundle.language.enableSplit = false`; kiểm `bcpkix` TrustAll có còn trong APK release; `HardcodedText` "‹ ›", `SmallSp` widget, dọn `UnusedResources` → `lintDebug` xanh

### 5B — Đồng bộ Google Drive & an toàn dữ liệu
- [x] 33. H2 — `clearToken` sau revoke và khi gặp 401 rồi `authorize()` lại 1 lần (cân nhắc nâng `play-services-auth` ≥ 21.4.0: `clearToken` / `revokeAccess`)
- [x] 34. M3 + M4 — Import luôn ghi tombstone khôi phục; không xác định được email/`sub` thì dừng sync trước mọi thao tác ghi
- [x] 35. M5 + L4 — "Xoá toàn bộ Drive" xác minh list rỗng mới báo thành công/đăng xuất, kiểm mã revoke; `signOut`/wipe lấy `mutex`, revoke trước `Identity.signOut`, sync kiểm cờ phiên sau khi lấy lock
- [x] 36. M6 + L1 + L6 — Gom & merge file `eink_sync_v1.json` trùng tên rồi xoá bản thừa; kiểm `version` trước PATCH (đổi thì merge lại); sort payload theo key trước khi so sánh/ghi
- [x] 37. M7 — Tải/import sách từ cloud: `runCatching` từng sách + đếm lỗi, dọn temp/`books/`/cover trong `finally`, kiểm SHA-256, `renameTo` thay copy
- [x] 38. M8 + L10 — Sync/xoá cloud chạy trên `appScope`, rethrow `CancellationException`; map lỗi thô sang chuỗi localized (import, search, `httpGet` ném `DriveHttpException`), xoá cloud gặp `hasResolution()` bật `needsReconnect`
- [x] 39. M9 + L2 + L8 — Bắt `IOException` khi ghi (giữ "dirty", báo UI, receiver `runCatching`); `AtomicJsonFile` cách ly file khi lỗi đọc, khôi phục từ `.tmp`, fsync thư mục; ghi tombstone trước commit annotation; mutex quanh import + disable nút khi `importing` (đếm job); `backfillMissingCovers` lưu id trước khi thử + bắt `Throwable`
- [x] 40. L5 + L7 + L9 + L22 (`updatedAt`) — Tombstone chặn `changedAt` tương lai; sync thủ công hỏi trước khi tải/đăng sách qua mạng trả phí; từ điển cắt ~500 ký tự + `langpair=auto|<target>`; `savePdfReadingMode` không đổi `updatedAt`, `saveProgress` bỏ qua locator không đổi, override locator đánh dấu bằng saved state thay vì so đồng hồ

### 5C — PDF
- [x] 41. M10 + M11 + L11 — Một `PDDocument`/phiên trên `IO.limitedParallelism(1)` + cache analysis theo trang trong holder (bỏ code chết `seedAnalyses`/`analysisCache` nếu không dùng), prefetch trang kề; GUIDED chặn lật khi chưa có regions và giữ `pendingBackwardLandingPage`; smart tap-zoom ở SMART phân tích trang hiện tại; trích chữ bằng 1 lần `getText` + `startPage/endPage`
- [x] 42. M12 + L13 — Tap-zoom khi autoCrop đổi rect sang toạ độ crop; `clampOffset` theo kích thước ảnh hiển thị
- [x] 43. M13 + M14 — Reflow giữ mốc đọc (trang nguồn + đoạn + offset) khi dàn lại; callback `onScreenTurn` đếm lật màn reflow/vùng GUIDED (thống kê + flash)
- [x] 44. M15 + L12 + L14 — Bỏ `renderMultiplier = 3` và `prefetchAdjacent`, trang kề render 1×, zoom > 2× render vùng đang nhìn (debounce sau pinch); `derivedStateOf`/`rememberUpdatedState` cho scale/bitmap/analysis; `openPdfHolder` ra khỏi main; `cropCache` → `ConcurrentHashMap`; OOM retry ở ×√0.5; `PdfReflowEngine` dùng `endsWith`
- [x] 45. M16 — `PdfDocumentCache` lưu theo khúc + ghi atomic + tăng `CURRENT_SCHEMA_VERSION`, cap segment ưu tiên theo vị trí; snippet PDF màn khoá cache theo `(bookId, page)`, không đọc lại cả file
- [x] 46. L15 — Trang fallback trong Reflow: `cd_pdf_page`, spinner/lỗi/retry, cho zoom; ảnh inline không cướp vùng tap lật trang + có `contentDescription`; nút "Đóng" overlay ≥ 48 dp; volume không lật khi overlay mở

### 5D — Khởi động & Reader EPUB
- [x] 47. M17 — Repository load trên IO + `loaded` flag (VM chờ); `warmUpForUiAsync` trong `ReaderActivity`; khởi tạo/gọi sync ngoài main; `LibraryRepository` nhận `Lazy<ReadiumEngine>`; `PdfBookConfigStore` ghi trên IO
- [x] 48. M18 — Thay mốc 1 s bằng cờ một lần `expectRelayout`/`expectJump` (đặt ở `onConfigurationChanged` và trước `navigator.go`, timeout dự phòng ~3 s)
- [x] 49. L16 + L21 — Dải cạnh trái: tap = lùi trang hoặc chuyển drag sang `InputListener` Readium; phím âm lượng bỏ qua khi Search/Contents/Settings/toolbar đang mở (cờ `overlayOpen` trong VM)
- [x] 50. L17 + L18 — Dialog ghi chú: `dismissOnBackPress = false`/xác nhận, nháp + `pendingNoteLocator` vào ViewModel/SavedStateHandle, không toast khi quote trống; quyền thông báo: xin lại khi còn rationale, nếu không thì snackbar mở cài đặt thông báo
- [x] 51. L19 + L20 + L22 (reader) + L23 — Ghost chỉ áp filter ở fallback API 24–25, bỏ `recycle()`, chỉ đảo slot khi capture thành công; throttle tiến độ: phần ghi `NonCancellable`, hẹn lại khi còn pending; `retryLoad` bỏ qua khi đang Loading; footer clock theo lifecycle

### 5E — Thư viện, Launcher, Wallpaper, Widget, l10n, a11y
- [x] 52. M19 + M20 — Bìa ở Launcher áp grayscale theo setting; khu app ghim cuộn được / giới hạn hàng + "Xem thêm"
- [x] 53. M21 + L27 — Chặn pop làm rỗng back stack; tab Stats `rememberSaveable`
- [x] 54. M22 + L26 — TutorialOverlay chặn touch bằng `pointerInput` + ẩn semantics phía dưới; a11y còn lại (swatch tone Launcher, vùng chạm < 48 dp, `Role.RadioButton`/`selected` cho chip/pill, `ToggleOptionRow` dùng `toggleable`, semantics chart/heatmap)
- [x] 55. L24 + L25 — Đồng hồ Launcher theo 12/24h hệ thống, AM/PM wallpaper theo locale app, xoá formatter chết; plurals `stats_heatmap_days` (+ các `PluralsCandidate` khác), bỏ "BOOK" cứng, thứ/đơn vị theo resource/`DayOfWeek.getDisplayName`, xoá chuỗi không dùng, sort tên bằng `Collator`
- [ ] 56. L28 + L29 — Widget refresh khi qua ngày / đổi múi giờ, ngôn ngữ / sau sync; wallpaper cache context, formatter, rank, `Paint`/`StaticLayout`, `removeCallbacks` ở `onDestroy`, decode cover ngoài main
- [ ] 57. L30 + L31 — `docs/privacy-policy.md` bổ sung (snippet trên màn khoá, nút "Dịch ngoài", device-transfer, giới hạn text từ điển); `.gitignore` chặn `*.jks`/`*.keystore`

### 5F — Nghiệm thu
- [ ] 58. `testDebugUnitTest` + `lintDebug` xanh; chạy bản release R8 trên máy; cài AAB (bundletool) kiểm ngôn ngữ; backup/restore; đi qua danh sách test tay mục 5 của `docs/audit-2026-11-19.md`

---

## Nhật ký

### 1. D1 — Sync ghi đè cloud khi đọc remote thất bại — 2026-11-18
**Nguyên nhân:** `httpGet` trả `null` với mọi HTTP non-2xx, `downloadSyncPayload` trả `null` khi JSON lỗi, `listAppDataFiles` trả map rỗng khi lỗi và chỉ đọc 200 file đầu. Sync hiểu những trường hợp này là "Drive chưa có dữ liệu" rồi upload payload chỉ của máy hiện tại → ghi đè hoặc tạo file trùng.

**Đã sửa** (`data/sync/GoogleDriveSyncManager.kt`, `res/values*/strings.xml`):
- Thêm `DriveRemoteReadException`; `httpGet` ném lỗi với mọi mã non-2xx (kèm HTTP code).
- `downloadSyncPayload` trả `SyncPayload` không null, ném lỗi khi tải hoặc parse thất bại.
- `listAppDataFiles` phân trang theo `nextPageToken` (pageSize 1000), parse lỗi thì ném lỗi.
- `fetchUserProfile` lỗi không làm hỏng sync (bọc `runCatching`), vì chỉ để hiển thị email.
- Khi đọc remote thất bại, sync và xoá-sách-trên-cloud dừng lại **trước** mọi thao tác ghi, hiện thông báo mới `sync_remote_read_failed` (vi/en) có mã lỗi.

**Cách kiểm tra:** `:app:compileDebugKotlin` pass. Chưa test trên máy. Kiểm tra tay: tắt mạng giữa chừng / thu hồi quyền Drive → sync báo "Không đọc được dữ liệu trên Google Drive…", file `eink_sync_v1.json` trên Drive không bị đổi.

**Còn để lại cho mục khác:** upload JSON chưa có timeout (mục 13); thông báo các lỗi khác vẫn là message thô (mục 23); file JSON trên Drive bị hỏng thật sẽ chặn sync cho tới khi người dùng "Xoá toàn bộ dữ liệu Drive".

**Bước tiếp theo:** mục 2 — D2 (ghi file atomic + mutex), vì sync vừa sửa vẫn có thể ghi đè `library.json` song song với lưu tiến độ.

### 2. D2 — Ghi file dữ liệu an toàn — 2026-11-18
**Nguyên nhân:** 5 file dữ liệu (`library.json`, `bookmarks.json`, `highlights.json`, `reading_stats.json`, `pdf_book_configs.json`) ghi đè trực tiếp bằng `writeText` → app bị kill giữa chừng là file cụt. Khi đọc, lỗi parse bị nuốt thành danh sách rỗng, lần ghi tiếp theo lưu rỗng đè lên vĩnh viễn. `LibraryRepository`/`AnnotationRepository` cập nhật state rồi ghi file ngoài lock → nhiều coroutine ghi song song, snapshot cũ có thể ghi sau cùng.

**Đã sửa:**
- Mới: `data/storage/AtomicJsonFile.kt` — ghi vào `<tên>.tmp`, `fsync`, rồi `renameTo` thay file gốc (atomic trên Android); đọc lỗi parse thì đổi tên file hỏng thành `<tên>.corrupt-<timestamp>` trước khi trả mặc định, nên dữ liệu cũ không bao giờ bị ghi đè mất.
- `data/book/BookIndexStore.kt`, `data/stats/ReadingStatsRepository.kt`, `reader/pdf/PdfBookConfigStore.kt`: chuyển sang `AtomicJsonFile`.
- `data/book/LibraryRepository.kt`, `data/annotation/AnnotationRepository.kt`: `commit` bọc update state + ghi file trong `Mutex` → các lần ghi tuần tự, file trên đĩa luôn khớp state mới nhất.
- Test mới `AtomicJsonFileTest` (3 case: thiếu file, ghi đè không để lại `.tmp`, file hỏng được backup và không bị đè).

**Cách kiểm tra:** `testDebugUnitTest` cho `AtomicJsonFileTest`, `BookIndexStoreTest`, `PdfBookConfigStoreTest`, `ReadingStatsTest` — 11/11 pass.

**Còn để lại:** mỗi lần lật trang vẫn ghi cả `library.json` (giờ còn phải chờ mutex) → mục 3. `PdfBookConfigStore.update` vẫn ghi đĩa trên thread gọi (file nhỏ, xử lý ở mục 17). Chưa có UI báo người dùng khi phát hiện file hỏng (file backup nằm trong `filesDir`).

**Bước tiếp theo:** mục 3 — P1, debounce lưu tiến độ đọc (gom ghi 1–2 s, flush khi `onPause`/`onCleared`).

### 3. P1 — Gom lưu tiến độ đọc — 2026-11-18
**Nguyên nhân:** `ReaderViewModel.onLocatorChanged` mỗi lần đổi vị trí (lật trang, seek, repaginate) launch 1 coroutine `saveProgress` → ghi lại toàn bộ `library.json` + phát `books` mới cho mọi màn đang quan sát. Lật nhanh/giữ phím âm lượng = hàng chục lần ghi/giây.

**Đã sửa** (`ui/reader/ReaderViewModel.kt`):
- Giữ vị trí mới nhất trong `pendingProgress`; chỉ ghi tối đa **1 lần / 1,5 s** (`PROGRESS_SAVE_INTERVAL_MS`), luôn ghi vị trí mới nhất (throttle, không phải bỏ lần cuối).
- `onReaderForegroundChanged(false)` (gọi từ `onPause`) huỷ hẹn giờ và **ghi ngay** vị trí đang chờ trước khi flush thống kê. Vì `viewModelScope` chạy `Main.immediate`, state thư viện được cập nhật trước khi `onPause` refresh widget và chạy sync → widget/sync thấy vị trí mới nhất.
- `onCleared` ghi nốt vị trí còn chờ (scope `IO + NonCancellable`) để không mất khi đóng reader.
- Chuyển `Locator.toJSON()` từ mỗi lần lật trang sang chỉ lúc ghi.

**Cách kiểm tra:** `:app:compileDebugKotlin` pass. Chưa test trên máy. Kiểm tra tay: lật nhanh 20 trang → bấm Home ngay → mở lại sách phải đúng trang cuối; widget/home hiện đúng tiến độ.

**Còn để lại:** `LockScreenNavReceiver` vẫn ghi tiến độ trực tiếp mỗi lần bấm (tần suất thấp, xử lý ở mục 18). Nếu đúng lúc `onPause` mà một lần ghi khác đang giữ mutex thì state cập nhật trễ vài ms sau khi sync bắt đầu (mục 8 sẽ debounce sync nên hết ảnh hưởng).

**Bước tiếp theo:** mục 4 — P2, đưa reflow/paginate PDF ra khỏi main thread và chỉ tính khi đang ở chế độ reflow (nguy cơ ANR với PDF lớn).

### 4. P2 — Reflow PDF chạy nền, chỉ khi cần — 2026-11-18
**Nguyên nhân:** trong `PdfPagerReader`, `reflowMetas` (chạy `PdfReflowEngine.buildReflowPage` + `PdfGraphicMasker` cho **mọi** trang) và `reflowScreens` (`textMeasurer.measure` từng đoạn, kèm binary search khi tách đoạn) nằm trong `remember{}` → chạy đồng bộ trên main thread lúc compose, ở **mọi** chế độ đọc, và chạy lại khi xoay máy/đổi font/cỡ chữ/lề/giãn dòng.

**Đã sửa** (`ui/reader/PdfPagerReader.kt`, `res/values*/strings.xml`):
- `reflowMetas` → `produceState`, chạy trên `Dispatchers.Default`, **chỉ khi** `readingMode == HYBRID_REFLOW`; ở chế độ khác trả `null` (không tính, giải phóng bộ nhớ). Tách thành hàm `buildReflowMetas` có `ensureActive()` mỗi trang → đổi chế độ/thoát reader là dừng ngay.
- `reflowScreens` → `produceState` trên `Dispatchers.Default`, dùng một `TextMeasurer` riêng (`cacheSize = 0`) tạo từ `LocalFontFamilyResolver`/`LocalDensity`/`LocalLayoutDirection` thay cho `rememberTextMeasurer`; lambda đo chữ kiểm tra job còn active để huỷ giữa chừng khi đổi font/cỡ chữ liên tục.
- Khi đang ở chế độ reflow mà chưa dàn xong → hiện spinner + "Đang dàn trang chữ…" (`pdf_reflow_preparing`, vi/en) thay vì treo UI. Khi đổi font/cỡ chữ, màn cũ vẫn hiện cho đến khi bản mới tính xong (không chớp trắng).

**Cách kiểm tra:** `:app:compileDebugKotlin` pass. Chưa test trên máy. Kiểm tra tay: mở PDF 300+ trang ở chế độ Trang gốc → mở ngay, không ANR; chuyển sang Reflow → thấy "Đang dàn trang chữ…" rồi hiện nội dung; kéo cỡ chữ liên tục → UI không đứng.

**Còn để lại:** đổi font/cỡ chữ ở reflow vẫn có thể giữ sai vị trí trang (D8, mục 14). Phân tích PDFBox toàn bộ sách trước trang đầu, prefetch bitmap ở reflow mode, `PdfGraphicMasker` O(H·V) → mục 17. Thứ tự cột sai → mục 6.

**Bước tiếp theo:** mục 5 — P3, LRU bitmap PDF tính theo byte và giới hạn kích thước render 2× (tránh OOM khi zoom/GUIDED_COLUMN).

### 5. P3 — Bộ nhớ ảnh trang PDF — 2026-11-18
**Nguyên nhân:** `PdfRendererHolder` cache `LruCache<String, ImageBitmap>(8)` — giới hạn theo **số ảnh**, không theo dung lượng. Ở GUIDED_COLUMN hoặc zoom > 1.35× trang render 2× (30–50 MB/ảnh trên màn 1440p) → cache có thể giữ 250–400 MB → OOM. Lỗi OOM bị `runCatching` nuốt nên người dùng chỉ thấy trang trắng. Ngoài ra mở PDF mật khẩu/hỏng làm rò file descriptor.

**Đã sửa:**
- Tách `PdfRendererHolder` + `openPdfHolder` từ `PdfPagerReader.kt` (1.218 → 1.074 dòng) sang file mới `ui/reader/PdfRendererHolder.kt`.
- Mới `PdfBitmapBudget`: cache = 1/8 heap, kẹp trong 16–96 MB, tính theo KB (`sizeOf` = rộng × cao × 4); một ảnh đơn tối đa min(32 MB, 1/8 heap). Ảnh vượt mức được thu nhỏ đều theo √(max/pixels) — 2× trên màn 1440p (~11,7 MP) còn ~8 MP, vẫn nét.
- Gộp 3 nhánh render (trang đầy đủ / trang cắt lề / vùng ảnh inline) vào một hàm `renderScaled` dùng chung giới hạn trên.
- `renderWithMemoryRetry`: gặp `OutOfMemoryError` thì xoá cache và thử lại 1 lần thay vì trả trang trắng ngay.
- `openPdfHolder`: đóng `ParcelFileDescriptor` nếu `PdfRenderer` mở thất bại (một phần D9).
- Test mới `PdfBitmapBudgetTest` (3 case).

**Cách kiểm tra:** `:app:compileDebugKotlin` pass; `PdfBitmapBudgetTest` 3/3 pass. Chưa test trên máy. Kiểm tra tay: PDF lớn ở chế độ Theo cột, lật 20+ trang và zoom qua lại → không trắng trang, không crash; Android Studio Memory Profiler: graphics/native không vượt ~100 MB do cache.

**Còn để lại:** zoom > 2× vẫn mờ (render tối đa 2×) và chớp trắng khi vượt ngưỡng 1.35× → mục 22. Prefetch trang kề ở chế độ reflow + ảnh inline chưa cache → mục 17. `close()` chưa lấy mutex (race với render đang chạy) → mục 15.

**Bước tiếp theo:** mục 6 — D6, reflow PDF nhiều cột giữ đúng thứ tự đọc (hiện `sortedBy { topFraction }` trộn đoạn cột 1 và cột 2).

### 6. D6 — Thứ tự đọc reflow PDF nhiều cột — 2026-11-18
**Nguyên nhân:** `PdfReflowEngine.buildReflowPage` đã sắp các dòng theo (cột, top) trước khi ghép đoạn, nhưng ở bước cuối lại `sortedBy { it.topFraction }` toàn bộ đoạn + ảnh → các đoạn cùng độ cao ở cột 1 và cột 2 bị xen kẽ (A1, B1, A2, B2…).

**Đã sửa** (`reader/pdf/PdfReflowEngine.kt`):
- `mergeLinesIntoParagraphs` trả kèm vị trí dòng đầu của mỗi đoạn (`PlacedParagraph`).
- Bước ghép cuối sắp theo (cột, top): đoạn lấy cột theo dòng đầu, ảnh/bảng/khối ký hiệu lấy cột theo bounds của nó — dùng chung `columnOrderBucket` với bước sắp dòng. Trang 1 cột: mọi thứ cùng cột 0 → giữ nguyên hành vi cũ (sắp theo top).
- Test mới `twoColumnPageKeepsColumnReadingOrderWithImageInSecondColumn`: 2 cột × 2 đoạn + ảnh ở cột phải → thứ tự trái1, trái2, phải1, ẢNH, phải2 (code cũ ra trái1, phải1, ẢNH, …).

**Cách kiểm tra:** toàn bộ test `reader.pdf.*` 26/26 pass. Kiểm tra tay: PDF bài báo/tạp chí 2 cột ở chế độ Reflow đọc liền mạch hết cột trái rồi sang cột phải.

**Còn để lại:** khối trải ngang 2 cột (tiêu đề phụ/hình lớn) nằm **giữa** trang vẫn bị đưa lên đầu trang (giới hạn sẵn có của `columnOrderBucket`, cả cho text); xử lý đúng cần chia trang thành các dải ngang — ghi nhận, chưa nằm trong plan. Tỉ lệ ảnh inline sai → mục 15.

**Bước tiếp theo:** mục 7 — D7, tone Dark làm EPUB bị đảo màu 2 lần (trang gần như trắng).

### 7. D7 — Tone Dark với EPUB — 2026-11-18
**Nguyên nhân:** ở tone DARK, `EInkPreferencesMapper.toEpub` đã cho Readium vẽ nền `#121212` / chữ `#EAEAEA`, rồi `ReaderScreen` gắn hardware layer với `EInkColorFilter.paintOf(settings)` có bước **đảo màu** → EPUB thành nền gần trắng, chữ tối, lệch hẳn với khung app tối xung quanh. Bước đảo màu chỉ đúng cho nội dung vốn sáng (bitmap PDF, ảnh truyện CBZ).

**Đã sửa:**
- `reader/EInkColorFilter.kt`: `matrixOf`/`paintOf` thêm tham số `contentAlreadyDark` (mặc định `false`); khi `true` thì tone DARK bỏ bước đảo màu, vẫn giữ grayscale + contrast.
- `ui/reader/ReaderScreen.kt`: truyền `contentAlreadyDark = nav is EpubNavigatorFragment`. PDF (`PdfPagerReader`) và CBZ (`ImageNavigatorFragment`) giữ nguyên hành vi đảo màu.

**Cách kiểm tra:** `:app:compileDebugKotlin` pass (không viết unit test vì `android.graphics.ColorMatrix` là stub trên JVM). Kiểm tra tay: tone Tối → EPUB nền tối chữ sáng, khớp thanh trên/dưới; PDF và CBZ ở tone Tối vẫn được đảo sang nền tối; các tone khác không đổi.

**Còn để lại:** ảnh "ghosting" chụp WebView không đi qua color filter nên ở tone Tối/Ấm bóng mờ lệch màu (mục 18). Với tone Giấy/Ấm/Xám, nền EPUB sau grayscale + contrast + tint hơi khác nền khung app (sẵn có, ảnh hưởng nhỏ — ghi nhận, chưa đưa vào plan).

**Bước tiếp theo:** mục 8 — P4, `onPause` của reader: debounce/guard Drive sync và cache snippet màn khoá (hiện mỗi lần pause chạy full sync + đọc lại cả chương).

### 8. P4 — Việc nặng trong `onPause` của reader — 2026-11-18
**Nguyên nhân:** `ReaderActivity.onPause` chạy mỗi lần mở dialog quyền, share sheet, chuyển app…, và mỗi lần đều: (1) gọi `syncInBackgroundIfSignedIn()` → full Drive sync (userinfo + list + tải + upload JSON), các lần gọi dồn hàng sau mutex; (2) đọc lại cả chương EPUB + regex strip HTML hoặc phân tích lại trang PDF để lấy đoạn chữ màn khoá; (3) phần (2) chạy trong `lifecycleScope` nên bị huỷ khi đóng reader → màn khoá không cập nhật vị trí cuối (lỗi B4).

**Đã sửa:**
- `data/sync/GoogleDriveSyncManager.kt`: `syncInBackgroundIfSignedIn` thành **debounce 20 s + gộp yêu cầu**: chỉ có tối đa 1 job nền; mọi lần gọi trong lúc chờ/đang chạy được gộp, sau khi chạy xong nếu có yêu cầu mới thì chờ 20 s rồi chạy thêm đúng 1 lần (không mất lần cập nhật cuối). Sync thủ công (nút trong app) không bị ảnh hưởng.
- `AppContainer.kt`: thêm `appScope` (SupervisorJob + Default) cho việc cần sống lâu hơn Activity.
- `ReaderActivity.kt`:
  - `postLockScreenNotification` bỏ qua nếu vị trí (sách + href + position + progression) không đổi so với lần trước; `force = true` khi vừa được cấp quyền thông báo hoặc vừa bật lại tính năng.
  - Cache đoạn chữ đã tách theo chương EPUB (`cachedChapterSnippets`) → lật trang trong cùng chương chỉ tính lại vị trí đoạn, không đọc lại HTML.
  - Chạy trích xuất + `updateSession` + hiện thông báo trên `appScope` (IO) với `applicationContext` → không bị huỷ khi đóng reader, và ghi file session không còn trên main thread.
  - Nếu trích xuất ra rỗng (chương chỉ có ảnh, hoặc publication đã đóng) thì giữ nguyên session cũ thay vì ghi đè rỗng.

**Cách kiểm tra:** `:app:compileDebugKotlin` pass. Chưa test trên máy. Kiểm tra tay: đọc sách, bật/tắt share sheet nhiều lần → Drive chỉ sync 1 lần sau ~20 s; đọc vài trang rồi bấm Back thoát reader → khoá màn hình, thông báo hiện đúng đoạn đang đọc.

**Còn để lại:** sync vẫn chạy trong coroutine của process (chết theo process), chưa dùng WorkManager/backoff, vẫn upload JSON dù không đổi → mục 20. EPUB đổi sang chương mới đúng lúc reader vừa đóng có thể không đọc được chương (publication đã đóng) → giữ đoạn cũ. Widget vẫn refresh mỗi `onPause` (nhẹ, giữ nguyên).

**Bước tiếp theo:** mục 9 — P5, kéo thanh độ sáng / vuốt cạnh trái không ghi settings + submit Readium preferences mỗi pixel.

### 9. P5 — Kéo độ sáng & áp lại cài đặt sách — 2026-11-18
**Nguyên nhân:** mỗi sự kiện kéo (slider độ sáng ở thanh dưới, vuốt dọc dải cạnh trái) gọi `updateSettings` → ghi lại cả 22 key SharedPreferences, reset bộ đếm flash; và vì `LaunchedEffect(nav, settings)` lấy **cả object settings** làm key nên còn: tạo `Paint` + `setLayerType` mới, `submitPreferences` Readium (CSS áp lại, có thể dàn trang lại), `evaluateJavascript`. Bật/tắt footer, paper grain, xoay màn hình… cũng kích hoạt toàn bộ chuỗi này.

**Đã sửa:**
- `reader/EInkSettingsStore.kt`: thêm `preview` (chỉ cập nhật state trong bộ nhớ) và `persist` (ghi state hiện tại xuống prefs).
- `ui/reader/ReaderViewModel.kt`: `previewBrightness` / `commitBrightness`; `updateSettings` chỉ reset bộ đếm flash khi đổi cài đặt **khác** độ sáng.
- `ui/reader/ReaderBars.kt`: `ReaderBottomBar`/`ThemeAndBrightnessRow` thêm `onBrightnessChangeFinished` (slider `onValueChangeFinished`, nút A/% gọi cả hai).
- `ui/reader/ReaderScreen.kt`:
  - Slider và vuốt cạnh trái dùng `previewBrightness` khi kéo (cửa sổ vẫn đổi sáng ngay vì `ReaderActivity` theo dõi state), `commitBrightness` khi thả tay/huỷ.
  - Tách effect: color filter chỉ chạy lại khi `grayscale`/`contrast`/`tone` đổi; Readium `submitPreferences` + stroke JS chỉ chạy lại khi `EpubPreferences` (data class) thật sự khác.

**Cách kiểm tra:** `:app:compileDebugKotlin` pass. Chưa test trên máy. Kiểm tra tay: kéo slider độ sáng / vuốt cạnh trái liên tục → màn hình đổi sáng mượt, chữ EPUB không nháy/dàn lại; thoát vào lại reader → độ sáng giữ đúng giá trị cuối; bật/tắt chân trang → trang EPUB không bị dàn lại.

**Còn để lại:** các slider khác trong panel cài đặt (cỡ chữ, giãn dòng, lề) vẫn ghi prefs mỗi bước kéo — cần áp dụng ngay để xem trước nên giữ, có thể gom sau. Dải cạnh trái ẩn vẫn chưa được hướng dẫn cho người dùng (mục 27).

**Bước tiếp theo:** mục 10 — D10, cấu hình bản release: bật R8 + shrinkResources + keep rules, tách signing config khỏi debug key, và backup rules loại trừ `books/`, cover, prefs sync.

### 10. D10 — Cấu hình bản release & sao lưu — 2026-11-18
**Nguyên nhân:** bản release ký bằng debug key (không lên Play được), tắt R8 (APK không thu gọn/không làm rối code), và trỏ tới `proguard-rules.pro` không tồn tại. `allowBackup="true"` nhưng hai file rule là template, chưa gắn vào manifest → Auto Backup gom cả `books/` (dễ vượt quota 25 MB → hỏng âm thầm) và prefs đăng nhập Drive.

**Đã sửa:**
- `app/build.gradle.kts`:
  - `isMinifyEnabled = true`, `isShrinkResources = true`.
  - Signing config `release` đọc `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` từ Gradle property (`~/.gradle/gradle.properties`) hoặc biến môi trường; thiếu thì build ra APK **không ký** (không còn ký bằng debug key).
  - `ndk.abiFilters` = arm64-v8a, armeabi-v7a, x86_64, x86 (bỏ mips/armeabi đã lỗi thời của PDFium).
  - Loại `org/bouncycastle/pqc/**/*.properties` (~8 MB dữ liệu mã hoá hậu lượng tử, PDFBox không dùng).
- Mới `app/proguard-rules.pro`: giữ nguyên Readium, PDFBox (`com.tom_roush`), PDFium (JNI), `@JavascriptInterface`, serializer kotlinx.serialization của app; giữ số dòng trong stack trace.
- `res/xml/backup_rules.xml` + `data_extraction_rules.xml` gắn vào manifest:
  - Cloud backup chỉ gồm cài đặt (`eink_settings`, `eink_launcher_prefs`, `eink_tutorial_prefs`) và `reading_stats.json` — không sao lưu thư viện vì file sách không đi kèm.
  - Chuyển máy trực tiếp (device-transfer, không giới hạn dung lượng) mang theo toàn bộ trừ `pdf_cache/` (tạo lại được).

**Cách kiểm tra:** `:app:assembleRelease` thành công với R8; APK 51 MB → **34 MB** (chưa ký). Toàn bộ unit test 57/57 pass. **Chưa chạy bản release trên máy** — R8 có thể làm hỏng phần dùng JNI/reflection mà build không phát hiện được; cần cài thử và đi qua: mở EPUB, mở PDF (cả 3 chế độ), CBZ, tra từ điển, đăng nhập + đồng bộ Drive, widget, hình nền.

**Còn để lại:** quy tắc keep cho Readium/PDFBox đang rộng (giữ toàn bộ) — thu hẹp sau khi bản release chạy ổn để giảm thêm dung lượng. Nên phát hành dạng App Bundle (AAB) để Play tách theo ABI (mỗi máy chỉ tải ~6 MB PDFium thay vì cả 4 ABI). `windowSoftInputMode` trên `<application>` vẫn để nguyên (vô tác dụng; chuyển lên activity có thể làm WebView EPUB dàn lại khi bật bàn phím — cần thử riêng).

**Bước tiếp theo:** hết Đợt 1. Đợt 2 bắt đầu với mục 11 — D3, khi đồng bộ giữ lại highlight/bookmark của những sách không có trên máy hiện tại (hiện bị xoá khỏi cloud).

> Đợt 1 đã commit: `786e663` trên nhánh `fix/audit-batch-1`.

### 11. D3 — Giữ annotation của sách không có trên máy — 2026-11-18
**Nguyên nhân:** `mergeRemoteIntoLocal` bỏ qua bookmark/highlight remote nếu sách không có trên máy (không có chỗ gắn `bookId`), rồi `buildLocalPayload` dựng danh sách upload **chỉ từ annotation local** → annotation của các sách đó bị xoá khỏi `eink_sync_v1.json` cho mọi thiết bị. Xảy ra khi máy tắt "đồng bộ file sách", sách > 100 MB, tải sách thất bại, hoặc user xoá sách khỏi máy nhưng giữ trên cloud. (Danh sách sách thì đã được giữ bằng `putIfAbsent`, chỉ annotation bị mất.)

**Đã sửa:**
- Mới `data/sync/SyncAnnotationMerger.kt` (thuần Kotlin, test được): danh sách upload = annotation local (bỏ sách đã xoá khỏi cloud) + annotation remote của sách **không có trên máy và chưa bị xoá**; trùng id thì bản local thắng.
- `data/sync/GoogleDriveSyncManager.kt`: `buildLocalPayload` dùng merger cho cả bookmarks và highlights.
- Test mới `SyncAnnotationMergerTest` (3 case: giữ annotation sách chỉ có trên cloud, bỏ annotation sách đã xoá, local thắng khi trùng id).

**Cách kiểm tra:** `SyncAnnotationMergerTest` 3/3 pass (kèm compile). Kiểm tra tay với 2 máy: máy A có sách X + highlight; máy B tắt "đồng bộ file sách" (không có X) → B đồng bộ → A đồng bộ lại → highlight của X vẫn còn.

**Còn để lại:** xoá bookmark/highlight của sách **có** trên máy vẫn bị hồi sinh ở lần sync sau (không có tombstone) → mục 12.

**Bước tiếp theo:** mục 12 — D4, tombstone cho xoá bookmark/highlight/sách đưa vào `SyncPayload` (tương thích file cũ), giữ tombstone khi đăng xuất + thu hồi quyền Drive, sửa "Xoá toàn bộ dữ liệu cloud".

### 12. D4 — Đồng bộ thao tác xoá (tombstone) — 2026-11-18
**Nguyên nhân:**
- Xoá bookmark/highlight chỉ xoá ở máy; lần sync sau merge theo id lấy lại bản trên cloud → hiện về.
- Danh sách hash sách đã xoá khỏi cloud chỉ nằm trong SharedPreferences của một máy → máy khác tải sách lên lại.
- Đăng xuất gọi `prefs.clear()` → mất luôn danh sách đó; không thu hồi quyền Drive.
- "Xoá toàn bộ dữ liệu Drive" thêm mọi sách trên máy vào danh sách đã xoá → thư viện hiện tại không bao giờ đồng bộ lại, không báo gì; còn máy khác thì tải lên lại ngay.

**Đã sửa:**
- `SyncModels.kt`: thêm `SyncTombstone(key, changedAt, deleted)` (kiểu "ghi sau thắng": `deleted = false` là khôi phục); `SyncPayload` có thêm `bookTombstones`, `annotationTombstones` (mặc định rỗng → đọc được file cũ).
- Mới `data/sync/SyncTombstoneMerger.kt` (thuần Kotlin): gộp theo key lấy bản mới hơn (bằng giờ thì "xoá" thắng), bỏ dấu cũ hơn 365 ngày, `upsert` không đổi gì nếu trạng thái giữ nguyên, `withoutDeleted` lọc payload.
- Mới `data/sync/SyncTombstoneStore.kt`: lưu ở `files/sync_tombstones.json` (qua `AtomicJsonFile`, không nằm trong SharedPreferences nên đăng xuất không xoá); gắn với email tài khoản — đăng nhập tài khoản **khác** thì bắt đầu lại từ rỗng. Danh sách cũ trong prefs được chuyển sang một lần khi khởi động.
- `AnnotationRepository`: xoá bookmark/highlight → ghi dấu xoá; hoàn tác (undo) → ghi dấu khôi phục; thêm `dropDeleted(ids)` để áp dấu xoá từ máy khác. Xoá sách chỉ khỏi máy (`removeAllForBook`) **không** ghi dấu, annotation vẫn còn trên cloud.
- `GoogleDriveSyncManager`:
  - Sync: tải payload → gộp dấu xoá từ cloud → xoá local các annotation đã bị xoá ở máy khác → lọc remote → upload kèm toàn bộ dấu xoá.
  - Xoá 1 sách khỏi cloud: cũng gộp + ghi dấu xoá vào file trên Drive.
  - Nhập lại sách đã xoá: ghi dấu khôi phục (thắng dấu xoá cũ trên các máy khác).
  - Đăng xuất: giữ dấu xoá, thu hồi quyền Drive qua `https://oauth2.googleapis.com/revoke` (bản `play-services-auth` 21.3.0 chưa có `revokeAccess`).
  - "Xoá toàn bộ dữ liệu Drive": xoá file → xoá dấu xoá local → thu hồi quyền → **đăng xuất máy này** (thay vì chặn đồng bộ vĩnh viễn). Hộp thoại xác nhận và thông báo hoàn tất (vi/en) nói rõ điều này và việc máy khác còn đăng nhập có thể tải lên lại.
- Test mới `SyncTombstoneMergerTest` (6 case) + 1 case mới trong `SyncAnnotationMergerTest`.

**Cách kiểm tra:** 10/10 test trong `data.sync` pass (kèm compile). Kiểm tra tay với 2 máy cùng tài khoản:
1. Máy A xoá 1 highlight → sync → máy B sync: highlight biến mất trên B, không quay lại trên A.
2. Máy A xoá sách X khỏi cloud → máy B sync: B không tải X lên lại (B vẫn giữ X trên máy).
3. Máy B nhập lại X → sync → A sync: X có lại trên cloud.
4. Đăng xuất rồi đăng nhập lại: sách đã xoá khỏi cloud không bị tải về.
5. "Xoá toàn bộ dữ liệu Drive": máy bị đăng xuất, đăng nhập lại thì cả thư viện đồng bộ bình thường.

**Còn để lại:**
- Bản app cũ (chưa có mục này) ghi file sync sẽ làm rơi trường dấu xoá; máy bản mới vẫn còn dấu xoá ở local nên ghi lại ở lần sync kế tiếp.
- Dấu xoá quá 365 ngày bị bỏ: máy offline hơn 1 năm có thể làm hồi sinh mục đã xoá.
- Đang sync mà bấm đăng xuất thì `fetchUserProfile` của lượt sync đó có thể ghi lại email (lỗi có từ trước, hiếm).
- `GoogleDriveSyncManager.kt` vẫn rất dài (~780 dòng) — nên tách phần HTTP Drive ra file riêng ở một mục dọn dẹp sau.

**Bước tiếp theo:** mục 13 — D5: thêm timeout cho `uploadSyncJson`, lỗi upload một sách không làm hỏng cả lượt sync, merge `dailyGoalMinutes` của thống kê theo thời điểm sửa.

### 13. D5 — Timeout upload JSON, cô lập lỗi upload sách, merge `dailyGoalMinutes` — 2026-11-19
**Nguyên nhân:**
- `uploadSyncJson` không đặt `connectTimeout`/`readTimeout` và không bọc `conn.disconnect()` trong `try/finally` → mạng treo dễ kẹt trong `mutex`, UI kẹt "đang đồng bộ".
- `uploadMissingBooks` ném exception ngay khi 1 sách upload lỗi (413/quota/mạng) → chặn toàn bộ sync tiến độ, bookmark, highlight và các sách còn lại.
- `ReadingStatsRepository.mergeFromRemote` dùng `maxOf(local.dailyGoalMinutes, remote.dailyGoalMinutes)` → hạ mục tiêu đọc hàng ngày bị sync trả lại giá trị cao cũ. (Riêng phần phân trang `listAppDataFiles` đã được xử lý từ mục 1 — D1.)

**Đã sửa:**
- `GoogleDriveSyncManager.kt`:
  - `uploadSyncJson`: thêm `connectTimeout = 15_000`, `readTimeout = 30_000` cho cả `POST` và `PATCH`, bọc trong `try ... finally { conn.disconnect() }`.
  - `uploadMissingBooks`: bọc `uploadBinaryFileToAppData` bằng `runCatching` theo từng sách, đếm số sách lỗi và trả về `Int` để tiếp tục các sách khác và vẫn hoàn tất sync JSON.
  - `completeSyncWithToken`: nếu có sách upload thất bại thì hiển thị `sync_partial_book_upload_failed` (đã thêm ở `values/strings.xml` và `values-vi/strings.xml`) thay vì `sync_success`.
- `ReadingStatsRepository.kt`:
  - Thêm `dailyGoalUpdatedAt: Long = 0L` vào `ReadingStatsData` và `nowProvider` vào `ReadingStatsRepository`; `setDailyGoalMinutes` cập nhật `dailyGoalUpdatedAt`.
  - `mergeFromRemote`: chọn `dailyGoalMinutes` theo `dailyGoalUpdatedAt` mới hơn (nếu cả hai cùng `0L` từ bản cũ thì fallback `maxOf`).
- `ReadingStatsTest.kt`: thêm 2 test (`mergeFromRemoteUsesNewerDailyGoalTimestampEvenWhenLower`, `mergeFromRemoteLegacyZeroTimestampsFallBackToMax`).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`).

**Còn để lại:** chưa có test unit riêng cho lớp HTTP của `GoogleDriveSyncManager` (timeout và cô lập lỗi upload từng sách kiểm tra bằng review code).

**Bước tiếp theo:** mục 14 — D8: giữ vị trí đọc khi recreate activity, locator notification, reflow đổi font, bookmark toggle, settings ≠ lật trang.

### 14. D8 — Vị trí đọc, reflow PDF, bookmark & thống kê lật trang — 2026-11-19
**Nguyên nhân:**
- `ReaderActivity` dùng `super.onCreate(null)` và `installNavigatorFactory` / `ReaderScreen` luôn lấy `ready.initialLocator` ban đầu → recreate activity (đổi dark mode hệ thống, font scale…) bị bật về vị trí lúc mới mở sách.
- `EXTRA_LOCATOR_JSON` từ notification/bookmark luôn thắng `book.lastLocatorJson`, kể cả khi người dùng đã đọc xa hơn rồi process bị kill và Android redeliver lại `Intent`.
- Reflow PDF (`PdfPagerReader.kt`): khi đổi font/lề tạo `reflowScreens` mới, `rememberPagerState` giữ nguyên index cũ của màn hình trước đó và `LaunchedEffect(reflowPagerState, reflowScreens)` phát `settledPage` cũ, ghi đè sai `currentPdfPage`.
- Đổi cỡ chữ, lề, kiểu chữ… khiến Readium/reflow phát `onLocatorChanged` với `progression` dịch nhẹ → `ReaderViewModel` đếm thành `pendingPages += 1` và kích hoạt `flashController`.
- `toggleBookmark()` trả về `Boolean` (`false` cả khi `currentLocator == null` lẫn khi xoá bookmark) → `ReaderScreen` hiện nhầm "Đã xoá dấu trang"; đồng thời `findMatchingBookmark` dùng dung sai `0.015` progression khiến 2 trang kề bị coi là một và xoá nhầm.

**Đã sửa:**
- `ReaderActivity.kt`:
  - `ReaderScreen`, `installNavigatorFactory`, `epubFragmentFactory` ưu tiên `viewModel.currentLocatorSnapshot() ?: ready.initialLocator`.
  - `intentFor` gắn thêm `EXTRA_LOCATOR_CREATED_AT`; `ReaderActivity` đọc xong gọi `intent.removeExtra(...)` cho cả 2 extra.
- `ReaderViewModel.kt`:
  - `load()`: chỉ dùng `overrideLocatorJson` nếu `overrideLocatorCreatedAt <= 0L || overrideLocatorCreatedAt >= book.updatedAt`.
  - `updateSettings` / `updatePdfConfig`: khi đổi các thuộc tính bố cục (`font`, `fontScale`, `fontWeight`, `lineHeight`, `paragraphSpacing`, `justifyText`, `pageMargins`, `orientationLock`, `pdfAutoCrop`) hoặc config PDF, đặt `suppressPageTurnUntilMs` (1000 ms) để `onLocatorChanged` do dàn trang lại không tăng `pendingPages` hay kích hoạt flash/ghost.
  - `toggleBookmark()`: đổi kiểu trả về thành `Boolean?` (`null` khi `currentLocator == null`); siết dung sai `findMatchingBookmark` từ `0.015` / `0.01f` / `0.005f` xuống `0.002` / `0.002f`.
- `ReaderScreen.kt`: chỉ hiện snackbar bookmark khi `toggleBookmark()` trả về khác `null`.
- `PdfPagerReader.kt`: trong `LaunchedEffect(reflowPagerState, reflowScreens)`, gọi `reflowPagerState.scrollToPage(findScreenIndexForPdfPage(reflowScreens, currentPdfPage))` **trước** khi `collect` `snapshotFlow { reflowPagerState.settledPage }`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`).

**Còn để lại:** 5 tình huống trên (recreate activity, redeliver intent từ notification, đổi font ở PDF reflow, đổi font EPUB, toggle bookmark khi chưa có locator) cần kiểm tra tay trên thiết bị/emulator.

**Bước tiếp theo:** mục 15 — D9: import trùng `contentHash`, `CoverBitmapCache` grayscale, `LocaleHelper` / `LauncherActivity` đổi ngôn ngữ, try/finally khi import lỗi, refresh widget khi xoá sách, `PdfRenderer.close()` race & leak fd, tỉ lệ ảnh reflow, snippet màn khoá khi đóng reader, bắt exception wallpaper.

### 15. D9 — Import trùng, cover xám, locale, widget, PdfRenderer close, reflow ảnh & wallpaper — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **Import trùng `contentHash`:** `LibraryRepository.import` gọi `ensureContentHashes()` và truyền tập hash hiện có vào `BookImporter.import`; sau khi copy & tính `sha256Hex`, nếu trùng thì xoá file tạm và trả về lỗi `lib_import_duplicate` (đã thêm ở `values/strings.xml` & `values-vi/strings.xml`). Đồng thời bọc `CoverExtractor.extract` trong `try/catch/finally` để luôn `publication.close()` và dọn file khi lỗi.
- **Cover luôn xám:** `CoverBitmapCache.decodeAndCache` bỏ `toGrayscaleImageBitmap` (trả bitmap màu `RGB_565` trực tiếp), để `BookCover` tự áp `ColorFilter` theo setting `grayscale`.
- **Locale & `LauncherActivity`:** `LocaleHelper.wrap` bỏ `Locale.setDefault(locale)` (tránh làm hỏng `AppLanguage.SYSTEM`); `effectiveLanguageCode` đọc từ `Resources.getSystem().configuration.locales[0]`; `LauncherActivity` lưu `appliedLanguage` ở `onCreate` và tự `recreate()` ở `onResume()` nếu ngôn ngữ đã đổi.
- **Import crash & kẹt `importing`:** `LibraryViewModel.import` bọc `try / catch (Throwable)` (nếu `CancellationException` thì rethrow) + `finally { mutableImporting.value = false }`.
- **Widget không cập nhật khi xoá/nhập sách:** `LibraryViewModel.remove` và `import` (khi thành công) gọi `EInkReadingWidgetProvider.refreshAll(app)`.
- **`PdfRenderer.close()` race:** `PdfRendererHolder` thêm `@Volatile closed`, kiểm tra `closed` trong `mutex.withLock` ở `renderRegion`/`renderPage`, và `close()` đẩy việc đóng `renderer`/`descriptor` vào `CoroutineScope(Dispatchers.IO + NonCancellable)` bên trong `mutex.withLock`. (Mở file lỗi ở `openPdfHolder` đã đóng `descriptor` từ trước.)
- **Tỉ lệ ảnh inline reflow:** thêm `pageAspectRatio` vào `ReflowPageMeta` (`PdfPagerReader.buildReflowMetas` truyền `pageWidthPt / pageHeightPt`), `PdfReflowPaginator` nhân `aspect` với `pageMeta.pageAspectRatio`.
- **Snippet màn khoá khi đóng reader:** `ReaderActivity.postLockScreenNotification` đã chạy trên `container.appScope` từ P4.
- **Wallpaper crash:** `EInkWallpaperService.drawFrame()` thêm `catch (_: Throwable) {}` quanh `lockCanvas()` / `renderWallpaper()`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`).

**Còn để lại:** hết Đợt 2. Các hành vi UI/hệ thống (đổi ngôn ngữ `LauncherActivity`, widget, wallpaper, đóng PDF khi đang render) cần kiểm tra tay trên thiết bị/emulator.

**Bước tiếp theo:** sang Đợt 3 — mục 16 (P6): tối ưu cold start (lazy/async init) và đặt widget `updatePeriodMillis = 0`.

### 16. P6 — Tối ưu cold start (lazy/async) & tắt widget periodic wake — 2026-11-19
**Nguyên nhân:**
- `EmptyApplication.onCreate()` gọi `PDFBoxResourceLoader.init(applicationContext)` và tạo `AppContainer` đồng bộ trên main thread — khởi tạo `ReadiumEngine` và đọc toàn bộ JSON (library, bookmarks, highlights, stats, pdf config, sync tombstones, lockscreen) ngay cả khi process chỉ bật lên cho widget, wallpaper hoặc broadcast.
- `eink_widget_info.xml` đặt `updatePeriodMillis="1800000"`, ép Android cold-start app mỗi 30 phút.

**Đã sửa:**
- `EmptyApplication.kt`: bỏ `PDFBoxResourceLoader.init` khỏi `onCreate()`.
- `AppContainer.kt`: chuyển toàn bộ dependency (`engine`, `library`, `settings`, `pdfBookConfigs`, `syncTombstones`, `annotations`, `dictionary`, `stats`, `sync`, `lockScreenReading`) sang `by lazy`, và thêm `warmUpForUiAsync()` để pre-warm `settings`, `library`, `annotations`, `stats` song song trên `Dispatchers.IO`.
- `MainActivity.kt` & `LauncherActivity.kt`: gọi `container.warmUpForUiAsync()` trong `onCreate()`.
- `PdfPageAnalyzer.kt`: thêm `ensureInitialized(context)` (thread-safe, chạy 1 lần); gọi trên `Dispatchers.IO` tại các điểm dùng PDFBox (`PdfPagerReader.kt`, `ReaderActivity.kt`, `ReaderViewModel.kt`).
- `eink_widget_info.xml`: đổi `android:updatePeriodMillis` từ `1800000` thành `0`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra cold start và mở sách PDF reflow trên máy thật/emulator để xác nhận PDFBox init lười hoạt động bình thường.

**Còn để lại:** chưa gắn lịch refresh widget khi qua ngày mới (widget vẫn tự cập nhật khi mở/đọc sách và khi thêm/xoá sách).

**Bước tiếp theo:** mục 17 — P7: tối ưu PDF (phân tích lười, dùng chung classifier, tắt prefetch ở reflow, tách/giới hạn cache `vectorSegments`, cache region reflow, tối ưu `PDFTextStripper`).

### 17. P7 — Tối ưu hiệu năng PDF (phân tích lười, cache region, giới hạn vector, tái dùng stripper) — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **Phân tích PDFBox chặn toàn bộ sách:** `PdfPagerReader.kt` trước đây chặn hiển thị cho tới khi `analyzeAllPages` xong toàn bộ sách, không check huỷ coroutine. Nay ở chế độ trang gốc (`SMART_ORIGINAL` / `GUIDED_COLUMN`), trang đầu hiển thị ngay; `analyzeAllPages` chỉ chạy khi ở `HYBRID_REFLOW` (hoặc load từ `PdfDocumentCache`), truyền `shouldContinue = { isActive }` để dừng ngay khi đóng/đổi chế độ; chế độ `GUIDED_COLUMN` phân tích lười từng trang đang xem qua `produceState` nếu chưa có cache toàn sách.
- **Classifier mở file 2 lần:** `PdfDocumentClassifier` bỏ bước mở `ParcelFileDescriptor` + `PdfRenderer` riêng, dùng `PdfPageAnalyzer.analyzeSamplePages(file, ::samplePageIndices)` (mở `PDDocument` 1 lần); `ReaderViewModel.ensurePdfClassified` ưu tiên `PdfDocumentCache` trước khi chạy classifier.
- **Prefetch thừa ở reflow:** `PdfPagerReader.kt` chỉ chạy `holder.prefetchAdjacent(...)` khi `!isReflowMode`.
- **Cache vector không giới hạn & không dọn khi xoá sách:** `PdfPageAnalyzer.kt` giới hạn tối đa `400` `vectorSegments` và `100` `filledRects` mỗi trang; `PdfDocumentCache.delete(file)` được gọi trong `LibraryRepository.remove(id)` để dọn cache khi xoá sách.
- **Region image reflow không cache:** `PdfRendererHolder.renderRegion` nay cache bitmap theo key `reg:$index:$left:$top:$right:$bottom:$maxWidth` trong `pageBitmapCache`.
- **Tạo `PDFTextStripper` mới mỗi trang:** `PdfPageAnalyzer.kt` dùng chung `ReusableLineStripper` cho toàn bộ vòng lặp trang trong `analyzeAllPages`, `analyzePages`, `analyzeSamplePages`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay trên thiết bị/emulator với sách PDF lớn (>300 trang): mở chế độ trang gốc hiện ngay không chờ, chuyển sang reflow có thể huỷ giữa chừng, vuốt qua lại giữa các trang reflow có ảnh inline không render lại.

**Còn để lại:** `PdfDocumentCache` vẫn ghi/đọc một file JSON cho toàn sách khi dùng reflow (với số segment mỗi trang đã được giới hạn).

**Bước tiếp theo:** mục 18 — P8: tối ưu Reader EPUB (ghosting reuse bitmap/`PixelCopy`, `LockScreenReadingStore` & `LockScreenNavReceiver` IO + `goAsync()`, chỉ inject JS khi đổi `href`, tránh parse JSON bookmark mỗi lần lật, thu hẹp recomposition `ReaderScreen`).

### 18. P8 — Tối ưu Reader EPUB, ghosting & lock-screen — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **Ghosting alloc bitmap & thiếu color filter:** `ReaderScreen.kt` tách `ReaderGhostAndFlashLayer`, tái sử dụng 2 buffer `Bitmap` luân phiên (tránh cấp phát mới mỗi lần lật trang), dùng `PixelCopy.request` trên API 26+ (fallback `Canvas.draw` trên API 24–25), và truyền `ghostColorFilter` vào `RefreshFlashOverlay` (`RefreshFlashOverlay.kt`).
- **Lock-screen IO trên main thread:** `LockScreenReadingStore` chuyển `saveToDisk` / `clear` sang `Dispatchers.IO` qua `AtomicJsonFile` với `writeLock`; `LockScreenNavReceiver` dùng `goAsync()` và chạy toàn bộ cập nhật + lưu tiến độ trên `Dispatchers.IO`.
- **Inject JS mỗi lần lật trang:** `ReaderScreen.kt` chỉ gọi `applyReaderStrokeStyle` trong `currentLocator.collect` khi `locator.href.toString()` thay đổi (vẫn giữ gọi khi đổi `epubPreferences`).
- **Parse JSON mọi bookmark mỗi lần lật:** `ReaderViewModel.kt` thêm `parsedBookmarks` để parse `Locator` một lần khi danh sách `bookmarks` thay đổi, `isCurrentPageBookmarked` dùng `findMatchingBookmarkParsed` không parse lại mỗi lần lật.
- **Recomposition toàn `ReaderScreen` khi lật trang:** bỏ `collectAsStateWithLifecycle()` cho `progress`, `flashTrigger`, `pageTurnTrigger`, `pagesSinceFlash` ở đầu `ReaderScreen`; tách `ReaderFooterBarContainer`, `ReaderGhostAndFlashLayer` và đọc `progress` cục bộ trong `ReaderBottomBar`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay trên thiết bị/emulator: bật ghosting lật trang EPUB (kiểm tra màu ghost khớp tone), chuyển chương kiểm tra stroke style, bấm prev/next trên notification màn khoá.

**Còn để lại:** `ReaderScreen` vẫn nhận `bookmarks`/`highlights` dạng `List` ở cấp màn hình (chỉ thay đổi khi người dùng thêm/xoá bookmark hoặc highlight).

**Bước tiếp theo:** mục 19 — P9: tối ưu Thư viện & Launcher (`CoverBitmapCache` theo KB, `remember` stats/gamification & resource ID, đồng hồ `repeatOnLifecycle(STARTED)`, bỏ `isLiveWallpaperActive()` trong composition, tránh `backfillMissingCovers` mở lại mọi sách mỗi lần khởi động, refresh app launcher khi cài/gỡ).

### 19. P9 — Tối ưu Thư viện & Launcher — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **`CoverBitmapCache`:** đặt giới hạn `16 MB` theo `sizeOf` (tính theo `width * height * 2 / 1024` KB cho `RGB_565`) và siết `computeInSampleSize` (`width / (sample * 2) >= maxDim`) để thumbnail được downsample đúng mức.
- **Tính lại stats/gamification & `LocaleHelper.wrap()` mỗi recomposition:** `ReadingGamification.str` dùng trực tiếp `context?.getString(resId)` (activity context đã wrap locale từ `attachBaseContext`); `ReadingStatsSheet.kt` và `LauncherActivity.kt` bọc `rankFor`, `badgesFor`, `recentDaysSeries`, `currentBook`, `completedCount`, `activeDaysCount`, `avgMinutes7d`, `pagesPerHour` trong `remember(...)`.
- **Clock loop chạy nền ở Launcher:** `LauncherActivity.kt` chuyển vòng lặp đồng hồ/pin sang `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` và căn `delay` tới đầu phút kế tiếp.
- **Binder call `isLiveWallpaperActive()` trong composition:** `LibraryScreen.kt` lưu `wallpaperActive` trong `remember` và chỉ cập nhật lại ở `Lifecycle.Event.ON_RESUME`.
- **`backfillMissingCovers` mở lại sách không bìa mỗi lần khởi động:** `LibraryRepository.kt` lưu danh sách `attempted_ids` vào `SharedPreferences("eink_cover_backfill")` để bỏ qua các sách đã thử tách bìa mà không có.
- **Danh sách app Launcher:** bỏ `prewarmIcons` toàn bộ app; đăng ký `BroadcastReceiver` (`ACTION_PACKAGE_ADDED` / `REMOVED` / `CHANGED`) để tự làm mới danh sách và evict icon của app vừa đổi.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay trên thiết bị/emulator: gõ tìm kiếm trong thư viện (không giật), cài/gỡ app cập nhật drawer launcher, quay lại từ chọn wallpaper cập nhật banner.

**Còn để lại:** chưa tách `ReadingGamification` sang trả `@StringRes` (để giữ nguyên signature cho unit test hiện có).

**Bước tiếp theo:** mục 20 — P10: cache `Paint`/grain cho `EInkWallpaperService`, tối ưu sync nền (`GoogleDriveSyncManager` / `WorkManager` + chỉ upload file sách qua Wi-Fi).

### 20. P10 — Tối ưu Wallpaper, Sync nền & tiết kiệm dữ liệu di động — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **Wallpaper tạo lại `Paint`, `Typeface` & vẽ ~1700 điểm grain mỗi frame:** `EInkWallpaperService.kt` cache sẵn `baseTypeface`, `boldTypeface`, `normalTypeface` và toàn bộ 11 `Paint` trên `Engine` (chỉ cập nhật khi `w` hoặc `fgColor` đổi qua `ensurePaints`); thay vòng lặp vẽ grain mỗi frame bằng `grainTileBitmap` (`256×256`) + `BitmapShader` (`TileMode.REPEAT`), vẽ toàn màn hình trong 1 lệnh `drawRect` và recycle trong `onDestroy()`; downsample cover `inSampleSize = 2`, `RGB_565` trong `loadCachedCover`.
- **Sync gọi `fetchUserProfile` mỗi lần & upload JSON dù không đổi:** `GoogleDriveSyncManager.kt` chỉ gọi `fetchUserProfile(token)` khi `email` đang trống; so sánh `mergedPayload` với `remote` (bỏ qua `updatedAt`) trước khi gọi `uploadSyncJson`.
- **Sync nền không backoff & upload sách qua 4G mặc định:** `KEY_SYNC_BOOKS` mặc định `false` cho user mới; `runBackgroundSyncOnce()` kiểm tra `isUnmeteredNetwork()` (`!cm.isActiveNetworkMetered`) trước khi tải/đăng file sách và áp dụng exponential backoff (`20s * 2^failures`, tối đa 10 phút) khi lỗi liên tiếp.
- **PDFBox + Pdfium:** cả hai vẫn được giữ nguyên vì `PdfPageAnalyzer` dùng PDFBox để trích xuất chữ/vector cho reflow & phân loại, còn `ReadiumEngine` dùng Pdfium cho metadata/positions.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay trên thiết bị/emulator: bật Live Wallpaper kiểm tra chữ/grain/cover; sync nền khi không đổi dữ liệu không upload lại JSON, khi dùng mạng di động không tự upload file sách.

**Còn để lại:** hết Đợt 3. Sync nền hiện dùng `backgroundScope` + exponential backoff (chưa kéo thêm dependency `WorkManager` nên sẽ không chạy khi process bị OS kill hẳn).

**Bước tiếp theo:** sang Đợt 4 — mục 21 (U1): giữ trạng thái Thư viện (tab, bộ lọc, tìm kiếm, vị trí cuộn, view mode) khi mở màn Thống kê hoặc xoay màn hình.

### 21. U1 — Giữ trạng thái Thư viện khi mở Thống kê & xoay màn hình — 2026-11-19
**Nguyên nhân:** `Navigation.kt` dùng biến `statsOpen` + `if/else` để tráo `LibraryScreen` và `ReadingStatsScreen` trong cùng một entry `Library`, làm `LibraryScreen` bị huỷ khỏi composition và mất toàn bộ tab, tác giả, từ khoá tìm kiếm, vị trí cuộn mỗi lần mở Thống kê; đồng thời `selectedTab`, `selectedAuthor`, `query` trong `LibraryScreen` chỉ dùng `remember` nên mất khi xoay màn hình hoặc process death.

**Đã sửa:**
- `NavigationKeys.kt`: thêm `@Serializable data object ReadingStats : NavKey`.
- `Navigation.kt`: tách `ReadingStatsScreen` thành `entry<ReadingStats>` riêng trong `NavDisplay` (dùng chung `LibraryViewModel`); mở Thống kê đẩy `ReadingStats` vào `backStack`, quay lại pop khỏi `backStack` nên Nav3 tự giữ nguyên trạng thái & vị trí cuộn của `Library`.
- `LibraryScreen.kt`: đổi `selectedTab`, `selectedAuthor`, `query` sang `rememberSaveable`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay trên thiết bị/emulator: chọn tab, lọc tác giả, gõ tìm kiếm, cuộn danh sách sách → mở Thống kê → bấm Back → mọi trạng thái và vị trí cuộn của Thư viện vẫn giữ nguyên.

**Còn để lại:** không có.

**Bước tiếp theo:** mục 22 — U2 + U3: PDF zoom không chớp trắng, smart tap-to-zoom cả khi bật `autoCrop`, lật trang khi đang zoom, placeholder/lỗi/retry, pinch zoom quanh ngón tay & thông báo lỗi mở PDF rõ ràng.

### 22. U2 + U3 — PDF zoom mượt, tap-zoom, lật trang khi zoom & placeholder/retry — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **Zoom qua 1.35× chớp trắng (U2):** `PdfPagerReader.kt` bỏ `renderMultiplier` khỏi key của `remember` giữ `bitmap`, để ảnh cũ tiếp tục hiển thị trong lúc render bản độ phân giải cao hơn (nâng `renderMultiplier` lên `3` khi `scale > 2.4f`, vẫn bị giới hạn an toàn bởi `PdfBitmapBudget`).
- **Smart tap-to-zoom bị tắt khi `autoCrop = true`:** thêm `PdfRendererHolder.cachedCropRect(index)` để quy đổi toạ độ chạm trên ảnh đã crop về toạ độ trang gốc, giúp smart tap-to-zoom hoạt động cả khi bật `autoCrop`.
- **Không lật trang được khi đang zoom:** bỏ nhánh chặn trong `onTap` khi `scale > 1.01f` để chạm cạnh trái/phải 25% luôn lật trang bình thường.
- **Pinch zoom quanh tâm view:** dùng `event.calculateCentroid(useCurrent = false)` tính độ lệch tâm để giữ điểm dưới ngón tay cố định khi phóng to/thu nhỏ.
- **Thiếu placeholder/lỗi/retry:** thêm `renderFailed` + `retryNonce`, hiển thị `CircularProgressIndicator` khi đang tải và thông báo `pdf_page_render_error` kèm nút `action_retry` (đã thêm ở `values/strings.xml` & `values-vi/strings.xml`) khi render lỗi.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay trên thiết bị/emulator: pinch zoom quá 1.35×/2.4× (không chớp trắng, nét hơn), double-tap khi bật `autoCrop`, tap cạnh trái/phải để lật trang khi đang zoom.

**Còn để lại:** chưa phân biệt riêng thông báo lỗi PDF có mật khẩu/hỏng khi mở file (`pdf_load_error`) và giữ nguyên `autoCrop` theo từng trang.

**Bước tiếp theo:** mục 23 — U4: thông báo lỗi sync localized (phân biệt offline, hết phiên/401, Drive đầy) và trạng thái "Kết nối lại Google Drive" khi background sync gặp `hasResolution()`.

### 23. U4 — Thông báo lỗi sync localized & trạng thái "Kết nối lại Google Drive" — 2026-11-19
**Nguyên nhân:** `GoogleDriveSyncManager` trả thẳng `err.localizedMessage` (tiếng Anh thô như "Unable to resolve host...", "Drive upload failed (401)") và không phân biệt offline / timeout / 401 hết phiên / Drive đầy; khi background sync gặp `hasResolution()` thì im lặng dừng mà không báo UI.

**Đã sửa:**
- `SyncModels.kt`: thêm `needsReconnect: Boolean = false` vào `SyncUiState`.
- `GoogleDriveSyncManager.kt`:
  - Thêm `DriveHttpException(statusCode, message)` và `formatSyncError(err)` ánh xạ lỗi mạng (`UnknownHostException`/`ConnectException`), timeout (`SocketTimeoutException`), HTTP 401/403/507/429/5xx và `ApiException` sang các chuỗi đã localize (`sync_error_offline`, `sync_error_timeout`, `sync_error_auth_expired`, `sync_error_quota_or_permission`, `sync_error_server`, `sync_error_http`, `sync_needs_reconnect`, `sync_reconnect_btn` trong `values/strings.xml` & `values-vi/strings.xml`).
  - Khi `runBackgroundSyncOnce()` gặp `hasResolution()` hoặc khi sync nhận HTTP 401, bật `needsReconnect = true` và đặt thông báo `sync_needs_reconnect` / `sync_error_auth_expired`; reset `needsReconnect = false` khi sync thành công hoặc đăng xuất.
- `ReadingStatsSheet.kt`: khi `syncState.needsReconnect == true`, nút hành động hiển thị `sync_reconnect_btn` ("Kết nối lại Google Drive").

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay trên thiết bị/emulator: tắt mạng bấm Sync báo "Không có kết nối mạng", thu hồi quyền Drive thấy trạng thái & nút "Kết nối lại Google Drive".

**Còn để lại:** không có.

**Bước tiếp theo:** mục 24 — U5: quyền thông báo, notification màn khoá không đăng lại sau khi user gạt, tên channel theo ngôn ngữ, focus mode xin quyền DND từ settings panel, không nuốt lỗi intent setup/DND/dịch ngoài.

### 24. U5 — Quyền thông báo, notification màn khoá, Focus mode & báo lỗi intent — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **Xin `POST_NOTIFICATIONS` mỗi lần mở sách & không tắt toggle khi từ chối:** `ReaderActivity.kt` thêm dialog giải thích (`notif_rationale_*`) trước khi xin quyền lần đầu (`eink_permissions/notif_asked_once`); nếu người dùng từ chối hoặc đã từ chối trước đó thì tự tắt `lockScreenReader = false`.
- **Notification màn khoá tự đăng lại sau khi user gạt:** `LockScreenReadingNotifier.kt` gắn `setDeleteIntent` (`LockScreenNavReceiver.ACTION_DISMISSED`) để lưu `dismissed_by_user`; `EInkWallpaperService.ensureLockScreenNotificationOnLock()` bỏ qua đăng lại nếu user đã gạt (cờ này tự xoá khi app đăng phiên đọc mới).
- **Tên channel không cập nhật theo ngôn ngữ:** `LockScreenReadingNotifier.ensureChannel` cập nhật `existing.name` khi `existing != null`.
- **Focus mode từ `ReaderSettingsPanel` bỏ qua nhắc quyền DND:** tách `handleToggleFocusMode` trong `ReaderScreen.kt` và truyền vào `ReaderSettingsPanel` (`ReaderSettingsPanel.kt`).
- **Nuốt lỗi `startActivity`:** `EInkSystemSetupHelper` (`openHomeLauncherSettings`, `openLiveWallpaperPicker`) và `FocusModeController.openDndPermissionSettings` hiện `Toast` (`error_open_system_settings`) khi thất bại; `ReaderScreen.kt` hiện snackbar `error_open_browser` khi không mở được trang dịch ngoài.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay trên thiết bị/emulator: từ chối quyền thông báo ở Reader, gạt bỏ notification màn khoá rồi khoá/mở lại màn hình, bật Focus mode từ Settings panel khi chưa cấp quyền DND.

**Còn để lại:** không có.

**Bước tiếp theo:** mục 25 — U6: dialog ghi chú highlight không mất text khi chạm ngoài/back, checkbox xoá trên Drive mặc định tắt + undo, `TutorialOverlay` chặn touch không xuyên xuống dưới, thông báo lỗi mở sách thân thiện + nút thử lại.

### 25. U6 — An toàn thao tác nhập liệu, xoá sách, TutorialOverlay & lỗi mở sách — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **Dialog ghi chú highlight mất nội dung khi chạm ngoài:** `HighlightNoteDialog.kt` đổi sang `rememberSaveable` cho `note` và đặt `DialogProperties(dismissOnClickOutside = false)`; `ReaderActivity.kt` hiển thị `Toast` xác nhận (`reader_highlight_saved`) khi highlight nhanh.
- **Dialog xoá sách mặc định xoá luôn trên Drive:** `LibraryScreen.kt` đổi mặc định `deleteFromCloud = false` để tránh xoá nhầm bản sao lưu đám mây.
- **`TutorialOverlay` cho bấm xuyên xuống màn hình dưới:** `TutorialOverlay.kt` thêm `.clickable(indication = null) {}` lên `Box` gốc để chặn toàn bộ touch xuyên qua.
- **Lỗi mở sách hiện chuỗi kỹ thuật thô của Readium & không có nút thử lại:** `ReaderViewModel.kt` kiểm tra `!file.exists()`, thay thông báo lỗi mở sách bằng chuỗi đã localize (`reader_cannot_open_book` / `reader_book_not_found`) và thêm `retryLoad()`; `ReaderActivity.kt` thêm nút `action_retry` vào `CenteredMessage`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay trên thiết bị/emulator: gõ ghi chú rồi chạm ngoài dialog (không đóng), mở dialog xoá sách (checkbox Drive mặc định tắt), chạm vùng trống trên TutorialOverlay (không bấm xuyên), mở sách lỗi thấy nút Thử lại.

**Còn để lại:** chưa hỗ trợ hoàn tác (undo) khôi phục file sách sau khi đã xoá cục bộ.

**Bước tiếp theo:** mục 26 — U7: Accessibility (`contentDescription`, `minimumInteractiveComponentSize()`, `Role.Tab`/`selected`/`stateDescription`, semantics cho biểu đồ/heatmap).

### 26. U7 — Accessibility (`contentDescription`, touch target 48dp, `semantics`) — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **Thiếu `contentDescription` & touch target < 48dp:**
  - `ReaderBars.kt`: bổ sung `contentDescription` và nâng vùng chạm tối thiểu `48.dp` cho nút quay lại (`‹`), `A−`, `A+`, tìm kiếm, bookmark (kèm `stateDescription` `Đã đánh dấu`/`Chưa đánh dấu`) và các ô chọn tone (`Role.RadioButton`, `selected`, `48.dp` hit target).
  - `LibraryScreen.kt`: thêm `contentDescription` (`cd_dismiss_banner`) và `48.dp` min size cho nút `✕` banner; thêm `Role.Tab` + `selected` cho `EInkTabRow`.
  - `ReadingStatsSheet.kt`: thêm `contentDescription` + `48.dp` min size cho nút quay lại (`‹`) và nút `−5`/`+5` (`GoalStepCircleButton`); thêm `Role.Tab` + `selected` cho tab Tổng quan / Cài đặt.
  - `notification_lockscreen_nav.xml`: nâng kích thước nút `btn_prev` / `btn_next` lên `48dp × 48dp` và thêm `android:contentDescription`.
  - `PdfPagerReader.kt`: thay chuỗi cứng `"Trang ..."` bằng `stringResource(R.string.cd_pdf_page, pageIndex + 1)`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay với TalkBack trên thiết bị/emulator.

**Còn để lại:** chưa thêm `semantics` mô tả dữ liệu chi tiết cho biểu đồ cột/heatmap và custom action lật trang cho tap zone PDF.

**Bước tiếp theo:** mục 27 — U8 + U9: Launcher pins, empty state có nút Import, sort thư viện, xử lý dải 24dp cạnh trái Reader, phím âm lượng tôn trọng dialog & setting tắt/bật, đồng hồ 12h/24h.

### 27. U8 + U9 — Launcher pins, Thư viện empty/sort & tinh chỉnh Reader — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **Launcher pins bị cắt ở 4 app đầu (`LauncherActivity.kt`):** bỏ `pinnedApps.take(4)`, hiển thị toàn bộ app đã ghim theo từng hàng 4 ô (`pinnedApps.chunked(4)`).
- **Empty state Thư viện & Sort (`LibraryScreen.kt`):** thêm nút `lib_import_first_book` ("+ Nhập sách") ở `EmptyLibrary` khi chưa có sách; thêm `LibrarySort` (`RECENT`, `TITLE`, `PROGRESS`) lưu bằng `rememberSaveable` và nút chuyển ở thanh lọc.
- **Fallback tiếng Việt cứng (`LibraryViewModel.kt`):** đổi chuỗi fallback khi `context == null` sang tiếng Anh trung tính.
- **Dải 24dp cạnh trái Reader (`ReaderScreen.kt`):** thu hẹp xuống `14.dp` và bỏ `detectTapGestures` khỏi dải này để không cướp thao tác chạm/chọn chữ (vẫn giữ kéo dọc chỉnh độ sáng).
- **Phím âm lượng (`EInkSettings.kt`, `EInkSettingsStore.kt`, `ReaderSettingsPanel.kt`, `ReaderActivity.kt`):** thêm tuỳ chọn `volumeButtonsTurnPages` (mặc định bật) trong Cài đặt Reader; `onKeyDown` / `onKeyUp` không lật trang khi setting tắt hoặc khi đang mở dialog ghi chú / từ điển.
- **Nút Search PDF (`ReaderActivity.kt`, `ReaderScreen.kt`):** truyền `isSearchable = current.publication.isSearchable` để ẩn nút tìm kiếm khi tài liệu không hỗ trợ.
- **Đồng hồ 12h/24h (`ReaderFooterBar.kt`, `EInkWallpaperService.kt`):** dùng `DateFormat.getTimeFormat` / `DateFormat.is24HourFormat` theo cài đặt hệ thống và căn `delay` footer theo đầu phút kế tiếp.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay trên thiết bị/emulator: ghim > 4 app ở Launcher, đổi sort thư viện, tắt phím âm lượng hoặc mở dialog từ điển rồi bấm volume, đổi định dạng 12h/24h hệ thống.

**Còn để lại:** không có.

**Bước tiếp theo:** mục 28 — U10 + U11 (mục cuối): xử lý chuỗi cứng còn sót, thêm `<plurals>` cho streak/active days và cập nhật `docs/privacy-policy.md` khớp với code.

### 28. U10 + U11 — Localization (`plurals`, chuỗi cứng) & Privacy Policy — 2026-11-19
**Nguyên nhân & Đã sửa:**
- **Chuỗi cứng & thiếu `<plurals>` (U10):**
  - `values/strings.xml` & `values-vi/strings.xml`: thêm `<plurals name="stats_xp_streak_plurals">` (phân biệt `1 day` / `N days` ở tiếng Anh) và `dict_no_data`, `dict_no_definition`.
  - `ReadingStatsSheet.kt`: dùng `pluralStringResource(R.plurals.stats_xp_streak_plurals, ...)`.
  - `DictionaryService.kt`: thay `"Không có dữ liệu từ điển"` / `"Không có định nghĩa"` bằng `str(R.string.dict_no_data, ...)` / `str(R.string.dict_no_definition, ...)`.
  - `widget_eink_reading.xml`: thay `"INKIFY"` cứng bằng `@string/app_name` + `textAllCaps="true"`.
- **Privacy policy lệch với code (U11):**
  - `docs/privacy-policy.md` (cả tiếng Việt & tiếng Anh): bổ sung danh sách API bên thứ ba (`api.dictionaryapi.dev`, `translate.googleapis.com`, `api.mymemory.translated.net`), ghi rõ đồng bộ file sách mặc định tắt và chỉ qua Wi-Fi ở nền, nêu Android Auto Backup và lưu ý về thiết bị khác còn đăng nhập khi xóa dữ liệu Drive.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`).

**Kết thúc kế hoạch:** 28/28 mục trong `docs/fix-plan.md` đã hoàn thành (`[x]`). Còn lại việc test tay các luồng UI trên máy thật/emulator và `lintDebug` nếu cần trước khi phát hành.

### 29. H1 — PDF Reflow không kẹt spinner, bắt lỗi theo từng trang & mở toolbar khi chờ — 2026-11-19
**Nguyên nhân:**
- `PdfPageAnalyzer.analyzeAllPages` (cùng `analyzePages`, `analyzeSamplePages`) bọc toàn bộ vòng lặp trong một `runCatching` → chỉ cần 1 trang bị lỗi parse hoặc `OutOfMemoryError` là cả hàm trả về `emptyList()`.
- Trong `PdfPagerReader.kt`, khi `computed` trả về rỗng, `allAnalyses` không được gán nên `loadedAnalyses` giữ `null` mãi → kẹt ở màn hình spinner "Đang mở sách…".
- Cả 2 màn hình spinner chờ (`loadedAnalyses == null` và `reflowScreensState == null`) đều không gắn `pointerInput` cho `onCenterTap`, trong khi `chromeVisible` mặc định `false`, khiến người dùng không thể mở thanh công cụ để thoát hoặc đổi chế độ đọc (và vì `HYBRID_REFLOW` đã lưu nên mở lại sách vẫn kẹt).

**Đã sửa:**
- `reader/pdf/PdfPageAnalyzer.kt`: thêm `emptyPageAnalysis(pageIndex)` và `analyzePageSafely(pageIndex)` bắt `Throwable` (trừ `CancellationException` được rethrow) theo **từng trang** trong `analyzeAllPages`, `analyzePages`, `analyzeSamplePages`. Trang lỗi trả về `PdfPageAnalysis` rỗng để `PdfReflowEngine` tự chuyển trang đó sang `FallbackOriginal` thay vì làm hỏng cả cuốn sách; nếu `PDDocument.load(file)` lỗi ngay từ đầu thì trả về `emptyList()`.
- `ui/reader/PdfPagerReader.kt`:
  - Khi `isReflowMode` phân tích trả về rỗng nhưng coroutine vẫn `isActive`, gán `allAnalyses = emptyList()` để thoát trạng thái `loadedAnalyses == null` và tự động fallback xuống nhánh pager hiển thị trang gốc (`SMART_ORIGINAL` layout), giúp sách luôn đọc được.
  - Thêm `.pointerInput(onCenterTap) { detectTapGestures(onTap = { onCenterTap() }) }` vào `Box` gốc của cả 2 màn hình spinner (`loadedAnalyses == null` và `reflowScreensState == null`) để chạm màn hình luôn bật/tắt được thanh công cụ.
- `test/.../reader/pdf/PdfReflowEngineTest.kt`: thêm 2 unit test `analyzePageSafelyReturnsEmptyAnalysisOnErrorAndFallsBackToOriginal` và `analyzePageSafelyRethrowsCancellationException`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`). Kiểm tra tay: mở PDF nặng hoặc lỗi ở chế độ Reflow → trong lúc spinner quay chạm vào màn hình mở được toolbar; nếu PDFBox không đọc được file thì tự hiển thị trang gốc thay vì quay spinner vĩnh viễn.

**Còn để lại:** không có.

**Bước tiếp theo:** mục 30 — H3: Bookmark EPUB so theo `href` + khoảng progression của trang hiện tại (dùng `PaginationListener`), chỉ so `position` cho PDF/CBZ/fixed-layout.

### 30. H3 — Bookmark EPUB khớp chính xác theo khoảng progression của trang hiện tại — 2026-11-19
**Nguyên nhân:** `ReaderViewModel.findMatchingBookmarkParsed` kiểm tra nhánh `locPos != null && entryPos != null -> locPos == entryPos` trước nhánh `progression`. Locator EPUB của Readium luôn có `position` (mỗi `position` = 1024 byte nén, tương ứng ~2–4 trang màn hình) nên nhánh `progression` không bao giờ chạy; biểu tượng bookmark sáng trên nhiều trang liên tiếp và bấm ở trang kề xoá nhầm bookmark của trang khác.

**Đã sửa:**
- Mới `ui/reader/BookmarkMatcher.kt` (thuần Kotlin, kèm `BookmarkMatchMode`, `BookmarkLocation`, `EpubPageWindow`):
  - `matchModeFor(format, isFixedLayout)`: chỉ dùng `REFLOWABLE_EPUB` khi `format == BookFormat.EPUB && !isFixedLayout`; dùng `POSITION_BASED` cho PDF, CBZ và EPUB fixed-layout (`EpubLayout.FIXED`).
  - Với `REFLOWABLE_EPUB`: khớp theo `href` + khoảng `progression` của trang hiện tại `[start, start + 1 / totalPages)` (dựa trên `totalPages` từ `PaginationListener.onPageChanged`, tính kèm epsilon biên `min(1e-6, pageSpan * 0.01)` để tránh sai số dấu phẩy động ở ranh giới trang kế và đóng khoảng `[start, 1.0]` ở trang cuối chương). Nếu chưa có thông tin trang (`totalPages <= 0` hoặc `pageWindow` chưa khớp locator hiện tại), fallback về dung sai `0.002` trên `progression` (không dùng `position`).
  - Với `POSITION_BASED` (PDF, CBZ, EPUB fixed-layout): ưu tiên khớp `sameHref && locPos == entryPos`, fallback `progression` (`< 0.002`) / `totalProgression` (`< 0.002f`).
- `ReaderActivity.kt`: gắn `EpubNavigatorFragment.PaginationListener` vào `EpubNavigatorFactory.createFragmentFactory` trong `epubFragmentFactory`, chuyển `(totalPages, locator)` sang `viewModel.onEpubPageChanged(totalPages, locator)`.
- `ui/reader/ReaderViewModel.kt`: xác định `bookmarkMatchModeFlow` theo `book.format` và `pub.metadata.presentation.layout == EpubLayout.FIXED` trong `load()`; lưu `epubPageWindowFlow` từ `onEpubPageChanged`, kết hợp vào `isCurrentPageBookmarked` và `findMatchingBookmark`, và dùng `BookmarkMatcher.matches(...)`.
- Mới `test/.../ui/reader/BookmarkMatcherTest.kt`: 8 unit test bao phủ chọn chế độ matching, EPUB reflowable không khớp nhầm trang kề cùng `position`, khớp bookmark nằm giữa khoảng trang sau khi đổi cỡ chữ, trang cuối chương, khác `href`, fallback khi thiếu/lệch thông tin trang, và `POSITION_BASED` cho PDF/CBZ/EPUB fixed-layout.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (79/79 tests pass, gồm 8/8 test trong `BookmarkMatcherTest`). Kiểm tra tay: mở EPUB cỡ chữ lớn, đặt bookmark trang N → lật sang N+1/N+2 icon không sáng và bấm bookmark ở N+1 tạo bookmark mới thay vì xoá bookmark trang N.

**Còn để lại:** không có.

**Bước tiếp theo:** mục 31 — M1 + M2 + L3: Notification màn khoá (`deleteIntent`/PREV/NEXT tường minh, snippet rỗng, `capturedAt`, `stepSnippet`, `LockScreenReadingStore` tuần tự).

### 31. M1 + M2 + L3 — Notification màn khoá, giữ vị trí chính xác & ghi session tuần tự — 2026-11-19
**Nguyên nhân:**
- **M1:** `LockScreenReadingNotifier.show` tạo `deleteIntent` bằng implicit `Intent(ACTION_DISMISSED).setPackage(pkg)`, trong khi `<intent-filter>` của `LockScreenNavReceiver` trong `AndroidManifest.xml` chỉ khai báo `LOCKSCREEN_PREV` và `LOCKSCREEN_NEXT` → broadcast khi người dùng gạt bỏ notification không bao giờ tới receiver, `dismissed_by_user` luôn `false`, khiến `EInkWallpaperService` đăng lại notification mỗi lần bật màn hình khoá.
- **M2:** Khi `extractLockScreenSnippets` trả về danh sách rỗng (chương chỉ có ảnh, PDF scan không có text layer, hoặc publication đã đóng trước khi coroutine chạy), `ReaderActivity.postLockScreenNotification` thoát sớm (`return@launch`) và giữ nguyên session cũ (có thể của chương trước hoặc sách khác). Khi người dùng bấm Prev/Next hoặc chạm mở từ notification cũ: (1) `LockScreenReadingNotifier` tạo `ReaderActivity.intentFor` với `EXTRA_LOCATOR_CREATED_AT = System.currentTimeMillis()` lúc dựng notification (ví dụ từ wallpaper khi bật màn hình) nên locator cũ thắng `book.updatedAt` mới hơn; (2) bấm Prev ở snippet đầu hoặc Next ở snippet cuối trong `LockScreenReadingStore.stepSnippet` vẫn trả về `current` thay vì `null`, làm `LockScreenNavReceiver` gọi `library.saveProgress` với locator cũ và `updatedAt = now`, ghi đè lùi tiến độ đọc và đẩy lên cloud qua sync.
- **L3:** `LockScreenReadingStore` dùng `Dispatchers.IO` không giới hạn độ song song (mỗi lần `saveToDisk` / `clear` là một `launch` riêng tranh lock `synchronized`), không đảm bảo thứ tự thực thi giữa các coroutine → snapshot cũ có thể ghi đè sau hoặc làm hồi sinh file sau `clear()`; `stepSnippet` đọc-sửa-ghi `_state.value` không nguyên tử.

**Đã sửa:**
- `reader/LockScreenReadingNotifier.kt` & `AndroidManifest.xml`:
  - Đổi cả 3 `PendingIntent` (`ACTION_PREV`, `ACTION_NEXT`, `ACTION_DISMISSED`) sang explicit intent `Intent(context, LockScreenNavReceiver::class.java).setAction(...)`, đồng thời bổ sung `<action android:name="com.example.emptyapp.LOCKSCREEN_DISMISSED" />` vào `<intent-filter>` của `LockScreenNavReceiver`.
  - Thêm tham số `locatorCreatedAt: Long = 0L` vào `LockScreenReadingNotifier.show(...)` và truyền xuống `ReaderActivity.intentFor(...)`.
- `ReaderActivity.kt`, `reader/LockScreenNavReceiver.kt`, `system/EInkWallpaperService.kt`:
  - `ReaderActivity.intentFor` nhận thêm `locatorCreatedAt: Long = System.currentTimeMillis()` để giữ nguyên thời điểm chụp locator thực tế.
  - Trong `ReaderActivity.postLockScreenNotification`: chụp `capturedAt` và `locatorJson` ngay khi lấy locator hiện tại; nếu `extractLockScreenSnippets` trả về rỗng thì cập nhật session với 1 snippet fallback (tiêu đề chương từ locator/TOC hoặc tên sách) kèm locator hiện tại để luôn ghi đè session cũ (hoặc `clear()` + `cancel()` nếu không có cả tên sách), và truyền `locatorCreatedAt = lockState.capturedAt` khi hiển thị notification.
  - `LockScreenNavReceiver` và `EInkWallpaperService.ensureLockScreenNotificationOnLock` truyền `locatorCreatedAt = lockState.capturedAt` vào `LockScreenReadingNotifier.show`.
- `reader/LockScreenReadingStore.kt`:
  - Thêm `capturedAt: Long = System.currentTimeMillis()` vào `LockScreenReadingState`.
  - Đổi `ioScope` sang `Dispatchers.IO.limitedParallelism(1)` và dùng `stateLock` kết hợp `_state.update { ... }`; hàm `schedulePersistSnapshot()` luôn đọc snapshot mới nhất của `_state.value` trên kênh tuần tự để ghi hoặc xoá file, đảm bảo `clear()` và `updateSession()` không bao giờ bị ghi đè ngược thứ tự.
  - `stepSnippet(delta)` trả về `null` (và không ghi đĩa) khi `current == null`, `snippets.isEmpty()`, hoặc `nextIdx == current.currentSnippetIndex` (đang ở biên đầu/cuối); cập nhật progression trong `locatorJson` bằng `kotlinx.serialization.json` thuần Kotlin và làm mới `capturedAt` khi bước chuyển thành công.
- Mới `test/.../reader/LockScreenReadingStoreTest.kt`: 3 unit test kiểm tra `updateSession` (clamp index, lưu & reload `capturedAt`), `stepSnippet` (trả `null` ở biên đầu/cuối và khi snippet rỗng, cập nhật progression & `capturedAt` khi bước hợp lệ), và `clear` (xoá file trên đĩa, không hồi sinh dữ liệu cũ).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest` pass toàn bộ (82/82 tests pass, gồm 3/3 test mới trong `LockScreenReadingStoreTest`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 32 — Lint gate: sửa 24 `LocalContextGetResourceValueCall` + `MissingPermission`, cấu hình `bundle.language.enableSplit = false`, dọn warning/error lint để `lintDebug` xanh.

### 32. Lint gate — sửa `LocalContextGetResourceValueCall`, `MissingPermission`, `bundle.language.enableSplit`, kiểm `bcpkix` & dọn resource — 2026-11-19
**Nguyên nhân:**
- 24 lỗi `LocalContextGetResourceValueCall`: gọi `context.getString(...)` trực tiếp trong `@Composable` (`ReaderScreen.kt`, `LibraryScreen.kt`, `ReadingStatsSheet.kt`, `LauncherActivity.kt`) không tự invalidate khi đổi `Configuration`.
- 1 lỗi `MissingPermission`: `LockScreenReadingNotifier.show()` gọi `NotificationManagerCompat.from(context).notify(...)` mà chưa kiểm tra `areNotificationsEnabled()` và `checkSelfPermission(POST_NOTIFICATIONS)` trực tiếp tại nơi gọi.
- `AppBundleLocaleChanges` (`LocaleHelper.kt:33`): app cho phép đổi ngôn ngữ động nhưng `app/build.gradle.kts` chưa tắt tách ngôn ngữ trong AAB (`bundle.language.enableSplit = false`).
- `TrustAllX509TrustManager` ×3 trong `bcpkix-jdk15to18-1.72` (dependency transitively từ `pdfbox-android` dùng `org.bouncycastle.cert`/`cms`/`tsp` để đọc chữ ký/mã hoá PDF).
- `HardcodedText` `"‹"` / `"›"` trong `res/layout/notification_lockscreen_nav.xml`, `SmallSp` (`10sp`) trong `res/layout/widget_eink_reading.xml`, và 40 `UnusedResources` trong `values/strings.xml` & `values-vi/strings.xml`.

**Đã sửa:**
- `ui/reader/ReaderScreen.kt`, `ui/library/LibraryScreen.kt`, `ui/library/ReadingStatsSheet.kt`, `LauncherActivity.kt`: chuyển toàn bộ 24 lệnh `context.getString(...)` trong composable sang `stringResource(...)` resolve ở cấp composable rồi truyền vào callback/lambda.
- `reader/LockScreenReadingNotifier.kt`: kiểm tra `notificationManager.areNotificationsEnabled()` và `ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED` (API 33+) trước khi gọi `notificationManager.notify(...)`.
- `app/build.gradle.kts` & `LocaleHelper.kt`: thêm `bundle { language { enableSplit = false } }` để AAB luôn giữ đủ `values` và `values-vi`, kèm `@SuppressLint("AppBundleLocaleChanges")` trên `LocaleHelper.wrap`.
- Kiểm `bcpkix-jdk15to18-1.72`: đối chiếu `app/build/outputs/mapping/release/mapping.txt` xác nhận R8 đã strip hoàn toàn `org.bouncycastle.est.ESTService` và các class `TrustAll` khỏi APK release (chỉ giữ `org.bouncycastle.cms`/`cert`/`operator` mà `pdfbox-android` cần để mở PDF mã hoá); thêm `lint { disable += "TrustAllX509TrustManager" }` trong `app/build.gradle.kts`.
- `res/layout/notification_lockscreen_nav.xml`: thay `"‹"` / `"›"` cứng bằng `@string/lockscreen_nav_prev_symbol` và `@string/lockscreen_nav_next_symbol`.
- `res/layout/widget_eink_reading.xml`: nâng cỡ chữ `widget_badge` từ `10sp` lên `11sp`.
- `res/values/strings.xml` & `res/values-vi/strings.xml`: xoá 40 chuỗi `UnusedResources` không còn sử dụng (`wallpaper_streak`, `settings_language`, `sync_auth_failed`, `stats_xp_streak_format`, `lockscreen_action_prev/next`, `onboard_toggle_volume`, và bộ `tutorial_header`/`tutorial_s1..s4_*` cũ đã thay bằng `onboard_*`).

**Cách kiểm tra:** `.\gradlew.bat lintDebug testDebugUnitTest` pass toàn bộ (`BUILD SUCCESSFUL`, `lintDebug`: **0 errors**, `testDebugUnitTest`: **82/82 pass**).

**Còn để lại:** các mục `PluralsCandidate` và `ConstantLocale` sẽ được xử lý ở Story 55 (L24 + L25).

**Bước tiếp theo:** mục 33 — H2: `clearToken` sau revoke và khi gặp 401 rồi `authorize()` lại 1 lần.

### 33. H2 — `clearToken` sau revoke, phát hiện 401 & tự động `authorize()` lại 1 lần — 2026-11-19
**Nguyên nhân:**
- `signOut` và `deleteAllCloudData` chỉ thu hồi token qua HTTP POST tới `oauth2.googleapis.com/revoke` mà không xoá token khỏi cache cục bộ của Google Play Services (`clearToken` / `revokeAccess`). Khi đăng nhập lại, `AuthorizationClient.authorize()` tiếp tục trả về token đã chết từ cache mà không có `hasResolution()`, gây vòng lặp HTTP 401 → `needsReconnect` → "Kết nối lại" vẫn nhận lại chính token cũ.
- Khi các thao tác Drive (`completeSyncWithToken`, `deleteBookFromCloud`, `deleteAllCloudData`, `runBackgroundSyncOnce`) gặp 401 — kể cả ở `httpDeleteFile`, `downloadFileTo`, hoặc `uploadBinaryFileToAppData` — app chưa xoá token chết và chưa thử `authorize()` lại để lấy token mới hoặc `pendingIntent` xin cấp quyền lại.

**Đã sửa:**
- `gradle/libs.versions.toml`: nâng `playServicesAuth` từ `21.3.0` lên `21.4.0` (bổ sung `AuthorizationClient.clearToken(ClearTokenRequest)` và `AuthorizationClient.revokeAccess(RevokeAccessRequest)`).
- Mới `data/sync/DriveAuthRetry.kt`:
  - `DriveAuthRetry.isAuthExpiredError`: nhận diện `DriveHttpException(401)` và `DriveRemoteReadException(statusCode = 401)` (gồm cả chuỗi `cause`), đồng thời giữ `statusCode` trên `DriveRemoteReadException`.
  - `DriveAuthRetry.executeWithSingle401Retry`: khi thao tác gặp 401 thì gọi `clearToken` cho token lỗi, gọi `authorize()` lại đúng **1 lần**, trả về `NeedsResolution` nếu cần cấp quyền lại, từ chối retry nếu token mới rỗng hoặc trùng token cũ, và thử chạy lại thao tác với token mới tối đa **1 lần** (nếu lần 2 vẫn 401 thì xoá nốt token mới và báo `authExpired`).
- `data/sync/GoogleDriveSyncManager.kt`:
  - Thêm `clearCachedAccessToken(token)` (gọi `AuthorizationClient.clearToken(ClearTokenRequest)` kết hợp fallback `GoogleAuthUtil.clearToken(appContext, token)` trên `Dispatchers.IO`, bọc bắt lỗi giữ `CancellationException`), `revokeClientAccess` (`AuthorizationClient.revokeAccess` với `Account` và scopes đang dùng), và `revokeAndClearSession`.
  - `signOut()` và `deleteAllCloudData()` sau khi revoke đều gọi `clearCachedAccessToken` và `revokeClientAccess` (đối với `deleteAllCloudData()`, thu hồi/xoá đúng `usedToken` vừa dùng nếu vừa retry 401 thành công).
  - Áp dụng `executeWithSingle401Retry` cho `completeSyncWithToken` (cả foreground `signInAndSync`/`handleConsentResult` và `runBackgroundSyncOnce`), `deleteBookFromCloud`, `deleteAllCloudData`: khi `authorize()` trả về `hasResolution()` thì bật `needsReconnect = true`, đặt `statusMessage = sync_needs_reconnect` và trả `NeedsConsent(pendingIntent)` ở thao tác foreground; ở background sync đặt `needsReconnect = true`, `sync_needs_reconnect` và tăng backoff.
  - Cập nhật `httpDeleteFile`, `downloadFileTo` (kèm `try/finally` dọn file tạm ở `downloadMissingBooks`) và `uploadMissingBooks` ném `DriveHttpException(401, ...)` / propagate 401 thay vì nuốt mất mã 401.
- Mới `test/.../data/sync/DriveAuthRetryTest.kt`: 11 unit test bao phủ nhận diện 401 (`DriveHttpException`, `DriveRemoteReadException`, `cause`), không retry khi không phải 401, clear + `authorize()` + retry tối đa 1 lần, dừng ở lần 401 thứ hai, trả `NeedsResolution`, chặn retry khi token trùng/rỗng, xử lý lỗi `clearToken`/`authorize`, và rethrow `CancellationException`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, `testDebugUnitTest`: **93/93 pass**, gồm **11/11** test mới trong `DriveAuthRetryTest`; `lintDebug`: **0 errors**). Kiểm tra tay đề nghị: đăng xuất rồi đăng nhập lại ngay và đồng bộ; thu hồi quyền tại `myaccount.google.com` rồi bấm Sync → hiện "Kết nối lại" và mở màn hình cấp quyền mới.

**Còn để lại:** đồng bộ kiểm tra email/`sub` trước khi ghi và dọn logic `signOut`/`wipe` giữ `mutex` trước `Identity.signOut` sẽ xử lý tiếp ở mục 34 (M3 + M4) và mục 35 (M5 + L4).

**Bước tiếp theo:** mục 34 — M3 + M4: Import luôn ghi tombstone khôi phục; không xác định được email/`sub` thì dừng sync trước mọi thao tác ghi.

### 34. M3 + M4 — Import luôn ghi tombstone khôi phục & dừng sync khi không xác định được email/`sub` — 2026-11-19
**Nguyên nhân:**
- **M3:** `GoogleDriveSyncManager.unmarkDeletedHash` chỉ gọi `tombstones.markBooks(..., deleted = false)` khi `contentHash in tombstones.deletedBookHashes()`. Khi máy B chưa đồng bộ kể từ lúc máy A xoá sách khỏi cloud, `contentHash` chưa có trong tombstone local của B → import lại sách không ghi dấu khôi phục; lần sync tiếp theo gộp tombstone xoá từ cloud khiến sách bị coi là đã xoá và bị lọc bỏ. Ngoài ra `SyncTombstoneMerger.upsert` không cập nhật `changedAt` khi `key` đã tồn tại với `deleted = false` (ví dụ nhập lại sau một mốc khôi phục cũ hơn dấu xoá trên cloud).
- **M4:** Trong `syncOnceWithToken`, khi `state.value.email` đang trống, `fetchUserProfile(token)` bị bọc trong `runCatching` (nuốt lỗi HTTP/mạng/401) và bỏ qua luôn bước `tombstones.bindToAccount(...)` nếu `email` vẫn `null` (hoặc khi JSON `/oauth2/v3/userinfo` không có `email` dù có `sub`). Kết quả là tombstone của tài khoản A cũ còn lưu trên máy vẫn bị áp lên token của tài khoản B mới — có thể xoá nhầm `book_<hash>` trên Drive B và upload tombstone A lên B. Tương tự ở `deleteBookWithToken` nếu chưa bind tài khoản.

**Đã sửa:**
- `data/sync/GoogleDriveSyncManager.kt`:
  - `unmarkDeletedHash(contentHash)`: bỏ điều kiện `if (contentHash in tombstones.deletedBookHashes())`, luôn gọi `tombstones.markBooks(listOf(contentHash), deleted = false)` khi `contentHash` hợp lệ.
  - Thay `fetchUserProfile` + `runCatching` bằng `resolveAndBindAccount(token)` gọi qua `SyncAccountResolver.resolveAndBindAccount`, đặt ở đầu cả `syncOnceWithToken` và `deleteBookWithToken` trước mọi thao tác đọc/merge/ghi/xoá Drive. Nếu chưa có `email`/`accountId` đã lưu thì bắt buộc gọi `/oauth2/v3/userinfo` (lan truyền 401 cho `DriveAuthRetry`, lỗi mạng/HTTP hoặc thiếu cả `email` lẫn `sub` sẽ ném `DriveRemoteReadException("userinfo")` / exception tương ứng và dừng sync ngay); khi xác định được `accountId` (`email` hoặc fallback `sub`) thì gọi `tombstones.bindToAccount(accountId)` trước khi chạm dữ liệu Drive.
- `data/sync/SyncTombstoneMerger.kt`:
  - Cập nhật `upsert` để khi `deleted = false`: luôn tạo mới `SyncTombstone(key, now, deleted = false)` nếu chưa có trong `existing`, hoặc làm mới `changedAt = now` nếu bản ghi hiện tại có `changedAt < now` (vẫn giữ nguyên hành vi không đổi danh sách khi `deleted = true` đã tồn tại).
- Mới `data/sync/SyncAccountResolver.kt`:
  - `parseUserProfile(rawJson)` đọc `email` (fallback `sub`) và `name` bằng `kotlinx.serialization.json`; `resolveAndBindAccount(...)` kiểm tra/lấy định danh tài khoản và luôn bind tombstone trước khi tiếp tục.
- `test/.../data/sync/SyncTombstoneMergerTest.kt` & mới `test/.../data/sync/SyncAccountResolverTest.kt`:
  - Thêm 2 test trong `SyncTombstoneMergerTest` (import khi chưa có tombstone local thắng tombstone xoá cũ trên cloud; re-import làm mới timestamp `deleted = false`).
  - Thêm 8 test trong `SyncAccountResolverTest` bao phủ parse `email`/`sub`, dừng trước bind/ghi Drive khi userinfo lỗi hoặc thiếu định danh, giữ nguyên tombstone cũ khi lỗi, truyền 401 qua `DriveAuthRetry`, reset tombstone tài khoản A khi chuyển sang tài khoản B, và test tích hợp `SyncTombstoneStore` cho kịch bản M3.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, `testDebugUnitTest`: **103/103 pass**, gồm **8/8** trong `SyncTombstoneMergerTest` và **8/8** trong `SyncAccountResolverTest`; `lintDebug`: **0 errors**).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 35 — M5 + L4: "Xoá toàn bộ Drive" xác minh list rỗng mới báo thành công/đăng xuất, kiểm mã revoke; `signOut`/wipe lấy `mutex`, revoke trước `Identity.signOut`, sync kiểm cờ phiên sau khi lấy lock.

### 35. M5 + L4 — Xác minh rỗng khi xoá toàn bộ Drive, kiểm mã revoke & bảo vệ phiên sync qua `mutex`/`sessionGeneration` — 2026-11-19
**Nguyên nhân:**
- **M5:** `httpDeleteFile` nuốt mọi ngoại lệ (trừ 401) thành `false` và các caller bỏ qua giá trị trả về; vòng lặp `repeat(5)` trong `deleteAllCloudData` dùng `return@repeat` (chỉ bỏ qua 1 lượt lặp chứ không thoát vòng) và sau 5 vòng không kiểm tra lại xem Drive đã thực sự rỗng hay chưa mà vẫn xoá tombstone local, đăng xuất và báo `sync_all_cloud_deleted`. Ngoài ra `revokeToken` không kiểm tra HTTP response code trả về từ `https://oauth2.googleapis.com/revoke`.
- **L4:** `signOut()` không lấy `mutex`, không tăng cờ phiên và không huỷ `backgroundSyncJob`, đồng thời gọi `Identity.getSignInClient(appContext).signOut()` ngay trên luồng gọi trước khi coroutine fire-and-forget kịp lấy token và gọi revoke. Một lượt sync hoặc `deleteBookFromCloud` đang chạy dở hoặc đang xếp hàng chờ `mutex` vẫn tiếp tục chạy sau khi `signOut()`/`deleteAllCloudData()` đã dọn state, ghi đè lại `KEY_EMAIL`, `KEY_LAST_SYNC`, `KEY_CLOUD_BOOKS_JSON` và `SyncUiState`.

**Đã sửa:**
- Mới `data/sync/DriveWipeCoordinator.kt`:
  - `wipeAll(...)`: liệt kê toàn bộ ID file hiện có trong `appDataFolder`, thoát ngay (`return`) khi danh sách rỗng; thực hiện xoá lặp tối đa `maxPasses = 5` vòng (lan truyền ngay `CancellationException`, `SyncSessionInvalidatedException` và lỗi `401` cho `DriveAuthRetry`, ghi nhận lỗi xoá gần nhất nếu gặp lỗi mạng/HTTP khác) và luôn kiểm tra `listFileIds()` lần cuối — nếu vẫn còn file trên Drive thì ném lại lỗi xoá (hoặc `IOException`), đảm bảo không xoá tombstone, không đăng xuất và không báo thành công giả.
  - Bổ sung `isRevokeResponseAccepted(statusCode)` (`code in 200..299 || code == 400`) và `isDeleteResponseSuccess(statusCode)` (`code in 200..299 || code == 404`).
- Mới `data/sync/SyncSessionGuard.kt`:
  - Quản lý `generation` (`AtomicLong`), `SyncSessionInvalidatedException`, theo dõi `activeSignOutJob` (`registerSignOutJob`/`clearSignOutJob`/`awaitPendingSignOut`), `runWithSessionLock(mutex, expectedGeneration, ...)` kiểm tra cả `generation` và điều kiện phiên cả trước lẫn sau khi lấy `mutex`, và `executeSignOutLocked(...)` lấy `mutex`, thực hiện revoke trước `Identity.signOut()`, dọn prefs và chỉ reset UI nếu cùng thế hệ phiên.
- `data/sync/GoogleDriveSyncManager.kt`:
  - Tích hợp `DriveWipeCoordinator.wipeAll` vào `deleteAllCloudData()` (kèm `listAppDataFileEntries` lấy đủ mọi file ID kể cả trùng tên), cập nhật `httpDeleteFile` ném `DriveHttpException` / `IOException` thay vì nuốt lỗi (riêng dọn file `book_<hash>` trong `syncOnceWithToken` giữ cơ chế bỏ qua lỗi non-401), và cập nhật `revokeToken(token)` trả về `Boolean` qua `isRevokeResponseAccepted`.
  - Tích hợp `SyncSessionGuard`: `signOut()` tăng `sessionGeneration`, huỷ `backgroundSyncJob` (`cancelBackgroundSync`), xoá key tài khoản trong `prefs` và reset UI state ngay lập tức rồi chạy `executeSignOutLocked` trên `backgroundScope` lấy `mutex`, revoke + `clearCachedAccessToken` + `revokeClientAccess` **trước** `Identity.getSignInClient(appContext).signOut()` (đợi hoàn tất qua `awaitPendingSignOut()` nếu người dùng bấm đăng nhập lại ngay).
  - Trong `signInAndSync`, `handleConsentResult`, `runBackgroundSyncOnce`, `completeSyncWithToken`, `syncOnceWithToken`, `deleteBookFromCloud`, `deleteBookWithToken`, `deleteAllCloudData`, `resolveAndBindAccount` và `finishRetryOutcome`: chụp `expectedGeneration` trước khi chờ `mutex`/mạng và kiểm tra lại bên trong `mutex` cũng như trước mọi bước ghi `tombstones`, `prefs` (`KEY_EMAIL`, `KEY_LAST_SYNC`, `KEY_CLOUD_BOOKS_JSON`) và `state`.
- Mới `test/.../data/sync/DriveWipeCoordinatorTest.kt` (11 tests) và `test/.../data/sync/SyncSessionGuardTest.kt` (7 tests) bao phủ thoát sớm khi rỗng, xoá qua nhiều vòng, ném lỗi khi Drive còn file sau tối đa các vòng hoặc khi offline/5xx, tích hợp retry 401, kiểm mã revoke/delete, chặn sync đang chờ `mutex` hoặc đang chạy giữa chừng sau `signOut`/wipe, đảm bảo thứ tự `revoke` trước `Identity.signOut()`, và không để `signOut` cũ ghi đè lần đăng nhập mới.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, `testDebugUnitTest`: **121/121 pass**, gồm **11/11** trong `DriveWipeCoordinatorTest` và **7/7** trong `SyncSessionGuardTest`; `lintDebug`: **0 errors**).

**Còn để lại:** gom và merge các file `eink_sync_v1.json` trùng tên, kiểm tra `version` trước PATCH và sort payload trước khi so sánh/ghi sẽ xử lý ở mục 36 (M6 + L1 + L6).

**Bước tiếp theo:** mục 36 — M6 + L1 + L6: Gom & merge file `eink_sync_v1.json` trùng tên rồi xoá bản thừa; kiểm `version` trước PATCH (đổi thì merge lại); sort payload theo key trước khi so sánh/ghi.

### 36. M6 + L1 + L6 — Gom & merge `eink_sync_v1.json` trùng tên, kiểm `version` trước PATCH & chuẩn hoá thứ tự payload — 2026-11-19
**Nguyên nhân:**
- **M6:** `listAppDataFiles` đưa toàn bộ file `appDataFolder` vào `Map<String, String>` (`map[name] = id`, bản sau đè bản trước) và không yêu cầu `createdTime`/`version`. Nếu Drive có nhiều file `eink_sync_v1.json` trùng tên (do 2 máy sync lần đầu cùng lúc hoặc lỗi D1 trước đây), các máy có thể đọc/ghi vào các file khác nhau gây phân nhánh dữ liệu; `deleteBookWithToken` cũng chỉ lấy `lastOrNull`.
- **L1:** Sync thực hiện read-modify-write không điều kiện (đọc `eink_sync_v1.json`, tải/upload sách tốn thời gian, rồi PATCH/POST trực tiếp mà không kiểm tra `version`/`md5Checksum` hay kiểm tra file trùng vừa được máy khác POST), nên 2 máy sync đồng thời có thể ghi đè mất cập nhật của nhau.
- **L6:** So sánh `mergedPayload.copy(updatedAt = 0L) != remote.copy(updatedAt = 0L)` và JSON serialize phụ thuộc thứ tự phần tử của các list (`books`, `bookmarks`, `highlights`, `stats.dailyStats`, `bookTombstones`, `annotationTombstones`) vốn khác nhau giữa các máy, khiến từ 2 máy trở lên hầu như lượt sync nào cũng PATCH dù dữ liệu không đổi.

**Đã sửa:**
- Mới `data/sync/SyncPayloadCanonicalizer.kt`:
  - Thêm `SyncPayloadCanonicalizer.canonicalize(payload)` / `SyncPayload.canonicalized()` sắp xếp ổn định toàn bộ các danh sách trong `SyncPayload` (`books` theo `contentHash`/`title`/`updatedAt`, `bookmarks` theo `id`, `highlights` theo `id`, `stats.dailyStats` theo `date`, `bookTombstones` và `annotationTombstones` theo `key`).
  - Thêm `hasPayloadChanged(localMerged, remote)` chuẩn hoá cả hai phía và bỏ qua `updatedAt` trước khi so sánh.
- Mới `data/sync/SyncRemotePayloadMerger.kt`:
  - Thêm `DriveFileRevision`, `DriveFileEntry`, `SyncFileResolution` và `SyncRemotePayloadMerger.resolveSyncFiles(...)` gom tất cả entry `eink_sync_v1.json`, chọn `primaryEntry` theo `createdTime` cũ nhất (parse ISO-8601 `Instant`/`OffsetDateTime`, tie-break theo `id` nhỏ nhất) và trả về danh sách `duplicateEntries`.
  - Thêm `mergePayloads(payloads, now, today)` merge nhiều `SyncPayload` trùng theo quy tắc LWW cho `books`, `bookmarks`, `highlights`, `stats` (`dailyStats` max theo ngày, `dailyGoalMinutes` LWW theo `dailyGoalUpdatedAt`, tính lại streak), `bookTombstones` và `annotationTombstones`, lọc `withoutDeleted` và trả về dạng `.canonicalized()`.
- Mới `data/sync/DriveSyncMetadataParser.kt`:
  - Parse thuần Kotlin (`kotlinx.serialization.json`) cho phản hồi `files.list` và `files.get` (`id`, `name`, `createdTime`, `version`, `md5Checksum`, `nextPageToken`).
- Mới `data/sync/DriveSyncUploadCoordinator.kt`:
  - `downloadAndMergeSyncFiles(...)`: tải và merge toàn bộ file `eink_sync_v1.json` trùng (khi file trùng trả 404 do máy khác vừa xoá sau merge thì tải lại `primaryEntry` để không bỏ sót dữ liệu).
  - `uploadWithVersionCheckAndCleanup(...)` + `DriveUploadTransport`: kiểm tra `version`/`md5Checksum` của `primaryEntry.id` trước khi PATCH; nếu đổi thì tải lại `SyncPayload` mới nhất, merge lại (tối đa `maxRetries = 2` lần, quá ngưỡng ném `DriveSyncConflictException`), rồi mới PATCH `primaryEntry.id` và chỉ xoá các `duplicateEntries` sau khi PATCH thành công. Khi tạo file mới (`primaryEntry == null`), kiểm tra lại trước khi POST và sau khi POST để gom/merge ngay nếu có máy khác vừa POST trùng. Khi có file trùng cần gộp (`duplicateEntries.isNotEmpty()`) vẫn bắt buộc PATCH + xoá bản thừa ngay cả khi nội dung đã khớp.
- `data/sync/GoogleDriveSyncManager.kt`:
  - Cập nhật `listAppDataFileEntries` lấy `files(id,name,createdTime,version,md5Checksum)` qua `DriveSyncMetadataParser`; thêm `fetchFileMetadata` (`GET .../drive/v3/files/$fileId?fields=id,name,createdTime,version,md5Checksum`) và cập nhật `uploadSyncJson` trả về `DriveFileEntry?`.
  - Tích hợp `SyncRemotePayloadMerger`, `DriveSyncUploadCoordinator`, `SyncPayloadCanonicalizer` và `createUploadTransport` vào cả `syncOnceWithToken` lẫn `deleteBookWithToken`, xoá đủ các file `book_<hash>.*` trùng từ `allEntries`, và chỉ lưu `canonicalized()` lên Drive.
- Mới `test/.../data/sync/SyncPayloadCanonicalizerTest.kt` (3 tests), `test/.../data/sync/SyncRemotePayloadMergerTest.kt` (5 tests), `test/.../data/sync/DriveSyncUploadCoordinatorTest.kt` (5 tests) và `test/.../data/sync/DriveSyncUploadRaceTest.kt` (4 tests) bao phủ chuẩn hoá thứ tự & `hasPayloadChanged`, chọn `primaryEntry` theo `createdTime`/`id`, merge LWW nhiều payload trùng, parser metadata Drive, bỏ qua PATCH khi không đổi, PATCH trước khi xoá bản thừa (không xoá khi PATCH lỗi), phát hiện đổi `version` & giới hạn retry, xử lý race pre-POST/post-POST/404 và giữ `CancellationException`/401.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, `testDebugUnitTest`: **138/138 pass**, gồm **17/17** test mới thuộc 4 file test sync; `lintDebug`: **0 errors**). Kiểm tra tay đề nghị với 2 máy: cho 2 máy mới đồng bộ lần đầu cùng lúc → tự gộp về 1 file `eink_sync_v1.json`; đồng bộ lặp lại khi không đọc thêm → không còn PATCH thừa.

**Còn để lại:** không có.

**Bước tiếp theo:** mục 37 — M7: Tải/import sách từ cloud (`runCatching` từng sách + đếm lỗi, dọn temp/`books/`/cover trong `finally`, kiểm SHA-256, `renameTo` thay copy).

### 37. M7 — Tải/import sách từ cloud: cô lập lỗi từng sách, kiểm SHA-256, `renameTo` thay copy & dọn file rác trong `finally` — 2026-11-19
**Nguyên nhân:**
- Trong `GoogleDriveSyncManager.downloadMissingBooks`, `library.importFromSyncedFile` không được bọc cô lập theo từng sách: chỉ cần 1 sách tải về bị lỗi (đầy đĩa, file hỏng trên Drive, hoặc `OutOfMemoryError` khi trích xuất cover CBZ/PDF lớn) là thoát cả phiên sync trước khi merge tiến độ/annotation và upload `SyncPayload`; lần sync sau lại thử đúng cuốn đó gây kẹt vĩnh viễn.
- `tempFile` đặt ở `cacheDir` (có thể khác mount/filesystem với `filesDir/books`) và `importFromSyncedFile` copy toàn bộ nội dung sang `books/<id>.<ext>` (tốn gấp đôi dung lượng đĩa trên file tới 100 MB).
- `LibraryRepository.importFromSyncedFile` không kiểm tra SHA-256 của file tải về so với `contentHash` mong đợi, không dọn `target` (`books/<id>`) và `coverFile` (`covers/<id>.jpg`) trong `finally` khi mở sách/commit thất bại hoặc khi đụng trùng `contentHash` lúc commit, và lỗi trích xuất cover có thể làm hỏng cả việc import sách.

**Đã sửa:**
- Mới `data/book/SyncedBookFileImporter.kt`:
  - `importSyncedBook(...)`: kiểm tra `sourceFile` tồn tại/không rỗng, kiểm tra trùng `contentHash` (không phân biệt hoa/thường) **trước** khi tạo `destFile` hoặc trích xuất cover, tính `BookImporter.sha256Hex(sourceFile)` và đối chiếu với `expectedContentHash` (nếu lệch thì trả `Failed(IOException("SHA-256 mismatch..."))` và xoá sạch `sourceFile`).
  - `moveFileToTarget(sourceFile, targetFile)`: dùng `sourceFile.renameTo(targetFile)` để di chuyển atomic trên cùng filesystem, kèm fallback `copyTo` + `fd.sync()` + `sourceFile.delete()` nếu `renameTo` trả `false`.
  - Trong khối `finally`: luôn xoá `sourceFile`, và nếu `!committed` (lỗi mở sách, lỗi commit, huỷ coroutine, hoặc phát hiện trùng hash ở bước commit cuối) thì luôn xoá sạch cả `targetFile` lẫn `coverFile` để không bao giờ để lại file mồ côi.
- `data/book/LibraryRepository.kt`, `data/book/BookImporter.kt`, `data/book/CoverExtractor.kt`:
  - `CoverExtractor.extract` bắt mọi `Throwable` (kể cả `OutOfMemoryError`, nhưng rethrow `CancellationException`), xoá `targetFile` dở dang và trả `false`.
  - `LibraryRepository.importFromSyncedFile` uỷ quyền qua `SyncedBookFileImporter.importSyncedBook`: kiểm tra `engine.open(bookFile).getOrThrow()` với EPUB/CBZ để từ chối file hỏng, nhưng bọc riêng `CoverExtractor.extract` sao cho lỗi cover chỉ đặt `coverFileName = null` chứ không làm hỏng import sách.
  - `BookImporter.import` cũng bọc riêng bước trích xuất cover để lỗi cover không làm thất bại việc nhập sách hợp lệ.
- Mới `data/sync/DriveBookDownloadCoordinator.kt` & cập nhật `data/sync/GoogleDriveSyncManager.kt`, `res/xml/data_extraction_rules.xml`:
  - Đặt `tempFile` trong `File(appContext.filesDir, "sync_tmp")` (cùng partition với `filesDir/books` để `renameTo` luôn atomic; thêm `exclude domain="file" path="sync_tmp/"` trong `data_extraction_rules.xml`).
  - Bọc tải + kiểm SHA-256 + `importFromSyncedFile` của từng sách trong `try ... catch ... finally`: rethrow `CancellationException`, `SyncSessionInvalidatedException` và lỗi 401 (`DriveAuthRetry.isAuthExpiredError(err)`), đếm `failedCount` cho mọi lỗi tải/hash/import khác rồi tiếp tục các sách còn lại và hoàn tất merge/upload `SyncPayload`; luôn xoá `tempFile` trong `finally`.
  - Gộp `failedBookDownloads + failedBookUploads` trong `SyncRunSummary` để hiển thị thông báo `sync_partial_book_upload_failed` khi có file sách tải hoặc tải lên thất bại.
- Mới `test/.../data/book/SyncedBookFileImporterTest.kt` (7 tests) và `test/.../data/sync/DriveBookDownloadCoordinatorTest.kt` (5 tests): kiểm tra SHA-256 khớp/lệch, kiểm trùng trước khi tạo file, fallback `moveFileToTarget`, dọn sạch `tempFile`/`destFile`/`coverFile` khi lỗi mở sách hoặc trùng lúc commit, vẫn import được sách khi lỗi cover/OOM, cô lập lỗi từng sách trên cloud và rethrow 401/cancel/session invalidated.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, `testDebugUnitTest`: **150/150 pass**, gồm **12/12** test mới trong `SyncedBookFileImporterTest` và `DriveBookDownloadCoordinatorTest`; `lintDebug`: **0 errors**).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 38 — M8 + L10: Sync/xoá cloud chạy trên `appScope`, rethrow `CancellationException`; map lỗi thô sang chuỗi localized, xoá cloud gặp `hasResolution()` bật `needsReconnect`.

### 38. M8 + L10 — Sync/xoá cloud chạy trên `appScope`, rethrow `CancellationException` & chuẩn hoá thông báo lỗi localized — 2026-11-19
**Nguyên nhân:**
- **M8:** Khi người dùng bấm "Đồng bộ ngay", "Xóa khỏi Drive" hoặc "Xóa dữ liệu trên Drive" tại `ReadingStatsScreen` rồi bấm Back rời màn Thống kê, coroutine được khởi chạy trên `rememberCoroutineScope()` của composable bị huỷ ngay giữa chừng (sau khi đã tải/merge dữ liệu nhưng chưa kịp upload JSON); đồng thời một số chỗ dùng `runCatching { ... }.getOrElse { ... }` có thể nuốt `CancellationException` và biến thành lỗi `"Sync failed"` giả.
- **L10:**
  - `LibraryViewModel.import`: khi `repository.import(uri)` trả `Result.failure(e)` hoặc ném ngoại lệ kỹ thuật (message thô của Readium, `ENOSPC` / `"No space left"`, `"Cannot replace library.json"`), app ghép nguyên văn `e.message` vào `lib_import_error` đưa thẳng lên Snackbar.
  - `ReaderViewModel.searchInBook` / `loadNextSearchPage`: khi `next()` trả `onFailure { err -> ... }`, app hiển thị thẳng `err.message` thô của Readium thay vì chuỗi localized `reader_search_error`.
  - `GoogleDriveSyncManager`: khi `httpGet` gặp mã lỗi HTTP non-2xx và ném `DriveRemoteReadException(statusCode = code)`, `formatSyncError` trả luôn `err.message` (`sync_remote_read_failed` chung chung) thay vì phân loại theo mã HTTP (`401`, `403`, `429`, `507`, `5xx`) như `DriveHttpException`.

**Đã sửa:**
- `AppContainer.kt`, `Navigation.kt`, `ui/library/ReadingStatsSheet.kt`, `data/sync/GoogleDriveSyncManager.kt`:
  - Truyền `appScope` vào `GoogleDriveSyncManager` và bổ sung `launchSignInAndSync(onNeedConsent)`, `launchHandleConsentResult(data)`, `launchDeleteAllCloudData()` (cùng `deleteBookFromCloudAsync`) chạy trên `backgroundScope` của sync manager. `ReadingStatsScreen` gọi các hàm này để tiến trình đồng bộ / xoá dữ liệu Drive tiếp tục chạy trọn vẹn ngay cả khi người dùng thoát màn Thống kê, và bọc `runCatching { consentLauncher.launch(request) }` an toàn trên Main thread khi cần mở màn hình cấp quyền.
  - Đảm bảo mọi nhánh `runCatching` / `getOrElse` / `formatSyncError` trong `GoogleDriveSyncManager` (`handleConsentResult`, `uploadMissingBooks`, `downloadSyncPayload`, `formatSyncError`) đều kiểm tra `if (err is CancellationException) throw err`.
  - Cập nhật `DriveRemoteReadException` giữ cả `statusCode` lẫn `cause = DriveHttpException(code, ...)` khi có mã HTTP, và cập nhật `GoogleDriveSyncManager.formatSyncError` phân loại mã HTTP (`401` → `sync_error_auth_expired`, `403`/`507` → `sync_error_quota_or_permission`, `429`/`5xx` → `sync_error_server`, mã HTTP khác → `sync_error_http`) cho cả `DriveRemoteReadException` lẫn `DriveHttpException` (vẫn giữ thông báo `sync_remote_read_failed` cho lỗi `parse`/`metadata`/`userinfo` không phải mã HTTP).
  - Xác nhận `authorizeForSignedInOperation` ở đầu `deleteBookFromCloud` và `deleteAllCloudData` khi gặp `authResult.hasResolution()` luôn bật `needsReconnect = true` và đặt `statusMessage = str(R.string.sync_needs_reconnect)`.
- `ui/library/LibraryViewModel.kt`, `res/values/strings.xml`, `res/values-vi/strings.xml`:
  - Thêm chuỗi `lib_import_failed` và `lib_error_storage_full` vào `values/strings.xml` & `values-vi/strings.xml`.
  - Thêm `LibraryViewModel.formatImportError` & `isStorageFullError`: chỉ giữ các lý do đã được localize từ `BookImporter` (`lib_import_duplicate`, `import_unsupported_format`, `import_cannot_open_file`), ánh xạ lỗi đầy bộ nhớ (`ENOSPC` / `No space left`) sang `lib_error_storage_full`, và ẩn toàn bộ message kỹ thuật thô khác về `lib_import_invalid_file` / `lib_import_failed`.
- `ui/reader/ReaderViewModel.kt`:
  - Trong `searchInBook`, `loadMoreSearchResults` và `loadNextSearchPage`: bọc `try/catch` (rethrow `CancellationException`) và dùng chuỗi localized `reader_search_error` (`formatSearchError`) thay vì truyền `err.message` thô của Readium lên UI.
- Mới `test/.../data/sync/SyncErrorFormatterTest.kt` (5 tests) và `test/.../ui/library/LibraryViewModelErrorTest.kt` (4 tests): kiểm tra phân loại mã HTTP `401`/`403`/`507`/`429`/`5xx` cho cả `DriveRemoteReadException` và `DriveHttpException`, giữ thông báo `parse` khi không có HTTP code, ánh xạ lỗi import sách (`lib_import_duplicate`, `ENOSPC`, ẩn lỗi kỹ thuật thô của Readium/`library.json`), và rethrow `CancellationException`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, `testDebugUnitTest`: **159/159 pass**, gồm **9/9** test mới trong `SyncErrorFormatterTest` và `LibraryViewModelErrorTest`; `lintDebug`: **0 errors**).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 39 — M9 + L2 + L8: Bắt `IOException` khi ghi (giữ "dirty", báo UI, receiver `runCatching`); `AtomicJsonFile` cách ly file khi lỗi đọc, khôi phục từ `.tmp`, fsync thư mục; ghi tombstone trước commit annotation; mutex quanh import + disable nút khi `importing` (đếm job); `backfillMissingCovers` lưu id trước khi thử + bắt `Throwable`.

### 39. M9 + L2 + L8 — Chịu lỗi ghi đĩa (`dirty` + báo UI), `AtomicJsonFile` khôi phục `.tmp`/cách ly khi lỗi đọc/fsync thư mục, tombstone trước commit, mutex import & `backfillMissingCovers` an toàn — 2026-11-19
**Nguyên nhân:**
- **M9:** `LibraryRepository.commit`, `AnnotationRepository.commitBookmarks`/`commitHighlights`, `ReadingStatsRepository`, `SyncTombstoneStore.mutate` chỉ cập nhật state trong bộ nhớ rồi gọi thẳng `store.write(...)` không bắt `IOException` — khi đầy đĩa sẽ làm crash coroutine caller (`viewModelScope`, `LockScreenNavReceiver`), đồng thời lần ghi kế tiếp có thể bỏ qua nếu transform không làm đổi state (`next == data`). `LockScreenNavReceiver.onReceive` không bọc `try/catch` trong `goAsync()` nên lỗi bất ngờ làm sập process.
- **L2:** `AtomicJsonFile.read()` bỏ qua khôi phục từ `<name>.tmp` khi `!file.exists()`, chỉ cách ly file sang `.corrupt-<ts>` khi lỗi `decode` mà bỏ sót lỗi I/O khi `file.readText()` (dẫn tới lần ghi sau ghi rỗng đè mất dữ liệu cũ), và không `fsync` thư mục cha sau `renameTo`.
- **L8:** `AnnotationRepository.removeBookmark`/`removeHighlight` gọi `commitBookmarks`/`commitHighlights` trước khi ghi tombstone; `LibraryRepository.import` và `importFromSyncedFile` chưa có mutex chung quanh bước kiểm tra `existingHashes` → import → commit; `LibraryViewModel.import` dùng boolean đơn nên 2 job gối nhau tắt `importing` sớm; `LibraryScreen` chưa chặn bấm đúp nút `+ Nhập sách` ở empty state và trạng thái đang mở picker; `LibraryRepository.backfillMissingCovers` chỉ lưu `attempted_ids` sau cả vòng lặp và không bọc `try/catch (Throwable)` quanh từng sách.

**Đã sửa:**
- `data/storage/AtomicJsonFile.kt` (**L2**):
  - Trong `read(defaultValue)`: nếu `!file.exists()` nhưng `<name>.tmp` tồn tại và đọc/parse thành công thì tự động khôi phục (`renameTo` + `fsync` thư mục cha, fallback `copyTo` + xoá `.tmp`) và trả về dữ liệu khôi phục; khi `file.exists()` mà gặp bất kỳ `Throwable` nào (trừ `CancellationException`) ở `readText` hoặc `decodeFromString`, luôn cách ly file sang `<name>.corrupt-<timestamp>`; nếu cách ly thất bại thì giữ cờ `unquarantinedReadError = true` để `write` từ chối ghi đè (ném `IOException`) thay vì làm mất dữ liệu cũ.
  - Sau `renameTo` thành công trong `write`, gọi `defaultFsyncDirectory(file.parentFile)` dùng `Os.open(..., O_RDONLY)` + `Os.fsync` trên Android (fallback `FileChannel.force(true)` trên API 26+, bọc `runCatching` an toàn trên Windows/FS không hỗ trợ).
- `data/book/LibraryRepository.kt`, `data/annotation/AnnotationRepository.kt`, `data/stats/ReadingStatsRepository.kt`, `data/sync/SyncTombstoneStore.kt` (**M9**):
  - Bọc bước ghi đĩa trong `try/catch (Throwable)` (rethrow `CancellationException`), giữ cờ `dirty` (`bookmarksDirty`/`highlightsDirty` ở `AnnotationRepository`) để lần ghi kế tiếp vẫn flush ngay cả khi state không đổi, và phát `lastWriteError: StateFlow<Boolean>`.
- `reader/LockScreenNavReceiver.kt` (**M9**):
  - Bọc khối xử lý `ACTION_DISMISSED` bằng `runCatching` và bọc toàn bộ khối xử lý `ACTION_PREV`/`ACTION_NEXT` trong `try/catch (Throwable)` (rethrow `CancellationException`) bên trong `try ... finally { pendingResult.finish() }`.
- `ui/library/LibraryViewModel.kt`, `ui/library/LibraryScreen.kt`, `ui/reader/ReaderViewModel.kt`, `ui/reader/ReaderScreen.kt`, `ui/library/ReadingStatsSheet.kt`, `Navigation.kt`, `res/values*/strings.xml` (**M9 + L8**):
  - Thêm chuỗi `error_save_data` (vi/en), tổng hợp `writeError: StateFlow<Boolean>` trong `LibraryViewModel` & `ReaderViewModel`, hiển thị Snackbar (`LibraryScreen`, `ReaderScreen`) / Toast (`ReadingStatsScreen`) khi ghi file thất bại.
  - `LibraryViewModel`: dùng bộ đếm `activeImportCount` (đồng bộ qua lock + `job.invokeOnCompletion`) để `importing` chỉ về `false` khi job cuối cùng kết thúc.
  - `LibraryScreen`: theo dõi thêm `pickerLaunching` để vô hiệu hoá cả nút `+ Nhập sách` (`EmptyLibrary`), nút `+` (`LibraryBottomBar`) và `TutorialOverlay` ngay khi đang mở picker hoặc đang `importing`.
- `data/annotation/AnnotationRepository.kt` (**L8**):
  - Trong `removeBookmark`, `restoreBookmark`, `removeHighlight`, `restoreHighlight`: gọi `tombstones?.markAnnotations(...)` bên trong `writeMutex.withLock` **trước** khi cập nhật `StateFlow` và ghi `bookmarks.json`/`highlights.json`.
- `data/book/LibraryRepository.kt` (**L8**):
  - Thêm `importMutex` bọc quanh `import` (`importWithHashes`) và `importFromSyncedFile`; `backfillMissingCovers` lưu `attempted_ids` (`commit()`) trước khi mở từng sách và bọc mỗi sách trong `try/catch (Throwable)` (rethrow `CancellationException`, đóng `Publication` trong `finally`, dọn cover dở dang).
- Unit test mới & bổ sung: `AtomicJsonFileTest` (+4), `AnnotationRepositoryTest` (+3), `LibraryRepositoryTest` (+5), `SyncTombstoneStoreTest` (+1), `ReadingStatsTest` (+1), `LibraryViewModelErrorTest` (+1).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, `testDebugUnitTest`: **174/174 pass** gồm 15 test mới; `lintDebug`: **0 errors**).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 40 — L5 + L7 + L9 + L22 (`updatedAt`): Tombstone chặn `changedAt` tương lai; sync thủ công hỏi trước khi tải/đăng sách qua mạng trả phí; từ điển cắt ~500 ký tự + `langpair=auto|<target>`; `savePdfReadingMode` không đổi `updatedAt`, `saveProgress` bỏ qua locator không đổi, override locator đánh dấu bằng saved state thay vì so đồng hồ.

### 40. L5 + L7 + L9 + L22 (`updatedAt`) — Chặn `changedAt`/`updatedAt` tương lai, xác nhận đồng bộ sách qua mạng trả phí, giới hạn từ điển 500 ký tự + `auto|<target>`, giữ ổn định `updatedAt` & trạng thái tiêu thụ override locator — 2026-11-19
**Nguyên nhân:**
- **L5:** `SyncTombstoneMerger.merge` giữ nguyên `changedAt` của tombstone nhận được mà không giới hạn mốc tương lai — một thiết bị lệch đồng hồ sang tương lai (ví dụ năm 2030) sẽ tạo tombstone "bất tử" thắng mọi thao tác khôi phục về sau. Tương tự, `LibraryRepository.applySyncedProgress` và `SyncRemotePayloadMerger` nhận trực tiếp `remoteUpdatedAt` tương lai từ máy khác khiến `book.updatedAt` trên máy này bị khoá ở mốc tương lai.
- **L7:** Khi bật `syncBookFiles`, đồng bộ nền đã kiểm tra `isUnmeteredNetwork()`, nhưng đồng bộ thủ công (`signInAndSync`) luôn đặt `allowBookFiles = true` mà không hỏi người dùng khi đang dùng mạng di động/trả phí (`isActiveNetworkMetered`), có thể tự động tải hoặc đăng file sách tới 100 MB/cuốn qua 4G.
- **L9:** `DictionaryService` không giới hạn độ dài văn bản gửi tới dịch vụ bên thứ ba, và fallback MyMemory cố định `langpair` theo cặp `vi|en` hoặc `en|vi` thay vì `auto|<target>`.
- **L22 (`updatedAt`):**
  - `LibraryRepository.savePdfReadingMode` luôn gán `updatedAt = System.currentTimeMillis()` (kể cả khi `ReaderViewModel.onCleared()` gọi lại mỗi lần đóng sách PDF), làm tăng `updatedAt` khi chỉ đổi/lưu lại chế độ đọc khiến tiến độ đọc cũ thắng bản mới hơn trên máy khác khi sync.
  - `LibraryRepository.saveProgress` vẫn ghi đĩa và tăng `updatedAt` ngay cả khi `locatorJson` và `progress` không đổi.
  - `ReaderActivity` chỉ gọi `intent.removeExtra(EXTRA_LOCATOR_JSON)` — không có tác dụng sau khi process bị kill và Android khôi phục lại `Intent` gốc, có thể áp lại `overrideLocatorJson` cũ đè lên vị trí vừa đọc.

**Đã sửa:**
- `data/sync/SyncTombstoneMerger.kt`, `data/sync/SyncRemotePayloadMerger.kt`, `data/book/LibraryRepository.kt` (**L5 + L22**):
  - Thêm `SyncTombstoneMerger.MAX_FUTURE_SKEW_MS = 5 * 60_000L` và `clampTimestamp(timestamp, now)`: trong `SyncTombstoneMerger.merge`, kẹp `changedAt` vượt quá `now + MAX_FUTURE_SKEW_MS` về `now` để chặn tombstone tương lai bất tử nhưng vẫn dung nạp lệch đồng hồ nhẹ dưới 5 phút.
  - Trong `LibraryRepository.applySyncedProgress`, `LibraryRepository.importFromSyncedFile` và `SyncRemotePayloadMerger.mergePayloads`: kẹp `remoteUpdatedAt.coerceAtMost(now)` để tiến độ từ thiết bị sai đồng hồ tương lai không khoá cứng `updatedAt` cục bộ.
  - Trong `LibraryRepository.savePdfReadingMode(id, mode)`: bỏ qua không ghi nếu `pdfReadingMode == mode` (trừ khi đang `dirty`), và **không** cập nhật `updatedAt` khi đổi chế độ đọc PDF.
  - Trong `LibraryRepository.saveProgress(id, locatorJson, progress)`: nếu `lastLocatorJson == locatorJson && abs(it.progress - clamped) < 1e-6f` (và không `dirty`), bỏ qua không ghi đĩa và không tăng `updatedAt`.
- `data/sync/GoogleDriveSyncManager.kt`, `ui/library/ReadingStatsSheet.kt`, `Navigation.kt`, `res/values/strings.xml`, `res/values-vi/strings.xml` (**L7**):
  - Thêm `shouldConfirmMeteredBookSync(): Boolean` (`state.value.syncBookFiles && isMeteredNetwork()`), bổ sung tham số `allowBookFiles: Boolean = state.value.syncBookFiles` cho `launchSignInAndSync`, `signInAndSync`, `launchHandleConsentResult`, `handleConsentResult` (lưu `pendingConsentAllowBookFiles` xuyên suốt luồng OAuth consent).
  - Trong `ReadingStatsScreen`: khi bấm đồng bộ mà `shouldConfirmMeteredBookSync()` là `true`, hiển thị hộp thoại xác nhận (`sync_metered_confirm_title` / `sync_metered_confirm_message`) với 3 lựa chọn localized (vi/en): "Đồng bộ cả file sách" (`allowBookFiles = true`), "Chỉ đồng bộ tiến độ & ghi chú" (`allowBookFiles = false`), hoặc "Hủy".
- `data/dictionary/DictionaryService.kt` (**L9**):
  - Thêm `MAX_QUERY_CHARS = 500`, cắt ngắn văn bản đầu vào trong `normalizeQuery`, `normalizeWord` và `translate` tối đa 500 ký tự (`take(MAX_QUERY_CHARS)`).
  - Cập nhật fallback MyMemory dùng `buildMyMemoryLangPair(targetLang)` = `"auto|$normalizedTarget"` (`langpair=auto|$targetLang`).
- `ReaderActivity.kt`, `ui/reader/ReaderViewModel.kt` (**L22**):
  - Lưu cờ `STATE_OVERRIDE_LOCATOR_CONSUMED` vào `onSaveInstanceState` của `ReaderActivity` (đọc lại từ `savedInstanceState` trước `super.onCreate(null)` để không ảnh hưởng cơ chế dựng lại Readium Fragment) kết hợp với cờ `overrideLocatorConsumed` và `resolveInitialLocatorChoice` trong `ReaderViewModel` để đảm bảo `overrideLocatorJson` chỉ được tiêu thụ đúng một lần duy nhất và không bao giờ áp lại sau khi recreate hoặc process death.
- Unit test mới trong `SyncTombstoneMergerTest`, `LibraryRepositoryTest` (+3), `DictionaryServiceTest` (+2), `BookmarkMatcherTest` (+1).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, `testDebugUnitTest`: **181/181 pass**; `lintDebug`: **0 errors**).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 41 — M10 + M11 + L11 (PDF: một `PDDocument`/phiên trên `IO.limitedParallelism(1)` + cache analysis theo trang trong holder, prefetch trang kề, GUIDED chặn lật khi chưa có regions, smart tap-zoom ở SMART, trích chữ 1 lần).

### 41. M10 + M11 + L11 — Một `PDDocument`/phiên trên `IO.limitedParallelism(1)`, cache & prefetch analysis theo trang, chặn lật & giữ `pendingBackwardLandingPage` ở GUIDED, smart tap-zoom ở SMART_ORIGINAL & trích chữ 1 lần `getText` — 2026-11-19
**Nguyên nhân:**
- **M10:** `PdfPagerReader` phân tích lười từng trang bằng `PdfPageAnalyzer.analyzePages(file, listOf(pageIndex))` mở riêng một `PDDocument.load(file)` cho mỗi trang đang compose (3 trang song song), bỏ trống `holder.seedAnalyses` / `analysisCache` như code chết và không prefetch trang kề. Ở `GUIDED_COLUMN`, khi `pageAnalysis == null`, `guidedRegions` trả về 1 vùng toàn trang nên `activePageRegionPrev`/`Next` trả `false` khiến tap cạnh hoặc giữ phím âm lượng lật bỏ qua cả trang; đồng thời khi lật lùi (`pendingBackwardLandingPage == pageIndex`), code gán `guidedRegionIndex = 0` và xoá ngay `pendingBackwardLandingPage` trước khi regions thật trả về.
- **M11:** `PdfPagerReader` chỉ chạy phân tích trang khi `readingMode == GUIDED_COLUMN`, nên ở `SMART_ORIGINAL` (mặc định) nếu sách chưa từng mở Reflow thì `pageAnalysis` luôn `null` và double-tap luôn zoom 2.2× cố định thay vì `findTapTargetRect`.
- **L11:** Trong `PdfPageAnalyzer.analyzeAllPages`, việc tái sử dụng `ReusableLineStripper` nhưng gọi `getText(document)` riêng cho từng trang vẫn khiến `PDFTextStripper.processPages` duyệt lại toàn bộ `PDPageTree` từ đầu tới cuối N lần (O(N²) trên sách 500–1000 trang).

**Đã sửa:**
- `reader/pdf/PdfPageAnalyzer.kt` (**L11**):
  - Tách `BaseLineStripper` dùng chung logic gom chunk `PdfTextLine` (tắt ghi separator vào `StringWriter` để tránh cấp phát chuỗi thừa) và thêm `BatchLineStripper` override `startDocument`/`processPage`/`startPage`/`endPage` gom `Map<Int, List<PdfTextLine>>` chỉ trong **1 lượt `getText(document)`** cho toàn bộ tài liệu (hỗ trợ `shouldContinue`, cập nhật tiến độ và cô lập lỗi từng trang trong `failedPages`).
  - `analyzeAllPages` dùng `BatchLineStripper` 1 lượt rồi lắp ghép graphics/images qua `analyzePageSafely` theo từng trang (chỉ fallback về `ReusableLineStripper` cho trang lỗi hoặc khi batch `getText` lỗi ở sách hỏng).
  - Cập nhật `ReusableLineStripper` override `processPages` để chỉ xử lý đúng trang đích thay vì duyệt toàn bộ `PDPageTree`.
- Mới `ui/reader/PdfAnalysisSession.kt` & cập nhật `ui/reader/PdfRendererHolder.kt` (**M10 + M11**):
  - Mở **một `PDDocument` duy nhất cho cả phiên** (lười khi có yêu cầu chưa nằm trong cache) và đóng an toàn trong `close()` trên cùng `Dispatchers.IO.limitedParallelism(1)` (kết hợp một `ReusableLineStripper` dùng tuần tự).
  - Kết nối thật `analysisCache` (`ConcurrentHashMap<Int, PdfPageAnalysis>`) + `analysisVersion: StateFlow<Int>`: triển khai `seedAnalyses`, `cachedAnalysis(pageIndex)`, `getOrAnalyzePage(pageIndex)` (trả `emptyPageAnalysis` nếu mở PDFBox thất bại để không kẹt điều hướng, trả `null` nếu đã `closed`), và `prefetchAdjacentAnalyses(centerIndex)` (ưu tiên `centerIndex + 1`, `centerIndex - 1`).
- Mới `reader/pdf/PdfGuidedNavigation.kt` & cập nhật `ui/reader/PdfPagerReader.kt` (**M10 + M11**):
  - Truyền `context` vào `openPdfHolder(file, context)` và đọc `pageAnalysis` trực tiếp từ `loadedAnalyses` hoặc `holder.cachedAnalysis(pageIndex)` kèm `holder.analysisVersion`.
  - Ở `GUIDED_COLUMN`: phân tích ưu tiên `pagerState.currentPage` rồi mới `holder.prefetchAdjacentAnalyses(pagerState.currentPage)`; `PdfGuidedNavigation.stepPrev`/`stepNext` luôn trả `consumed = true` để **chặn lật trang** khi `pageAnalysis == null` hoặc đang chờ landing backward; `PdfGuidedNavigation.resolveRegionIndex` chỉ gán `guidedRegionIndex = guidedRegions.lastIndex` và gọi `onConsumeBackwardLanding()` **sau khi `pageAnalysis != null`**.
  - Ở `SMART_ORIGINAL`: tự động phân tích trang hiện tại khi settle (`pagerState.settledPage`) qua `holder.getOrAnalyzePage(...)`, và trong `onDoubleTap` dùng `rememberUpdatedState` + gọi `holder.getOrAnalyzePage(pageIndex)` nếu chưa có analysis để `findTapTargetRect` luôn có dữ liệu (cộng thêm `computeDoubleTapCamera`).
- `app/build.gradle.kts` & unit test mới (**15 tests mới**):
  - Cấu hình `packagePdfBoxTestResources` (đóng gói `com/tom_roush/pdfbox/resources/**` từ `mergeDebugAssets` cho `testImplementation`) + `unitTests.isReturnDefaultValues = true` để chạy test `PDDocument`/`PDFTextStripper` thật trên JVM.
  - Mới `test/.../reader/pdf/PdfPageAnalyzerBatchTest.kt` (6 tests), `test/.../reader/pdf/PdfGuidedNavigationTest.kt` (4 tests) và `test/.../ui/reader/PdfAnalysisSessionTest.kt` (5 tests).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, `testDebugUnitTest`: **196/196 pass**, gồm **15/15** test mới; `lintDebug`: **0 errors**). Kiểm tra tay đề nghị: mở sách nhiều cột ở `GUIDED_COLUMN` chưa từng mở Reflow (lật nhanh không bỏ trang, lật lùi vào vùng cuối trang trước); mở sách ở `SMART_ORIGINAL` chưa mở Reflow rồi double-tap vào cột chữ/ảnh (zoom đúng vùng thay vì 2.2× cố định); mở Reflow sách PDF dài (>500 trang) thấy bước trích chữ chỉ quét 1 lượt.

**Còn để lại:** không có.

**Bước tiếp theo:** mục 42 — M12 + L13: Tap-zoom khi `autoCrop` đổi rect sang toạ độ crop; `clampOffset` theo kích thước ảnh hiển thị.

### 42. M12 + L13 — Tap-zoom khi `autoCrop` đổi rect sang toạ độ crop; `clampOffset` theo kích thước ảnh hiển thị — 2026-11-19
**Nguyên nhân:**
- **M12:** Trong `computeDoubleTapCamera` (`PdfPagerReader.kt`), khi `autoCrop` bật, toạ độ chạm trên bitmap đã crop được đổi sang hệ toạ độ trang gốc để gọi `PdfRegionNavigator.findTapTargetRect`, nhưng `PdfRect` trả về vẫn ở hệ toạ độ trang gốc (0..1 của trang đầy đủ) rồi đưa thẳng vào `computeCameraTransform` — nơi hiểu `region` theo không gian của bitmap đã crop đang hiển thị → tâm và tỉ lệ zoom của cột/khối chữ bị lệch ~170 px và cắt mép cột.
- **L13:** `clampOffset` (`PdfPagerReader.kt`) và `computeCameraTransform` (`PdfRegionNavigator.kt`) tính biên pan `maxX = containerWidth * (scale - 1f) / 2f`, `maxY = containerHeight * (scale - 1f) / 2f` theo kích thước container thay vì kích thước ảnh thực tế hiển thị với `ContentScale.Fit` (`dispW`, `dispH`), cho phép pan vào vùng dải trống (letterbox) ở chiều ảnh chưa vượt quá container.

**Đã sửa:**
- `reader/pdf/PdfRegionNavigator.kt`:
  - Thêm `mapPageRectToCropSpace(rect: PdfRect, crop: PdfRect): PdfRect` quy đổi `rect` từ hệ toạ độ trang gốc sang hệ toạ độ `[0, 1]` của bitmap đã crop (`(rect.left - crop.left) / crop.width`, v.v., kèm fallback an toàn về `rect` nếu `crop` suy biến hoặc vùng sau khi kẹp quá hẹp `< 0.02f`).
  - Thêm `clampOffsetForFitContent(offsetX, offsetY, scale, containerWidth, containerHeight, bitmapWidth, bitmapHeight)` tính kích thước hiển thị thực tế `(dispW, dispH)` theo `ContentScale.Fit` (fallback về kích thước container khi chưa có kích thước bitmap) và kẹp theo `maxX = ((dispW * scale - containerWidth) / 2f).coerceAtLeast(0f)`, `maxY = ((dispH * scale - containerHeight) / 2f).coerceAtLeast(0f)`.
  - Cập nhật `computeCameraTransform` dùng `clampOffsetForFitContent` để biên dịch chuyển camera của cả tap-zoom lẫn `GUIDED_COLUMN` luôn bám sát mép ảnh hiển thị.
- `ui/reader/PdfPagerReader.kt`:
  - Trong `computeDoubleTapCamera`: khi có `cropRect` hợp lệ (`cropRect.width > 0f && cropRect.height > 0f`), quy đổi `pageRect` qua `PdfRegionNavigator.mapPageRectToCropSpace` trước khi gọi `computeCameraTransform`.
  - Cập nhật `clampOffset` uỷ quyền qua `PdfRegionNavigator.clampOffsetForFitContent` và truyền kích thước bitmap hiện tại ở mọi điểm gọi (gesture pinch/pan trang PDF, fallback double-tap 2.2×, và `PdfReflowImageZoomOverlay` trừ phần padding 32 dp).
- `test/.../reader/pdf/PdfRegionNavigatorTest.kt`:
  - Thêm 4 unit test mới kiểm tra `mapPageRectToCropSpace` trên trang 2 cột có `autoCrop`, fallback khi `crop` suy biến/không giao nhau, và `clampOffsetForFitContent` chặn pan vào dải trống letterbox cũng như fallback khi chưa có bitmap.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, `testDebugUnitTest`: **200/200 pass**, gồm **4/4** test mới trong `PdfRegionNavigatorTest`; `lintDebug`: **0 errors**).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 43 — M13 + M14: Reflow giữ mốc đọc (trang nguồn + đoạn + offset) khi dàn lại; callback `onScreenTurn` đếm lật màn reflow/vùng GUIDED (thống kê + flash).

### 43. M13 + M14 — Reflow giữ mốc đọc khi dàn lại & đếm lật màn reflow/vùng GUIDED — 2026-11-19
**Nguyên nhân:**
- **M13:** Khi dàn lại trang ở chế độ Reflow (đổi cỡ chữ, font, lề, xoay màn hình), `findScreenIndexForPdfPage` trả về màn hình **đầu tiên** chứa `currentPdfPage` và `resolveSnapBackPage` lấy `sources.first()` → đang ở màn giữa của một trang hoặc màn ghép `[9, 10]` sẽ nhảy về màn `[8, 9]`, làm `currentPdfPage` lùi dần qua mỗi lần đổi cài đặt.
- **M14:** `ReaderViewModel.onLocatorChanged` chỉ đếm lật trang khi `href#progression#position` thay đổi, nên khi lật qua nhiều màn Reflow trong cùng 1 trang PDF nguồn hoặc bước qua các vùng cột ở `GUIDED_COLUMN` trong cùng 1 trang PDF, thống kê `pendingPages` và chu kỳ full refresh e-ink (`flashController`) không được tăng.

**Đã sửa:**
- `reader/pdf/PdfReflowPaginator.kt`:
  - Thêm `ReflowReadingAnchor(sourcePageIndex, itemIndex, charOffset)` và `ReflowElementRange(sourcePageIndex, itemIndex, charRange)`; gắn `itemIndex`, `charOffset`, `charEndOffset` cho từng `PaginatedReflowElement` khi `paginate`, lưu `startAnchor` và `elementRanges` trên mỗi `ReflowScreenPage`.
  - Thêm `findScreenIndexForAnchor(screens, anchor)` dò đúng màn hình chứa `(sourcePageIndex, itemIndex, charOffset)` (fallback về phần tử cùng đoạn có `charOffset` gần nhất phía trước, sau đó ưu tiên màn có `sourcePages.first() == sourcePageIndex`).
  - Cập nhật `findScreenIndexForPdfPage` ưu tiên màn có `sourcePages.firstOrNull() == pdfPageIndex` trước khi xét `pdfPageIndex in sourcePages`.
  - Thêm `evaluateScreenSettle(screen, previousScreenIndex, previousPdfPage, isExplicitJump)` xác định `snapPdfPage`, `updatedAnchor` và `isSubPageTurn` (chỉ `true` khi người dùng lật sang màn khác có cùng `snapPdfPage == previousPdfPage` và không phải nhảy/dàn lại).
- `reader/pdf/PdfGuidedNavigation.kt`: thêm cờ `changedRegionOnSamePage` vào `GuidedRegionStep` khi bước tới/lùi sang vùng khác trên cùng một trang PDF ở `GUIDED_COLUMN`.
- `ui/reader/PdfPagerReader.kt`, `ui/reader/ReaderScreen.kt`, `ui/reader/ReaderViewModel.kt`:
  - Giữ `currentReflowAnchor` trong `PdfPagerReader`, cuộn theo `findScreenIndexForAnchor` khi `reflowScreens` dàn lại, và đặt lại mốc đầu trang khi nhảy trang từ bên ngoài (TOC/slider).
  - Thêm callback `onScreenTurn` kết nối tới `ReaderViewModel.onSubPageScreenTurned()` (tôn trọng cửa sổ chặn dàn trang `suppressPageTurnUntilMs`, tăng `pendingPages` và bước `flashController` mà không đếm kép khi chuyển qua ranh giới trang PDF mới).
- `test/.../reader/pdf/PdfReflowPaginatorTest.kt`, `test/.../reader/pdf/PdfGuidedNavigationTest.kt`, `test/.../ui/reader/ReaderViewModelPageTurnTest.kt`: thêm 8 unit test kiểm tra bảo toàn mốc đọc qua nhiều lần đổi cỡ chữ, `findScreenIndexForAnchor`, `findScreenIndexForPdfPage`, `evaluateScreenSettle` và đếm lật màn con / vùng GUIDED trong `ReaderViewModel`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **208/208 tests pass**, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 44 — M15 + L12 + L14: tối ưu bộ nhớ & render PDF (bỏ `renderMultiplier = 3` và `prefetchAdjacent`, trang kề render 1×, zoom > 2× render vùng đang nhìn, `derivedStateOf`/`rememberUpdatedState`, `openPdfHolder` ra khỏi main, `cropCache` → `ConcurrentHashMap`, OOM retry ×√0.5, `PdfReflowEngine` `endsWith`).

### 44. M15 + L12 + L14 — Tối ưu bộ nhớ & render PDF, tile zoom > 2×, tránh recompose khi pinch, mở PDF trên IO, `ConcurrentHashMap` & retry OOM ×√0.5 — 2026-11-19
**Nguyên nhân:**
- **M15:** `renderMultiplier = 3` vẫn bị `PdfBitmapBudget` kẹp cùng mức ~8,4 MP với 2× nhưng tốn thêm render + entry cache và vẫn mờ ở 4,5×; `holder.prefetchAdjacent` render 2 trang kề song song với `beyondViewportPageCount = 1` ở mức 2× trong `GUIDED_COLUMN`, tranh mutex và ngốn bộ nhớ ngoài budget.
- **L12:** `renderMultiplier` và `LaunchedEffect(scale, ...)` đọc `scale` trực tiếp trong composition khiến mỗi khung hình pinch đều recompose và khởi động lại effect; `pointerInput` cần tách hẳn khỏi `bitmap`/`pageAnalysis`/`controller`/`onCenterTap` qua `rememberUpdatedState` để không reset gesture đang double-tap.
- **L14:** `openPdfHolder` mở `ParcelFileDescriptor` + `PdfRenderer` trên main thread trong `remember(file)`; `cropCache` dùng `HashMap` không đồng bộ giữa thread render và main thread; `renderWithMemoryRetry` thử lại đúng kích thước vừa gây OOM; `PdfReflowEngine.mergeLinesIntoParagraphs` gọi `currentText.toString()` mỗi dòng không ngắt đoạn (O(L²)).

**Đã sửa:**
- `reader/pdf/PdfRegionNavigator.kt` (**M15**):
  - Thêm `computeVisibleViewportBitmapRect(...)` và `computeVisibleViewportPageRect(...)` tính hình chữ nhật vùng trang đang hiển thị chuẩn hoá `0..1` theo `scale`, `offset`, kích thước container/bitmap (`ContentScale.Fit`) và quy đổi về toạ độ trang gốc khi có `cropRect` (`autoCrop`).
- `ui/reader/PdfRendererHolder.kt` (**M15 + L14**):
  - Bỏ hàm `prefetchAdjacent`; gộp nhánh render vùng vào `renderRegionInternal` và bổ sung `renderViewportTile(index, region, maxWidth, maxHeight)` render vùng đang nhìn ở độ phân giải màn hình (không nhồi tile nhất thời vào `pageBitmapCache` làm đẩy ảnh toàn trang).
  - Đổi `cropCache` từ `HashMap` sang `ConcurrentHashMap<Int, NormalizedCropRect>`.
  - Tách `renderWithMemoryRetry(onEvictCache, render)` (pure Kotlin, rethrow `CancellationException`): khi gặp `OutOfMemoryError`, xoá cache và gọi lại với `OOM_RETRY_SCALE_FACTOR = 0.70710677f` (`√0.5`, giảm 50% số pixel) áp vào cả `renderPage` lẫn `renderRegionInternal`.
- `ui/reader/PdfPagerReader.kt` (**M15 + L12 + L14**):
  - Chuyển `openPdfHolder` sang `produceState<PdfHolderLoadState>` chạy trên `Dispatchers.IO` (luôn đóng `opened` trong `finally` kể cả khi huỷ giữa chừng, kết hợp `DisposableEffect(holder)`), hiển thị spinner khi đang mở và `pdf_load_error` nếu thất bại (có tap mở toolbar).
  - Bỏ lời gọi `holder.prefetchAdjacent(...)`; giới hạn `renderMultiplier` tối đa `2` (chỉ `2` ở `isCurrentPage` khi `GUIDED_COLUMN || scale > 1.35f`, còn trang kề `!isCurrentPage` luôn `1×`).
  - Dùng `derivedStateOf` cho `isCurrentPage`, `isPageZoomed`, `isDeepZoomed` (`isCurrentPage && scale > 2.0f`) và `renderMultiplier`; thay `LaunchedEffect(scale, ...)` bằng `LaunchedEffect(isPageZoomed, isCurrentPage)`; dùng `rememberUpdatedState` cho `bitmap`, `pageAnalysis`, `controller`, `onCenterTap` trong `pointerInput`.
  - Khi `isDeepZoomed` (`scale > 2.0f`), debounce `180 ms` qua `snapshotFlow { ... }.distinctUntilChanged().debounce(180L).collectLatest` để gọi `holder.renderViewportTile(...)` và phủ tile sắc nét đồng bộ theo `graphicsLayer`, tự xoá tile ngay khi `scale <= 2.0f` hoặc đổi trang.
- `reader/pdf/PdfReflowEngine.kt` (**L14**):
  - Thêm `CharSequence.lastNonWhitespaceIndex()` và `CharSequence.endsWithHyphenBeforeTrailingSpaces()` duyệt ngược trực tiếp trên `StringBuilder` không cấp phát `String`; thay `currentText.toString()` và `prev.text.trimEnd()` trong `mergeLinesIntoParagraphs`.
- `test/.../reader/pdf/PdfRegionNavigatorTest.kt`, `test/.../ui/reader/PdfBitmapBudgetTest.kt`, `test/.../reader/pdf/PdfReflowEngineTest.kt`: thêm 7 unit test mới cho `computeVisibleViewportPageRect` (không/có `autoCrop`, letterbox, input lỗi), `renderWithMemoryRetry` (OOM retry ở `√0.5`, thất bại kép, `CancellationException`) và `endsWithHyphenBeforeTrailingSpaces`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **215/215 tests pass** qua 38 file test, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 45 — M16: `PdfDocumentCache` lưu theo khúc + ghi atomic + tăng `CURRENT_SCHEMA_VERSION`, cap segment ưu tiên theo vị trí; snippet PDF màn khoá cache theo `(bookId, page)`, không đọc lại cả file.

### 45. M16 — `PdfDocumentCache` lưu theo khúc + ghi atomic + tăng `CURRENT_SCHEMA_VERSION`, cap segment ưu tiên theo vị trí & cache snippet PDF màn khoá — 2026-11-19
**Nguyên nhân:**
- `PdfDocumentCache` lưu toàn bộ trang phân tích của cả sách vào một file JSON nguyên khối bằng `writeText`/`readText`, không qua `AtomicJsonFile`; mỗi lần `onPause` đổi trang khi bật đọc màn khoá (`ReaderActivity`), phân loại sách (`ReaderViewModel.ensurePdfClassified`), hoặc mở PDF ở chế độ gốc/cột (`PdfPagerReader`) đều decode toàn bộ ~1000 trang (~40 MB JSON) chỉ để lấy 2–5 trang hoặc thậm chí không dùng hết.
- `PdfPageAnalyzer.extractGraphicsStats` ngừng thu thập ngay khi `vectorSegments.size >= 400` / `filledRects.size >= 100` theo thứ tự content stream → bảng biểu hoặc công thức ở nửa dưới/cuối trang mất sạch đường kẻ; `CURRENT_SCHEMA_VERSION` giữ ở `2` nên cache cũ chưa cap không bị vô hiệu hoá.
- `ReaderActivity.extractLockScreenSnippets` chưa có cache snippet theo `(bookId, pageIndex)` cho PDF (khác với `cachedChapterSnippets` của EPUB).

**Đã sửa:**
- `reader/pdf/PdfDocumentCache.kt`:
  - Tăng `CURRENT_SCHEMA_VERSION` từ `2` lên `4` để vô hiệu hoá mọi file cache cũ chưa chia khúc/chưa cap.
  - Chia dữ liệu thành thư mục riêng cho từng sách (`manifest.json` + các file khúc `chunk_XXXX.json` kích thước `DEFAULT_CHUNK_SIZE = 50` trang), tất cả đều đọc/ghi an toàn qua `AtomicJsonFile` (xoá manifest trước khi ghi các khúc và ghi manifest sau cùng; khôi phục `.tmp` / cách ly `.corrupt-*`).
  - Bổ sung `loadManifest(file)`, `loadPages(file, pageIndices)` và `loadPageRange(file, startPage, endPageInclusive)` chỉ đọc đúng các khúc chứa các trang được yêu cầu, kèm LRU cache dùng chung theo tiến trình (`manifestMemoryCache` = 16, `chunkMemoryCache` = 8) khoá theo `(cacheDir, canonicalPath, fileSize, lastModified)`.
  - Tự dọn sạch thư mục khúc và file legacy cũ khi khác `schemaVersion` / `fileSize` / `lastModified` hoặc khi gọi `delete(file)` (đồng thời xoá khỏi LRU trong bộ nhớ).
- `reader/pdf/PdfPageAnalyzer.kt`:
  - Thêm `GraphicsCollectorBuffer` thu thập tới ngưỡng an toàn (`MAX_RAW_VECTOR_SEGMENTS_PER_PAGE = 4000`, `MAX_RAW_FILLED_RECTS_PER_PAGE = 1000`, nén nửa bộ đệm theo vị trí khi vượt ngưỡng) và định tuyến mọi nét vẽ trong `flushStrokedPath` qua buffer thay vì cắt cụt theo thứ tự content stream.
  - Tách hàm thuần Kotlin `capVectorSegmentsByPosition` (cap 400) và `capFilledRectsByPosition` (cap 100) ưu tiên giữ ứng viên kẻ bảng/ô bảng và phân bổ theo 20 dải dọc của trang (water-filling stratified sampling từ trên xuống dưới), đồng thời tính biến đổi CTM bằng trường số của `Matrix` (`transformCtm`) để tránh phụ thuộc `android.graphics.PointF`.
- `ReaderActivity.kt`, `ui/reader/ReaderViewModel.kt`, `ui/reader/PdfAnalysisSession.kt`, `ui/reader/PdfRendererHolder.kt`, `ui/reader/PdfPagerReader.kt`:
  - Thêm `PdfSnippetMemoryCache` (`cachedPdfPageSnippets`) theo `(bookId, pageIndex)` và hàm `extractPdfLockScreenSnippets`: ưu tiên cache bộ nhớ → `PdfDocumentCache.loadPages` (chỉ đọc khúc chứa `pageIdx`/`pageIdx + 1`) → fallback `PdfPageAnalyzer.analyzePages`.
  - `ReaderViewModel.ensurePdfClassified` đọc `loadManifest` + `loadPages(file, PdfDocumentClassifier.samplePageIndices(pageCount))` thay vì `load(file)`.
  - `PdfPagerReader` chỉ gọi `cache.load(file)` khi `isReflowMode == true`; khi ở `SMART_ORIGINAL` / `GUIDED_COLUMN`, `PdfRendererHolder` / `PdfAnalysisSession` kiểm tra `cache.loadPages` theo từng trang trước khi mở `PDDocument`.
- Unit test mới & mở rộng: `test/.../reader/pdf/PdfDocumentCacheTest.kt` (6 tests), `test/.../reader/pdf/PdfPageAnalyzerCapTest.kt` (6 tests), `test/.../ReaderActivityPdfSnippetTest.kt` (1 test), `test/.../ui/reader/PdfAnalysisSessionTest.kt` (+1 test).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **229/229 tests pass** qua 41 file test, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 46 — L15: Trang fallback trong Reflow (`cd_pdf_page`, spinner/lỗi/retry, cho zoom); ảnh inline không cướp vùng tap lật trang + có `contentDescription`; nút "Đóng" overlay ≥ 48 dp; volume không lật khi overlay mở.

### 46. L15 — Trang fallback trong Reflow (`cd_pdf_page`, spinner/lỗi/retry, cho zoom); ảnh inline không cướp vùng tap lật trang + có `contentDescription`; nút "Đóng" overlay ≥ 48 dp; volume không lật khi overlay mở — 2026-11-19
**Nguyên nhân:**
- Trang fallback trong Reflow (`PaginatedReflowElement.FallbackPage`) dùng chuỗi cứng `"Trang ${singleFallback.sourcePage + 1}"` cho `contentDescription`, không có spinner khi đang tải (`fallbackBitmap == null && !renderFailed`), không có thông báo lỗi + nút Thử lại khi render thất bại, và không cho double-tap để phóng to trang fallback.
- Ảnh inline (`PaginatedReflowElement.InlineImage`) dùng `Modifier.fillMaxWidth().clickable { onImageTap(element) }` chiếm hết chiều ngang màn hình nên cướp luôn vùng chạm 25% cạnh trái/phải dùng để lật trang của màn Reflow, và thiếu `contentDescription` (cả trên màn hình Reflow lẫn trong `PdfReflowImageZoomOverlay`).
- Nút "Đóng" trên `PdfReflowImageZoomOverlay` chỉ có `padding(horizontal = 14.dp, vertical = 8.dp)` (~35 dp cao, dưới chuẩn 48 dp).
- Khi `PdfReflowImageZoomOverlay` đang mở, bấm phím âm lượng vẫn gọi `controller.goBackward()`/`controller.goForward()` làm lật màn Reflow bên dưới.

**Đã sửa:**
- `res/values/strings.xml`, `res/values-vi/strings.xml`: thêm chuỗi `cd_pdf_inline_image` (EN: `"Illustration on page %1$d"`, VI: `"Hình minh họa trang %1$d"`).
- `reader/pdf/PdfReflowPaginator.kt`: thêm `ReflowImageTapAction` (`PREV_PAGE`, `NEXT_PAGE`, `OPEN_ZOOM`) và hàm thuần Kotlin `resolveReflowImageTapAction(tapX, containerWidth)` bảo toàn dải chạm 25% trái/phải để lật trang và 50% vùng giữa để mở zoom/tác vụ trung tâm.
- `ui/reader/PdfPagerReader.kt`:
  - Trang fallback trong Reflow: đổi `contentDescription` sang `stringResource(R.string.cd_pdf_page, singleFallback.sourcePage + 1)`; bổ sung `renderFailed` + `retryNonce` để hiển thị `CircularProgressIndicator` khi đang tải và `pdf_page_render_error` + nút `action_retry` khi lỗi; hỗ trợ `onDoubleTap` mở `PdfReflowImageZoomOverlay` với `ReflowZoomTarget.FallbackPage` trong khi chạm 25% trái/phải vẫn lật trang và chạm vùng giữa bật/tắt thanh công cụ.
  - Ảnh inline (`PaginatedReflowElement.InlineImage`): bỏ `.clickable` toàn chiều ngang, thay bằng `pointerInput` + `detectTapGestures` dùng `resolveReflowImageTapAction` (chạm 25% trái/phải gọi `onPrevPage()`/`onNextPage()`, chạm vùng giữa hoặc double-tap mở `PdfReflowImageZoomOverlay`); gắn `contentDescription = stringResource(R.string.cd_pdf_inline_image, element.sourcePage + 1)` cho ảnh inline và ảnh trong `PdfReflowImageZoomOverlay`.
  - Nút "Đóng" (`action_close`) trên `PdfReflowImageZoomOverlay`: thêm `.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).heightIn(min = 48.dp)` và `contentAlignment = Alignment.Center`.
  - Chặn lật màn Reflow khi overlay mở: thêm cờ `overlayOpen` trên `PdfPagerController` (chặn trong `goBackward()`/`goForward()`), đồng bộ qua `DisposableEffect(controller, zoomedTarget)`, kiểm tra `zoomedTarget == null` trong `controller.onPrev`/`onNext` và khoá `userScrollEnabled = zoomedTarget == null` trên `HorizontalPager` của Reflow.
- `test/.../reader/pdf/PdfReflowPaginatorTest.kt`: thêm 2 unit test (`resolveReflowImageTapActionPreservesLeftAndRightPageTurnZones`, `pdfPagerControllerSuppressesPrevAndNextWhenOverlayIsOpen`).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **231/231 tests pass**, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 47 — M17: Repository load trên IO + `loaded` flag (VM chờ); `warmUpForUiAsync` trong `ReaderActivity`; khởi tạo/gọi sync ngoài main; `LibraryRepository` nhận `Lazy<ReadiumEngine>`; `PdfBookConfigStore` ghi trên IO.

### 47. M17 — Repository load trên IO + `loaded` flag (VM chờ); `warmUpForUiAsync` trong `ReaderActivity`; khởi tạo/gọi sync ngoài main; `LibraryRepository` nhận `Lazy<ReadiumEngine>`; `PdfBookConfigStore` ghi trên IO — 2026-11-19
**Nguyên nhân:**
- Cold start từ widget/notification vào `ReaderActivity` không gọi `warmUpForUiAsync()`; lazy `viewModel` truy cập `container.library` (đọc đồng bộ `library.json` và đánh giá ngay `engine` dựng `ReadiumEngine`), `pdfBookConfigs`, `annotations` (cùng `syncTombstones`), `stats` trên main thread. Ở `MainActivity`/`LauncherActivity`, `warmUpForUiAsync()` chạy song song nhưng `by lazy` (`SYNCHRONIZED`) vẫn chặn main chờ IO.
- `ReaderActivity.onPause` (và `LibraryViewModel` sau import/xoá) chạm `container.sync` lần đầu trên main thread, dựng `GoogleDriveSyncManager` và chạy di trú/đọc prefs + `SyncTombstoneStore` trên main.
- `PdfBookConfigStore.update` ghi đĩa + `fsync` đồng bộ ngay trên main thread trong khi đọc/đổi mode PDF.

**Đã sửa:**
- `data/book/LibraryRepository.kt`, `AppContainer.kt`:
  - `LibraryRepository` thêm `constructor(context: Context, engineLazy: Lazy<ReadiumEngine>)` (giữ tương thích `constructor(context, engine)`), truyền `engineLazy` từ `AppContainer` để không khởi tạo `ReadiumEngine` cho tới khi mở/nhập sách.
  - Khởi tạo state rỗng + `loaded: StateFlow<Boolean>` (`preloadInBackground = true` khi tạo từ `Context`), đọc `library.json` trên `Dispatchers.IO` qua `awaitLoaded()`/`ensureLoadedLocked()` trong `writeMutex` trước khi `backfillMissingCovers()` và mọi thao tác đọc/ghi (`importWithHashes`, `remove`, `saveProgress`, `savePdfReadingMode`, `ensureContentHashes`, `importFromSyncedFile`, `commit`).
- `data/annotation/AnnotationRepository.kt`, `data/stats/ReadingStatsRepository.kt`, `data/sync/SyncTombstoneStore.kt`:
  - Bổ sung `loaded: StateFlow<Boolean>`, `awaitLoaded()`, `ensureLoadedLocked()` và khởi tạo không chặn I/O khi tạo từ `AppContainer` (`preloadInBackground = true`), nạp JSON trên `Dispatchers.IO` và bảo vệ mọi hàm ghi/xoá/merge (`dropDeleted`, `mergeFromRemote`, `recordReading`, `setDailyGoalMinutes`, `snapshot`, `mutate`), đồng thời giữ tương thích đồng bộ mặc định cho các constructor test cũ.
- `reader/pdf/PdfBookConfigStore.kt`:
  - Thêm `loaded: StateFlow<Boolean>`, `awaitLoaded()` và `flushForTest()` + tham số `ioDispatcher` cho test; khi tạo từ `Context` (`AppContainer`), nạp trên `Dispatchers.IO.limitedParallelism(1)` và trong `update(bookId, transform)` cập nhật ngay `configs` trong bộ nhớ, đẩy việc ghi đĩa qua `CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))` tuần tự (giữ nguyên biến đổi phát sinh trước khi preload xong để không mất dữ liệu cũ).
- `ui/reader/ReaderViewModel.kt`, `ReaderActivity.kt`, `ui/library/LibraryViewModel.kt`, `ui/library/LibraryScreen.kt`, `data/sync/GoogleDriveSyncManager.kt`, `reader/LockScreenNavReceiver.kt`, `system/EInkReadingWidgetProvider.kt`:
  - `ReaderActivity.onCreate` gọi `container.warmUpForUiAsync()`; `AppContainer.warmUpForUiAsync()` khởi động trước cả `pdfBookConfigs`, `syncTombstones`, `lockScreenReading`, `sync`.
  - `ReaderActivity.onPause` (và `LibraryViewModel` khi unmark/delete cloud) gọi `container.sync` qua `container.appScope.launch(Dispatchers.IO)`.
  - `ReaderViewModel.load()` chạy trên `ioDispatcher` (`Dispatchers.IO`), gọi `awaitLoaded()` cho `repository`, `pdfBookConfigs`, `annotations`, `statsRepository` trước `repository.find(bookId)` và mở sách (`repository.engine.open(file)`).
  - `GoogleDriveSyncManager` chuyển di trú legacy `KEY_DELETED_HASHES` sang `backgroundScope` (IO) và gọi `awaitLoaded()` trước khi sync/xoá cloud; `LibraryViewModel.remove`, `LockScreenNavReceiver`, `EInkReadingWidgetProvider` chờ `awaitLoaded()` khi chưa nạp xong; `LibraryScreen` giữ `selectedAuthor` và tránh nhấp nháy empty state trước khi `booksLoaded == true`.
- Unit test mới & mở rộng (**+9 tests**): `LibraryRepositoryTest.kt` (+2), `PdfBookConfigStoreTest.kt` (+3), `AnnotationRepositoryTest.kt` (+1), `ReadingStatsTest.kt` (+1), và mới `ui/reader/ReaderViewModelLoadTest.kt` (2 tests).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **240/240 tests pass** qua 42 file test, `0 lint errors`).

**Còn để lại:** kiểm tra tay mục 14 trong `docs/audit-2026-11-19.md` (force-stop → chạm widget/notification với StrictMode).

**Bước tiếp theo:** mục 48 — M18: Thay mốc 1 s bằng cờ một lần `expectRelayout`/`expectJump` (đặt ở `onConfigurationChanged` và trước `navigator.go`, timeout dự phòng ~3 s).

### 48. M18 — Thay mốc 1 s bằng cờ một lần `expectRelayout`/`expectJump` (đặt ở `onConfigurationChanged` và trước `navigator.go`, timeout dự phòng ~3 s) — 2026-11-19
**Nguyên nhân:**
- `ReaderViewModel` dùng cửa sổ chặn cố định `suppressPageTurnUntilMs` (`SETTINGS_RELAYOUT_SUPPRESS_MS = 1000L`) chỉ khi gọi `updateSettings` (thay đổi thuộc tính bố cục) hoặc `updatePdfConfig`:
  - Xoay máy hoặc đổi kích thước cửa sổ (`configChanges="orientation|screenSize|screenLayout|smallestScreenSize"`) làm Readium/PDF dàn trang lại và phát `onLocatorChanged` mà không qua `updateSettings` → bị tính nhầm là lật trang (`pendingPages++` + flash/ghost).
  - Trên máy e-ink chậm, dàn trang > 1 s vẫn bị tính là lật trang; ngược lại nếu dàn trang xong nhanh và người dùng lật trang thật trong vòng 1 s thì lần lật thật bị nuốt.
  - Nhảy trang chủ động qua thanh tiến độ (`onSeekToProgress`), mục lục TOC (`onSelectToc`), bookmark/highlight (`onSelectLocatorJson`), hoặc kết quả tìm kiếm (`onSelectLocator`) trong `ReaderScreen.kt` không đặt cờ chặn nên cũng bị đếm là lật trang và kích hoạt flash.

**Đã sửa:**
- `ui/reader/ReaderViewModel.kt`:
  - Thay `suppressPageTurnUntilMs` (`1_000L`) bằng cờ một lần `pendingNonTurnTransition` kèm thời hạn dự phòng `nonTurnTransitionExpiresAtMs` (`RELAYOUT_JUMP_FALLBACK_TIMEOUT_MS = 3_000L`).
  - Thêm `expectRelayout()` (gọi khi `updateSettings` đổi thuộc tính bố cục, khi `updatePdfConfig`, và khi `ReaderActivity.onConfigurationChanged`) và `expectJump()` (gọi trước các thao tác nhảy trang chủ động).
  - Thêm `consumeNonTurnTransitionIfActive()`: khi `onPositionKeyChanged` (với `previous != null && previous != key`) hoặc `onSubPageScreenTurned()` được gọi, nếu cờ đang bật và chưa quá 3 s thì tiêu thụ (xoá) cờ ngay lập tức và bỏ qua `recordPageTurnStep()`; lần lật trang thật ngay sau đó của người dùng vẫn được đếm bình thường.
- `ReaderActivity.kt`:
  - Override `onConfigurationChanged(newConfig: Configuration)` gọi `super.onConfigurationChanged(newConfig)` và `viewModel.expectRelayout()`.
- `ui/reader/ReaderScreen.kt`:
  - Gọi `viewModel.expectJump()` trước khi nhảy trang bằng `navigator?.go(...)` hoặc `pdfController.jumpToPage(...)` tại `onSeekToProgress` (`ReaderBottomBar`), `onSelectToc` & `onSelectLocatorJson` (`ReaderContentsSheet`), và `onSelectLocator` (`ReaderSearchSheet`).
- `test/.../ui/reader/ReaderViewModelPageTurnTest.kt`:
  - Cập nhật và bổ sung unit test `relayoutSuppressionConsumesExactlyOneTransitionEvenOverOneSecondAndCountsImmediateNextTurn` và `expectRelayoutOrJumpExpiresAfterFallbackTimeoutWhenNoTransitionArrives` (**241/241 tests pass**).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **241/241 tests pass** qua 42 file test, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 49 — L16 + L21: Dải cạnh trái: tap = lùi trang hoặc chuyển drag sang `InputListener` Readium; phím âm lượng bỏ qua khi Search/Contents/Settings/toolbar đang mở (cờ `overlayOpen` trong VM).

### 49. L16 + L21 — Dải cạnh trái chạm lùi trang & bỏ qua phím âm lượng khi mở overlay/toolbar trong Reader — 2026-11-19
**Nguyên nhân:**
- **L16:** Dải cạnh trái `14.dp` ở `ReaderScreen.kt` chỉ xử lý `detectVerticalDragGestures` để kéo chỉnh độ sáng, không xử lý `onTap` → vùng `14.dp` sát mép trái thành vùng chết khi chạm (chặn chạm lùi trang truyền xuống nội dung bên dưới).
- **L21:** `ReaderActivity.onKeyDown`/`onKeyUp` chỉ kiểm tra `pendingNoteLocator.value != null || viewModel.dictionaryState.value != null`; khi đang mở Search sheet, Contents sheet, Settings panel hoặc thanh toolbar (`chromeVisible`), bấm phím âm lượng vẫn lật trang bên dưới thay vì điều chỉnh âm lượng hệ thống.

**Đã sửa:**
- `ui/reader/ReaderScreen.kt` (**L16 + L21**):
  - Bổ sung `.pointerInput(Unit) { detectTapGestures(onTap = { latestOnLeftEdgeTap() }) }` tách riêng khỏi `.pointerInput(Unit)` của `detectVerticalDragGestures` trên dải cạnh trái `14.dp`: chạm mép trái gọi `pdfController.goBackward()` (với PDF) hoặc `(navigator as? OverflowableNavigator)?.goBackward(animated = settings.animations)` (với EPUB/CBZ), trong khi kéo dọc vẫn chỉnh độ sáng mượt.
  - Đồng bộ `viewModel.setOverlayOpen(chromeVisible || settingsOpen || contentsOpen || searchOpen)` qua `LaunchedEffect(chromeVisible, settingsOpen, contentsOpen, searchOpen)` và reset về `false` trong `DisposableEffect(Unit) { onDispose { viewModel.setOverlayOpen(false) } }`.
- `ui/reader/ReaderViewModel.kt` (**L21**):
  - Thêm `overlayOpen: StateFlow<Boolean>` (`mutableOverlayOpen`) và `fun setOverlayOpen(open: Boolean)`.
- `ReaderActivity.kt` (**L21**):
  - Tách hàm thuần Kotlin `shouldHandleVolumePageTurn(volumeButtonsTurnPages, pendingNoteOpen, dictionaryOpen, overlayOpen)` và helper `canTurnPageWithVolumeKey(keyCode)` dùng chung cho cả `onKeyDown` và `onKeyUp`, bỏ qua lật trang bằng phím âm lượng khi `viewModel.overlayOpen.value == true` (ngoài cài đặt `volumeButtonsTurnPages`, dialog ghi chú và dialog từ điển).
- `test/.../ui/reader/ReaderViewModelPageTurnTest.kt`:
  - Thêm 2 unit test mới (`setOverlayOpenUpdatesOverlayStateFlowAndSuppressesVolumePageTurn`, `shouldHandleVolumePageTurnBlocksWhenSettingDisabledOrAnyModalOrOverlayOpen`).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **243/243 tests pass** qua 42 file test, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 50 — L17 + L18: Dialog ghi chú (`dismissOnBackPress = false`/xác nhận, nháp + `pendingNoteLocator` vào ViewModel/SavedStateHandle, không toast khi quote trống) và quyền thông báo (xin lại khi còn rationale, nếu không thì snackbar mở cài đặt thông báo).

### 50. L17 + L18 — Dialog ghi chú (`dismissOnBackPress = false`, nháp + `pendingNoteLocator` trong ViewModel + `onSaveInstanceState`, không toast khi quote trống) & Quyền thông báo màn khoá (xin lại khi còn rationale, nếu bị chặn thì hiện Snackbar mở cài đặt thông báo) — 2026-11-19
**Nguyên nhân:**
- **L17 (INC #25):**
  - `HighlightNoteDialog` chỉ đặt `DialogProperties(dismissOnClickOutside = false)` mà chưa đặt `dismissOnBackPress = false` → bấm Back khi đang gõ ghi chú sẽ đóng ngay dialog và làm mất nháp.
  - `ReaderActivity` gọi `super.onCreate(null)` để tránh crash khôi phục Fragment của Readium, khiến `rememberSaveable` trong `HighlightNoteDialog` và field `pendingNoteLocator` trên `ReaderActivity` bị mất khi recreate Activity (đổi dark mode hệ thống, xoay màn hình, "Don't keep activities") hoặc sau process death.
  - Khi bấm menu `MENU_HIGHLIGHT` trong `ReaderActivity`, Toast `reader_highlight_saved` ("Đã lưu highlight") luôn hiển thị kể cả khi `locator.text.highlight` trống (trong khi `ReaderViewModel.addHighlight` bỏ qua không lưu).
- **L18 (INC #24):**
  - Dialog rationale (`notif_rationale_title`) không chặn `dismissOnClickOutside = false` và đánh dấu `notif_asked_once = true` ngay cả khi người dùng chạm ra ngoài hoặc bấm "Để sau" (`notif_rationale_later`) — làm mất cơ hội hiện lại rationale mà chưa hề gọi system permission prompt.
  - Sau lần hỏi đầu tiên (`notif_asked_once = true`), khi người dùng bật lại toggle "Đọc trên màn hình khóa" (`lockScreenReader = true`) mà chưa có quyền `POST_NOTIFICATIONS` (hoặc thông báo bị tắt ở cấp ứng dụng), code tự tắt `lockScreenReader = false` im lặng mà không xin quyền lại khi `shouldShowRequestPermissionRationale == true`, cũng không báo hay dẫn người dùng tới màn hình Cài đặt thông báo khi quyền đã bị chặn vĩnh viễn.

**Đã sửa:**
- `ui/reader/HighlightNoteDialog.kt` (**L17**):
  - Đặt `DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false)` để chỉ đóng dialog khi người dùng chủ động bấm "Hủy" hoặc "Lưu".
  - Chuyển state `note` từ `rememberSaveable` cục bộ sang nhận `note: String` và `onNoteChange: (String) -> Unit` từ `ReaderViewModel`.
- `ui/reader/ReaderViewModel.kt` (**L17 + L18**):
  - Đổi `addHighlight(locator, note)` sang trả về `Boolean` (`false` nếu `locator.text.highlight` trống/blank, `true` nếu hợp lệ và đã gửi lưu).
  - Thêm `pendingNoteLocator: StateFlow<Locator?>`, `pendingNoteDraft: StateFlow<String>` và các hàm `openNoteDialog`, `updateNoteDraft`, `dismissNoteDialog`, `savePendingNote`, `pendingNoteLocatorJsonSnapshot`, `restorePendingNoteIfAbsent` để giữ nguyên dialog ghi chú + nội dung nháp qua configuration changes và khôi phục từ `savedInstanceState`.
  - Thêm `notificationPermissionBlockedCount: StateFlow<Int>`, `notifyNotificationPermissionBlocked()` và `consumeNotificationPermissionBlocked()` để báo `ReaderScreen` hiển thị Snackbar mở cài đặt thông báo.
- `ReaderActivity.kt`, `ui/reader/ReaderScreen.kt`, `res/values/strings.xml`, `res/values-vi/strings.xml` (**L17 + L18**):
  - Lưu `STATE_PENDING_NOTE_LOCATOR_JSON` và `STATE_PENDING_NOTE_DRAFT` trong `onSaveInstanceState` và khôi phục vào `viewModel.restorePendingNoteIfAbsent(...)` trong `onCreate` trước/sau `super.onCreate(null)`.
  - Ở `MENU_HIGHLIGHT` và `onSaveNote`, chỉ hiển thị Toast `reader_highlight_saved` khi `viewModel.addHighlight(...) == true`.
  - Thêm chuỗi `notif_permission_blocked_msg` và `notif_open_settings_action` vào `values/strings.xml` & `values-vi/strings.xml`.
  - Tách hàm thuần Kotlin `resolveNotificationPermissionAction(sdkInt, hasRuntimePermission, areNotificationsEnabled, askedOnce, shouldShowRationale)` trả về `PROCEED`, `SHOW_RATIONALE`, hoặc `BLOCKED_OPEN_SETTINGS`.
  - Trên dialog rationale: đặt `DialogProperties(dismissOnClickOutside = false)`, chỉ ghi `notif_asked_once = true` khi người dùng bấm nút `notif_rationale_allow` để gọi system prompt (`requestNotificationPermission.launch(...)`); bấm "Để sau" hoặc đóng dialog chỉ tắt `lockScreenReader = false`.
  - Khi `resolveNotificationPermissionAction` trả về `BLOCKED_OPEN_SETTINGS` (hoặc kết quả `RequestPermission` bị từ chối với `shouldShowRequestPermissionRationale == false`), tắt `lockScreenReader = false` và gọi `viewModel.notifyNotificationPermissionBlocked()` để `ReaderScreen` hiện Snackbar kèm nút hành động mở `openAppNotificationSettings()` (`Settings.ACTION_APP_NOTIFICATION_SETTINGS` với fallback `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` và Toast báo lỗi nếu không mở được).
- `test/.../ui/reader/ReaderViewModelPageTurnTest.kt`:
  - Thêm 3 unit test mới (`addHighlightReturnsFalseForBlankQuoteAndTrueForNonBlankQuote`, `pendingNoteStateSupportsDraftEditingSnapshotAndSavedInstanceStateRestoration`, `resolveNotificationPermissionActionHandlesFirstRequestRationaleAndBlockedCases`).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **246/246 tests pass** qua 42 file test, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 51 — L19 + L20 + L22 (reader) + L23: Ghost chỉ áp filter ở fallback API 24–25, bỏ `recycle()`, chỉ đảo slot khi capture thành công; throttle tiến độ: phần ghi `NonCancellable`, hẹn lại khi còn pending; `retryLoad` bỏ qua khi đang Loading; footer clock theo lifecycle.

### 51. L19 + L20 + L22 (reader) + L23 — Ghost chỉ áp filter ở fallback API 24–25, bỏ `recycle()`, chỉ đảo slot khi capture thành công; throttle tiến độ: phần ghi `NonCancellable`, hẹn lại khi còn pending; `retryLoad` bỏ qua khi đang Loading; footer clock theo lifecycle — 2026-11-19
**Nguyên nhân:**
- **L19 (REG #18):**
  - Trên API 26+, `PixelCopy.request(window, ...)` chụp từ window surface đã đi qua hardware layer color filter của `ReaderScreen`, nhưng `ReaderGhostAndFlashLayer` vẫn truyền `ghostColorFilter` vào `RefreshFlashOverlay` khiến ảnh ghost bị áp color filter lần thứ 2 (CBZ ở tone DARK bị đảo màu ngược trở lại).
  - `captureGhostSnapshot` gọi `existing?.recycle()` khi tạo lại buffer lúc đổi kích thước view, có nguy cơ đụng bitmap mà `RefreshFlashOverlay` đang vẽ ở frame cuối.
  - `ReaderGhostAndFlashLayer` đảo buffer slot trước khi biết kết quả chụp và trên API 26+ khi `PixelCopy` thất bại lại rơi xuống `view.draw(canvas)`.
- **L20 (INC #3):**
  - `progressSaveJob` chỉ chạy 1 lần `delay(PROGRESS_SAVE_INTERVAL_MS)` rồi `flushProgress()`; nếu một locator mới đến trong lúc `flushProgress()` đang chờ `repository.saveProgress(...)` thì `progressSaveJob?.isActive == true` nên không tạo job mới và locator mới không được hẹn lưu tiếp.
  - `flushProgress()` xoá `pendingProgress = null` trước khi gọi `repository.saveProgress(...)` mà không bọc trong `withContext(NonCancellable)`, nên khi `onReaderForegroundChanged(false)` (`onPause`) gọi `progressSaveJob?.cancel()` đúng lúc đang chờ `writeMutex`/IO thì tiến độ đang ghi bị huỷ và mất luôn.
- **L22 (reader):**
  - `retryLoad()` luôn gọi `viewModelScope.launch { load() }` mà không kiểm tra job đang chạy → bấm "Thử lại" nhiều lần liên tiếp tạo nhiều `load()` chạy song song và có thể làm rò rỉ `Publication` mở trước đó.
- **L23 (NEW):**
  - `ReaderFooterBar` dùng `while (true)` trực tiếp trong `LaunchedEffect(context)` mà không gắn với lifecycle → vòng lặp cập nhật đồng hồ vẫn thức dậy mỗi phút ngay cả khi Activity đang ở trạng thái `STOPPED`.

**Đã sửa:**
- `ui/reader/ReaderScreen.kt` (**L19**):
  - Thêm `GhostSnapshotCapture(bitmap, capturedViaPixelCopy)` và hàm thuần Kotlin `shouldApplyGhostOverlayFilter(sdkInt, capturedViaPixelCopy)` (`true` chỉ khi `sdkInt < Build.VERSION_CODES.O || !capturedViaPixelCopy`).
  - Trong `captureGhostSnapshot`: bỏ `existing?.recycle()` (để GC thu hồi an toàn khi đổi kích thước); trên API 26+ chỉ trả về ảnh khi `PixelCopy.SUCCESS` (`capturedViaPixelCopy = true`, thất bại trả về `null`), trên API 24–25 dùng fallback `Canvas` (`capturedViaPixelCopy = false`).
  - Trong `ReaderGhostAndFlashLayer`: chỉ đảo `nextBufferSlot = 1 - slotIndex` và cập nhật `latestSnapshot` khi `captured != null`; chỉ truyền `ghostColorFilter` vào `RefreshFlashOverlay` khi `shouldApplyGhostOverlayFilter(...) == true` (trên API 26+ với `PixelCopy` truyền `null`).
- `ui/reader/ReaderViewModel.kt` (**L20 + L22 reader**):
  - Trong `onLocatorChanged`: đổi `progressSaveJob` sang vòng lặp `while (pendingProgress != null && isActive) { delay(PROGRESS_SAVE_INTERVAL_MS); flushProgress() }` để locator mới đến trong lúc đang lưu luôn được hẹn lưu ở nhịp kế tiếp.
  - Trong `flushProgress()`: bọc phần xoá `if (pendingProgress === snapshot) pendingProgress = null` và `repository.saveProgress(...)` trong `withContext(NonCancellable)` để `progressSaveJob?.cancel()` ở `onPause` chỉ huỷ `delay` chứ không bao giờ làm đứt quãng lần ghi đã lấy `snapshot`.
  - Thêm `private var loadJob: Job? = null` và `startLoad()` dùng chung cho `init` và `retryLoad()`: bỏ qua ngay nếu `loadJob?.isActive == true`, huỷ `loadJob` trong `onCleared()`, và đóng `previousPub.close()` an toàn nếu đã có `Publication` cũ trước khi gán `publication = pub`.
- `ui/reader/ReaderFooterBar.kt` (**L23**):
  - Dùng `LocalLifecycleOwner.current` và `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` bao quanh vòng lặp `while (true)` cập nhật đồng hồ chân trang.
- `test/.../ui/reader/ReaderViewModelPageTurnTest.kt`, `test/.../ui/reader/ReaderViewModelLoadTest.kt`:
  - Thêm 3 unit test mới (`shouldApplyGhostOverlayFilterSkipsFilterForPixelCopyOnApi26PlusAndAppliesForCanvasFallback`, `throttledProgressSaveReschedulesLocatorArrivingDuringInFlightSaveAndSurvivesCancelOnPause`, `retryLoadIgnoresDuplicateCallsWhileLoadingIsAlreadyActive`).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **249/249 tests pass** qua 42 file test, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 52 — M19 + M20: Bìa ở Launcher áp grayscale theo setting; khu app ghim cuộn được / giới hạn hàng + "Xem thêm".

### 52. M19 + M20 — Bìa ở Launcher áp grayscale theo setting; khu app ghim cuộn được / giới hạn hàng + "Xem thêm" — 2026-11-19
**Nguyên nhân:**
- **M19 (REG #15):** `CoverBitmapCache` trả bitmap bìa màu nguyên bản để nơi hiển thị tự áp `ColorFilter` theo `settings.grayscale`, nhưng `CurrentBookHeroCard` trong `LauncherActivity.kt` vẽ `Image(bitmap = currentCover, ...)` mà chưa truyền `grayscale` / `colorFilter`.
- **M20 (REG #27):** `MinimalistHomePage` dùng `Column(fillMaxSize, SpaceBetween)` không cuộn và hiển thị toàn bộ `pinnedApps` không giới hạn; khi ghim nhiều app (hoặc trên màn hình thấp / xoay ngang), nội dung phía trên đẩy thanh dock `Library` / `Apps` ở đáy ra khỏi màn hình.

**Đã sửa:**
- `ui/eink/CoverBitmapCache.kt` & `ui/library/BookCover.kt` (**M19**):
  - Thêm helper dùng chung `CoverBitmapCache.colorFilterFor(grayscale: Boolean)` trả về `ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })` khi `grayscale == true` và `null` khi `false`; `BookCover` gọi lại helper này qua `remember(grayscale)`.
- `LauncherActivity.kt`, `res/values/strings.xml`, `res/values-vi/strings.xml` (**M19 + M20**):
  - Truyền `grayscale = settings.grayscale` qua `EInkHomePager` → `MinimalistHomePage` → `CurrentBookHeroCard`, áp `remember(grayscale) { CoverBitmapCache.colorFilterFor(grayscale) }` cho `Image` bìa sách.
  - Tách thanh dock đáy thành `LauncherBottomDock` cố định ngoài vùng nội dung chính; bọc toàn bộ phần nội dung phía trên dock trong `BoxWithConstraints(Modifier.weight(1f, fill = true).fillMaxWidth())` với `Column` có `verticalScroll(rememberScrollState()).heightIn(min = maxHeight)` và `HomeSectionArrangement` (giữ giãn đều `SpaceBetween` khi đủ chỗ và khoảng cách tối thiểu `12.dp` khi cuộn trên màn hình thấp/xoay ngang).
  - Tách hàm thuần Kotlin `slicePinnedAppsForHome(pinnedApps, maxVisible = HOME_PINNED_MAX_VISIBLE)` (giới hạn tối đa `8` app = 2 hàng trên Home) và `layoutHomeSectionPositions(...)`; khi `overflowCount > 0` hiển thị thêm nút `OutlinedButton` `"Xem thêm (+%1$d) ›"` / `"More (+%1$d) ›"` (`launcher_pinned_more`) mở App Drawer.
- `test/.../LauncherHomeLayoutTest.kt`:
  - Thêm 3 unit test mới (`slicePinnedAppsForHomeKeepsAllAppsUpToMaxAndCountsOverflowAboveMax`, `layoutHomeSectionPositionsUsesMinimumSpacingWhenOverflowingAndSpaceBetweenWhenExtraHeightExists`, `coverBitmapCacheColorFilterForMatchesGrayscaleFlag`).

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **252/252 tests pass** qua 43 file test, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 53 — M21 + L27: Chặn pop làm rỗng back stack; tab Stats `rememberSaveable`.

### 53. M21 + L27 — Chặn pop làm rỗng back stack; tab Stats rememberSaveable — 2026-11-19
**Nguyên nhân:**
- **M21 (REG #21):** `onBack = { backStack.removeLastOrNull() }` ở `NavDisplay` và `ReadingStatsScreen` không kiểm tra kích thước back stack; khi bấm Back/nút "‹" nhanh 2 lần lúc animation thoát, back stack bị pop rỗng gây crash (`require(backstack not empty)` ở Navigation 3). Đồng thời, bấm đúp nút mở Stats (`onOpenStats`) có thể đẩy nhiều màn `ReadingStats` trùng nhau vào stack.
- **L27 (NEW):** Tab Stats dùng `remember` (`ReadingStatsSheet.kt`) nên khi đổi ngôn ngữ (`recreate()` Activity), trạng thái tab bị reset về "Tổng quan".

**Đã sửa:**
- `Navigation.kt` & `NavigationUtils.kt` (**M21**):
  - Thêm các helper thuần Kotlin `popBackStackSafely` và `pushNavKeyIfNotTop`; cập nhật `NavDisplay` chỉ pop khi `backStack.size > 1` và `ReadingStats` chỉ pop khi `backStack.size > last && last == ReadingStats`.
  - `onOpenStats` kiểm tra `backStack.lastOrNull() != ReadingStats` trước khi push.
- `ui/library/ReadingStatsSheet.kt` (**L27**):
  - Đổi `var selectedTab by remember { mutableIntStateOf(0) }` sang `rememberSaveable { mutableIntStateOf(0) }` để giữ nguyên tab đang chọn khi recreate Activity (đổi ngôn ngữ).
- `test/.../NavigationUtilsTest.kt`:
  - Thêm unit test cho `popBackStackSafely` và `pushNavKeyIfNotTop`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **255/255 tests pass** qua 44 file test, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 54 — M22 + L26: TutorialOverlay chặn touch bằng `pointerInput` + ẩn semantics phía dưới; a11y còn lại.

### 54. M22 + L26 — TutorialOverlay chặn touch bằng pointerInput + ẩn semantics phía dưới; a11y còn lại — 2026-11-19
**Nguyên nhân:**
- **M22 (INC #25):** `TutorialOverlay` dùng `.clickable(indication = null) {}` để chặn chạm nên gộp toàn bộ nội dung overlay thành một node "button" với TalkBack; cây semantics của `LibraryScreen` phía dưới vẫn hiển thị nên TalkBack vẫn có thể vuốt xuống nút Xoá sách.
- **L26 (INC #26):** Swatch chọn tone ở Launcher là `18.dp` không nhãn/role; các nút `?`, `Stats`, `+`, `viewMode`, xoá tìm kiếm `×` có vùng chạm `< 48.dp`; các chip/pill chọn tone, hướng, ngôn ngữ, tác giả, sort, font, chế độ PDF thiếu `Role.RadioButton` + trạng thái `selected`; `ToggleOptionRow`, các switch cài đặt và `SwitchRow` bị tách thành 2 điểm focus; biểu đồ 7 ngày và heatmap 12 tuần thiếu `contentDescription` cho TalkBack.

**Đã sửa:**
- `ui/library/TutorialOverlay.kt` & `ui/library/LibraryScreen.kt` (**M22 + L26**):
  - `TutorialOverlay`: thay `.clickable(...) {}` bằng `.pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } }` để chặn chạm mà không phá cây semantics của overlay; `ToneChip` dùng `.selectable(selected, role = Role.RadioButton)`; `ToggleOptionRow` dùng `.toggleable(value = checked, role = Role.Switch)` + `.semantics(mergeDescendants = true) {}` với `Switch(..., onCheckedChange = null)`.
  - `LibraryScreen`: gắn `.clearAndSetSemantics {}` cho `Column` nội dung thư viện khi `tutorialOpen == true`; mở rộng vùng chạm các nút `?`, `Stats`, `+`, `viewMode`, xoá tìm kiếm `×` lên `48.dp` (giữ nguyên kích thước hiển thị và đặt `contentDescription` đầy đủ); tách nút sort thành các `FilterPill` cho từng `LibrarySort` và gắn `.selectable(selected = active, role = Role.RadioButton)` cho `FilterPill`.
- `LauncherActivity.kt` (**L26**):
  - Bọc mỗi swatch tone trong `Box(Modifier.size(48.dp))` với `.selectable(selected, role = Role.RadioButton)` và `contentDescription` theo `tone_*`; đặt `Modifier.weight(1f)` cho dòng trạng thái pin/streak để không đẩy 5 swatch tràn khỏi màn hình hẹp.
  - Tăng nút xoá tìm kiếm `×` (dùng `cd_clear_search`) và nút `viewMode` ở App Drawer lên `48.dp`.
- `ui/library/ReadingStatsSheet.kt` (**L26**):
  - Thêm helper thuần Kotlin `buildSevenDayChartAccessibilityDescription(...)` và `buildHeatmapAccessibilityDescription(...)`, gắn `.clearAndSetSemantics { contentDescription = ... }` cho vùng biểu đồ 7 ngày và lưới heatmap 12 tuần.
  - Gắn `.selectable(..., role = Role.RadioButton)` cho các chip tone, hướng màn hình (`OrientationLock`) và ngôn ngữ (`AppLanguage`); gộp các hàng switch đồng bộ file sách, vân giấy, đen trắng, nháy E-Ink bằng `.toggleable(..., role = Role.Switch)` + `.semantics(mergeDescendants = true) {}` + `Switch(..., onCheckedChange = null)`.
- `ui/reader/ReaderSettingsPanel.kt` (**L26**):
  - Gắn `Modifier.semantics { role = Role.RadioButton; this.selected = isSelected }` cho các nút trong `ChoiceRow` (font, chế độ PDF, hướng màn hình) và gộp `SwitchRow` thành 1 điểm focus bằng `.toggleable(..., role = Role.Switch)` + `.semantics(mergeDescendants = true) {}`.
- `test/.../ui/library/ReadingStatsAccessibilityTest.kt`:
  - Thêm 3 unit test kiểm tra `buildSevenDayChartAccessibilityDescription` và `buildHeatmapAccessibilityDescription`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **257/257 tests pass** qua 45 file test, `0 lint errors`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 55 — L24 + L25: Đồng hồ Launcher theo 12/24h hệ thống, AM/PM wallpaper theo locale app, xoá formatter chết; plurals `stats_heatmap_days`, bỏ "BOOK" cứng, thứ/đơn vị theo resource/`DayOfWeek.getDisplayName`, xoá chuỗi không dùng, sort tên bằng `Collator`.

### 55. L24 + L25 — Đồng hồ Launcher 12/24h, AM/PM wallpaper theo locale app, plurals & sort Collator — 2026-11-19
**Nguyên nhân:**
- **L24 (INC #27):** `LauncherActivity` dùng `LAUNCHER_TIME_FORMATTER` `"HH:mm"` cứng (và có thêm `LAUNCHER_DATE_FORMATTER` chết, kích hoạt cảnh báo `ConstantLocale`); `EInkWallpaperService` định dạng `"h:mm a"` bằng `Locale.getDefault()` (lệch AM/PM khi ngôn ngữ app khác ngôn ngữ máy) và giữ 2 formatter chết `timeFormatter`/`dateFormatter`.
- **L25 (INC #28):** Còn 3 cảnh báo `PluralsCandidate` trong `values/strings.xml` (`stats_heatmap_days`, `stats_chart_7d_sub`, `sync_partial_book_upload_failed`); chuỗi `"BOOK"` cứng ở `LauncherActivity` và `EInkWallpaperService`; `ReadingStatsSheet` rẽ nhánh `isEn` cho thứ trong tuần (`shortWeekdayLabel`) và đơn vị `"m/h"`/`"p/g"`; `LibraryScreen` (tên sách, danh sách tác giả) và `LauncherActivity` (danh sách app) sort bằng `lowercase()` khiến chữ tiếng Việt có dấu (`Đ`, `Ă`, `Â`) bị đẩy ra sau `"Z"`.

**Đã sửa:**
- `LocaleHelper.kt` (**L24 + L25**):
  - Thêm `effectiveLocale(context)`, `formatClockTime(time, is24Hour, locale)` và `nameComparator(locale)` dùng `Collator.getInstance(locale)` (`Collator.SECONDARY`).
- `LauncherActivity.kt` & `system/EInkWallpaperService.kt` (**L24 + L25**):
  - Đồng hồ Launcher và Wallpaper dùng `DateFormat.is24HourFormat(...)` + `LocaleHelper.formatClockTime(..., appLocale)` để hỗ trợ 12/24h theo hệ thống và AM/PM theo locale app; xoá các formatter chết (`LAUNCHER_TIME_FORMATTER`, `LAUNCHER_DATE_FORMATTER`, `timeFormatter`, `dateFormatter`).
  - Thay `"BOOK"` cứng bằng `R.string.book_badge_label` (`BOOK` / `SÁCH`).
  - `queryLaunchableApps` sort bằng `LocaleHelper.nameComparator(appLocale)`.
- `res/values/strings.xml`, `res/values-vi/strings.xml`, `data/sync/GoogleDriveSyncManager.kt`, `ui/library/ReadingStatsSheet.kt` (**L25**):
  - Chuyển `stats_heatmap_days`, `stats_chart_7d_sub`, `sync_partial_book_upload_failed` sang `<plurals>` ở cả `values` và `values-vi`, cập nhật call site dùng `pluralStringResource` / `resources.getQuantityString`.
  - Thêm `stats_unit_minute_short` (`m`/`p`) và `stats_unit_hour_short` (`h`/`g`), bỏ nhánh `isEn` trong `ReadingStatsSheet.kt`; `shortWeekdayLabel` dùng `DayOfWeek.getDisplayName(TextStyle.SHORT, locale)`.
- `ui/library/LibraryScreen.kt` (**L25**):
  - Sort tiêu đề sách (`LibrarySort.TITLE`) và danh sách `authors` bằng `LocaleHelper.nameComparator(appLocale)`.
- `test/.../LocaleHelperTest.kt` & `test/.../ui/library/ReadingStatsAccessibilityTest.kt`:
  - Thêm unit test cho `formatClockTime` (12h/24h theo locale) và `nameComparator` (xếp `Đ`, `Ă`, `Â` đúng thứ tự tiếng Việt trước `Z`); cập nhật `ReadingStatsAccessibilityTest` theo `Locale`.

**Cách kiểm tra:** `.\gradlew.bat testDebugUnitTest lintDebug` pass toàn bộ (`BUILD SUCCESSFUL`, **259/259 tests pass** qua 46 file test, `0 lint errors`, `0 PluralsCandidate`, `0 ConstantLocale`, `0 UnusedResources`).

**Còn để lại:** không có.

**Bước tiếp theo:** mục 56 — L28 + L29: Widget refresh khi qua ngày / đổi múi giờ, ngôn ngữ / sau sync; wallpaper cache context, formatter, rank, `Paint`/`StaticLayout`, `removeCallbacks` ở `onDestroy`, decode cover ngoài main.
