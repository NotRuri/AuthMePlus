package one.ruri.authmeplus

import fr.xephi.authme.api.v3.AuthMeApi
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

class Handlers(
    private val plugin: JavaPlugin,
    private var cfg: FileConfiguration,
) : Listener,
    CommandExecutor,
    TabCompleter {
    private val api = AuthMeApi.getInstance()

    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.getCommand("amp")?.let {
            it.setExecutor(this)
            it.setTabCompleter(this)
        }
    }

    fun shutdown() {}

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!cfg.getBoolean("settings.enableplugin", true)) return

        val player = event.player
        val name = player.name

        val pAddress = player.address?.address

        if (pAddress == null || !Utils.isIpSafe(pAddress, cfg)) return

        if (api.isAuthenticated(player)) return

        Bukkit.getAsyncScheduler().runNow(plugin) {
            val premiumResult = Utils.checkUsernameIsPremium(plugin.logger, name)

            when (premiumResult) {
                1 -> {
                    player.scheduler.run(plugin, { _ ->
                        if (!player.isOnline) return@run

                        if (!api.isRegistered(name)) {
                            val randomPass =
                                java.util.UUID
                                    .randomUUID()
                                    .toString()
                                    .replace("-", "")
                            api.registerPlayer(name, randomPass)
                            plugin.logger.info("Auto-registered premium player: $name")
                        }

                        if (!api.isAuthenticated(player)) {
                            api.forceLogin(player)
                            player.sendMessage(
                                Utils.getMessage(cfg, "messages.auto_login_premium", "&aYour premium account has been verified!"),
                            )
                            plugin.logger.info("Auto-logged premium player: $name")
                        }
                    }, null)
                }

                0 -> {
                    if (!cfg.getBoolean("settings.accept_cracked", false)) {
                        player.scheduler.run(plugin, { _ ->
                            if (player.isOnline) {
                                player.kick(
                                    Utils.getMessage(cfg, "messages.kick_not_premium", "&cNot a premium account."),
                                )
                            }
                        }, null)
                    }
                }

                else -> {
                    plugin.logger.warning("Mojang API check failed for $name - skipping premium auto-login.")
                }
            }
        }
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>,
    ): Boolean {
        if (args.isEmpty()) {
            cfg.getStringList("messages.help").forEach { line ->
                sender.sendMessage(Utils.color(line))
            }
            return true
        }

        if (!args[0].equals("reload", ignoreCase = true) && !cfg.getBoolean("settings.enableplugin", true)) {
            sender.sendMessage(Utils.getMessage(cfg, "messages.plugin_disabled", "&cPlugin disabled."))
            return true
        }

        return when (args[0].lowercase(Locale.ROOT)) {
            "reload" -> {
                handleReload(sender)
            }

            "version" -> {
                handleVersion(sender)
            }

            "about" -> {
                handleAbout(sender)
            }

            else -> {
                cfg.getStringList("messages.help").forEach { line ->
                    sender.sendMessage(Utils.color(line))
                }
                true
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>,
    ): MutableList<String> {
        val completions = mutableListOf<String>()
        if (args.size == 1) {
            val partial = args[0].lowercase(Locale.ROOT)
            for (cmd in arrayOf("reload", "version", "about")) {
                if (cmd.startsWith(partial)) completions.add(cmd)
            }
        }
        return completions
    }

    private fun handleReload(sender: CommandSender): Boolean {
        if (!sender.hasPermission("amp.reload") && !sender.isOp) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED))
            return true
        }
        return try {
            plugin.reloadConfig()
            cfg = plugin.config
            sender.sendMessage(Utils.getMessage(cfg, "messages.reload_success", "&aConfiguration reloaded."))
            plugin.logger.info("Configuration reloaded by ${sender.name}")
            true
        } catch (e: Exception) {
            sender.sendMessage(Utils.getMessage(cfg, "messages.reload_fail", "&cFailed to reload config."))
            plugin.logger.warning("Failed to reload config: ${e.message}")
            true
        }
    }

    private fun handleVersion(sender: CommandSender): Boolean {
        val version = plugin.pluginMeta.version
        val lines = cfg.getStringList("messages.version")
        if (lines.isEmpty()) {
            sender.sendMessage(Component.text("AuthMePlus v$version", NamedTextColor.GOLD))
            return true
        }
        for (line in lines) {
            sender.sendMessage(Utils.color(line.replace("%version%", version)))
        }
        return true
    }

    private fun handleAbout(sender: CommandSender): Boolean {
        val lines = cfg.getStringList("messages.about")
        if (lines.isEmpty()) {
            sender.sendMessage(Component.text("No plugin information has been configured.", NamedTextColor.RED))
            return true
        }
        for (line in lines) {
            sender.sendMessage(Utils.color(line))
        }
        return true
    }
}
