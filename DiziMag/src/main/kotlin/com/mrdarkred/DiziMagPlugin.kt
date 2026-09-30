@file:Suppress("DEPRECATION_ERROR", "DEPRECATION")
package com.mrdarkred

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DiziMagPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziMagProvider())
    }
}
