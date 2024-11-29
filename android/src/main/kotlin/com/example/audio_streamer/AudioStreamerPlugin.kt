package com.example.audio_streamer

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import androidx.annotation.NonNull
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import java.util.concurrent.Executors
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType

class AudioStreamerPlugin : FlutterPlugin, EventChannel.StreamHandler, MethodCallHandler {

  private var methodChannel: MethodChannel? = null
  private val executor = Executors.newSingleThreadExecutor()
  private var eventSink: EventChannel.EventSink? = null
  private val mainHandler = Handler(Looper.getMainLooper())

  override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    val messenger = binding.binaryMessenger
    val channel = EventChannel(messenger, "com.example.audio_streamer/events")
    channel.setStreamHandler(this)

    methodChannel = MethodChannel(messenger, "com.example.audio_streamer/methods")
    methodChannel?.setMethodCallHandler(this)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    executor.shutdownNow()
  }

  override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink) {
    this.eventSink = eventSink
  }

  override fun onCancel(arguments: Any?) {
    eventSink = null
  }

    private fun transformBuffer(sampleRate: Int, amplificationFactor: Double, buffer: ByteArray): ByteArray {
        val lowFreq = 300
        val highFreq = 3400

        // Convert byte buffer to ShortArray
        val shortBuffer = ShortArray(buffer.size / 2) { i ->
            ((buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)).toShort()
        }

        // Convert ShortArray to DoubleArray for FFT processing
        val doubleBuffer = shortBuffer.map { it.toDouble() }.toDoubleArray()

        // Perform FFT
        val fft = FastFourierTransformer(DftNormalization.STANDARD)
        val spectrum = fft.transform(doubleBuffer, TransformType.FORWARD)

        // Amplify frequencies within the human voice range
        for (i in 0 until spectrum.size / 2) {
            val freq = i * sampleRate / spectrum.size
            if (freq in lowFreq..highFreq) {
                spectrum[i] = spectrum[i].multiply(amplificationFactor)
                spectrum[spectrum.size - i - 1] = spectrum[spectrum.size - i - 1].multiply(amplificationFactor) // Mirror frequency
            }
        }

        // Perform inverse FFT
        val amplifiedBuffer = fft.transform(spectrum, TransformType.INVERSE)

        // Convert back to ShortArray
        val amplifiedShortBuffer = amplifiedBuffer.map { it.real.coerceIn(-32768.0, 32767.0).toInt().toShort() }.toShortArray()

        // Convert ShortArray back to ByteArray
        val amplifiedByteArray = ByteArray(amplifiedShortBuffer.size * 2)
        for (i in amplifiedShortBuffer.indices) {
            amplifiedByteArray[i * 2] = (amplifiedShortBuffer[i].toInt() and 0xFF).toByte()
            amplifiedByteArray[i * 2 + 1] = (amplifiedShortBuffer[i].toInt() shr 8).toByte()
        }

        return amplifiedByteArray
    }

  private fun startRecording(recordingMode: Int, sampleRate: Int, amplificationFactor: Double) {
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    val audioRecord = AudioRecord(recordingMode, sampleRate, channelConfig, audioFormat, minBufSize)
    var acousticEchoCanceler: AcousticEchoCanceler? = null
    // Initialize AcousticEchoCanceler for the AudioRecord instance
    if (AcousticEchoCanceler.isAvailable()) {
      acousticEchoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
      acousticEchoCanceler.enabled = true
    }

    var audioData = ByteArray(minBufSize)
    audioRecord.startRecording()

    while (eventSink != null) {
      val readSize = audioRecord.read(audioData, 0, audioData.size, AudioRecord.READ_BLOCKING)
      if (readSize > 0) {
        if (amplificationFactor > 1.0) {
          audioData = transformBuffer(sampleRate, amplificationFactor, audioData)
        }
        audioData = transformBuffer(sampleRate, 2.0, audioData);
        mainHandler.post {
          eventSink?.success(audioData)
        }
      }
    }
    // Cleanup
    acousticEchoCanceler?.release()
    acousticEchoCanceler = null
    audioRecord.stop()
    audioRecord.release()
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "startRecording" -> {
                executor.execute {
                    startRecording(
                      // Recording mode.
                      // If you want to echo cancellation, you should use VOICE_COMMUNICATION.
                      // see https://developer.android.com/reference/android/media/MediaRecorder.AudioSource
                      call.argument<Int>("recordingMode") ?: MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                       call.argument<Int>("sampleRate") ?: 16000, call.argument<Double>("amplificationFactor") ?: 1.0
                    )
                }
                result.success(null)
            }
            "stopRecording" -> {
                eventSink = null
                result.success(null)
            }
            else -> {
                result.notImplemented()
            }
        }
    }
}
