# Cấu hình kết nối

Sửa file `app.properties` ở thư mục gốc dự án:

| Thuộc tính | Ý nghĩa |
| --- | --- |
| `server.host` | IP hoặc hostname của máy chạy chat server; dùng IP LAN nếu client ở máy khác. |
| `server.port` | Cổng TCP của chat server, phải giống nhau trên server và các client. |
| `db.url` | Địa chỉ MySQL, cổng, tên database và các tùy chọn JDBC. |
| `db.username` | Tài khoản MySQL. |
| `db.password` | Mật khẩu MySQL; có thể để trống sau dấu `=`. |

Khởi động lại chương trình sau khi sửa; không cần biên dịch lại. Client GUI lấy IP mặc định từ file và vẫn cho phép nhập IP khác trên giao diện. Client dòng lệnh cũng sử dụng file này. Chat server lắng nghe trên mọi địa chỉ mạng ở cổng đã cấu hình; `server.host` là địa chỉ để client kết nối.

Chạy từ thư mục gốc dự án bằng các file `.bat` hiện có hoặc đặt Working directory của IntelliJ thành thư mục gốc dự án. Nếu chạy từ thư mục khác, thêm VM option để chỉ rõ file cấu hình:

```text
-Dchat.config=C:/Users/qank0/IdeaProjects/LAB1_Chat/app.properties
```

File sử dụng UTF-8. Với mật khẩu chứa dấu gạch chéo ngược, viết `\\` để biểu diễn một ký tự `\` theo cú pháp Java properties. Thông tin database chỉ được server sử dụng; client không gửi tài khoản hay mật khẩu database qua mạng.
