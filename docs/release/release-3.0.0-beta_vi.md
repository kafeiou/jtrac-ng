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
   - **Mặc định hiển thị lịch sử (`showHistory = true`)**: Hiển thị chi tiết từng lần sửa đổi và bình luận trực tiếp trên danh sách phiếu công việc.
   - **Tự động xây dựng lại chỉ mục bất đồng bộ khi khởi động**: Phát hiện nâng cấp phiên bản bộ phân tích (`lucene.analyzer.version = 3.0.0-subtoken-v1`) và tự động chạy lại chỉ mục trong nền sau khi khởi động.
   - **Di chuyển Docker Hub**: Cập nhật tài liệu 8 ngôn ngữ sang `kafeiou/jtrac-ng:latest`.

---

## 7. Hướng dẫn nâng cấp và tính tương thích

- **Cơ sở dữ liệu**: Tương thích hoàn toàn với bản 2.3.3-2.0.0; **không cần chạy bất kỳ mã script nâng cấp nào**.
- **Triển khai**: Ghi đè tệp `target/jtrac.war` vào `ROOT.war` hiện có trên máy chủ.
- **Tài liệu liên quan**:
  - [Hướng dẫn truy vấn Email và viết Prompt cho JTrac AI](../llm/PROMPT_EXAMPLES_vi.md)
  - [Ghi chú phát hành phiên bản trước (2.3.3-2.0.0)](release-2.3.3-2.0.0_vi.md)
