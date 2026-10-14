# Reglas de ProGuard para Mixi
# Por ahora la app no usa minificación, pero el archivo debe existir.

# yt-dlp corre en Python embebido: no tocar nada de estas clases
-keep class com.yausername.** { *; }
-keep class com.yausername.youtubedl_android.** { *; }
