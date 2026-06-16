package one.ruri.authmeplus;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class Handlers implements Listener, CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;
    private final Bridge authMe;

    public Handlers(JavaPlugin plugin, FileConfiguration cfg, Bridge authMe) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.authMe = authMe;
    }

    public void register() {
        this.plugin
            .getServer()
            .getPluginManager()
            .registerEvents(this, this.plugin);
        if (this.plugin.getCommand("amp") != null) {
            this.plugin.getCommand("amp").setExecutor(this);
            this.plugin.getCommand("amp").setTabCompleter(this);
        }
    }

    public void shutdown() {}

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!this.cfg.getBoolean("settings.enableplugin", true)) return;

        Player player = event.getPlayer();
        String name = player.getName();

        InetAddress pAddress = (player.getAddress() != null)
            ? player.getAddress().getAddress()
            : null;

        if (pAddress == null || !Utils.isIpSafe(pAddress, this.cfg)) return;

        if (this.authMe.isAuthenticated(player)) return;

        Bukkit.getAsyncScheduler().runNow(this.plugin, task -> {
            int premiumResult = Utils.checkUsernameIsPremium(
                this.plugin.getLogger(),
                name
            );

            if (premiumResult == 1) {
                player.getScheduler().run(
                    this.plugin,
                    scheduledTask -> {
                        if (!player.isOnline()) return;

                        if (!this.authMe.isRegistered(name)) {
                            String randomPass = UUID.randomUUID()
                                .toString()
                                .replace("-", "");
                            this.authMe.registerPlayer(player, randomPass);
                            this.plugin
                                .getLogger()
                                .info(
                                    "Auto-registered premium player: " + name
                                );
                        }

                        if (!this.authMe.isAuthenticated(player)) {
                            this.authMe.forceLogin(player);
                            player.sendMessage(
                                Utils.getMessage(
                                    this.cfg,
                                    "messages.auto_login_premium",
                                    "&aYour premium account has been verified!"
                                )
                            );
                            this.plugin
                                .getLogger()
                                .info("Auto-logged premium player: " + name);
                        }
                    },
                    null
                );
            } else if (premiumResult == 0) {
                if (!this.cfg.getBoolean("settings.accept_cracked", false)) {
                    player.getScheduler().run(
                        this.plugin,
                        scheduledTask -> {
                            if (player.isOnline()) {
                                player.kickPlayer(
                                    Utils.getMessage(
                                        this.cfg,
                                        "messages.kick_not_premium",
                                        "&cNot a premium account."
                                    )
                                );
                            }
                        },
                        null
                    );
                }
            } else {
                this.plugin
                    .getLogger()
                    .warning(
                        "Mojang API check failed for " +
                            name +
                            " - skipping premium auto-login."
                    );
            }
        });
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (args.length == 0) {
            List<String> lines = this.cfg.getStringList("messages.help");
            if (lines != null) {
                for (String line : lines) {
                    sender.sendMessage(
                        ChatColor.translateAlternateColorCodes('&', line)
                    );
                }
            }
            return true;
        }

        if (
            !args[0].equalsIgnoreCase("reload") &&
            !this.cfg.getBoolean("settings.enableplugin", true)
        ) {
            sender.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.plugin_disabled",
                    "&cPlugin disabled."
                )
            );
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                return handleReload(sender);
            case "version":
                return handleVersion(sender);
            case "about":
                return handleAbout(sender);
            default:
                List<String> lines = this.cfg.getStringList("messages.help");
                if (lines != null) {
                    for (String line : lines) {
                        sender.sendMessage(
                            ChatColor.translateAlternateColorCodes('&', line)
                        );
                    }
                }
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (String cmd : new String[] { "reload", "version", "about" }) {
                if (cmd.startsWith(partial)) completions.add(cmd);
            }
        }
        return completions;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("amp.reload") && !sender.isOp()) {
            sender.sendMessage(
                ChatColor.RED +
                    "You do not have permission to use this command."
            );
            return true;
        }
        try {
            this.plugin.reloadConfig();
            this.cfg = this.plugin.getConfig();
            sender.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.reload_success",
                    "&aConfiguration reloaded."
                )
            );
            this.plugin
                .getLogger()
                .info("Configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.reload_fail",
                    "&cFailed to reload config."
                )
            );
            this.plugin
                .getLogger()
                .warning("Failed to reload config: " + e.getMessage());
        }
        return true;
    }

    private boolean handleVersion(CommandSender sender) {
        String version = this.plugin.getDescription().getVersion();
        List<String> lines = this.cfg.getStringList("messages.version");
        if (lines == null || lines.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "AuthMePlus v" + version);
            return true;
        }
        for (String line : lines) {
            sender.sendMessage(
                ChatColor.translateAlternateColorCodes(
                    '&',
                    line.replace("%version%", version)
                )
            );
        }
        return true;
    }

    private boolean handleAbout(CommandSender sender) {
        List<String> lines = this.cfg.getStringList("messages.about");
        if (lines == null || lines.isEmpty()) {
            sender.sendMessage(
                ChatColor.RED + "No plugin information has been configured."
            );
            return true;
        }

        for (String line : lines) {
            sender.sendMessage(
                ChatColor.translateAlternateColorCodes('&', line)
            );
        }
        return true;
    }
}
