package com.pipai.starsector.lopc

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCampaignEventListener
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition
import data.scripts.util.MagicSettings

class MarketManager : BaseCampaignEventListener(true), EconomyTickListener, MarketImmigrationModifier {

    private val modId = "less-op-colonies"
    private val immigrationMalus: Float = MagicSettings.getFloat(modId, "immigrationMalus")
    private val incomeScaling: Float = MagicSettings.getFloat(modId, "incomeScaling")
    private val incomeBreakpoints: List<Float> = MagicSettings.getList(modId, "incomeBreakpoints").map { it.toFloat() }
    private val incomeFunctions: List<(Float) -> Float>

    init {
        val funcs: MutableList<(Float) -> Float> = mutableListOf()

        (0..incomeBreakpoints.size).forEach { i ->
            val func: (Float) -> Float = when (i) {
                0 -> { x -> x }
                else -> {
                    val bp = incomeBreakpoints[i - 1]
                    { x -> (1f - incomeScaling) * (x - bp) + funcs.last()(bp) }
                }
            }
            funcs.add(func)
        }

        incomeFunctions = funcs
    }

    private fun actualIncome(x: Float): Float {
        val bpAbove = incomeBreakpoints.find { it > x }
        return if (bpAbove == null) {
            incomeFunctions.last()(x)
        } else {
            val i = incomeBreakpoints.indexOf(bpAbove)
            incomeFunctions[i](x)
        }
    }

    fun init() {
        val sector = Global.getSector()
        sector.listenerManager.addListener(this)
    }

    override fun modifyIncoming(market: MarketAPI, incoming: PopulationComposition) {
        if (market.faction.isPlayerFaction || market.isPlayerOwned) {
            incoming.weight.modifyMult("lessOpColonies", immigrationMalus, "LOPC Immigration Malus")
        }
    }

    override fun reportEconomyTick(iterIndex: Int) {
        val playerMarkets =
            Global.getSector().economy.marketsCopy.filter { it.faction.isPlayerFaction || it.isPlayerOwned }
        playerMarkets.forEach { market ->
            val target = actualIncome(market.netIncome)
            val malus = target / market.netIncome
            market.incomeMult.modifyMult("lessOpColonies", malus, "LOPC Administration Malus")
        }
    }

}
