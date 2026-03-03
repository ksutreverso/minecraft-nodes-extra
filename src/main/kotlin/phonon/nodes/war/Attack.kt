/**
 * Instance for attacking a chunk
 * - holds state data of attack
 * - functions as runnable thread for attack tick
 */

package phonon.nodes.war

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.boss.BossBar
import org.bukkit.entity.ArmorStand
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import phonon.nodes.Config
import phonon.nodes.Nodes
import phonon.nodes.objects.Coord
import phonon.nodes.objects.Town
import java.util.UUID

/**
 * attack
 *
 * one flag. one timer. a little floating ui.
 * - top line = who’s attacking
 * - bottom line = time left [mm:ss]
 * we tick in the background and shout updates at nearby players.
 */
public class Attack(
    val attacker: UUID, // attacker's UUID
    val town: Town, // attacker's town
    val coord: Coord, // chunk coord under attack
    val flagBase: Block, // fence base of flag
    val flagBlock: Block, // wool block for flag
    val flagTorch: Block, // torch block of flag
    val skyBeaconColorBlocks: List<Block>,
    val skyBeaconWireframeBlocks: List<Block>,
    val progressBar: BossBar, // progress bar
    val attackTime: Long, //
    var progress: Long, // initial progress, current tick count
) : Runnable {
    // no build region
    val noBuildXMin: Int
    val noBuildXMax: Int
    val noBuildZMin: Int
    val noBuildZMax: Int
    val noBuildYMin: Int
    val noBuildYMax: Int = 255 // temporarily set to height

    var thread: BukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Nodes.plugin!!, this, FlagWar.ATTACK_TICK, FlagWar.ATTACK_TICK)

    // floating nametags over the flag (above the torch so you can actually see them)
    val armorstand = AttackArmorStand(this, flagBase.world, flagBase.location.clone().add(0.5, 2.6, 0.5))

    // re-used json serialization StringBuilders
    val jsonStringBase: StringBuilder
    val jsonString: StringBuilder

    init {
        val flagX = flagBase.x
        val flagY = flagBase.y
        val flagZ = flagBase.z

        // set no build ranges
        this.noBuildXMin = flagX - Config.flagNoBuildDistance
        this.noBuildXMax = flagX + Config.flagNoBuildDistance
        this.noBuildZMin = flagZ - Config.flagNoBuildDistance
        this.noBuildZMax = flagZ + Config.flagNoBuildDistance
        this.noBuildYMin = flagY + Config.flagNoBuildYOffset

        // set boss bar progress
        val progressNormalized: Double = this.progress.toDouble() / this.attackTime.toDouble()
        this.progressBar.setProgress(progressNormalized)

        // pre-generate main part of the JSON serialization string
        this.jsonStringBase = generateFixedJsonBase(
            this.attacker,
            this.coord,
            this.flagBase,
        )

        // send armor stand packets to players in range
        try {
            this.armorstand.sendPackets()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // full json StringBuilder, initialize capacity to be
        // base capacity + room for progress ticks length
        val jsonStringBufferSize = this.jsonStringBase.capacity() + 20
        this.jsonString = StringBuilder(jsonStringBufferSize)
    }

    public override fun run() {
        FlagWar.attackTick(this)
    }

    public fun cancel() {
        this.thread.cancel()

        val attack = this
        Bukkit.getScheduler().runTask(
            Nodes.plugin!!,
            object : Runnable {
                override fun run() {
                    FlagWar.cancelAttack(attack)
                }
            },
        )
    }

    // returns json format string as a StringBuilder
    // only used with WarSerializer objects
    public fun toJson(): StringBuilder {
        // reset json StringBuilder
        this.jsonString.setLength(0)

        // add base
        this.jsonString.append(this.jsonStringBase)

        // add progress in ticks
        this.jsonString.append("\"p\":${this.progress}")
        this.jsonString.append("}")

        return this.jsonString
    }
}

