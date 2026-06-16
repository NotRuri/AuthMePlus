package one.ruri.authmeplus.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import one.ruri.authmeplus.Logger
import one.ruri.authmeplus.protocol.Protocol
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

object Cracked {
    fun execute(
        sender: CommandSender,
        plugin: JavaPlugin,
        log: Logger,
        cfg: FileConfiguration,
        protocolHandler: Protocol,
        action: String,
        username: String,
    ) {
        if (!sender.hasPermission("amp.cracked") && !sender.isOp) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED))
            return
        }

        when (action.lowercase()) {
            "add" -> add(sender, plugin, log, cfg, protocolHandler, username)
            "remove" -> remove(sender, plugin, log, cfg, protocolHandler, username)
            else -> sender.sendMessage(Component.text("Usage: /amp cracked <add|remove> <username>", NamedTextColor.RED))
        }
    }

    private fun add(
        sender: CommandSender,
        plugin: JavaPlugin,
        log: Logger,
        cfg: FileConfiguration,
        protocolHandler: Protocol,
        username: String,
    ) {
        val currentList = cfg.getStringList("settings.cracked_players")

        if (currentList.any { it.equals(username, ignoreCase = true) }) {
            sender.sendMessage(Component.text("$username is already in the cracked players list.", NamedTextColor.YELLOW))
            return
        }

        currentList.add(username)
        cfg.set("settings.cracked_players", currentList)
        plugin.saveConfig()

        protocolHandler.updateCrackedPlayers(currentList.toSet())

        sender.sendMessage(Component.text("Added $username to cracked players list.", NamedTextColor.GREEN))
        log.info("${sender.name} added $username to cracked players list")
    }

    private fun remove(
        sender: CommandSender,
        plugin: JavaPlugin,
        log: Logger,
        cfg: FileConfiguration,
        protocolHandler: Protocol,
        username: String,
    ) {
        val currentList = cfg.getStringList("settings.cracked_players")
        val removed = currentList.removeAll { it.equals(username, ignoreCase = true) }

        if (!removed) {
            sender.sendMessage(Component.text("$username is not in the cracked players list.", NamedTextColor.YELLOW))
            return
        }

        cfg.set("settings.cracked_players", currentList)
        plugin.saveConfig()

        protocolHandler.updateCrackedPlayers(currentList.toSet())

        sender.sendMessage(Component.text("Removed $username from cracked players list.", NamedTextColor.GREEN))
        log.info("${sender.name} removed $username from cracked players list")
    }
}
