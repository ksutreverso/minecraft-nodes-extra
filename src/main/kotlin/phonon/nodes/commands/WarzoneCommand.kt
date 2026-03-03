package phonon.nodes.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import phonon.nodes.Message
import phonon.nodes.objects.TerritoryId
import phonon.nodes.war.WarzoneManager

/**
 * /warzone
 *
 * quick admin switchboard for warzone sessions:
 * - /warzone id1,id2,id3 minutes  -> start a session
 * - /warzone stop                  -> end it now (cancel flags, release stuff)
 */
public class WarzoneCommand :
    CommandExecutor,
    TabCompleter {
    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("nodes.admin")) {
            Message.error(sender, "You do not have permission to use this")
            return true
        }

        if (args.isEmpty()) {
            Message.error(sender, "Usage: /warzone id1,id2,id3 duration_minutes | /warzone stop")
            return true
        }

        if (args.size == 1 && args[0].equals("stop", ignoreCase = true)) {
            WarzoneManager.stop()
            Message.print(sender, "Warzone stopped")
            return true
        }

        if (args.size < 2) {
            Message.error(sender, "Usage: /warzone id1,id2,id3 duration_minutes")
            return true
        }

        val idTokens = args[0].split(",").filter { it.isNotBlank() }
        val ids = mutableListOf<TerritoryId>()
        for (token in idTokens) {
            val num = token.toIntOrNull()
            if (num == null) {
                Message.error(sender, "Invalid territory id \"$token\"")
                return true
            }
            ids.add(TerritoryId(num))
        }

        val duration = args[1].toIntOrNull()
        if (duration == null || duration <= 0) {
            Message.error(sender, "Invalid duration \"${args[1]}\"")
            return true
        }

        WarzoneManager.start(ids, duration)
        Message.print(sender, "Warzone enabled for ${ids.size} territories for $duration minutes")
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> = emptyList()
}
