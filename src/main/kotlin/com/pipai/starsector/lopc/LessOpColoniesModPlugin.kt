package com.pipai.starsector.lopc

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global

class LessOpColoniesModPlugin : BaseModPlugin() {
    override fun onEnabled(wasEnabledBefore: Boolean) {
        if (!wasEnabledBefore) {
            Global.getLogger(this.javaClass).info("Loading LOPC...")
            MarketManager().init()
        }
    }

    override fun onGameLoad(newGame: Boolean) {
        Global.getSector().listenerManager.getListeners(MarketManager::class.java).first().signalUpdate()
    }
}
