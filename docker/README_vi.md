# Đóng gói & Triển khai JTrac NG Docker (Jetty 12.x + Eclipse Temurin 17+)

[English](README.md) | [繁體中文](README_zh-TW.md) | [簡體中文](README_zh-CN.md) | [日本語](README_ja.md) | [Tiếng Việt](README_vi.md) | [Deutsch](README_de.md) | [Español](README_es.md) | [Français](README_fr.md)

Thư mục này cung cấp môi trường xây dựng nhiều giai đoạn (Multi-stage Build) và triển khai container Docker gốc cho phiên bản JTrac NG hiện đại hóa.

---

## Tính năng nổi bật (Features)

- **Môi trường hiện đại**: Dựa trên hình ảnh chính thức `jetty:12-jre17-eclipse-temurin` tích hợp các mô-đun `ee8-deploy` và `ee8-webapp`, hỗ trợ nguyên bản Servlet 4.0 (`javax.servlet`).
- **Xây dựng nhiều giai đoạn (Multi-stage Build)**: Tự động biên dịch `jtrac.war` từ mã nguồn bằng `maven:3.9-eclipse-temurin-17`, không yêu cầu cài đặt trước JDK hoặc Maven trên máy chủ.
- **Hỗ trợ đầy đủ phông chữ đa ngôn ngữ**: Tích hợp sẵn `fontconfig`, `fonts-noto-cjk`, `fonts-noto-core` (tiếng Việt và dấu thanh), `fonts-dejavu-core`, đảm bảo không bị lỗi ô vuông (tofu) hay lỗi font khi trích xuất văn bản và xuất báo cáo.
- **Tự động sửa quyền và hạ quyền an toàn**: Entrypoint tự động sửa quyền sở hữu thư mục gắn kết `/jtrac-data` thành `jetty:jetty` (UID 999) và hạ quyền thực thi an toàn thông qua `gosu`.

---

## Bắt đầu nhanh (Quick Start)

### Cách 1: Lệnh Docker gốc (Khuyến nghị)

Từ thư mục `docker`, sử dụng thư mục gốc của dự án (`..`) làm ngữ cảnh xây dựng:

```bash
cd docker
docker build -f Dockerfile -t jtrac-ng:latest -t jtrac-ng:3.0.0-beta ..
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng jtrac-ng:latest
```

Mở `http://localhost:8888/` trong trình duyệt (tài khoản mặc định: `admin` / `admin`).

---

### Cách 2: Tập lệnh hỗ trợ đa nền tảng

- **Windows**:
  ```cmd
  cd docker
  build.bat
  run.bat
  ```

- **Linux / macOS**:
  ```bash
  cd docker
  chmod +x *.sh
  ./build.sh
  ./run.sh
  ```

---

### Cách 3: Chạy hình ảnh chính thức từ Docker Hub

**[https://hub.docker.com/r/kafeiou/jtrac-ng](https://hub.docker.com/r/kafeiou/jtrac-ng)**

```bash
docker run -d -p 8888:8080 -v jtrac_data:/jtrac-data --name jtrac-ng kafeiou/jtrac-ng:latest
```

---

## Xử lý sự cố: Đồng bộ mã nguồn & Xung đột thẻ Tag trên máy Build (Troubleshooting)

Nếu lệnh `git pull` trên máy chủ build Docker hoặc máy kiểm thử bị lỗi, hãy áp dụng các giải pháp sau:

1. **Lỗi từ chối ghi đè thẻ Tag (`would clobber existing tag`)**:
   Khi một thẻ phiên bản (như `3.0.0-beta`) được cập nhật cưỡng bức trên kho lưu trữ từ xa, Git mặc định sẽ chặn việc ghi đè. Hãy thêm cờ `-f`:
   ```bash
   git pull --tags -f
   ```

2. **Đặt lại nhanh về trạng thái từ xa (Khuyến nghị cho máy Build)**:
   Để xóa bỏ mọi tập tin tạm hoặc sai khác ký tự xuống dòng và đồng bộ chính xác 100%:
   ```bash
   git fetch --tags -f && git reset --hard origin/master
   ```

3. **Cấu hình lệnh tắt đồng bộ một chạm (Git Alias)**:
   Thiết lập một lần trên máy chủ để sau này chỉ cần nhập `git sync`:
   ```bash
   git config --global alias.sync "!git fetch --tags -f && git reset --hard origin/master"
   ```
