# Nhật ký thay đổi

## 1.3.0

### Mới
- **Cập nhật ngay trong ứng dụng**: *Cài đặt* ở thẻ *Giới thiệu* tải, kiểm tra (SHA-256, chữ ký) và cài phiên bản mới
  – không cần trình duyệt. *Tự động cài đặt bản cập nhật* tự làm việc này; từ Android 12 không hỏi lại sau lần đầu
- **Văn bản riêng cho tạm dừng và radio** (thẻ *Hiển thị* → *Văn bản cho*); ô trống dùng văn bản khi phát
- **Ủng hộ ứng dụng**: một liên kết PayPal nhỏ, tùy ý ở thẻ *Giới thiệu*

### Cải thiện
- **Kiểm tra cập nhật** vẫn hoạt động khi hết giới hạn yêu cầu của GitHub (VPN, địa chỉ dùng chung)
- **Ảnh bìa Deezer**: tìm tự do theo nghệ sĩ + tên bài khi tìm chính xác không thấy gì
- **Không còn "Tạm dừng" bị kẹt**: Discord phải xác nhận đã xóa trạng thái, nếu không sẽ xóa lại; dịch vụ chỉ dừng sau đó

## 1.2.1

### Cải thiện
- **Dịch vụ nền**: chỉ khởi động khi thực sự có bài đang phát, không phải ngay khi mở Poweramp, và tự dừng khi không
  còn gì phát (sau thời hạn tạm dừng)
- **Tải ảnh bìa lên**: tên tệp ngẫu nhiên thay cho "cover.jpg" – x0.at đã bắt đầu từ chối tải lên

## 1.2.0

### Mới
- **Liên kết tài khoản Discord** (thẻ *Trạng thái*): tên thật và ảnh hồ sơ của bạn trong bản xem trước trực tiếp và ở
  thẻ *Trạng thái* – chỉ lưu hồ sơ công khai
- **Thêm 26 ngôn ngữ**: tiếng Pháp, Tây Ban Nha, Bồ Đào Nha (Brazil), Ý, Hà Lan, Đan Mạch, Thụy Điển, Na Uy, Phần Lan,
  Ba Lan, Séc, Hungary, Romania, Hy Lạp, Thổ Nhĩ Kỳ, Serbia, Ả Rập, Hindi, Indonesia, Việt, Thái, Trung,
  Nhật và Hàn (bản dịch AI – rất hoan nghênh góp ý sửa lỗi)
- **Chọn ngôn ngữ** trong *Cài đặt ứng dụng* (trên Android 13+ cũng có trong cài đặt ứng dụng của Android)

### Cải thiện
- Tiết kiệm pin hơn: “Đã tạm dừng” biến mất đúng giờ khi điện thoại đang ngủ; không đánh thức máy khi trạng thái đã tắt
  hoặc chưa có Discord; kết nối lại giãn dần thời gian chờ
- Ít dữ liệu mạng và bộ nhớ hơn: ảnh bìa được kiểm tra lại ít thường xuyên hơn, album không tìm thấy sẽ không được tìm lại
  trong 12 giờ, ảnh được giải phóng khi ứng dụng chạy nền
- Sửa lỗi: cài đặt được nhập sẽ được hiệu chỉnh, nhấn phát sẽ đưa trạng thái trở lại sau khi bị dừng từ trình quản lý tác vụ

## 1.1.0

### Mới
- **Cài đặt ứng dụng** trong menu ba chấm: giao diện *Theo hệ thống* / *Sáng* / *Tối*, **Đen AMOLED** cho chế độ tối và
  màu **Material You** (Android 12+)
- **Giới thiệu**: ảnh GitHub của tác giả và các liên kết được căn giữa
- **Văn bản luân phiên**: `%artist% || %title%` hiển thị luân phiên nghệ sĩ và tên bài (*Khoảng thời gian luân phiên* ở
  thẻ *Hiển thị*, 5–60 giây)
- **Biểu tượng ứng dụng mới** theo phong cách của Poweramp: bong bóng thoại với các cột equalizer (cũng có dạng biểu tượng
  theo chủ đề trên Android 13+); logo trong ứng dụng, màn hình khởi động và ảnh trên Discord cũng đồng bộ

### Mượt mà hơn
- Bản dựng được tối ưu (R8 + baseline profile): màn hình và việc chuyển thẻ không còn giật
- Cài đặt, trạng thái của Poweramp và hình ảnh được tải ở chế độ nền
- Bản xem trước trực tiếp chỉ cập nhật vị trí phát mỗi giây; các thẻ ở xa mở ngay lập tức; nhật ký cuộn mượt mà

## 1.0.0

### Mới
- Bản phát hành đầu tiên: **Discord Rich Presence cho Poweramp** – hoạt động với cả bản ổn định và bản beta của Poweramp
- **“Đang nghe Poweramp”** với thanh tiến trình (hoặc “Đang chơi” với thời gian đã trôi qua)
- **Ảnh bìa không cần dịch vụ phát trực tuyến**: ảnh bìa mà Poweramp hiển thị được tải lên x0.at, catbox.moe hoặc Imgur,
  kèm tùy chọn tìm kiếm trực tuyến (Spotify, Deezer, Apple Music / iTunes, Bandcamp, Discogs,
  MusicBrainz + Cover Art Archive)
- Mẫu văn bản với biến (`%title%`, `%artist%`, `%album%`, `%bar%` …) và bản xem trước trực tiếp những gì
  Discord hiển thị
- Hành vi khi tạm dừng (hiện “Đã tạm dừng” hoặc ẩn, gỡ sau một khoảng tạm dừng) và bộ lọc cho luồng phát và thư mục
- Kiểm tra cập nhật (bản phát hành trên GitHub)
- Tiếng Anh, tiếng Đức, tiếng Nga và tiếng Ukraina
