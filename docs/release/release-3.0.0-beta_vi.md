# Ghi chú Phát hành JTrac NG (Release Notes) - 3.0.0-beta

[English](release-3.0.0-beta_en.md) | [繁體中文](release-3.0.0-beta_zh-TW.md) | [简体中文](release-3.0.0-beta_zh-CN.md) | [日本語](release-3.0.0-beta_ja.md) | [Tiếng Việt](release-3.0.0-beta_vi.md) | [Deutsch](release-3.0.0-beta_de.md) | [Español](release-3.0.0-beta_es.md) | [Français](release-3.0.0-beta_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Version](https://img.shields.io/badge/Version-3.0.0--beta-orange.svg)](../../pom.xml)
[![Status](https://img.shields.io/badge/Status-Beta%20Preview-yellow.svg)](release-3.0.0-beta_vi.md)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

> [!NOTE]
> **Trạng thái hiện tại: Bản xem trước thử nghiệm Beta (Pre-release / Beta Preview)**  
> Tài liệu này là nhật ký phát hành động (Living Release Notes). Trong giai đoạn thử nghiệm Beta, mọi tính năng bổ sung, tinh chỉnh cấu hình và sửa lỗi tiếp theo sẽ được tự động cập nhật liên tục vào đây.

---

## Mục lục
1. [Tổng quan về các điểm nổi bật](#1-tổng-quan-về-các-điểm-nổi-bật)
2. [🤖 Trợ lý truy vấn Email AI (AI Query Copilot với Ollama)](#2--trợ-lý-truy-vấn-email-ai-ai-query-copilot-với-ollama)
3. [📦 Nâng cấp thư viện cốt lõi & Loại bỏ cảnh báo Java 11](#3--nâng-cấp-thư-viện-cốt-lõi--loại-bỏ-cảnh-báo-java-11)
4. [🎨 Hiện đại hóa giao diện, Phóng to chữ & Chuyển đổi giao diện](#4--hiện-đại-hóa-giao-diện-phóng-to-chữ--chuyển-đổi-giao-diện)
5. [🛡️ Tăng cường bảo mật môi trường Production](#5--tăng-cường-bảo-mật-môi-trường-production)
6. [⚙️ Cấu hình hệ thống và cải thiện độ ổn định](#6--cấu-hình-hệ-thống-và-cải-thiện-độ-ổn-định)
7. [Hướng dẫn nâng cấp và tính tương thích](#7-hướng-dẫn-nâng-cấp-và-tính-tương-thích)

---

## 1. Tổng quan về các điểm nổi bật

Kế thừa kiến trúc hiện đại từ phiên bản 2.0.0, JTrac NG 3.0.0-beta giới thiệu **Trợ lý truy vấn Email AI (AI Query Copilot tích hợp Ollama)**, nâng cấp trình phân tích cú pháp XML để loại bỏ hoàn toàn cảnh báo truy cập phản chiếu trên Java 11, tăng cường khả năng tiếp cận (chế độ phóng chữ 4 mức với A+++, chuyển đổi theme sáng/tối 3 trạng thái) và củng cố bảo mật toàn diện.

---

## 2. 🤖 Trợ lý truy vấn Email AI (AI Query Copilot với Ollama)

1. **Mở rộng truy vấn 2 giai đoạn & Chống Prompt Injection**:
   - Tích hợp mô hình Ollama LLM, tự động phân tích email để trích xuất từ khóa thực thể và từ đồng nghĩa kỹ thuật.
   - Thiết lập vùng đệm an toàn `<untrusted_user_query>` để ngăn chặn đánh cắp chỉ lệnh và mã độc.
2. **Truy vấn trọng số kết hợp & Điểm thưởng song ngữ**:
   - Tính điểm chính xác theo Tóm tắt (+3), Chi tiết (+1), Bình luận (+1) và Tệp đính kèm (+1), cộng thêm 5 điểm cho khớp nối song ngữ Anh - Việt.
   - Thêm tham số `llm.retrieval.max_tickets` trong bảng `config` (mặc định 50).
3. **Quy trình xử lý Map-Reduce phân tán**:
   - **Giai đoạn Map**: Phân tích từng ticket và tệp đính kèm (hỗ trợ tới 100.000 ký tự mỗi tệp, đọc file PDF, Word, Excel, TXT, LOG, CSV) thành bản tóm tắt trung gian.
   - **Giai đoạn Reduce**: Tổng hợp các tóm tắt thành 3 phần rõ ràng: Tóm tắt điều hành, Phát hiện chính & Giải pháp, Khuyến nghị hành động.
   - Đảm bảo khối `finally` dọn sạch thư mục tạm, không để lại rác trên đĩa cứng.
4. **Liên kết báo cáo Web lưu giữ 14 ngày & Hỗ trợ tải xuống HTML ngoại tuyến**:
   - **Loại bỏ nguy cơ bị cổng an ninh email chặn**: Không còn gửi tệp đính kèm `.html` vốn hay bị cổng bảo mật (Exchange/Outlook/Gmail) chặn, thay bằng liên kết trực tiếp an toàn.
   - **Vòng đời 14 ngày và tự động dọn dẹp hàng giờ (TTL Auto-Pruning)**: Máy chủ lưu trữ báo cáo an toàn trong 14 ngày và tự động xóa tệp hết hạn qua tác vụ hàng giờ, chi phí bảo trì bằng 0.
   - **Khả năng sử dụng ngoại tuyến 100% với một cú nhấp chuột**: Thanh công cụ thường trực hỗ trợ tải tệp HTML ngoại tuyến (`JTrac-AI-Report-[Date].html`), tích hợp sẵn công cụ Mermaid.js cho môi trường cách ly không mạng.
5. **Hướng dẫn viết Prompt đa ngôn ngữ**: Cung cấp tài liệu [`docs/llm/PROMPT_EXAMPLES_*.md`](../llm/PROMPT_EXAMPLES_vi.md) với 4 kịch bản thực chiến.
6. **Nhóm theo Space, sắp xếp ID giảm dần & Tích hợp Mermaid.js ngoại tuyến 100%**:
   - **Bảng phân nhóm theo Space & Sắp xếp mới nhất trước (ID DESC)**: Tái cấu trúc bảng tóm tắt trong email và báo cáo HTML theo từng Space được cấp quyền kèm số lượng ticket; các ticket trong mỗi Space được sắp xếp theo số hiệu ID giảm dần (`ID DESC`). Nội dung email được giữ tối giản, không kèm cảnh báo Mermaid dư thừa.
   - **Công cụ Mermaid.js ngoại tuyến hoàn toàn**: Nhúng trực tiếp gói Mermaid.js (v10.9.1) vào Classpath và báo cáo HTML, loại bỏ hoàn toàn sự phụ thuộc vào CDN bên ngoài. Hỗ trợ tự động đổi giao diện sáng/tối (`prefers-color-scheme`) và cơ chế phục hồi lỗi cú pháp.
   - **Ràng buộc Prompt lưu đồ 2 cấp độ với chuẩn đóng ngoặc kép**: Bắt buộc tạo lưu đồ `flowchart TD/LR` ở cả giai đoạn Map (quy trình tái hiện/xử lý sự cố) và Reduce (tóm tắt tổng quan & kế hoạch hành động), yêu cầu nhãn nốt phải đặt trong dấu ngoặc kép để ngăn ngừa lỗi định dạng.
7. **Căn cứ dữ liệu khép kín JTrac, Gắn thẻ tri thức bên ngoài bắt buộc, Thông báo không có kết quả & Ẩn dữ liệu nhạy cảm**:
   - **Tuân thủ ngữ cảnh khép kín JTrac nghiêm ngặt (Strict JTrac Context Grounding)**: Ràng buộc phản hồi của LLM phải hoàn toàn dựa trên các ticket và tệp đính kèm được cấp quyền trong JTrac làm căn cứ duy nhất. Phân tích ticket riêng lẻ (pha Map) và Phát hiện trọng tâm (Reduce phần 2) bị cấm 100% việc đưa vào các phỏng đoán bên ngoài.
   - **Bắt buộc gắn thẻ tri thức bên ngoài trong phần tổng hợp (Mandatory External Knowledge Tagging)**: Chỉ trong phần tóm tắt tổng thể (Reduce phần 1 Tóm tắt điều hành) và phần 3 Hành động đề xuất, mô hình mới được phép bổ sung kiến thức thực tế chung nếu ticket nội bộ chưa đủ giải pháp, nhưng **BẮT BUỘC phải đính kèm nhãn `(Note: Recommended based on external reference knowledge)`** (`（參考外部資訊給予建議）`), giúp nguồn gốc thông tin hoàn toàn minh bạch.
   - **Thông báo an toàn khi không có kết quả (Zero-Hit Safe Notice)**: Khi truy vấn của người dùng không khớp với bất kỳ ticket hoặc tệp đính kèm nào trong các Space được phân quyền, hệ thống sẽ chặn ngay lập tức và gửi email thông báo không có kết quả kèm danh sách Space được cấp quyền cùng nguyên tắc căn cứ dữ liệu, tránh việc LLM suy diễn vô căn cứ và tự động dọn sạch hộp thư.
   - **Ẩn thông tin xác thực nhạy cảm (Confidential Credentials Masking)**: Triển khai `SensitiveDataMasker` để tự động lọc và thay thế mật khẩu (password), Bearer token, API key, khối khóa riêng tư và thông tin xác thực URL trong báo cáo HTML thành `***`, đồng thời bảo toàn trọn vẹn tên tài khoản và mã ticket.
   - **Phần cứng khuyến nghị & Cấu hình ngữ cảnh dài 200K (Recommended Hardware & 200K Context)**: Bổ sung chuẩn phần cứng và mô hình vào Hướng dẫn Prompt, khuyến nghị sử dụng GPU Flagship NVIDIA RTX 5090 (32GB VRAM) kết hợp `qwen2.5:32b`, bắt buộc cấu hình cửa sổ ngữ cảnh 200K (`num_ctx 200000`) qua Ollama Modelfile; cảnh báo nghiêm cấm sử dụng mô hình yếu hoặc ngữ cảnh ngắn để tránh bị cắt cụt dữ liệu gây hỏng phân tích.
8. **Bộ lọc cửa sổ thời gian truy xuất và miễn trừ mã phiếu rõ ràng (Time-Window Retrieval Filter & Exact RefId Exemption)**：
   - **Lọc cửa sổ thời gian động**: Đăng ký tham số toàn cục `llm.retrieval.max_age_days` (mặc định 365 ngày) trong bảng `config`. Đánh giá thời gian hoạt động cuối cùng của phiếu $\max(\text{item.timeStamp}, \text{latestHistory.timeStamp})$ làm tiêu chí, tự động loại bỏ các phiếu cũ không cập nhật quá 1 năm để tránh dữ liệu lỗi thời làm nhiễu ngữ cảnh AI; đặt thành 0 hoặc số âm sẽ vô hiệu hóa bộ lọc và tìm kiếm toàn bộ lịch sử.
   - **Miễn trừ hoàn toàn cho mã phiếu rõ ràng (Exact RefId)**: Khi người dùng chỉ định rõ mã phiếu (ví dụ: `PROJ-108`) trong tiêu đề hoặc nội dung email, hệ thống coi đó là ý định tra cứu chính xác và miễn trừ hoàn toàn khỏi giới hạn cửa sổ thời gian.
9. **Lọc từ dừng tìm kiếm, chuyển đổi thuật ngữ chuyên ngành hai bờ eo biển và hiển thị huy hiệu từ khóa (Stopwords Filtering, Cross-Strait Terminology Mapping & Keyword Dossier Badges)**:
   - **Lọc từ dừng truy xuất (Retrieval Stopwords Filtering)**: Đăng ký tham số toàn cục `llm.retrieval.stopwords` (mặc định là "`通知,工單`") trong bảng `config`. Loại trừ nghiêm ngặt các từ dừng gây nhiễu phổ biến (tự động loại trừ các từ tương đương như `notice`, `notification`, `ticket`, `issue`) trong cả Prompt và quy trình Java, ngăn chặn các truy vấn như "thông báo hóa đơn" hoặc "phiếu hóa đơn" bị tràn ngập bởi các phiếu thông báo không liên quan trong Lucene.
   - **Chuyển đổi thuật ngữ công nghệ hai bờ eo biển (Cross-Strait Terminology Mapping)**: Xây dựng từ điển đối chiếu thuật ngữ CNTT và nghiệp vụ hai chiều tích hợp sẵn (bao gồm 專案 ⇄ 项目, 發票 ⇄ 发票/开票, 程式碼 ⇄ 代码, 記憶體 ⇄ 内存, 欄位 ⇄ 字段, 排程 ⇄ 定时任务/计划任务, 檔案 ⇄ 文件, 資料庫 ⇄ 数据库, v.v.), kết hợp với hướng dẫn Prompt nâng cao để mở rộng khái niệm vùng miền thay vì chỉ thay thế từng ký tự đơn thuần.
   - **Hiển thị huy hiệu từ khóa minh bạch trong báo cáo và email (Transparent Keyword Badges in Dossier & Email)**: Hiển thị trực quan tất cả các từ khóa được trích xuất và mở rộng (Phồn thể, Giản thể, thuật ngữ vùng miền và bản dịch tiếng Anh) dưới dạng các huy hiệu con nhộng trong tiêu đề báo cáo HTML và nội dung email, đồng thời ghi lại vào bản tóm tắt trung gian Staging Markdown để đảm bảo tính minh bạch 100%.
   - **Trích dẫn cụm từ CJK và bảo vệ ngưỡng điểm liên quan**: Tự động bao bọc các từ tiếng Trung nhiều ký tự trong dấu ngoặc kép khi tìm kiếm Lucene để tránh nhiễu unigram, và loại bỏ nghiêm ngặt các phiếu có điểm liên quan $\le 0$.
10. **Cơ chế hỏi đáp RAG không ảo giác nghiêm ngặt và Thẻ trả lời trực tiếp trong Email (Strict Zero-Hallucination RAG & Direct Question-Answering Email Card)**:
   - **Ràng buộc miền đóng không ảo giác (Strict Closed-Domain RAG Grounding)**: Loại bỏ hoàn toàn các suy đoán tri thức bên ngoài. Bắt buộc mọi câu trả lời phải 100% dựa trên lịch sử phiếu và tài liệu đính kèm đã truy xuất, đồng thời trích dẫn rõ mã phiếu `[REF-ID]`. Khi không tìm thấy thông tin trong phiếu, mô hình phải nêu rõ "（現有工單資料中查無此項資訊 / Không có tài liệu trong phiếu）".
   - **Nhận diện ý định câu hỏi thông minh (Question Inquiry Detection)**: Tự động phân tích dấu hỏi (`?`, `？`) và các từ để hỏi đa ngôn ngữ (`請問`, `如何`, `tại sao`, `như thế nào`, `ở đâu`, `how`, `what`, `why`, v.v.), tự động chuyển sang chế độ trả lời câu hỏi.
   - **Hiển thị thẻ trả lời trực quan trong Email (Direct Answer Card in Email Body)**: Hiển thị trực tiếp thẻ "💬 回答問題 / Answer to Inquiry" nổi bật trong nội dung email (hoặc thẻ tóm tắt đối với tìm kiếm thông thường; thẻ cảnh báo khi không có phiếu phù hợp), tích hợp che giấu thông tin mật, liên kết phiếu tự động và lọc an toàn biểu đồ Mermaid.
11. **Đánh chỉ mục toàn văn tệp nén (.zip, .rar) & Nhận dạng ký tự hình ảnh Ollama Multimodal Vision OCR**:
    - **Trích xuất văn bản chuyên sâu cho tệp lưu trữ nén (`.zip`, `.rar`)**: Hỗ trợ trích xuất toàn văn cho các tài liệu được đóng gói bên trong tệp đính kèm nén, hỗ trợ `.zip` (luồng Java thuần) và `.rar` (Junrar thuần Java). Các tài liệu được giải nén (`.pdf`, `.docx`, `.xlsx`, `.txt`, `.csv`, `.md`, `.log`) được cấu trúc với nguồn dẫn `[Archive: folder/document.ext]` phục vụ lập chỉ mục Lucene và truy xuất AI RAG.
    - **Hàng rào bảo mật nghiêm ngặt (Phòng chống Zip Slip & Zip Bomb)**:
      - *Phòng chống Zip Slip*: Phát hiện và loại bỏ các mục chứa `..` hoặc đường dẫn tuyệt đối.
      - *Phòng chống Zip Bomb*: Giới hạn tối đa 100 tệp trên mỗi kho lưu trữ, tổng dung lượng giải nén tích lũy tối đa 30MB và tối đa 10MB cho mỗi tệp đơn lẻ.
      - *Ngăn chặn tấn công giải nén đệ quy*: Các kho lưu trữ lồng nhau được bỏ qua an toàn và không bao giờ giải nén đệ quy.
    - **Nhận dạng hình ảnh Ollama Multimodal Vision OCR**:
      - Chuyển đổi các tệp đính kèm hình ảnh độc lập (`.png`, `.jpg`, `.jpeg`, `.bmp`, `.webp`, `.gif`) và hình ảnh được giải nén từ tệp nén thành văn bản thuần có thể tìm kiếm bằng API thị giác của Ollama (`/api/chat` với dữ liệu `images` dạng Base64).
      - **Tự động thu nhỏ ảnh độ phân giải cao & Chuẩn hóa GIF (Auto-Downscaling & GIF Normalization)**: Tự động thu nhỏ hình ảnh có kích thước vượt quá 1280px về mức tối đa 1280px trước khi gửi đến Ollama, đồng thời chuẩn hóa nền trắng cho ảnh GIF và ảnh trong suốt. Giảm hơn 70% mức tiêu thụ VRAM của GPU và mã thông báo, ngăn chặn triệt để lỗi tràn bộ nhớ `CUDA error: out of memory`.
      - Cấu hình linh hoạt: `attachment.ocr.enabled` (mặc định true), `attachment.ocr.model` (hỗ trợ các mô hình thị giác chuyên dụng như `llama3.2-vision`, `minicpm-v` hoặc `moondream`; để trống sẽ sử dụng mô hình LLM mặc định) và `attachment.ocr.max_image_size_mb` (mặc định 5MB).
      - *Duy trì tính sẵn sàng*: Nếu Ollama ngoại tuyến hoặc hết thời gian chờ, hệ thống sẽ bỏ qua OCR một cách an toàn mà không làm gián đoạn việc tạo phiếu hoặc lập chỉ mục.

---

## 3. 📦 Nâng cấp thư viện cốt lõi & Loại bỏ cảnh báo Java 11

1. **Nâng cấp `dom4j` lên `2.1.4`**:
   - Nâng cấp thư viện cũ `dom4j:1.6.1` lên bản `org.dom4j:dom4j:2.1.4`.
   - Giải quyết triệt để cảnh báo `WARNING: An illegal reflective access operation has occurred` khi chạy trên Tomcat 9 và JDK 11.

---

## 4. 🎨 Hiện đại hóa giao diện, Phóng to chữ & Chuyển đổi giao diện

1. **Chu kỳ phóng to cỡ chữ 4 giai đoạn**:
   - Hỗ trợ 100% (Tiêu chuẩn), 115% (Dễ chịu), 130% (Rõ nét) và **Chế độ cực lớn A+++ (145%)**.
   - Tích hợp cơ chế chống chớp nháy (Anti-FOUC) và bảo vệ cấu trúc bảng, lưu cấu hình vào `localStorage`.
2. **Chuyển đổi giao diện 3 trạng thái (Theme Switcher)**:
   - Chuyển đổi mượt mà giữa Tự động (Theo OS), Sáng (Light) và Tối (Dark) bằng một nút bấm.
3. **Thanh tìm kiếm thống nhất & Điều hướng thông minh**:
   - Tích hợp nút tìm kiếm bên trong ô nhập, nhận diện nhanh mã ticket (nhập `PROJ-123` chuyển thẳng đến ticket), hỗ trợ tìm kiếm toàn cục cho quản trị viên.
4. **Tối ưu hóa di động (Mobile RWD)**:
   - Menu trượt (Drawer) trên thiết bị di động, modal xem lịch sử dạng Bottom-Sheet, phân trang capsule căn giữa.
5. **Cửa sổ Modal chờ "Đang tìm kiếm, vui lòng đợi..." kèm độ trễ chống nhấp nháy 150ms**:
   - Hiển thị thẻ modal căn giữa với vòng xoay CSS thuần khiết trên cả 4 biểu mẫu tìm kiếm (tìm kiếm nhanh trên máy tính, ngăn kéo tìm kiếm di động, tìm kiếm nâng cao và bộ lọc danh sách) cũng như khi chuyển trang phân trang, sắp xếp cột và xóa tìm kiếm (`✕`).
   - Tích hợp **độ trễ 150ms** giúp ngăn chặn nhấp nháy màn hình khi phản hồi nhanh nhưng vẫn hiển thị phản hồi trực quan rõ ràng khi Ollama mở rộng từ đồng nghĩa, đi kèm chặn truy vấn rỗng, bỏ qua khi giữ `Ctrl`/`Cmd` mở tab mới, tự động ẩn khi quay lại trang (`pageshow`) và tự động đóng an toàn sau 15 giây.

---

## 5. 🛡️ Tăng cường bảo mật môi trường Production

1. **Bộ lọc tiêu đề bảo mật toàn cục**: Bổ sung `X-Frame-Options`, `X-Content-Type-Options`, `Strict-Transport-Security`, `Content-Security-Policy`.
2. **Ngăn chặn công cụ tìm kiếm (`robots.txt`)**: Ngăn bot mạng thu thập thông tin ticket nội bộ.
3. **Chống giả mạo**: Cảnh báo quyền Guest, kiểm tra whitelist tham số URL, chống nhấp đúp gửi form nhiều lần.

---

## 6. ⚙️ Cấu hình hệ thống và cải thiện độ ổn định

1. Bổ sung `status.nullValid = ` trên toàn bộ 8 ngôn ngữ để loại bỏ log debug Wicket.
2. Nâng cấp nút bật/tắt Boolean trong cài đặt thành `IndicatingDropDownChoice`.
3. Đăng ký rõ ràng Driver JDBC cho các kết nối cơ sở dữ liệu.
4. Tự động nhận diện UTF-8 cho tệp đính kèm văn bản và xử lý đường dẫn Logo tương đối.
5. **Hướng dẫn tập lệnh Docker Build & Phòng chống xung đột thẻ Git**:
   - Thêm thông báo hướng dẫn khi khởi chạy trong `docker/build.bat` và `docker/build.sh` với lệnh đồng bộ nhanh (`git fetch --tags -f && git reset --hard origin/master`) khi gặp lỗi ghi đè thẻ Tag hoặc sai khác mã nguồn.
   - Hoàn thiện tài liệu `docker/` chuẩn 8 ngôn ngữ kèm mục xử lý sự cố đồng bộ.
7. **Tái định vị thương hiệu JTrac NG & Nâng cấp phiên bản ngữ nghĩa (v3.0.0-beta)**:
   - Chính thức nâng cấp thương hiệu dự án thành **JTrac NG** (Next Generation) và nhảy vọt phiên bản lên **3.0.0-beta**, giải quyết triệt để xung đột tìm kiếm 15 năm với bản cũ 2.1.0/2.3.x trên Google SEO.
   - Di chuyển kho lưu trữ chính thức sang [https://github.com/kafeiou/jtrac-ng](https://github.com/kafeiou/jtrac-ng), đồng bộ chân trang Web, thanh điều hướng di động và mô tả Maven POM.
8. **Hiện đại hóa toàn diện tìm kiếm toàn văn Lucene và xem lịch sử (Tách Sub-token, mở rộng ký tự đại diện & mặc định hiển thị lịch sử)**:
   - **Bộ tách Sub-token (`SubTokenFilter`)**: Tự động phân tách Email (ví dụ `user@gmail.com`) và tên tệp phức hợp (ví dụ `thunderbird_gmail.pdf`) thành các token thành phần (`user`, `gmail`, `com`, `thunderbird`, `pdf`), giúp tìm kiếm từ đơn `gmail` khớp chính xác email và tệp đính kèm.
   - **Mở rộng truy vấn thông minh & hỗ trợ ký tự đại diện đứng đầu**: Tự động mở rộng từ khóa thành `(term OR term*)`, kích hoạt `allowLeadingWildcard = true` cho truy vấn `*từkhóa*`, hỗ trợ dung sai cụm từ tiếng Trung (`phraseSlop = 2`).
   - **Duyệt không gian thuần gọn và mở rộng lịch sử theo nhu cầu tìm kiếm (Clean Space Browsing & Search-Driven History Expansion)**: Khi duyệt không gian, mặc định `showHistory = false` hiển thị danh sách phiếu gọn gàng (mỗi phiếu hiển thị 1 dòng `EFC-109`), loại bỏ hoàn toàn tình trạng mở rộng lịch sử gây rối mắt. Chế độ lịch sử (`showHistory = true`) chỉ được tự động kích hoạt khi tìm kiếm từ khóa hoặc mở biểu mẫu tìm kiếm nâng cao, và tự động thu gọn lại khi xóa tìm kiếm.
   - **Tối ưu hóa thứ tự sắp xếp lịch sử theo thời gian (Chronological Order: Parent Ticket Before Revisions)**: Điều chỉnh thứ tự sắp xếp khi mở lịch sử theo mã phiếu giảm dần và ID lịch sử tăng dần (`parent.id DESC, id ASC`). Đảm bảo phiếu chính (ví dụ: `xxx-109`) luôn hiển thị trước các nhận xét sửa đổi (ví dụ: `xxx-109(1)`), đồng thời duy trì gom nhóm theo phiếu khi sắp xếp theo các cột khác.
   - **Lọc lịch sử thông minh khi tìm kiếm từ khóa (Smart History Filtering)**: Khi bật chế độ xem lịch sử và thực hiện tìm kiếm, hệ thống lọc thông minh chỉ hiển thị bản ghi mở đầu hoặc các nhận xét sửa đổi thực sự chứa từ khóa, tự động ẩn các lịch sử thay đổi trạng thái không liên quan.
   - **Chuẩn hóa ngữ nghĩa và hỗ trợ sắp xếp cột "Thay đổi lần cuối" (`LAST_CHANGED`)**: Trong cả chế độ duyệt danh sách (`showHistory = false`) và kết quả tìm kiếm kèm lịch sử (`showHistory = true`), cột `LAST_CHANGED` luôn hiển thị thời gian của bản ghi lịch sử mới nhất thuộc phiếu chính (`targetItem.getLatestHistory().getTimeStamp()`), và hiển thị `"-"` khi phiếu mới chỉ được tạo mà chưa có bất kỳ chỉnh sửa hay bình luận nào tiếp theo (`history.size() <= 1`). Nhấp vào tiêu đề cột `LAST_CHANGED` sẽ sắp xếp theo thời gian hoạt động thực tế (lấy thời gian thay đổi cuối nếu có, hoặc thời gian tạo nếu là `"-"`).
   - **Tự động xây dựng lại chỉ mục bất đồng bộ khi khởi động**: Phát hiện nâng cấp phiên bản bộ phân tích (`lucene.analyzer.version = 3.0.0-subtoken-v1`) và tự động chạy lại chỉ mục trong nền sau khi khởi động.
   - **Di chuyển Docker Hub**: Cập nhật tài liệu 8 ngôn ngữ sang `kafeiou/jtrac-ng:latest`.
9. **Chuyển đổi hai chiều chữ Hán phồn thể/giản thể và mở rộng từ đồng nghĩa tìm kiếm với Ollama**:
   - **Bao phủ toàn diện biến thể chữ và từ đồng nghĩa chuyên ngành**: Khi tìm kiếm bằng chữ Hán, hệ thống tự động gọi Ollama để chuyển đổi hai chiều chữ phồn thể và giản thể (ví dụ: "專案" <-> "专案"), đồng thời mở rộng từ đồng nghĩa kỹ thuật/kinh doanh (ví dụ: "專案" <-> "项目", "程式碼" <-> "代码", "記憶體" <-> "内存", "網路" <-> "网络"), tạo thành truy vấn kết hợp Lucene giúp truy xuất liền mạch giữa các tài liệu phồn thể và giản thể.
   - **Không làm chậm truy vấn chữ cái/số thông thường**: Các từ khóa tiếng Anh hoặc mã số (như `EFC-109`, `login`) bỏ qua Ollama hoàn toàn (độ trễ 0ms).
   - **Bộ nhớ đệm LRU tốc độ cao**: Bộ nhớ đệm LRU an toàn đa luồng (dung lượng 1.000 mục) đảm bảo các truy vấn lặp lại phản hồi ngay lập tức (< 1ms).
   - **Kiểm soát thời gian chờ (mặc định 6 giây) và ngắt mạch an toàn**: Thêm cấu hình `llm.search.expansion.enabled` (mặc định true) và `llm.search.expansion.timeout` (mặc định 6 giây); nếu Ollama ngoại tuyến hoặc hết giờ, hệ thống tự động quay lại tìm kiếm từ khóa gốc mà không làm gián đoạn người dùng, ngắt mạch 30 giây để tránh chờ đợi lặp lại.
10. **Bảo toàn Đường dẫn Windows UNC và Dấu gạch chéo ngược trong Hiển thị Markdown (Markdown Windows UNC Path & Backslash Preservation)**:
    - **Khắc phục Nguyên nhân Gốc rễ**: Sửa lỗi khi nhập đường dẫn chia sẻ mạng Windows UNC (ví dụ: `\\hlmt.com.tw\SysVol\hlmt.com.tw\Policies\{9CECF8CB-B752-4E7C-A1FB-90CB3B021A07}\User\Scripts\Logon` hoặc `"\\hlmt.com.tw\..."`) trong chi tiết yêu cầu hoặc bình luận, cơ chế thoát dấu câu mặc định của CommonMark khiến hai dấu gạch chéo ngược ở đầu `\\` bị thu gọn thành một dấu `\`, và dấu gạch chéo ngược trước dấu ngoặc nhọn `\{` bị mất.
    - **Giữ nguyên 100% Kiểu chữ Tự nhiên**: Hiển thị dưới dạng văn bản thường, không ép buộc bọc thành thẻ code khối hộp, đảm bảo hài hòa thị giác với văn bản xung quanh.
    - **Hỗ trợ Toàn diện và Miễn nhiễm Khối Mã nguồn**: Hỗ trợ đầy đủ đường dẫn chia sẻ UNC (`\\server\share`), đường dẫn ổ đĩa cục bộ (`C:\...`), đường dẫn tương đối và dấu gạch chéo kép đơn lẻ (`\\`); tự động bỏ qua khối mã dòng (`` `...` ``) và khối mã có hàng rào (```` ```...``` ````) để tránh thoát kép, đồng thời bảo đảm cơ chế thoát dấu câu thông thường của Markdown vẫn hoạt động chính xác.
    - **Giữ nguyên Ngắt dòng Đơn (`.softbreak("<br/>")`)**: Cấu hình CommonMark `HtmlRenderer` với `.softbreak("<br/>")` để chuyển đổi các ký tự xuống dòng đơn (`\n` / `\r\n`) trong phần chi tiết phiếu và bình luận thành thẻ `<br/>`, giữ nguyên bố cục xuống dòng khi hiển thị.
11. **Logo Vector Viên nang JTrac NG và Nâng cấp Tiêu đề "Lightweight Knowledge Query System"**:
    - **Logo Thương hiệu Vector Toàn diện "JTrac [NG]"**: Thay thế tệp GIF cũ kỹ từ 15 năm trước bằng định dạng vector SVG sắc nét (chữ `JTrac` màu xanh đậm kết hợp huy hiệu viên nang chuyển sắc xanh lam-ngọc chứa chữ `NG` trắng nổi bật), hiển thị hoàn hảo trên màn hình Retina và tự thích ứng giao diện Sáng/Tối, đồng thời cung cấp PNG trong suốt và GIF kế thừa để đảm bảo tương thích ngược 100%.
    - **Định vị Tiêu đề Rõ ràng**: Nâng cấp toàn diện dòng chữ mặc định từ "JTrac - Open Source Issue Tracking System" thành "`Lightweight Knowledge Query System`" (Hệ thống Truy vấn Tri thức Tinh gọn), loại bỏ hoàn toàn các rắc rối thoát ký tự `&` và nêu bật giá trị cốt lõi hiện đại.
    - **Bố cục Logo Tinh gọn và Căn lề Trái Tối ưu cho Thiết bị Di động (Mobile Compact Logo & Tight Left-Aligned Header)**: Dành cho điện thoại thông minh và thiết bị di động (`<= 768px` và `<= 480px`), kích thước logo tự động thu nhỏ về chiều cao 26px (22px trên máy nhỏ, chiều rộng tối đa 90-110px). Xóa bỏ hoàn toàn ràng buộc bảng cũ `width="20%"` và `height="55px"`, đặt dòng tiêu đề sát ngay bên phải logo để giải phóng tối đa không gian màn hình ngang và tránh hiện tượng văn bản bị ngắt dòng dọc bất hợp lý.

---

## 7. Hướng dẫn nâng cấp và tính tương thích

- **Cơ sở dữ liệu**: Tương thích hoàn toàn với bản 2.3.3-2.0.0; **không cần chạy bất kỳ mã script nâng cấp nào**.
- **Triển khai**: Ghi đè tệp `target/jtrac.war` vào `ROOT.war` hiện có trên máy chủ.
- **Tài liệu liên quan**:
  - [Hướng dẫn truy vấn Email và viết Prompt cho JTrac AI](../llm/PROMPT_EXAMPLES_vi.md)
  - [Ghi chú phát hành phiên bản trước (2.3.3-2.0.0)](release-2.3.3-2.0.0_vi.md)
