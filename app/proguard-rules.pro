# Keep listener service - dipanggil oleh Android system
-keep class com.temanqris.listener.PaymentNotificationListener { *; }
-keep class com.temanqris.listener.ListenerKeepAliveService { *; }
-keep class com.temanqris.listener.BootReceiver { *; }

# Keep parser classes karena dipakai dengan reflection
-keep class com.temanqris.listener.parser.** { *; }
