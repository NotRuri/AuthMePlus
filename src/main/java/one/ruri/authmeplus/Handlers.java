package one.ruri.authmeplus;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
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
    private final FileConfiguration cfg;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.not_player",
                    "This command is only for players."
                )
            );
            return true;
        }

        Player p = (Player) sender;
        if (!this.cfg.getBoolean("settings.enableplugin", true)) {
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.plugin_disabled",
                    "&cPlugin disabled."
                )
            );
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("about")) {
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.help_header",
                    "&6=== AuthMePlus ==="
                )
            );
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.help_about",
                    "&e/amp about &f- How the plugin works and GDPR compliance."
                )
            );
            return true;
        }

        return handleAbout(p);
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;
        if (args.length == 1 && "about".startsWith(args[0].toLowerCase())) {
            completions.add("about");
        }
        return completions;
    }

    private boolean handleAbout(Player p) {
        List<String> lines = this.cfg.getStringList("messages.about");
        if (lines == null || lines.isEmpty()) {
            p.sendMessage(
                ChatColor.RED + "No plugin information has been configured."
            );
            return true;
        }

        for (String line : lines) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
        return true;
    }
}
