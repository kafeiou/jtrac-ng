# Ghi chú Phát hành JTrac NG (Release Notes) - 2.3.3-2.0.0

[English](release-2.3.3-2.0.0_en.md) | [繁體中文](release-2.3.3-2.0.0_zh-TW.md) | [简体中文](release-2.3.3-2.0.0_zh-CN.md) | [日本語](release-2.3.3-2.0.0_ja.md) | [Tiếng Việt](release-2.3.3-2.0.0_vi.md) | [Deutsch](release-2.3.3-2.0.0_de.md) | [Español](release-2.3.3-2.0.0_es.md) | [Français](release-2.3.3-2.0.0_fr.md)

---

[![Java](https://img.shields.io/badge/Java-11%20%7C%2017-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../../license.txt)
[![Fork From](https://img.shields.io/badge/Fork%20From-JTrac%202.3.3-blue)](https://jtrac.info)
[![Version](https://img.shields.io/badge/Version-2.3.3--2.0.0-blue.svg)](../../pom.xml)
[![Wicket](https://img.shields.io/badge/Wicket-9.16.0-blue.svg)](https://wicket.apache.org/)
[![Spring](https://img.shields.io/badge/Spring-5.3.37-brightgreen.svg)](https://spring.io/)

Dự án này bắt nguồn từ phiên bản [JTrac 2.3.3 (https://jtrac.info)](https://jtrac.info). Dự án nỗ lực cung cấp một hệ thống theo dõi và ghi chép văn bản Q&A gọn nhẹ, độ tương thích cao, hỗ trợ lưu trữ tĩnh ngoại tuyến và giao diện người dùng thân thiện, rất thích hợp cho mục đích quản lý tri thức (Knowledge Management), kèm theo tệp đính kèm cho các quy trình phức tạp.

---

## 🚀 Phiên bản 2.3.3-2.0.0 Nâng cấp Lớn (Major Architecture Upgrade)

1. **Nâng cấp Kiến trúc Backend (Spring 5.3 + Hibernate 5.6 + JUnit 5)**:
   - Nâng cấp lên Spring Framework 5.3.37, loại bỏ hoàn toàn `HibernateTemplate` và `TimerFactoryBean`.
   - Nâng cấp lên Hibernate ORM 5.6.15.Final với quản lý `SessionFactory` nguyên bản và truy vấn chuẩn JPA.
   - Chuyển đổi tìm kiếm toàn văn sang API Lucene nguyên bản, tách biệt khỏi thư viện cũ `spring-modules-lucene`.
   - Nâng cấp kiểm thử đơn vị sang JUnit 5 (Jupiter).
2. **Cải tiến Bảo mật Toàn diện (Spring Security 5.8 + Tự động chuyển đổi BCrypt)**:
   - Thay thế Acegi Security 1.0.7 cũ kỹ bằng Spring Security 5.8.14 chuẩn mực.
   - Cung cấp `JtracHybridPasswordEncoder`: tương thích mã băm MD5 cũ và tự động tái băm sang BCrypt an toàn ngay khi người dùng đăng nhập thành công.
3. **Nâng cấp Tầng Web (Apache Wicket 9.16.0)**:
   - Thay thế Wicket 1.3.7 từ năm 2008 bằng Wicket 9.16.0 với Generics toàn diện (`IModel<T>`).
   - Tương thích hoàn hảo với các Servlet Container chuẩn Servlet 4.0 (Jetty 10.0.26, Jetty 12, Tomcat 9, Tomcat 10+).
4. **Phân trang Danh sách Người dùng & Không gian Dự án (Pagination & Settings)**:
   - Danh sách người dùng (`UserListPage`) và không gian (`SpaceListPage`) hỗ trợ phân trang linh hoạt (10, 25, 50, 100, Tất cả).
   - Thêm tham số `users.list.pageSize` và `spaces.list.pageSize` vào bảng `config`.
5. **Sửa lỗi Sự kiện Ajax Phân quyền Dự án**:
   - Chuyển đổi sự kiện Ajax sang chuẩn DOM `"change"`, xử lý chống lỗi khi bỏ chọn quyền.
6. **Bộ lọc Tài nguyên Tĩnh Toàn cục (StaticResourceFilter)**:
   - Giải quyết triệt để lỗi 404 hình ảnh `../resources/*` ở các đường dẫn URL lồng nhau và bổ sung các biểu tượng còn thiếu.
7. **Sửa lỗi Gắn kết Model cho Upload Tệp (FileUpload Model Binding)**:
   - Gắn kết `ListModel` độc lập cho `FileUploadField` trong `ItemFormPage` và `ItemViewFormPanel`, loại bỏ lỗi ngoại lệ thuộc tính `file`.
8. **Nâng cấp Cơ sở dữ liệu và Kịch bản SQL**:
   - Cung cấp kịch bản nâng cấp [`etc/sql/upgrade-to-2.0.0.sql`](../../etc/sql/upgrade-to-2.0.0.sql) cho MySQL, PostgreSQL, SQL Server, Oracle.
   - Tích hợp `HsqldbDatabaseMigrator` tự động sao lưu và nâng cấp HSQLDB 1.8 lên 2.x khi khởi động.
9. **Xóa Bỏ Mô-đun Excel & Thu Nhỏ Kích Thước Gói WAR (Excel Module Removal & POI Deprecation)**:
   - Loại bỏ hoàn toàn tính năng nhập/xuất Excel và thư viện Apache POI, giảm kích thước gói WAR hơn 3 MB.
10. **Nâng Cấp Gói Sao Lưu Toàn Bộ Hệ Thống (`jtrac-dump.sql`)**:
    - Gói ZIP sao lưu toàn bộ hệ thống hiện bao gồm tệp kết xuất SQL độc lập `jtrac-dump.sql` (chứa ANSI DDL, chú thích phương ngữ cho MySQL/PostgreSQL/HSQLDB, các câu lệnh INSERT sắp xếp theo khóa ngoại và lệnh đặt lại sequence) phục vụ di chuyển dữ liệu và khôi phục sự cố.
11. **Phân Vùng Tệp Đính Kèm Theo ID Dự Án & Lập Chỉ Mục Toàn Văn Lucene**:
    - **Cấu Trúc Thư Mục Phân Vùng Theo ID Số (Tùy Chọn C)**: Tệp đính kèm được lưu theo ID dự án dạng số thuần túy (`${jtrac.home}/attachments/{spaceId}/{prefix}_{filename}`), loại bỏ hoàn toàn rủi ro khi đổi tên dự án.
    - **Cơ Chế Đọc Dự Phòng Kép (Dual-Read Fallback)**: Tự động chuyển hướng về thư mục gốc và thư mục cách ly (`attachments/0_ORPHAN/`), bảo đảm 0% lỗi liên kết tải xuống 404.
    - **Tự Động Di Chuyển Khi Khởi Động**: Quét và tự động di chuyển tệp đính kèm cũ vào các thư mục dự án khi máy chủ khởi động, kèm tệp đánh dấu hoàn tất (`.attachment_migrated`).
    - **Trích Xuất Văn Bản Đa Định Dạng**: Hỗ trợ `.xlsx`, `.docx` (bộ phân tích OpenXML thuần JDK), `.pdf` (Apache PDFBox 2.0.31), `.txt`, `.csv`, `.md`, `.log` cùng công cụ nhận diện bảng mã `SmartCharsetDetector`.
    - **Giới Hạn Bảo Vệ & Hàng Đợi Bất Đồng Bộ**: Giới hạn 10MB mỗi tệp và 50.000 ký tự; luồng xử lý nền (`ExecutorService`) giúp phản hồi tải lên tức thì.

---

## Công nghệ & Kiến trúc (Technologies & Architecture)

- **Ngôn ngữ chính**: Java 11 / 17
- **Web Framework**: Apache Wicket 9.16.0
- **IoC Container**: Spring Framework 5.3.37
- **Bảo mật**: Spring Security 5.8.14 (Mã hóa BCrypt)
- **ORM & Persistence**: Hibernate ORM 5.6.15.Final
- **Cơ sở dữ liệu hỗ trợ**: HSQLDB 2.x (mặc định), MySQL / MariaDB, PostgreSQL, Microsoft SQL Server, Oracle
- **Máy chủ Web hỗ trợ**:
  - **Jetty 10.x** (Hỗ trợ trực tiếp, đã kiểm thử thực tế trên Jetty 10.0.26)
  - **Jetty 12.x** (Bật module `ee8` để chạy trực tiếp)
  - **Tomcat 9.x** (Hỗ trợ trực tiếp)
  - **Tomcat 10.x / 11.x** (Hỗ trợ thông qua thư mục tự động chuyển đổi `webapps-javaee/` hoặc công cụ `jakartaee-migration`)
- **Công cụ đóng gói**: Apache Maven 3.9+

---

## 📜 Lịch sử Phiên bản (Release History)

- **Phiên bản tiếp theo (Bản xem trước)**: [Ghi chú Phát hành JTrac NG - 3.0.0-beta](release-3.0.0-beta_vi.md)
- **Phiên bản trước**: [Ghi chú Phát hành JTrac NG - 2.3.3-1.0.0](release-2.3.3-1.0.0_vi.md)

---

## Giấy phép (License)

JTrac là phần mềm mã nguồn mở theo giấy phép [Apache Software License, Version 2.0](../../license.txt).
