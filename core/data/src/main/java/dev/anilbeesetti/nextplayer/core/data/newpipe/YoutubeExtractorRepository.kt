package dev.anilbeesetti.nextplayer.core.data.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.Locale

class YoutubeExtractorRepository {

    /**
     * Mengekstrak StreamInfo dari url youtube.
     * Mengelompokkan resolusi video yang ada langsung dari stream list, bukan hardcoded.
     */
    suspend fun extractVideo(url: String): List<VideoQualityOption> = withContext(Dispatchers.IO) {
        val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url)

        val videoStreams = streamInfo.videoStreams
        val videoOnlyStreams = streamInfo.videoOnlyStreams
        val audioStreams = streamInfo.audioStreams

        if (videoStreams.isEmpty() && videoOnlyStreams.isEmpty()) {
            return@withContext emptyList()
        }

        // Cari audio dengan localization / best bitrate
        val bestAudioStream = findBestAudioStream(audioStreams)

        val result = mutableListOf<VideoQualityOption>()

        // 1. Ekstraksi Muxed Streams (Audio + Video)
        videoStreams.forEach { stream ->
            result.add(
                VideoQualityOption(
                    format = stream.format?.name ?: "Unknown",
                    resolution = stream.resolution ?: "Unknown",
                    videoUrl = stream.url ?: "",
                    audioUrl = null,                   // tidak butuh karena stream berjenis Muxed
                    size = -1L
                )
            )
        }

        // 2. Ekstraksi Video-Only Streams (DASH/HLS) dipasangkan dengan Audio Terpilih
        videoOnlyStreams.forEach { stream ->
            result.add(
                VideoQualityOption(
                    format = stream.format?.name ?: "Unknown",
                    resolution = stream.resolution ?: "Unknown",
                    videoUrl = stream.url ?: "",
                    audioUrl = bestAudioStream?.url,
                    size = -1L
                )
            )
        }

        return@withContext result.sortedByDescending { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
    }

    /**
     * Logika untuk mencari audio dengan Dubbing (Language) sesuai perangkat,
     * lalu fallback ke audio asli jika dubbing tidak ditemukan, dan cari bitrate tertinggi.
     */
    private fun findBestAudioStream(audioStreams: List<AudioStream>): AudioStream? {
        if (audioStreams.isEmpty()) return null

        // Ambil bahasa sistem
        val deviceLanguage = Locale.getDefault().language

        // Cari track dubbing yang locale bahasanya sama
        val localizedStreams = audioStreams.filter { 
            it.audioLocale?.language == deviceLanguage 
        }

        // Jika dub tidak tersedia, fallback ke default list stream
        val fallbackStreams = localizedStreams.ifEmpty { audioStreams }

        // Muxing ke auto track audio dengan bitrate tertinggi
        return fallbackStreams.maxByOrNull { it.averageBitrate } ?: fallbackStreams.firstOrNull()
    }
}
