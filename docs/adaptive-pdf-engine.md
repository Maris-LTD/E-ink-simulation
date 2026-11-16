# Kiến trúc Hệ thống Hiển thị & Reflow PDF Đa Tầng (Adaptive PDF Engine)

Tài liệu này tổng hợp thiết kế **Adaptive PDF Engine** cho ứng dụng đọc sách mô phỏng E-Ink, kết hợp các yêu cầu sản phẩm và các tinh chỉnh kỹ thuật phù hợp với kiến trúc hiện tại của dự án (Kotlin, Jetpack Compose, `minSdk = 24`).

---

## 1. Bộ phân loại tài liệu tự động (Auto-Detection Engine)

Ngay khi người dùng mở một tệp PDF mới, hệ thống quét nhanh **3–5 trang mẫu** (kết hợp đọc Metadata) dựa trên các chỉ số:
- Mật độ ký tự (character density / có text layer hay không)
- Số cột theo phân bố tọa độ trục $X$
- Mật độ đường vẽ vector (`lineTo`, `rect`, `curve`)
- Tỷ lệ diện tích ảnh bitmap trên trang

Từ đó phân loại tệp PDF thành 4 nhóm và tự động chọn chế độ đọc mặc định tối ưu:

| Nhóm tài liệu | Đặc điểm nhận diện | Chế độ mặc định |
| :--- | :--- | :--- |
| **Truyện chữ / Sách văn học** | Text layer dày, 1 cột, ít vector/ảnh | **Chế độ 3** (Hybrid Text Reflow) |
| **Tài liệu kỹ thuật / Paper khoa học** | 2 cột rõ rệt, nhiều bảng biểu / công thức / vector | **Chế độ 2** (Đọc theo cột) hoặc **Chế độ 1** (Trang gốc thông minh) |
| **Tạp chí (Magazine)** | Nhiều ảnh, bố cục tự do nhiều vùng | **Chế độ 1** (Trang gốc thông minh) |
| **Truyện tranh / Sách Scan** | Không có lớp text (hoặc tỷ lệ text gần 0) | Khóa Chế độ 3 (Reflow), mặc định **Chế độ 1** (Trang gốc thông minh) |

### Tinh chỉnh kỹ thuật (Auto-Detection)
- **Thay đổi về hiệu năng thực tế:** Do dùng parser trên JVM/Kotlin (xem Mục 5), quá trình parse 3–5 trang đầu thường mất khoảng **150ms – 500ms** (thay vì ~20ms như C++ thuần).
- **Giải pháp:** Chạy Auto-Detection bất đồng bộ trên `Dispatchers.IO` ở lần mở đầu tiên và **cache kết quả phân loại theo từng đầu sách** (`bookId`) vào bộ nhớ cục bộ (`BookIndexStore` / `BookEntry`) để các lần mở sau vào thẳng chế độ tối ưu ngay lập tức.

---

## 2. Ba chế độ hiển thị cốt lõi (Adaptive Reading Modes)

| Chế độ | Tên gọi & Mục đích | Cơ chế kỹ thuật |
| :--- | :--- | :--- |
| **Chế độ 1** | **Trang gốc thông minh**<br>*(Smart Fit-to-Content)* | • **Auto Margin Crop:** Quét pixel tìm vùng nội dung thực tế, cắt bỏ lề trắng 4 phía và render trực tiếp vùng nội dung khít màn hình *(đã triển khai tại `PdfMarginCropper.kt` & `PdfPagerReader.kt`)*.<br>• **Horizontal Lock:** Khi trang được phóng to vừa chiều ngang (Fit-to-Width) và dài theo chiều dọc, khóa thao tác trượt ngang vô tình khi đang cuộn đọc dọc.<br>• **Smart Tap-to-Zoom:** Chạm đúp vào một khối văn bản hoặc biểu đồ để tự động zoom vừa khít bounding box của khối đó (fallback về mức zoom cố định nếu không có box). |
| **Chế độ 2** | **Lướt theo vùng đọc**<br>*(Guided Column Navigation)* | • Chia trang PDF thành lưới khung nhìn theo cột (ví dụ: Trên cột 1 → Dưới cột 1 → Trên cột 2 → Dưới cột 2).<br>• Người dùng chạm màn hình hoặc bấm phím cứng như lật trang EPUB; camera tự động trượt và zoom lần lượt qua từng vùng đọc.<br>• **Tinh chỉnh kỹ thuật:** Thêm khoảng **overlap ~8–10% chiều cao** giữa hai bước nhảy liên tiếp trong cùng một cột để tránh cắt đôi dòng chữ ở mép màn hình. |
| **Chế độ 3** | **Dàn lại trang lai**<br>*(On-the-fly Hybrid Reflow)* | • Không convert cả cuốn sang `.epub`; xử lý trực tiếp trên RAM từng trang khi người dùng lật tới.<br>• Bóc tách độc lập khối chữ và khối ảnh kèm tọa độ trục dọc $Y$, sắp xếp từ trên xuống dưới và hiển thị bằng giao diện đọc tùy biến được cỡ chữ, phông chữ, giãn dòng, nền E-Ink. |

---

## 3. Cơ chế xử lý chi tiết bên trong Chế độ 3 (Hybrid Reflow)

