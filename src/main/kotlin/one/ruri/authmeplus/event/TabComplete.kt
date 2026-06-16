package one.ruri.authmeplus.event

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.util.Locale

class TabComplete : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>,
    ): MutableList<String> {
        val completions = mutableListOf<String>()
        when (args.size) {
            1 -> {
                val partial = args[0].lowercase(Locale.ROOT)
                for (cmd in arrayOf("reload", "version", "about", "cracked")) {
                    if (cmd.startsWith(partial)) completions.add(cmd)
                }
            }

            2 -> {
                if (args[0].equals("cracked", ignoreCase = true)) {
                    val partial = args[1].lowercase(Locale.ROOT)
                    for (cmd in arrayOf("add", "remove")) {
                        if (cmd.startsWith(partial)) completions.add(cmd)
                    }
                }
            }
        }
        return completions
    }
}
