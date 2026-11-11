# Hướng dẫn Biên dịch và Đóng gói JTrac NG (Tiếng Việt)

[English](BUILD_en.md) | [繁體中文](BUILD_zh-TW.md) | [简体中文](BUILD_zh-CN.md) | [日本語](BUILD_ja.md) | [Tiếng Việt](BUILD_vi.md) | [Deutsch](BUILD_de.md) | [Español](BUILD_es.md) | [Français](BUILD_fr.md)

Tài liệu này cung cấp hướng dẫn toàn diện về cách biên dịch và đóng gói dự án JTrac NG 3.0.0-beta bằng Apache Maven, bao gồm vòng đời Maven, quản lý bộ nhớ đệm phụ thuộc, cấu trúc lưu trữ WAR và bản phân phối Portable độc lập, khả năng tương thích máy chủ servlet và xử lý sự cố.

---

## 1. Yêu cầu môi trường tiên quyết

Trước khi bắt đầu biên dịch, hãy đảm bảo rằng môi trường cục bộ của bạn đáp ứng các điều kiện sau:

- **Hệ điều hành**: Windows / Linux / macOS
- **Bộ phát triển Java (JDK)**: **Java 21 LTS** (Khuyên dùng [Eclipse Temurin JDK 21+](https://adoptium.net/temurin/releases/?version=21), ví dụ `W:\developer\jdk-21.0.9`, yêu cầu tối thiểu Java 21)
  > [!IMPORTANT]
  > Kiến trúc cốt lõi đã được nâng cấp toàn diện lên **Spring 6.2.x**, **Jakarta EE 10 (Servlet 6.0)**, **Hibernate 5.6.x (Jakarta)** và **Apache Wicket 10.x**, hướng tới bytecode mục tiêu là **Java 21**. **JDK 8 / 11 / 17 không còn được hỗ trợ**. Bạn bắt buộc phải biên dịch bằng JDK 21+.
- **Apache Maven**: **Maven 3.9+** (ví dụ `W:\developer\apache-maven-3.9.9`)

### Cấu hình biến môi trường cục bộ

- **Windows (PowerShell)**:
  ```powershell
  $env:JAVA_HOME = "W:\developer\jdk-21.0.9"
  $env:PATH = "W:\developer\apache-maven-3.9.9\bin;$env:PATH"
  ```
- **Windows (CMD)**:
  ```cmd
  set "JAVA_HOME=W:\developer\jdk-21.0.9"
  set "PATH=W:\developer\apache-maven-3.9.9\bin;%PATH%"
  ```
- **Linux / macOS (Bash/Zsh)**:
  ```bash
  export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

Kiểm tra xác thực môi trường:
```bash
mvn -version
```
Kết quả hiển thị phải xác nhận Maven 3.9+ và Java 21.

---

## 2. Bảng lệnh xây dựng và biên dịch Maven

Chạy các lệnh sau trong thư mục gốc của dự án (nơi chứa tệp `pom.xml`):

| Lệnh | Giai đoạn / Tác vụ | Mô tả chi tiết | Tệp đầu ra |
|---|---|---|---|
| `mvn clean compile` | Biên dịch mã nguồn | Dọn dẹp và biên dịch `src/main/java` với bộ lọc UTF-8 | `target/classes/` |
| `mvn test-compile` | Biên dịch kiểm thử | Biên dịch tất cả các lớp kiểm thử trong `src/test/java` | `target/test-classes/` |
| `mvn test` | Chạy kiểm thử | Chạy 198 bài kiểm thử đơn vị JUnit 5 (tích hợp HSQLDB trong bộ nhớ) | Báo cáo kiểm thử |
| `mvn package` | Đóng gói WAR tiêu chuẩn | Chạy kiểm thử và đóng gói ứng dụng Web tiêu chuẩn | `target/jtrac.war` |
| `mvn package -DskipTests` | Đóng gói WAR nhanh | Bỏ qua kiểm thử để tạo tệp WAR nhanh chóng | `target/jtrac.war` |
| `mvn package -Pportable` | Đóng gói bản Portable đầy đủ | Chạy kiểm thử và tạo bản phân phối ZIP độc lập với Jetty 12.1 EE10 | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn package -Pportable -DskipTests` | Đóng gói Portable nhanh | Bỏ qua kiểm thử để tạo tệp ZIP độc lập siêu tốc | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `package-portable.bat` | Tập lệnh Windows | Nhấp đúp vào tệp ở thư mục gốc; tự động bỏ qua kiểm thử | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `./package-portable.sh` | Tập lệnh Linux/macOS | Chạy tập lệnh Bash để tạo nhanh tệp ZIP di động | `target/jtrac-ng-3.0.0-beta-portable.zip` |
| `mvn clean` | Dọn dẹp bản dựng | Xóa thư mục `target/` và các tệp tạm thời | - |
| `mvn clean package -f tools/jtrac-exporter/pom.xml -DskipTests` | Đóng gói công cụ CLI | Biên dịch công cụ dòng lệnh xuất dữ liệu độc lập (Fat JAR) | `tools/jtrac-exporter.jar` |

---

## 3. Cơ chế tự động tải gói phụ thuộc (`~/.m2/repository`)

JTrac NG được xây dựng trên nền tảng Maven tiêu chuẩn. Tất cả các thư viện của bên thứ ba đều được khai báo trong [`pom.xml`](../../pom.xml).

---

## 4. Cấu trúc lưu trữ nội bộ của gói WAR (`WEB-INF/lib/`)

Lệnh `mvn package` tạo ra tệp lưu trữ ứng dụng Web hoàn chỉnh: [`target/jtrac.war`](../../target/jtrac.war).

```text
jtrac.war
├── META-INF/
│   └── MANIFEST.MF
├── WEB-INF/
│   ├── classes/                 <-- Các lớp đã biên dịch và tài nguyên UTF-8
│   │   ├── info/jtrac/...
│   │   └── messages*.properties
│   ├── lib/                     <-- [Thư viện phụ thuộc hiện đại Jakarta EE 10]
│   │   ├── spring-core-6.2.19.jar
│   │   ├── wicket-core-10.12.0.jar
│   │   ├── hibernate-core-5.6.15.Final.jar
│   │   ├── spring-security-core-6.5.11.jar
│   │   ├── jakarta.servlet-api-6.0.0.jar
│   │   ├── hsqldb-2.7.4.jar
│   │   └── ... (tất cả các thư viện còn lại)
│   └── web.xml                  <-- Tệp cấu hình chuẩn Servlet 6.0
└── resources/                   <-- Tài nguyên tĩnh (CSS, logo vector SVG)
```

---

## 5. Ma trận tương thích của máy chủ Servlet Web

JTrac NG 3.0.0-beta tuân thủ đặc tả **Jakarta EE 10 / Servlet 6.0** (`jakarta.servlet.*`):

| Máy chủ Servlet | Phiên bản hỗ trợ | Phương thức triển khai |
|---|---|---|
| **Jetty 12.x** | 12.1.x+ (Khuyên dùng) | **Hỗ trợ gốc**: Kích hoạt mô-đun `ee10`:<br/>`java -jar start.jar --add-modules=server,http,ee10-deploy,ee10-webapp`<br/>Hoặc chạy trực tiếp bản phân phối di động (`jtrac-ng-3.0.0-beta-portable.zip`). |
| **Tomcat 10.x / 11.x** | 10.1.x / 11.0.x | **Hỗ trợ gốc**: Tích hợp gốc Jakarta EE 10. Sao chép `target/jtrac.war` vào `webapps/ROOT.war`. |
| **Vỏ chứa Docker** | 3.0.0-beta | **Hình ảnh chính thức**: Dựa trên `jetty:12.1-jre21-eclipse-temurin`. |

---

## 6. Xác thực bản dựng và xử lý sự cố thường gặp

### 6.1 Danh sách kiểm tra thành công
- [ ] `target/jtrac.war` (dung lượng khoảng 50~56 MB)
- [ ] `target/jtrac-ng-3.0.0-beta-portable.zip` (dung lượng khoảng 98 MB)
- [ ] `tools/jtrac-exporter.jar` (nếu đã biên dịch công cụ dòng lệnh)

### 6.2 Các sự cố thường gặp

1. **Lỗi phiên bản trình biên dịch (`invalid target release: 21`)**:
   - Nguyên nhân: JDK đang hoạt động trong thiết bị đầu cuối là JDK 17 hoặc cũ hơn.
   - Khắc phục: Thiết lập `JAVA_HOME` sang JDK 21+ và kiểm tra lại bằng lệnh `java -version`.
2. **Lỗi mã hóa ký tự (`unmappable character for encoding`)**:
   - Khắc phục: Thiết lập mã hóa UTF-8 cho Maven:
     ```powershell
     $env:MAVEN_OPTS = "-Dfile.encoding=UTF-8"
     ```
3. **Phụ thuộc tải về bị hỏng**:
   - Khắc phục: Buộc Maven cập nhật lại bộ nhớ đệm:
     ```bash
     mvn clean compile -U
     ```

---

## 7. Giải pháp không cần cài đặt: Bản dựng đa tầng Docker

Nếu bạn muốn biên dịch mà không cần cài đặt JDK hoặc Maven trên máy cục bộ:

👉 **Xem hướng dẫn chi tiết tại: [`docker/README.md`](../../docker/README.md)**
