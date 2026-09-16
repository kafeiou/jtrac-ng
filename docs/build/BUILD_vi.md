# Hướng dẫn Biên dịch và Đóng gói JTrac (Tiếng Việt)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

Tài liệu này hướng dẫn chi tiết cách sử dụng Apache Maven để biên dịch và đóng gói dự án JTrac NG 3.0.0-beta, phân tích vòng đời Maven, cơ chế lưu bộ nhớ đệm thư viện, cấu trúc gói WAR và CLI, ma trận kiểm thử Web Container và xử lý sự cố biên dịch.

---

## 1. Yêu cầu Môi trường

Trước khi bắt đầu biên dịch, hãy đảm bảo môi trường phát triển của bạn đáp ứng các yêu cầu sau:

- **Hệ điều hành**: Windows / Linux / macOS
- **Bộ công cụ Java (JDK)**: **Java 11+ / 17+** (Khuyến nghị JDK 17 LTS, ví dụ: `W:\developer\jdk-17.0.9`, yêu cầu tối thiểu Java 11+)
  > [!IMPORTANT]
  > Phiên bản hiện đại hóa đã nâng cấp lên Spring 5.3.x, Hibernate 5.6.x và Apache Wicket 9.x với bytecode mục tiêu là **Java 11**. **JDK 8 không còn được hỗ trợ**, vui lòng không sử dụng JDK 8 để biên dịch.
- **Apache Maven**: **Maven 3.9+** (ví dụ: `W:\developer\apache-maven-3.9.9`)

### Ví dụ Cấu hình Biến Môi trường

- **Windows (PowerShell)**:
  ```powershell
  $env:JAVA_HOME = "W:\developer\jdk-17.0.9"
  $env:PATH = "W:\developer\apache-maven-3.9.9\bin;$env:PATH"
  ```
- **Windows (CMD)**:
  ```cmd
  set "JAVA_HOME=W:\developer\jdk-17.0.9"
  set "PATH=W:\developer\apache-maven-3.9.9\bin;%PATH%"
  ```
- **Linux / macOS (Bash/Zsh)**:
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

Kiểm tra môi trường:
```bash
mvn -version
```
Kết quả hiển thị chính xác phiên bản Maven 3.9+ cùng Java 11 hoặc 17.

---

## 2. Ma trận Lệnh Biên dịch Maven

Chạy các lệnh sau tại thư mục gốc của dự án JTrac (nơi chứa `pom.xml`):

| Lệnh | Giai đoạn / Tác vụ | Mô tả |
|---|---|---|
| `mvn clean compile` | Biên dịch Mã nguồn | Dọn dẹp tệp cũ và biên dịch `src/main/java`, xử lý lọc tài nguyên UTF-8 |
| `mvn test-compile` | Biên dịch Kiểm thử | Biên dịch các lớp kiểm thử trong `src/test/java` |
| `mvn test` | Chạy Kiểm thử | Thực thi kiểm thử đơn vị JUnit 5 (kết hợp HSQLDB bộ nhớ nhúng, không cần cơ sở dữ liệu ngoài) |
| `mvn package` | Đóng gói Chính thức | Chạy toàn bộ kiểm thử và đóng gói thành tệp Web WAR (`target/jtrac.war`) |
| `mvn package -DskipTests` | Đóng gói Nhanh | Bỏ qua kiểm thử để đóng gói nhanh `target/jtrac.war` |
| `mvn clean` | Dọn dẹp | Xóa toàn bộ bộ nhớ đệm và tệp tạm trong thư mục `target/` |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | Đóng gói Công cụ CLI | Biên dịch và đóng gói công cụ xuất HTML dòng lệnh độc lập thành `tools/jtrac-exporter.jar` |

---

## 3. Cơ chế Lưu Bộ nhớ đệm Thư viện (`~/.m2/repository`)

JTrac được xây dựng trên chuẩn Apache Maven. Toàn bộ thư viện phụ thuộc của bên thứ ba (Spring 5.3.x, Wicket 9.x, Hibernate 5.6.x, Spring Security 5.8.x...) đều được khai báo trong [`pom.xml`](../../pom.xml).

