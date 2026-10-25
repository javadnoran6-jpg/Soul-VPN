# Aether App (اسکلت اولیه)

ساختار: MainActivity → AetherVpnService → HEV SOCKS5 tunnel → هسته Aether (127.0.0.1:1819)

## قبل از ساخت (دو فایل باینری لازم است)

1. هسته Aether برای اندروید (arm64):
   باینری را از Releases مخزن CluvexStudio/Aether بگیرید، با sha256 تطبیق بدهید،
   و با نام `libaether.so` بگذارید در:
   app/src/main/jniLibs/arm64-v8a/libaether.so

2. کتابخانه hev-socks5-tunnel:
   از مخزن heiher/hev-socks5-tunnel با NDK برای arm64-v8a بسازید و
   `libhev-socks5-tunnel.so` را کنار فایل بالا بگذارید.
   (کلاس hev.htproxy.TProxyService باید با بیلدی که می‌گیرید هم‌نام باشد؛ بررسی کنید.)

## ساخت
پوشه را در Android Studio باز کنید (Gradle wrapper را خودش می‌سازد) و Run بزنید.

## موارد باز (بررسی نشده)
- در پروژه AetherST ببینید هسته دقیقاً چطور روی اندروید اجرا و پیکربندی می‌شود.
- پشتیبانی UDP/DNS: اگر SOCKS5 هسته UDP را پشتیبانی نکند، DNS باید از مسیر TCP برود.
- مسیر ذخیره هویت هسته (WARP) باید پایدار باشد؛ اینجا filesDir است.
- لایسنس Aether، HEV (MIT) و در صورت استفاده از کد AetherST، ذکر منبع.
- SOCKS5 هسته احراز هویت ندارد؛ فقط روی 127.0.0.1 است و نباید جای دیگری باز شود.

## ساخت APK بدون کامپیوتر (GitHub Actions)
1. پروژه را در یک مخزن GitHub بگذارید و push کنید (خودکار ساخته می‌شود) یا Actions ← Build APK ← Run workflow.
2. workflow خودش آخرین Release هسته Aether را برای android arm64 پیدا و دانلود می‌کند، checksum را چک می‌کند و HEV را با NDK می‌سازد.
3. APK را از Artifacts دانلود و نصب کنید (امضای debug).
اگر مرحله Fetch خطا داد، لیست اسم فایل‌های Release در لاگ همان مرحله چاپ می‌شود.
مقدارهای protocol به‌جز masque حدسی‌اند؛ `--help` هسته را ببینید و `protocolValue` را اصلاح کنید.
