package com.pipai.starsector.lopc

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global

class LessOpColoniesModPlugin : BaseModPlugin() {
    override fun onEnabled(wasEnabledBefore: Boolean) {
        if (!wasEnabledBefore) {
            MarketManager().init()
        }
    }
}
