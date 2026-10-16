# Quy trình Git: không push thẳng lên main

Repo Private trên gói GitHub Free **không** bật được bảo vệ nhánh phía server, nên nhóm chặn bằng hook trên máy của từng người. Hook chỉ có tác dụng trên máy đã cài, vì vậy **mỗi người phải cài 1 lần**.

## Cài 1 lần sau khi clone

Cách 1: bấm đúp file `setup-git.bat` ở thư mục gốc repo.

Cách 2: chạy trong thư mục repo:

```
git config core.hooksPath .githooks
```

Kiểm tra đã cài: `git config core.hooksPath` phải in ra `.githooks`.

Từ lúc này, `git push origin main` sẽ bị chặn và hiện hướng dẫn.

## Quy trình mỗi việc

```
git checkout main
git pull origin main
git checkout -b feature/ten-viec            # ví dụ feature/an-driver-offers
... làm việc, rồi:
git add .
git commit -m "feat: mô tả ngắn"
git push -u origin feature/ten-viec
```

Sau đó:
1. Vào GitHub, bấm **Compare & pull request**, điền theo mẫu.
2. Nhờ 1 người trong nhóm xem qua và bấm Approve (hoặc nhắn trong nhóm).
3. Bấm **Squash and merge**, rồi **Delete branch**.
4. Về máy: `git checkout main` rồi `git pull origin main`.

## Lỡ commit lên main trên máy (chưa push)

```
git branch feature/ten-viec        # giữ commit ở nhánh mới
git reset --hard origin/main       # đưa main về giống GitHub
git checkout feature/ten-viec
```

Chỉ chạy `reset --hard` khi mọi thay đổi đã được commit, vì lệnh này xóa thay đổi chưa commit.

## Trường hợp khẩn cấp

Chủ repo có thể bỏ qua hook bằng `git push --no-verify`. Chỉ dùng khi thật cần và báo nhóm.

## Muốn GitHub chặn thật ở phía server

Đăng ký GitHub Student Developer Pack (education.github.com/pack) để có gói Pro, sau đó vào Settings → Rules → Rulesets và kiểm tra tính năng có được bật cho repo Private chưa. Khi bật được, ruleset chặn mọi người kể cả người quên cài hook.
