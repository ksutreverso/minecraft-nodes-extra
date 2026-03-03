package phonon.nodes.placeholders

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import net.md_5.bungee.api.ChatColor
import org.bukkit.entity.Player
import phonon.nodes.Nodes
import phonon.nodes.objects.townNametagViewedByPlayer
import phonon.nodes.utils.Color

/**
 * PlaceholderAPI expansion for Nodes plugin
 * Provides placeholders for town/nation info with RGB colors
 */
class NodesExpansion : PlaceholderExpansion() {

    override fun getIdentifier(): String = "nodes"
    override fun getAuthor(): String = "YourName"
    override fun getVersion(): String = "1.0.5"
    override fun persist(): Boolean = true
    override fun canRegister(): Boolean = true
	
    private val smallCapsMap = mapOf(
        'a' to 'ᴀ', 'b' to 'ʙ', 'c' to 'ᴄ', 'd' to 'ᴅ',
        'e' to 'ᴇ', 'f' to 'ғ', 'g' to 'ɢ', 'h' to 'ʜ',
        'i' to 'ɪ', 'j' to 'ᴊ', 'k' to 'ᴋ', 'l' to 'ʟ',
        'm' to 'ᴍ', 'n' to 'ɴ', 'o' to 'ᴏ', 'p' to 'ᴘ',
        'q' to 'ǫ', 'r' to 'ʀ', 's' to 'ꜱ', 't' to 'ᴛ',
        'u' to 'ᴜ', 'v' to 'ᴠ', 'w' to 'ᴡ', 'x' to 'x',
        'y' to 'ʏ', 'z' to 'ᴢ',
    )

    private fun toSmallCaps(text: String): String = buildString {
        text.forEach { char ->
            val lower = char.lowercaseChar()
            append(smallCapsMap[lower] ?: char)
        }
    }

    override fun onPlaceholderRequest(player: Player?, identifier: String): String? {
        if (player == null) return null

        val resident = Nodes.getResident(player) ?: return null
        val town = resident.town

        val idLower = identifier.lowercase()

        return when {
            idLower == "town_name" ->
                town?.name?.let { toSmallCaps(it) } ?: "ɴᴇɴʜᴜᴍᴀ"

            idLower == "town_leader" ->
                town?.leader?.name?.let { toSmallCaps(it) } ?: "ɴᴇɴʜᴜᴍᴀ"

            idLower == "town_population" ->
                town?.residents?.size?.toString() ?: "0"

            idLower == "town_claims" ->
                town?.territories?.size?.toString() ?: "0"

            idLower == "nation_name" ->
                town?.nation?.name?.let { toSmallCaps(it) } ?: "ɴᴇɴʜᴜᴍᴀ"

            idLower == "nation_capital" ->
                town?.nation?.capital?.name?.let { toSmallCaps(it) } ?: "ɴᴇɴʜᴜᴍᴀ"

            idLower == "display_name" ->
                town?.let { toSmallCaps(it.nation?.name ?: it.name) } ?: ""

            idLower == "town_diplomatic" ->
                resident.town?.let { toSmallCaps(townNametagViewedByPlayer(it, player)) } ?: ""

            idLower == "nation" ->
                resident.nation?.name?.let { toSmallCaps(it) } ?: ""

            idLower == "town_color" -> {
                if (town == null) {
                    ChatColor.of("#808080").toString()
                } else {
                    val nation = town.nation
                    val hexColor = if (nation != null) {
                        rgbToHexColor(nation.capital.color)
                    } else {
                        rgbToHexColor(town.color)
                    }
                    hexColor.toString()
                }
            }

            else -> null
        }
    }

    /**
     * Convert RGB color to hex ChatColor (matches TabIntegration logic)
     */
    private fun rgbToHexColor(color: Color): ChatColor {
        val r = color.r.coerceIn(0, 255)
        val g = color.g.coerceIn(0, 255)
        val b = color.b.coerceIn(0, 255)

        val hex = String.format("#%02x%02x%02x", r, g, b)
        return ChatColor.of(hex)
    }
}
