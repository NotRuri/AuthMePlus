package one.ruri.authmeplus.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import one.ruri.authmeplus.Logger
import one.ruri.authmeplus.Utils
import one.ruri.authmeplus.protocol.Protocol
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

class Dispatch(
    private val plugin: JavaPlugin,
    private var cfg: FileConfiguration,
    private val log: Logger,
    private val protocol: Protocol? = null,
) {
    fun dispatch(
        sender: CommandSender,
        args: Array<String>,
    ): Boolean {
        if (!args[0].equals("reload", ignoreCase = true) && !cfg.getBoolean("settings.enabled", true)) {
            sender.sendMessage(Utils.getMessage(cfg, "messages.plugin_disabled", "&cPlugin disabled."))
            return true
        }

        return when (args[0].lowercase(Locale.ROOT)) {
            "reload" -> {
                val newCfg = Reload.execute(sender, plugin, log, cfg)
                if (newCfg != null) {
                    cfg = newCfg
                    val crackedPlayers = newCfg.getStringList("settings.cracked_players").toSet()
                    protocol?.updateCrackedPlayers(crackedPlayers)
                }
                true
            }

            "version" -> {
                Version.execute(sender, plugin, cfg)
            }

            "about" -> {
                About.execute(sender, cfg)
            }

            "cracked" -> {
                if (args.size < 3) {
                    sender.sendMessage(Component.text("Usage: /amp cracked <add|remove> <username>", NamedTextColor.RED))
                    true
                } else {
                    Cracked.execute(sender, plugin, log, cfg, protocol!!, args[1], args[2])
                    true
                }
            }

            else -> {
                showHelp(sender)
                true
            }
        }
    }

    fun showHelp(sender: CommandSender) {
        cfg.getStringList("messages.help").forEach { line ->
            sender.sendMessage(Utils.color(line))
        }
    }
}
