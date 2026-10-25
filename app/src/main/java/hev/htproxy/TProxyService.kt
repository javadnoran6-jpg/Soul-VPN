package hev.htproxy

/**
 * اتصال JNI به hev-socks5-tunnel.
 * نام پکیج و کلاس باید دقیقاً همین باشد تا با libhev-socks5-tunnel.so بخورد.
 * این کتابخانه را خودتان با NDK بسازید (README را ببینید).
 *
 * مهم: کتابخانه موقع System.loadLibrary هر ۴ تابع زیر را (با همین نوع خروجی) به این کلاس
 * وصل می‌کند. اگر یکی کم باشد یا نوع خروجی‌اش فرق کند، NoSuchMethodError می‌آید
 * و اتصال بلافاصله قطع می‌شود. قرارداد فعلی طبق README مخزن hev:
 * StartService / StopService / IsRunning → Boolean و GetStats → LongArray.
 */
object TProxyService {
    init { System.loadLibrary("hev-socks5-tunnel") }

    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int): Boolean
    @JvmStatic external fun TProxyStopService(): Boolean
    @JvmStatic external fun TProxyIsRunning(): Boolean
    @JvmStatic external fun TProxyGetStats(): LongArray
}
