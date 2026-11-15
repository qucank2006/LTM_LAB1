# Bàn giao nâng cấp LAB1 Chat

## 1. Phạm vi xác minh

Chỉ đọc/chỉnh sửa file và phân tích tĩnh bằng IDE. Không chạy terminal, Maven, build, test, ứng dụng, MySQL hoặc SQL migration.
Các hành vi dưới đây đã được triển khai trong source và cần kiểm thử thực tế.
Không sửa target/classes; binary cũ chưa chứa nâng cấp.

## 2. Kết quả kiểm tra source ban đầu

- ClientGUI quản lý Swing và TCP; Client.java là entry point console cũ.
- Server relay TCP, danh sách Online và cung cấp địa chỉ P2P.
- LOGIN cũ chỉ báo danh bằng User do Client khai báo, không kiểm tra mật khẩu.
- ChatDatabase chỉ lưu messages bằng PreparedStatement và có file fallback khi MySQL lỗi.
- User đã tồn tại với username/IP/peerPort; Message có content, payload, timestamp, byte[] media.
- Group dùng header [GROUP_CHAT|TênNhóm|tv1,tv2] trong TEXT_MESSAGE; nhóm nằm trong bộ nhớ Client.
- Multicast: 230.0.0.1:8888. Broadcast: 255.255.255.255:9999.
- P2P GUI dùng DataStream: senderName, fileName, fileSize, dữ liệu; fallback bằng VIDEO_MESSAGE qua Server.
- Audio ghi/phát WAV 16 kHz, 16-bit mono. Video là gửi tệp, không có gọi video trực tiếp.
- users cũ có full_name, thiếu email/display_name/updated_at; setup cũ có DROP TABLE.
- Java 21 và Swing đã được dùng; các dependency/plugin JavaFX không có source sử dụng.

## 3. File mới

- src/main/java/client/ClientConnection.java
- src/main/java/client/LoginGUI.java
- src/main/java/client/RegisterGUI.java
- src/main/java/client/ChatTimeline.java
- src/main/java/model/AuthRequest.java
- src/main/java/model/AuthResult.java
- src/main/java/server/UserDAO.java
- src/main/java/server/AuthService.java
- src/main/java/util/ThemeManager.java
- MIGRATION_AUTH.sql
- UPGRADE_NOTES.md

## 4. File chỉnh sửa

- src/main/java/client/Client.java
- src/main/java/client/ClientGUI.java
- src/main/java/model/User.java
- src/main/java/model/MessageType.java
- src/main/java/server/Server.java
- src/main/java/util/AppConfig.java
- src/main/java/util/AudioUtil.java
- src/main/java/util/BroadcastHandler.java
- src/main/java/util/MulticastHandler.java
- app.properties
- pom.xml
- SETUP_DATABASE.sql
- CONFIGURATION.md

Message.java và ChatDatabase.java đã được đọc, giữ nguyên.

## 5. Chức năng triển khai

- Catppuccin Mocha bằng FlatLaf, font Segoe UI, focus/hover và dialog cùng Look & Feel.
- Login/Chat 1100×720, tối thiểu 900×600; Register là dialog riêng phù hợp biểu mẫu.
- Login Username/Email, Password, hiện/ẩn, Enter, Connecting/Online/Offline, Retry.
- Register có đủ 5 trường; validation Client/Server, lỗi ngay trên form và khóa gửi khi xử lý.
- Đăng ký thành công trở lại Login, điền username, không tự đăng nhập.
- UserDAO dùng PreparedStatement, kiểm tra trùng và bắt UNIQUE constraint khi cạnh tranh.
- Password plaintext đúng yêu cầu LAB, không hash. User/AuthResult không chứa password.
- Phiên gắn với ClientHandler.currentUser và onlineClients; không cần SessionManager riêng.
- Server chặn chat/danh bạ/xin IP trước xác thực; sender lấy từ phiên, không tin sender/userId của Client.
- Một tài khoản chỉ có một kết nối đăng nhập; logout/đóng socket thu hồi phiên.
- Một TCP socket, một cặp ObjectStream, một reader và writer có thứ tự mỗi kết nối.
- Timeout connect/handshake 5 giây; auth 12 giây; heartbeat 15 giây; read timeout Client 40 giây, Server 45 giây.
- Mất kết nối đóng Chat và tài nguyên UDP/P2P, về Login có Retry và yêu cầu đăng nhập lại.
- Sidebar tài khoản/kênh/nhóm/người dùng/đăng xuất; header loại kênh, số thành viên khi có.
- Bubble Swing bo góc, trái/phải, tên/thời gian; xuống dòng, cuộn theo khi ở cuối.
- Enter gửi, Shift+Enter xuống dòng; emoji, đính kèm, ghi âm và video có chức năng thực.
- TCP writer, gửi UDP, đọc/ghi video chạy ngoài EDT.
- Thông tin nhóm; lỗi offline, lỗi mở cổng, datagram quá lớn; tên tệp nhận có UUID và bỏ đường dẫn.
- Audio báo lỗi micro, không giả bản ghi thành công bằng sample tone khi thất bại.