### Quy trình Tải và Lưu đệm:
1. Khi chạy `mvn compile` hoặc `mvn package` lần đầu, Maven kết nối đến kho trung tâm (Maven Central) để tải thư viện.
2. Mọi tệp JAR tải về được lưu trong thư mục đệm cục bộ:
   - **Windows**: `%USERPROFILE%\.m2\repository\`
   - **Linux / macOS**: `~/.m2/repository/`
3. Các lần đóng gói tiếp theo diễn ra ngoại tuyến từ bộ nhớ đệm này. **Lập trình viên không cần tải hoặc cấu hình tệp JAR thủ công**.

---

## 4. Cấu trúc Gói WAR (`WEB-INF/lib/`)

Sau khi thực hiện `mvn package`, tệp gói Java Web chuẩn được tạo ra tại: [`target/jtrac.war`](../../target/jtrac.war).

### Cấu trúc Thư mục Bên trong:
```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- Các class đã biên dịch và tài nguyên UTF-8
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- [Trọng tâm: Toàn bộ thư viện JAR bên thứ ba]
│   │   ├── spring-core-5.3.37.jar
│   │   ├── wicket-core-9.16.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-5.8.14.jar
│   │   ├── hsqldb-2.7.2.jar
│   │   └── ... (tất cả các thư viện còn lại)
│   └── web.xml                  <-- Cấu hình Servlet 4.0
└── resources/                   <-- Tài nguyên tĩnh (CSS, biểu tượng)
```

- **Cô lập Classloader**: Servlet Container tự động cách ly `WEB-INF/lib/` của từng ứng dụng Web.
- **Triển khai Tự trị**: Không cần cài thêm thư viện nào vào máy chủ, chỉ cần đặt tệp `jtrac.war` là có thể chạy ngay.

---

## 5. Ma trận Tương thích Web Container

JTrac NG 3.0.0-beta tuân thủ chuẩn Servlet 4.0 (`javax.servlet`). Tệp WAR có thể triển khai trực tiếp trên các máy chủ hiện đại:

| Web Container | Phiên bản Hỗ trợ | Phương thức Triển khai |
|---|---|---|
| **Jetty 10.x** | 10.0.x (Khuyến nghị) | **Sẵn sàng chạy**: Sao chép `target/jtrac.war` thành `webapps/ROOT.war` và khởi động. |
| **Jetty 12.x** | 12.0.x (Mới nhất) | **Hỗ trợ trực tiếp**: Kích hoạt mô-đun `ee8`:<br/>`java -jar start.jar --add-modules=server,http,ee8-deploy,ee8-webapp`. |
| **Tomcat 9.x** | 9.0.x (Khuyến nghị) | **Sẵn sàng chạy**: Sao chép `target/jtrac.war` thành `webapps/ROOT.war` và khởi động. |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **Chuyển đổi Tự động**:<br/>1. **Cách A**: Đặt vào thư mục `webapps-javaee/` để máy chủ tự chuyển đổi.<br/>2. **Cách B**: Dùng công cụ `jakartaee-migration` để tạo `jtrac-jakarta.war` rồi đặt vào `webapps/`. |

### Ví dụ Xác thực trên Jetty 10 Cục bộ:
1. Sao chép `target/jtrac.war` thành `W:\developer\jetty-10.0.26\webapps\ROOT.war`.
2. Khởi động Jetty:
   ```powershell
   & "W:\developer\jdk-17.0.9\bin\java.exe" -jar W:\developer\jetty-10.0.26\start.jar
   ```
3. Truy cập trình duyệt: `http://localhost:8888/` (tài khoản mặc định: `admin` / `admin`).

---

## 6. Xác thực Sản phẩm và Xử lý Sự cố (Build Troubleshooting)

### 6.1 Danh sách Kiểm tra
Sau khi biên dịch xong, xác nhận các tệp sau đã được tạo:
- [ ] `target/jtrac.war` (dung lượng khoảng 18~22 MB, đã tối ưu kích thước sau khi bỏ Apache POI)
- [ ] `tools/jtrac-exporter.jar` (nếu có đóng gói công cụ CLI)

### 6.2 Các Lỗi Thường gặp và Cách khắc phục

1. **Lỗi Bảng mã Ký tự (`unmappable character for encoding`)**:
   - Nguyên nhân: Bảng mã mặc định của Windows console không đọc được chú thích UTF-8.
   - Khắc phục: Thiết lập biến môi trường trước khi chạy Maven:
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
2. **Lỗi Phiên bản JDK (`Fatal error compiling: invalid target release: 11`)**:
   - Nguyên nhân: Đang dùng JDK 8 hoặc cũ hơn trong terminal.
   - Khắc phục: Chuyển biến môi trường `JAVA_HOME` sang JDK 11 hoặc 17.
3. **Thư viện Tải về Bị lỗi hoặc Ngắt quãng**:
   - Khắc phục: Buộc Maven cập nhật lại toàn bộ bộ nhớ đệm:
     ```bash
     mvn clean compile -U
     ```
4. **Tràn Bộ nhớ (`java.lang.OutOfMemoryError`)**:
   - Khắc phục: Tăng bộ nhớ heap cho tiến trình Maven:
     ```bash
     export MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m"
     ```

---

## 7. Giải pháp Thay thế: Tự động Đóng gói bằng Docker Multi-Stage

Nếu bạn không muốn cài đặt JDK hoặc Maven trên máy cục bộ, dự án cung cấp sẵn giải pháp đóng gói tự động bằng Docker:

👉 **Xem hướng dẫn chi tiết tại: [`docker/README.md`](../../docker/README.md)**