// pre-generate main part of the JSON serialization string
// for the attack which does not change
// (only part that changes is progress)
// parts required for serialization:
// - attacker: player uuid
// - coord: chunk coord
// - block: flag base block (fence)
// - skyBeaconColorBlocks: track blocks in sky beacon
// - skyBeaconWireframeBlocks: track blocks in sky beacon
private fun generateFixedJsonBase(
    attacker: UUID,
    coord: Coord,
    block: Block,
): StringBuilder {
    val s = StringBuilder()

    s.append("{")

    // attacker uuid
    s.append("\"id\":\"$attacker\",")

    // chunk coord [c.x, c.z]
    s.append("\"c\":[${coord.x},${coord.z}],")

    // flag base block [b.x, b.y, b.z]
    s.append("\"b\":[${block.x},${block.y},${block.z}],")

    return s
}

public class AttackArmorStand(
    val attack: Attack,
    val world: World,
    val loc: Location,
    val maxViewDistance: Int = 3,
) {
    var townNameArmorstand = createArmorStand(world, loc)
    var progressArmorstand = createArmorStand(world, loc.add(0.0, -0.25, 0.0))

    // min/max x/z chunk view distance from this armor stand
    val minViewChunkX: Int
    val maxViewChunkX: Int
    val minViewChunkZ: Int
    val maxViewChunkZ: Int

    init {
        // calculate max chunk view distance from this armor stand
        val chunk = this.loc.chunk
        val chunkX = chunk.x
        val chunkZ = chunk.z

        minViewChunkX = chunkX - this.maxViewDistance
        maxViewChunkX = chunkX + this.maxViewDistance
        minViewChunkZ = chunkZ - this.maxViewDistance
        maxViewChunkZ = chunkZ + this.maxViewDistance
    }

    /**
     * Remove armorstand, for cleanup.
     */
    public fun remove() {
        this.townNameArmorstand.remove()
        this.progressArmorstand.remove()
    }

    /**
     * Check if armorstand is still valid.
     */
    public fun isValid(): Boolean = this.townNameArmorstand.isValid && this.progressArmorstand.isValid

    /**
     * Re-create new armorstand.
     */
    public fun respawn() {
        this.townNameArmorstand.remove()
        this.townNameArmorstand = createArmorStand(this.world, this.loc)
        this.progressArmorstand.remove()
        this.progressArmorstand = createArmorStand(this.world, this.loc.add(0.0, -0.25, 0.0))
    }

    /**
     * Send player-specific armor stand packets for players
     * within maxViewDistance chunks of this armorstand.
     */
    // refresh the nametags and poke nearby players with the latest text
    public fun sendPackets() {
        val plugin = Nodes.plugin ?: return
        val attackerName = Bukkit.getOfflinePlayer(attack.attacker)?.name ?: "attacker"
        val remainingSeconds = (attack.attackTime - attack.progress) / 20
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val formattedProgress = "[$minutes:${seconds.toString().padStart(2, '0')}]"

        // run on main thread to avoid async entity mutations
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                if (!this.world.isChunkLoaded(this.loc.chunk)) {
                    return@Runnable
                }
                try {
                    this.townNameArmorstand.customName(Component.text(attackerName))
                    this.progressArmorstand.customName(Component.text(formattedProgress))
                } catch (_: Exception) {
                    // ignore if entity got removed between ticks
                }
            },
        )
    }
}

/**
 * Namespaced key for marking armorstands as nodes plugin armorstands.
 */
internal val NODES_ARMORSTAND_KEY = NamespacedKey("nodes", "armorstand")

/**
 * Helper function to create a new armorstand with associated metadata.
 */
// Make a tiny, invisible armor stand that’s basically just a nametag.
// If the server supports extra toggles (marker/baseplate/arms), flip them off.
private fun createArmorStand(
    world: World,
    loc: Location,
): ArmorStand {
    val armorstand = world.spawn(loc, ArmorStand::class.java)
    armorstand.setSmall(true)
    armorstand.setGravity(false)
    armorstand.setVisible(false)
    armorstand.isCustomNameVisible = true
    try {
        armorstand.setBasePlate(false)
        armorstand.setArms(false)
        armorstand.setMarker(true)
    } catch (_: Exception) {
        // ignore if not supported on this server version
    }
    armorstand.persistentDataContainer.set(NODES_ARMORSTAND_KEY, PersistentDataType.INTEGER, 0)
    return armorstand
}