## 6. Chức năng cũ giữ lại

TCP chung/riêng, Online, xin IP/port, tạo/nhận nhóm qua header, Multicast/Broadcast,
ghi/phát WAV, emoji, gửi video, file P2P và relay dự phòng, mở tệp/thư mục, Save As, progress.
Không chuyển UDP/P2P thành TCP. Các port giữ nguyên.
Entry point console chuyển tới Login để loại bỏ báo danh thủ công.
Client và Server cần cập nhật cùng nhau vì LOGIN xác thực mới khác báo danh cũ.

## 7. SQL migration

- Database mới: chạy SETUP_DATABASE.sql rồi đăng ký tài khoản giả từ Client.
- Database cũ: sao lưu, chạy toàn bộ MIGRATION_AUTH.sql trong MySQL Workbench/phpMyAdmin.
- Migration tạo users nếu thiếu; thêm email/display_name/updated_at, mở rộng id/password.
- Giữ full_name, tài khoản, password và messages; không DROP dữ liệu hay tạo tài khoản mặc định.
- Tài khoản cũ có email legacy-ID@lab1.invalid, display_name lấy full_name hoặc username.
- Password cũ dưới 8 ký tự vẫn giữ; giới hạn 8 ký tự áp dụng đăng ký mới.
- Script dùng DELIMITER/stored procedure; cần quyền ALTER và CREATE/DROP ROUTINE.
- Schema tự sửa khác bản gốc cần đối chiếu cột/index trước migration.
- Server không tự migration; lỗi MySQL/schema trả lỗi xác thực.
- Fallback file chỉ lưu lịch sử, không được dùng xác thực.

## 8. Dependency

Thêm com.formdev:flatlaf:3.5.4.
Giữ mysql-connector-j:8.3.0 và Java 21.
Bỏ JavaFX dependency/plugin không sử dụng, gồm client.Main không tồn tại.
Không thêm BCrypt, SHA hoặc thư viện mã hóa mật khẩu.

## 9. Chạy Server trên 172.20.10.4 — người dùng thực hiện

1. Chọn JDK 21 cho project trong IntelliJ.
2. Máy Server phải thực sự có IP LAN 172.20.10.4.
3. Khởi động MySQL và tạo/migration ltm_chat theo mục 7.
4. Sửa db.url/db.username/db.password trên Server cho đúng MySQL.
5. Giữ server.host=172.20.10.4, server.bind=0.0.0.0, server.port=8000.
6. Tự đồng bộ Maven dependencies và biên dịch bằng IDE.
7. Working directory là thư mục gốc; Run class server.Server.
8. Cho phép TCP 8000 trên Windows Firewall mạng Private.

## 10. Chạy Client trên hai máy Windows — người dùng thực hiện

