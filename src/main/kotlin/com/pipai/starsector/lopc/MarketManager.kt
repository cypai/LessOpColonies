package com.pipai.starsector.lopc

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCampaignEventListener
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition
import data.scripts.util.MagicSettings

class MarketManager : BaseCampaignEventListener(true), EveryFrameScript, EconomyTickListener, MarketImmigrationModifier {

    private val modId = "less-op-colonies"
    private val immigrationMalus: Float = MagicSettings.getFloat(modId, "immigrationMalus")
    private val incomeScaling: Float = MagicSettings.getFloat(modId, "incomeScaling")
    private val incomeBreakpoints: List<Float> = MagicSettings.getList(modId, "incomeBreakpoints").map { it.toFloat() }
    private val incomeFunctions: List<(Float) -> Float>
    private var updateFlag = false

    init {
        val funcs: MutableList<(Float) -> Float> = mutableListOf()

        (0..incomeBreakpoints.size).forEach { i ->
            val func: (Float) -> Float = when (i) {
                0 -> { x -> x }
                else -> {
                    val bp = incomeBreakpoints[i - 1]
                    { x -> (1f - i * incomeScaling) * (x - bp) + funcs[i - 1](bp) }
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
        sector.addScript(this)
        sector.listenerManager.addListener(this)
//        listOf(10000f, 20000f, 50000f, 100000f, 200000f, 300000f).forEach { x ->
//            Global.getLogger(this.javaClass).info("LOPC f($x) = ${actualIncome(x)}")
//        }
    }

    override fun modifyIncoming(market: MarketAPI, incoming: PopulationComposition) {
        if (market.faction.isPlayerFaction || market.isPlayerOwned) {
            incoming.weight.modifyMult("lessOpColonies", immigrationMalus, "LOPC Immigration Malus")
        }
    }

    override fun reportEconomyTick(iterIndex: Int) {
        // We use update flags to properly calculate income after all the other modifiers are set
        signalUpdate()
    }

    fun signalUpdate() {
        updateFlag = true
    }

    private fun updateMarkets() {
        val playerMarkets =
            Global.getSector().economy.marketsCopy.filter { it.faction.isPlayerFaction || it.isPlayerOwned }
        playerMarkets.forEach { market ->
            // Remove malus from calc
            market.incomeMult.modifyMult("lessOpColonies", 1f, "LOPC Administration Malus")
            val originalIncome = market.netIncome
            val target = actualIncome(originalIncome)
            // This calc doesn't technically give the exact malus because income is more complicated, but it's good enough
            val malus = target / market.netIncome
            market.incomeMult.modifyMult("lessOpColonies", malus, "LOPC Administration Malus")

            Global.getLogger(this.javaClass).info("LOPC (${market.name}) $originalIncome $target $malus ${market.netIncome}")

            if (!market.immigrationModifiers.contains(this)) {
                market.addImmigrationModifier(this)
            }
        }
    }

    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return false
    }

    override fun advance(amount: Float) {
        if (updateFlag) {
            updateFlag = false
            updateMarkets()
        }
    }

}
