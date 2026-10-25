package hev.htproxy

/**
 * اتصال JNI به hev-socks5-tunnel.
 * نام پکیج و کلاس باید دقیقاً همین باشد تا با libhev-socks5-tunnel.so بخورد.
 * این کتابخانه را خودتان با NDK بسازید (README را ببینید).
 */
object TProxyService {
    init { System.loadLibrary("hev-socks5-tunnel") }

    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int)
    @JvmStatic external fun TProxyStopService()
}
