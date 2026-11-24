# R8 rules for the release build. Most libraries (Compose, Koin, Ktor, OkHttp, kotlinx
# serialization, Firebase, WorkManager, Glance) ship their own consumer rules.

-keepattributes *Annotation*
-keep class zhb.company.smart_home.data.remote.dto.** { *; }
-keep class zhb.company.smart_home.domain.model.** { *; }

# HiveMQ MQTT client (rules from its README): Netty and JCTools look up fields and classes by name.
-keepclassmembernames class io.netty.** { *; }
-keepclassmembers class org.jctools.** { *; }

# Optional Netty dependencies the client only uses when they are on the classpath: native epoll,
# WebSocket and proxy transports (the app uses plain TCP/TLS), the OpenSSL native library (TLS
# goes through the platform's SSLEngine), Jetty ALPN/NPN and log4j logging.
-dontwarn io.netty.channel.epoll.**
-dontwarn io.netty.handler.codec.http.**
-dontwarn io.netty.handler.proxy.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn org.eclipse.jetty.npn.**
