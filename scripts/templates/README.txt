================================================================================
  JTrac NG (v3.0.0-beta) 免安裝可攜綠色版 (Portable Edition) - 繁體中文
================================================================================

【環境需求】
  - 本系統需要 Java 21 (JDK 21) 或以上執行環境。
  - 若您的電腦尚未安裝 Java 21，請至以下官方網站下載安裝（免費用戶首選）：
    https://adoptium.net/temurin/releases/?version=21

【啟動系統】
  - Windows: 雙擊執行 start.bat（預設會自動開啟瀏覽器連線至 http://localhost:8080）
    * 若 8080 埠已被佔用，可用命令提示字元指定埠號啟動，例如：start.bat 8888
  - Linux / macOS: 在終端機執行 ./start.sh（或 ./start.sh 8888）

【停止系統】
  - Windows: 雙擊執行 stop.bat 或直接關閉啟動終端機視窗。
  - Linux / macOS: 在終端機執行 ./stop.sh 或按下 Ctrl+C。

【預設管理者帳號】
  - 帳號：admin
  - 密碼：admin
  - 初次登入後請立即前往「個人設定」變更密碼。

【資料儲存說明】
  - 本綠色版的所有資料（包括內建 HSQLDB 資料庫、附件檔案、Lucene 全文檢索索引）
    預設全部儲存在本資料夾內的「data」目錄中。
  - 完全不會在您的系統暫存區或 AppData 殘留檔案。
  - 要備份或搬遷至其他電腦，只需將整份資料夾複製帶走即可！


================================================================================
  JTrac NG (v3.0.0-beta) Portable Edition - English
================================================================================

[System Requirements]
  - Java 21 (JDK 21) or higher is required to run this application.
  - If Java 21 is not installed on your system, please download it from:
    https://adoptium.net/temurin/releases/?version=21

[Starting the Server]
  - Windows: Double-click start.bat (automatically opens http://localhost:8080 in your browser).
    * If port 8080 is already in use, specify a custom port in CMD, e.g.: start.bat 8888
  - Linux / macOS: Run ./start.sh in terminal (or ./start.sh 8888)

[Stopping the Server]
  - Windows: Double-click stop.bat or press Ctrl+C in the terminal.
  - Linux / macOS: Run ./stop.sh in terminal or press Ctrl+C.

[Default Admin Credentials]
  - Username: admin
  - Password: admin
  - Please change the password immediately after your first login in user settings.

[Storage & Portability]
  - All application data (embedded HSQLDB database, uploaded attachments, Lucene search indexes)
    is completely self-contained within the "data" directory in this folder.
  - Zero residue in system AppData or temporary directories.
  - To backup or migrate to another machine, simply copy the entire folder!
