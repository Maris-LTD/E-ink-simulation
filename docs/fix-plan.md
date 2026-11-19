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
- [ ] 18. P8 — Reader EPUB: ghosting PixelCopy, lock-screen IO, JS inject, bookmark parse, recomposition
- [ ] 19. P9 — Library/launcher: cover cache theo byte, remember stats, clock loop, wallpaper check, backfill cover, app list
- [ ] 20. P10 — Wallpaper cache Paint/grain, sync WorkManager + backoff, upload sách chỉ Wi-Fi

## Đợt 4 — UX
- [ ] 21. U1 — Giữ state thư viện khi mở Thống kê
- [ ] 22. U2 + U3 — PDF zoom không chớp trắng, tap-zoom, lật trang khi zoom, placeholder/retry
- [ ] 23. U4 — Thông báo lỗi sync localized, trạng thái "kết nối lại"
- [ ] 24. U5 — Quyền thông báo, notification màn khoá, focus mode, lỗi intent bị nuốt
- [ ] 25. U6 — Dialog ghi chú, checkbox xoá Drive mặc định tắt + undo, tutorial chặn touch, lỗi mở sách
- [ ] 26. U7 — Accessibility (contentDescription, 48dp, semantics)
- [ ] 27. U8 + U9 — Launcher pins, empty state, sort, cạnh trái ẩn, phím âm lượng, đồng hồ 12h
- [ ] 28. U10 + U11 — Chuỗi cứng, plurals, privacy policy

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