1. Hai máy dùng JDK 21, cùng source mới; tự đồng bộ dependency và biên dịch.
2. Cùng LAN, truy cập được 172.20.10.4; Wi-Fi không cô lập các thiết bị.
3. Giữ app.properties trong Working directory, không nhập IP/tên hiển thị trong UI.
4. Run client.LoginGUI (client.ClientGUI/client.Client cũng mở Login).
5. Khi Online, đăng ký hai tài khoản giả khác nhau và đăng nhập mỗi máy một tài khoản.
6. Cho phép Java dùng UDP 8888, UDP 9999, TCP P2P dải 9000–9999 trên mạng Private.
7. Thử chat chung/riêng/nhóm, UDP, audio, video và file.
8. Nếu Offline, kiểm tra Server/IP/Firewall và Retry. Nếu P2P bị chặn, thử relay.

## 11. Checklist thủ công

- Server chưa chạy: Connecting → Offline; Login khóa; Retry khi Server bật.
- MySQL tắt hoặc schema chưa migration: lỗi rõ ràng, không đăng nhập giả.
- Register trống, username sai, email sai, password ngắn, xác nhận không khớp.
- Username/email trùng; hai máy đăng ký đồng thời cùng dữ liệu.
- Register thành công về Login, không tự login.
- Login username/email, sai tài khoản/mật khẩu, Enter, hiện/ẩn password.
- Hai kết nối cùng tài khoản: kết nối thứ hai bị từ chối.
- Client chưa login gửi chat/GET_IP/GET_USER_LIST bị chặn.
- Client giả sender/userId: danh tính thực lấy từ phiên.
- TCP chung/riêng; người nhận offline; nhóm text/audio/video và thông tin thành viên.
- UDP hai máy; media nhỏ và datagram quá lớn.
- Micro không có/rút micro; ghi/phát WAV; logout đang ghi âm.
- Video TCP/UDP; file bị xóa, lỗi đọc/ghi ổ đĩa.
- P2P trực tiếp; chặn P2P thử relay; file rỗng/lớn/hủy/đứt mạng giữa truyền.
- Mở tệp/thư mục và Save As; tệp đã bị xóa.
- Dừng Server/rút mạng; Login/Retry, đăng nhập lại.
- Logout/đóng cửa sổ khi đang chat/media; login/logout nhiều lần không tích lũy listener.
- Resize 900×600; tiếng Việt, emoji, text dài/không dấu cách, Shift+Enter.
- Đọc tin cũ không bị kéo xuống; đang ở cuối theo tin mới.
- Kiểm tra dữ liệu users plaintext đúng yêu cầu, không password trong User response/log.

## 12. Giới hạn chưa xác minh

- Chưa compile, tải dependency, chạy SQL, render UI hoặc kiểm tra mạng thực tế.
- Rà soát tĩnh chưa báo lỗi Java ở các file đã kiểm tra; DAO có cảnh báo SQL chưa nhận diện users vì IDE chưa gắn datasource.
- Chưa kiểm tra IP 172.20.10.4, Firewall, router, multicast interface hoặc MySQL thực tế.
- UDP/P2P độc lập chưa có xác thực mật mã; login TCP không chứng minh danh tính gói UDP/header P2P.
- Group vẫn do Client khai báo thành viên. Server lọc theo danh sách, chưa có ACL nhóm bền vững.
- Không thêm join theo mã mời: source cũ nhận nhóm từ header, không có cơ chế mã mời.
- Video là gửi tệp, không gọi trực tiếp. Không JWT/OAuth, khôi phục mật khẩu, tải lịch sử DB hoặc khôi phục phiên tự động.
- UDP giữ một serialized Message/datagram, tối đa 65.507 byte gồm overhead. Media lớn cần TCP/P2P.
- Video/relay đọc toàn bộ file vào bộ nhớ; luồng mới giới hạn 64 MB. P2P trực tiếp vẫn dùng buffer.
- P2P chọn cổng ngẫu nhiên như cũ; cổng bận báo lỗi và bên gửi có thể thử relay.
- Text/audio hiển thị cục bộ khi đưa vào hàng gửi, chưa có receipt của người nhận.
- Nhóm/lịch sử hiển thị nằm trong bộ nhớ phiên và xóa khi logout.
- Plaintext password và TCP không TLS chỉ dành LAB nội bộ/tài khoản giả theo yêu cầu.