1. **Làm sạch văn bản (Smart Line-Merging):**
   - Gom các dòng có khoảng cách trục $Y$ gần nhau thành đoạn văn (`<p>`), xóa dấu ngắt dòng cứng (`\n`) ở cuối dòng, giữ lại dấu ngắt đoạn dựa trên khoảng cách dòng và thụt đầu dòng.
   - **Lọc Header / Footer lặp lại:** So sánh nội dung ở vùng 10% trên cùng và 10% dưới cùng qua 3–5 trang liên tiếp; nếu lặp lại (tên chương, tên sách) hoặc khớp mẫu số trang thì loại bỏ khỏi luồng Reflow.

2. **Chèn ảnh minh họa đúng ngữ cảnh:**
   - Các khối ảnh bitmap được cắt đúng vùng tọa độ và chèn xen kẽ giữa các đoạn văn theo đúng thứ tự trục dọc $Y$.
   - **Trang minh họa toàn trang (Full-page Illustration):** Nếu diện tích ảnh chiếm **$> 70\%$** diện tích trang, bỏ qua Reflow cho riêng trang đó và hiển thị nguyên bản toàn màn hình (Chế độ 1).

3. **Biểu đồ vector phức tạp & Tự động hạ cấp an toàn (Auto-Fallback):**
   - **Tinh chỉnh kỹ thuật:** Thay vì cố tách và gom từng nét vẽ vector + chữ chú thích thành ảnh PNG (dễ lỗi với công thức toán hoặc bảng phức tạp), ở giai đoạn đầu áp dụng chiến lược an toàn:
     - Nếu trang có mật độ đường kẻ vector (`line`/`rect`/`curve`) vượt ngưỡng phức tạp → **Auto-Fallback** trang đó về **Chế độ 1 (Trang gốc)** để giữ nguyên bố cục.
     - Có thể mở rộng ở bước sau bằng cách lấy bounding box của cụm vector và cắt trực tiếp vùng đó từ bitmap `PdfRenderer` để chèn như một khối ảnh vào Reflow.

4. **Tầng hiển thị (UI Rendering):**
   - **Tinh chỉnh kỹ thuật:** Dùng **Jetpack Compose (`LazyColumn` + `Text` / `AnnotatedString` + `Image`)** thay vì `WebView` HTML.
   - Lý do: Đồng bộ trực tiếp với `EInkTheme`, bộ font có sẵn (`Literata`, `Bitter`, `Rubik`), hiệu ứng `PaperGrainOverlay` / `RefreshFlashOverlay` và đảm bảo tốc độ chuyển chế độ tức thì mà không bị chớp màn hình trên thiết bị E-Ink.

---

## 4. Thiết kế trải nghiệm người dùng (UX)

- **Nút chuyển đổi tức thì (1-Tap Toggle):** Đặt nút chuyển chế độ ngay trên thanh công cụ đọc (`ReaderTopBar` hoặc `ReaderBottomBar`) để gạt nhanh giữa **Trang gốc / Đọc cột / Dàn chữ** tại đúng số trang đang xem.
- **Ghi nhớ theo từng đầu sách:** Lưu riêng cấu hình cho từng tệp PDF theo `bookId` (chế độ đọc, bật/tắt cắt lề, cỡ chữ Reflow, kết quả phân loại tài liệu) vào kho dữ liệu cục bộ để lần mở sau khôi phục đúng trạng thái.

---

## 5. Ngăn xếp kỹ thuật & Lộ trình triển khai

### Lựa chọn PDF Parser
- `android.graphics.pdf.PdfRenderer` (đang dùng) chỉ xuất bitmap, không cung cấp tọa độ chữ/ảnh/vector trên `minSdk = 24`.
- `readium-adapter-pdfium` (đã có trong `libs.versions.toml`) chỉ mở API cấp cao (render/search/cover), không trả về tọa độ chi tiết từng `TextPosition` và vector path.
- **Giải pháp:** Tích hợp **PDFBox-Android** (`com.tom-roush:pdfbox-android`) chuyên trách bóc tách cấu trúc trang (text coordinates, image bounds, vector density), kết hợp với `PdfRenderer` hiện tại cho việc render bitmap độ phân giải cao ở Chế độ 1 & Chế độ 2.

### Lộ trình 3 giai đoạn
1. **Giai đoạn 1 — Nền tảng Parser & Lưu cấu hình theo đầu sách:**
   - Tích hợp `pdfbox-android`.
   - Xây dựng `PdfPageAnalyzer` (trích xuất text lines kèm tọa độ `X/Y`, image bounds, vector density) và `PdfDocumentClassifier` (Auto-Detection).
   - Mở rộng lưu trữ theo từng `bookId` cho chế độ đọc & kết quả phân loại.
2. **Giai đoạn 2 — Chế độ 3 (Hybrid Reflow) & 1-Tap Toggle:**
   - Thuật toán Smart Line-Merging, lọc Header/Footer, chèn ảnh theo trục $Y$, Auto-Fallback cho trang >70% ảnh hoặc nhiều bảng biểu/vector.
   - Giao diện đọc Reflow bằng Compose (`LazyColumn`) dùng `EInkTheme` + nút chuyển chế độ nhanh trên thanh đọc.
3. **Giai đoạn 3 — Chế độ 2 (Đọc theo cột) & Smart Tap-to-Zoom (Chế độ 1):**
   - Dùng dữ liệu cột/khối từ `PdfPageAnalyzer` để chia lưới khung nhìn (kèm overlap 8–10%) cho Chế độ 2.
   - Bổ sung Horizontal Lock và Smart Tap-to-Zoom đúng khối nội dung cho Chế độ 1.
