# ─────────────────────────────────────────────────────────────────────
# ProGuard / R8 rules for Nexo (com.jruiz.nexo)
# ─────────────────────────────────────────────────────────────────────
# Estas reglas preservan las clases y métodos que R8 no puede analizar
# estáticamente: bridge JS↔Java, reflexión de sherpa-onnx y servicios
# Android registrados en el manifiesto.
# ─────────────────────────────────────────────────────────────────────

# ── 1. JavascriptInterface bridge ─────────────────────────────────
# Cualquier método anotado con @JavascriptInterface debe conservarse
# íntegro (nombre, firma) para que el WebView pueda invocarlo.
-keepclassmembers class com.jruiz.nexo.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}

# ── 2. sherpa-onnx (JNI / ONNX Runtime) ──────────────────────────
# El AAR de sherpa-onnx usa JNI internamente. Preservamos todas sus
# clases públicas para evitar que R8 elimine o renombre bindings.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# ── 3. Google Play Services Auth ──────────────────────────────────
# play-services-auth incluye sus propias reglas, pero reforzamos
# por seguridad ante posibles variantes de proguard consumer.
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ── 4. Kotlin stdlib (incluido por sherpa-onnx AAR) ──────────────
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ── 5. Servicios, receivers y providers del manifiesto ────────────
# Android los instancia por reflexión; R8 los eliminaría sin esto.
-keep public class com.jruiz.nexo.recordings.RecordingService
-keep public class com.jruiz.nexo.transcription.TranscriptionService
-keep public class com.jruiz.nexo.communications.NexoNotificationListener
-keep public class com.jruiz.nexo.calendar.CalendarWatchJob
-keep public class com.jruiz.nexo.input.SmsWatchJob
-keep public class com.jruiz.nexo.input.GmailWatchJob
-keep public class com.jruiz.nexo.documents.DocumentProvider
-keep public class com.jruiz.nexo.recordings.RecordingProvider
-keep public class com.jruiz.nexo.reminders.AlarmReceiver
-keep public class com.jruiz.nexo.reminders.BootReceiver
-keep public class com.jruiz.nexo.medications.MedicationAlarmReceiver
-keep public class com.jruiz.nexo.medications.MedicationActionReceiver
-keep public class com.jruiz.nexo.communications.InboxNotificationReceiver

# ── 6. Logging en release ─────────────────────────────────────────
# Elimina llamadas a Log.d y Log.v en release (opcional pero reduce
# información expuesta en logcat).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# ── 7. Ajustes generales ─────────────────────────────────────────
# Mantener atributos de excepciones y genéricos para depuración.
-keepattributes Exceptions,InnerClasses,Signature,SourceFile,LineNumberTable
-keepattributes *Annotation*

# No advertir sobre dependencias opcionales que no están en el classpath.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
