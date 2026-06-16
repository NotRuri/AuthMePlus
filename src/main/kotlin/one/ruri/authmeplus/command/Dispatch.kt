package one.ruri.authmeplus.command

import one.ruri.authmeplus.Logger
import one.ruri.authmeplus.Utils
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

class Dispatch(
    private val plugin: JavaPlugin,
    private var cfg: FileConfiguration,
    private val log: Logger,
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
                if (newCfg != null) cfg = newCfg
                true
            }

            "version" -> {
                Version.execute(sender, plugin, cfg)
            }

            "about" -> {
                About.execute(sender, cfg)
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
