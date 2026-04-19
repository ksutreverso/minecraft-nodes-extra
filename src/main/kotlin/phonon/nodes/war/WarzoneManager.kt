package phonon.nodes.war

/**
 * - “warzone” is a mini war mode for specific territories.
 * - global war can be off; these ids still play by war rules.
 * - loot is handed out every 3 minutes to whoever’s occupying a warzone territory.
 * - when the timer’s up (or you stop it), every warzone territory that was occupied by towns is unoccupied, the territories also can't be captured anymore.
 *
 * api:
 * - start(ids, minutes): fire up a session for those territory ids
 * - stop(): cancel flags, release occupations, clear state
 * - isactive()/contains(id): quick helpers for other systems
 */

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.scheduler.BukkitTask
import phonon.nodes.Message
import phonon.nodes.Nodes
import phonon.nodes.objects.TerritoryId
import phonon.nodes.war.FlagWar

public object WarzoneManager {
    private var active: Boolean = false
    private val territories: HashSet<TerritoryId> = hashSetOf()
    private var endTask: BukkitTask? = null
    private var rewardTask: BukkitTask? = null

    public fun isActive(): Boolean = active

    public fun contains(id: TerritoryId): Boolean = territories.contains(id)

    // spin up a session for these ids (minutes is real, not minecraft days)
    public fun start(ids: List<TerritoryId>, durationMinutes: Int) {
        stop()
        if (ids.isEmpty() || durationMinutes <= 0) {
            return
        }
        territories.addAll(ids)
        active = true
        Message.broadcast("Warzone activated for ${ids.size} for $durationMinutes minutes")
        // reward every 3 minutes
        val periodTicks = 20L * 60L * 3L
        rewardTask = Bukkit.getScheduler().runTaskTimer(
            Nodes.plugin!!,
            Runnable { giveRewards() },
            periodTicks,
            periodTicks,
        )
        // auto-shutdown when the timer hits zero
        val endTicks = 20L * 60L * durationMinutes.toLong()
        endTask = Bukkit.getScheduler().runTaskLater(
            Nodes.plugin!!,
            Runnable { stop() },
            endTicks,
        )
    }

    // stop the session and clean up everything we started
    public fun stop() {
        endTask?.cancel()
        endTask = null
        rewardTask?.cancel()
        rewardTask = null
        // cancel flags in these territories first so visuals/threads die fast
        val ids = territories.toList()
        for (attack in FlagWar.chunkToAttacker.values.toList()) {
            val chunk = Nodes.getTerritoryChunkFromCoord(attack.coord) ?: continue
            if (ids.contains(chunk.territory.id)) {
                attack.cancel()
            }
        }
        // then release occupation so they go back to neutral
        for (id in ids) {
            val terr = Nodes.getTerritoryFromId(id) ?: continue
            Nodes.releaseTerritory(terr)
        }
        if (active) {
            Message.broadcast("Warzone ended.")
        }
        active = false
        territories.clear()
    }

    // add resources to the income of the current occupier for each warzone territory
    private fun giveRewards() {
        if (!active) return
        for (id in territories) {
            val territory = Nodes.getTerritoryFromId(id) ?: continue
            val occupier = territory.occupier ?: continue
            // TODO: make configurable, way too lazy to do this right now so maybe later
            // you can change this to any item in minecraft
            Nodes.addToIncome(occupier, Material.IRON_BLOCK, 3)
            Nodes.addToIncome(occupier, Material.GOLD_BLOCK, 2)
            Nodes.addToIncome(occupier, Material.DIAMOND_BLOCK, 1)
        }
    }
}
