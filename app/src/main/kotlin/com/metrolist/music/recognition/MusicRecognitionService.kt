/**
 * Music Recognition Feature
 * 
 * This feature is based on the original MusicRecognizer project by Aleksey Saenko.
 * Original project: https://github.com/aleksey-saenko/MusicRecognizer
 * 
 * Special thanks to Aleksey Saenko for the music recognition implementation.
 */

package com.metrolist.music.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.metrolist.shazamkit.Shazam
import com.metrolist.shazamkit.models.RecognitionResult
import com.metrolist.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

/**
 * Service for recognizing music using audio fingerprinting.
 * Records audio from the microphone, generates a Shazam-compatible fingerprint,
 * and sends it to the Shazam API for recognition.
 */
object MusicRecognitionService {
    
    // Recording parameters
    private const val RECORDING_SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    // Max hands-off capture: if the user never taps "finish", auto-stop here (12s gives Shazam a
    // generous window for a clean match).
    private const val RECORDING_DURATION_MS = 12000L
    // Min capture before an early "finish" tap is honoured — below this Shazam rarely matches, so a
    // too-early tap still records up to this floor before it stops.
    private const val MIN_RECORDING_DURATION_MS = 4000L
    private const val TAG = "MusicRecognitionService"

    private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
    val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()

    // Set by [finishListening] so the user can stop capture early and jump straight to matching.
    // @Volatile: written from the UI thread, read by the recording loop on Dispatchers.IO.
    @Volatile
    private var stopRequested = false

    /**
     * Set to true by the widget service after it has already persisted the result to the
     * database, so that [RecognitionScreen] skips the duplicate insert.
     * Reset to false by [reset].
     */
    var resultSavedExternally: Boolean = false
    
    fun hasRecordPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Start the music recognition process.
     * Records audio, generates fingerprint, and queries Shazam API.
     */
    @SuppressLint("MissingPermission")
    suspend fun recognize(context: Context): RecognitionStatus = withContext(Dispatchers.IO) {
        if (!hasRecordPermission(context)) {
            Timber.tag(TAG).w("Microphone permission not granted, aborting recognition")
            return@withContext RecognitionStatus.Error("Microphone permission not granted")
        }

        // Clear any stale finish request from a previous (cancelled) run before we start listening.
        stopRequested = false
        _recognitionStatus.value = RecognitionStatus.Listening
        Timber.tag(TAG).d("Starting music recognition")
        
        try {
            // Step 1: Record audio
            val audioData = recordAudio()
            Timber.tag(TAG).d("Audio recorded: %d bytes", audioData.size)

            _recognitionStatus.value = RecognitionStatus.Processing
            
            // Step 2: Convert to mono if needed and resample to 16kHz
            Timber.tag(TAG).d("Resampling audio from %dHz to %dHz", RECORDING_SAMPLE_RATE, VibraSignature.REQUIRED_SAMPLE_RATE)
            val decodedAudio = DecodedAudio(
                data = audioData,
                channelCount = 1,
                sampleRate = RECORDING_SAMPLE_RATE,
                pcmEncoding = AUDIO_FORMAT
            )
            
            val resampledAudio = AudioResampler.resample(
                decodedAudio,
                VibraSignature.REQUIRED_SAMPLE_RATE
            ).getOrElse { error ->
                Timber.tag(TAG).e(error, "Audio resampling failed")
                _recognitionStatus.value = RecognitionStatus.Error("Failed to resample audio: ${error.message}")
                return@withContext _recognitionStatus.value
            }
            Timber.tag(TAG).d("Resampled audio: %d bytes, %d channels, %dHz", resampledAudio.data.size, resampledAudio.channelCount, resampledAudio.sampleRate)
            
            // Verify format
            require(
                resampledAudio.channelCount == 1 &&
                resampledAudio.sampleRate == VibraSignature.REQUIRED_SAMPLE_RATE &&
                resampledAudio.pcmEncoding == AudioFormat.ENCODING_PCM_16BIT &&
                ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN &&
                resampledAudio.data.isNotEmpty() && 
                resampledAudio.data.size % 2 == 0
            ) { "Invalid audio format for fingerprint generation" }
            
            // Step 3: Generate fingerprint using native library
            val signature = try {
                VibraSignature.fromI16(resampledAudio.data)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Fingerprint generation failed")
                _recognitionStatus.value = RecognitionStatus.Error("Failed to generate fingerprint: ${e.message}")
                return@withContext _recognitionStatus.value
            }
            
            // Step 4: Send to Shazam API
            val sampleDurationMs = (resampledAudio.data.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE
            Timber.tag(TAG).d("Fingerprint generated, sampleDurationMs=%d", sampleDurationMs)
            
            val result = Shazam.recognize(signature, sampleDurationMs)
            
            result.fold(
                onSuccess = { recognitionResult ->
                    Timber.tag(TAG).i("Recognition successful: '%s' by %s", recognitionResult.title, recognitionResult.artist)
                    _recognitionStatus.value = RecognitionStatus.Success(recognitionResult)
                },
                onFailure = { error ->
                    val message = error.message ?: "Unknown error"
                    Timber.tag(TAG).w(error, "Recognition API returned failure: %s", message)
                    _recognitionStatus.value = if (message.contains("No match", ignoreCase = true)) {
                        RecognitionStatus.NoMatch("No matches found. Try again with clearer audio.")
                    } else {
                        RecognitionStatus.Error(message)
                    }
                }
            )
            
            _recognitionStatus.value
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Recognition failed with exception")
            _recognitionStatus.value = RecognitionStatus.Error(e.message ?: "Recognition failed")
            _recognitionStatus.value
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun recordAudio(): ByteArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        Timber.tag(TAG).d("Recording audio: sampleRate=%d, bufferSize=%d, durationMs=%d", RECORDING_SAMPLE_RATE, bufferSize, RECORDING_DURATION_MS)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        val startTime = System.currentTimeMillis()
        
        try {
            audioRecord.startRecording()
            Timber.tag(TAG).d("AudioRecord started, recording up to %dms (min %dms)", RECORDING_DURATION_MS, MIN_RECORDING_DURATION_MS)

            // Stop when: the coroutine is cancelled (screen left), the hands-off cap is hit, or the
            // user tapped "finish" after at least the minimum useful sample. The buffer read returns
            // in a fraction of a second, so a finish tap is honoured near-instantly.
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= RECORDING_DURATION_MS) break
                if (stopRequested && elapsed >= MIN_RECORDING_DURATION_MS) {
                    Timber.tag(TAG).d("Finish requested at %dms — stopping capture early", elapsed)
                    break
                }
                val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        val totalBytes = outputStream.size()
        Timber.tag(TAG).d("Audio recording complete: %d bytes collected", totalBytes)
        outputStream.toByteArray()
    }
    
    /**
     * Stop capture early and go straight to matching. No-op unless we're currently listening (so a
     * stray tap during processing/result can't arm a stale flag). The recording loop honours this
     * once at least [MIN_RECORDING_DURATION_MS] has been captured.
     */
    fun finishListening() {
        if (_recognitionStatus.value is RecognitionStatus.Listening) {
            Timber.tag(TAG).d("finishListening: early stop requested")
            stopRequested = true
        }
    }

    fun reset() {
        Timber.tag(TAG).d("Recognition state reset")
        stopRequested = false
        _recognitionStatus.value = RecognitionStatus.Ready
        resultSavedExternally = false
    }
}
