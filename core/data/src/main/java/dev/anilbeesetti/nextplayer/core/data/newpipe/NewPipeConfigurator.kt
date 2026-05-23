package dev.anilbeesetti.nextplayer.core.data.newpipe

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

object NewPipeConfigurator {

    /**
     * Inisialisasi NewPipe dengan lokalisasi sesuai perangkat.
     * Secara bawaan Age Restriction tidak diberlakukan oleh extractor ini,
     * tetapi kita menetapkan fungsi inisiasi agar sesuai dengan bahasa/lokasi perangkat Android.
     */
    fun init(downloader: Downloader) {
        val defaultLocale = Locale.getDefault()
        val language = defaultLocale.language
        val country = defaultLocale.country

        // Atur lokalisasi secara dinamis menyesuaikan perangkat.
        // Konfigurasi parameter region / localization
        val localization = Localization(language, country)
        val contentCountry = org.schabi.newpipe.extractor.localization.ContentCountry(country)
        
        NewPipe.init(
            downloader, 
            localization,
            contentCountry
        )
    }
}
