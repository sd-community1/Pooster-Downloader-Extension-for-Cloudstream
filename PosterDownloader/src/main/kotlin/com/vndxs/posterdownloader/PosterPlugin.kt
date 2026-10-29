package com.vndxs.posterdownloader

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PosterPlugin : Plugin() {
    override fun load(context: Context) {
        registerVideoClickAction(DownloadPosterAction())
    }
}
