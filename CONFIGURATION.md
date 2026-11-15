# Cấu hình LAB1 Chat

Java 21, Swing. Cấu hình UTF-8 tập trung tại app.properties ở thư mục gốc.

| Thuộc tính | Giá trị / ý nghĩa |
| --- | --- |
| server.host | 172.20.10.4 — đích của Client |
| server.bind | 0.0.0.0 — địa chỉ Server bind |
| server.port | 8000 — TCP |
| db.url | MySQL ltm_chat, chỉ Server sử dụng |
| db.username | Tài khoản MySQL của Server |
| db.password | Mật khẩu MySQL, không phải mật khẩu tài khoản chat |

Client tự kết nối, không có ô nhập IP/tên hiển thị. Offline có Retry.
Sau mất kết nối phải đăng nhập lại. Không fallback localhost hoặc tự chạy Server.

Đặt Working directory trong IntelliJ là thư mục gốc dự án. Nếu khác, chỉ định VM option:
-Dchat.config=C:/Users/qank0/IdeaProjects/LAB1_Chat/app.properties

File dùng cú pháp Java properties: dấu gạch chéo ngược trong giá trị cần escape.
Client không mở MySQL; có thể để trống db.password trong bản cấu hình trên Client.

Entry point Server: server.Server.
Entry point Client: client.LoginGUI; client.ClientGUI và client.Client cũng mở Login.
Không có script .bat trong source hiện tại.

Database mới: SETUP_DATABASE.sql.
Database cũ: sao lưu rồi chạy MIGRATION_AUTH.sql; giữ tài khoản/lịch sử.
Hướng dẫn LAN, danh sách file và checklist: UPGRADE_NOTES.md.
