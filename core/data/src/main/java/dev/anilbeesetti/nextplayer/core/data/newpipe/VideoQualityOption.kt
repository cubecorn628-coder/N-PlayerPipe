package dev.anilbeesetti.nextplayer.core.data.newpipe

data class VideoQualityOption(
    val format: String, // misal: "MPEG-4", "WebM"
    val resolution: String, // misal: "1080p", "720p"
    val videoUrl: String,
    val audioUrl: String?, // Null jika video sudah berisikan audio (Muxed)
    val size: Long = -1L
)
