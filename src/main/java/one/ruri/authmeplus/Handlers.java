package one.ruri.authmeplus;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class Handlers
    implements Listener, CommandExecutor, TabCompleter
{

    private final JavaPlugin plugin;
    private final FileConfiguration cfg;
    private final Bridge authMe;
    private final File linkedFile;
    private final FileConfiguration linkedConfig;

    public Handlers(
        JavaPlugin plugin,
        FileConfiguration cfg,
        Bridge authMe
    ) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.authMe = authMe;
        this.linkedFile = new File(plugin.getDataFolder(), "linked.yml");
        if (!this.linkedFile.exists()) {
            this.linkedFile.getParentFile().mkdirs();
            try {
                this.linkedFile.createNewFile();
            } catch (IOException e) {
                plugin
                    .getLogger()
                    .warning("Could not create linked.yml: " + e.getMessage());
            }
        }
        this.linkedConfig = YamlConfiguration.loadConfiguration(
            this.linkedFile
        );
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

    public void shutdown() {
        if (this.linkedConfig != null && this.linkedFile != null) {
            try {
                this.linkedConfig.save(this.linkedFile);
            } catch (IOException e) {
                this.plugin
                    .getLogger()
                    .warning("Could not save linked.yml: " + e.getMessage());
            }
        }
    }

    private void saveLinkedConfigAsync() {
        Bukkit.getAsyncScheduler().runNow(this.plugin, task -> {
            try {
                this.linkedConfig.save(this.linkedFile);
            } catch (IOException e) {
                this.plugin
                    .getLogger()
                    .warning("Could not save linked.yml: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!this.cfg.getBoolean("settings.enableplugin", true)) return;

        Player player = event.getPlayer();
        String name = player.getName();
        String lower = name.toLowerCase();

        InetAddress pAddress = (player.getAddress() != null)
            ? player.getAddress().getAddress()
            : null;
        String currentIp = (pAddress != null)
            ? pAddress.getHostAddress()
            : null;

        if (pAddress == null || !Utils.isIpSafe(pAddress, this.cfg)) return;

        List<String> linkedIps = this.linkedConfig.getStringList(
            lower + ".ips"
        );
        boolean isIpAuthorized = false;

        if (currentIp != null && linkedIps != null) {
            for (String linkedIp : linkedIps) {
                if (currentIp.equals(linkedIp)) {
                    isIpAuthorized = true;
                    break;
                }
            }
        }

        if (isIpAuthorized) {
            if (!this.authMe.isAuthenticated(player)) {
                this.authMe.forceLogin(player);
                player.sendMessage(
                    Utils.getMessage(
                        this.cfg,
                        "messages.auto_login_success",
                        "&aAutomatic login detected!"
                    )
                );
                this.plugin.getLogger().info("Auto-logged player: " + name);
            }
            return;
        }

        if (
            this.authMe.isAuthenticated(player) &&
            this.cfg.getBoolean("settings.prompt_on_join", true)
        ) {
            boolean alreadyPrompted = this.linkedConfig.getBoolean(
                lower + ".prompted",
                false
            );
            if (!alreadyPrompted) {
                Bukkit.getAsyncScheduler().runNow(this.plugin, task -> {
                    boolean isPremium = Utils.checkUsernameIsPremium(
                        this.plugin.getLogger(),
                        name
                    );
                    if (isPremium) {
                        player.getScheduler().run(
                            this.plugin,
                            scheduledTask -> {
                                if (player.isOnline()) {
                                    player.sendMessage(
                                        Utils.getMessage(
                                            this.cfg,
                                            "messages.prompt_ip",
                                            "&eWould you like to enable login bypass for this IP?"
                                        )
                                    );
                                    player.sendMessage(
                                        Utils.getMessage(
                                            this.cfg,
                                            "messages.prompt_accept_howto",
                                            "&eUse &b/amp accept&e to allow this IP."
                                        )
                                    );
                                    player.sendMessage(
                                        Utils.getMessage(
                                            this.cfg,
                                            "messages.prompt_revoke_howto",
                                            "&eUse &b/amp revoke&e to remove the authorization."
                                        )
                                    );
                                    this.linkedConfig.set(
                                        lower + ".prompted",
                                        true
                                    );
                                    saveLinkedConfigAsync();
                                }
                            },
                            null
                        );
                    }
                });
            }
        }
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

        String lower = p.getName().toLowerCase();
        InetAddress pAddress = (p.getAddress() != null)
            ? p.getAddress().getAddress()
            : null;
        String currentIp = (pAddress != null)
            ? pAddress.getHostAddress()
            : null;

        if (args.length == 0) {
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.help_header",
                    "&6=== Premium Commands ==="
                )
            );
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.help_accept",
                    "&e/amp accept"
                )
            );
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.help_revoke",
                    "&e/amp revoke"
                )
            );
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.help_list",
                    "&e/amp list"
                )
            );
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.help_about",
                    "&e/amp about"
                )
            );
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "accept":
                return handleAccept(p, lower, pAddress, currentIp);
            case "revoke":
                return handleRevoke(p, lower, currentIp, args);
            case "list":
                return handleList(p, lower, args);
            case "about":
                return handleAbout(p);
            default:
                p.sendMessage(
                    Utils.getMessage(
                        this.cfg,
                        "messages.unknown_command",
                        "&eUnknown command."
                    )
                );
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
        if (!(sender instanceof Player)) return completions;

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String cmd : Arrays.asList(
                "accept",
                "revoke",
                "list",
                "about"
            )) {
                if (cmd.startsWith(partial)) completions.add(cmd);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String partial = args[1].toLowerCase();
            if (sub.equals("list")) {
                if ("sure".startsWith(partial)) completions.add("sure");
            } else if (sub.equals("revoke")) {
                if ("all".startsWith(partial)) completions.add("all");
            }
        }
        return completions;
    }

    private boolean handleAccept(
        Player p,
        String lower,
        InetAddress pAddress,
        String currentIp
    ) {
        if (!this.authMe.isAuthenticated(p)) {
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.not_authenticated",
                    "&cYou must be logged in to do this."
                )
            );
            return true;
        }

        if (pAddress == null || currentIp == null) {
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.no_ip",
                    "&cUnable to retrieve your IP address."
                )
            );
            return true;
        }

        if (!Utils.isIpSafe(pAddress, this.cfg)) {
            p.sendMessage(
                ChatColor.translateAlternateColorCodes(
                    '&',
                    "&4&lSecurity error &c: The server is not configured correctly with Velocity."
                )
            );
            p.sendMessage(
                ChatColor.translateAlternateColorCodes(
                    '&',
                    "&cFor safety, bypass is disabled. Ask an administrator to configure IP forwarding."
                )
            );
            this.plugin
                .getLogger()
                .warning(
                    "[Security] Bypass attempt blocked for " +
                        p.getName() +
                        " because the detected IP is a local/proxy IP (" +
                        currentIp +
                        ")."
                );
            return true;
        }

        p.sendMessage(
            Utils.getMessage(
                this.cfg,
                "messages.mojang_check",
                "&eChecking your account..."
            )
        );

        Bukkit.getAsyncScheduler().runNow(this.plugin, task -> {
            boolean isPremium = Utils.checkUsernameIsPremium(
                this.plugin.getLogger(),
                p.getName()
            );

            p.getScheduler().run(
                this.plugin,
                scheduledTask -> {
                    if (!isPremium) {
                        p.sendMessage(
                            Utils.getMessage(
                                this.cfg,
                                "messages.not_premium",
                                "&cThis account is not Premium."
                            )
                        );
                        return;
                    }

                    List<String> linkedIps = this.linkedConfig.getStringList(
                        lower + ".ips"
                    );
                    if (linkedIps == null) linkedIps = new ArrayList<>();

                    boolean alreadyLinked = false;
                    for (String linkedIp : linkedIps) {
                        if (currentIp.equals(linkedIp)) {
                            alreadyLinked = true;
                            break;
                        }
                    }

                    if (!alreadyLinked) {
                        linkedIps.add(currentIp);
                        this.linkedConfig.set(lower + ".ips", linkedIps);
                        this.linkedConfig.set(lower + ".prompted", true);
                        saveLinkedConfigAsync();
                        p.sendMessage(
                            Utils.getMessage(
                                this.cfg,
                                "messages.premium_link_success",
                                "&aYour premium account is now linked!"
                            )
                        );
                        this.plugin
                            .getLogger()
                            .info("Linked " + p.getName() + " to IP");
                    } else {
                        p.sendMessage(
                            Utils.getMessage(
                                this.cfg,
                                "messages.already_linked",
                                "&aThis IP is already allowed."
                            )
                        );
                    }
                },
                null
            );
        });
        return true;
    }

    private boolean handleRevoke(
        Player p,
        String lower,
        String currentIp,
        String[] args
    ) {
        List<String> linkedIps = this.linkedConfig.getStringList(
            lower + ".ips"
        );
        if (linkedIps == null) linkedIps = new ArrayList<>();

        if (args.length == 1) {
            if (currentIp == null) {
                p.sendMessage(
                    Utils.getMessage(
                        this.cfg,
                        "messages.no_ip",
                        "&cUnable to retrieve the IP."
                    )
                );
                return true;
            }

            boolean removed = false;
            for (int i = 0; i < linkedIps.size(); i++) {
                if (currentIp.equals(linkedIps.get(i))) {
                    linkedIps.remove(i);
                    removed = true;
                    break;
                }
            }

            if (removed) {
                this.linkedConfig.set(lower + ".ips", linkedIps);
                if (linkedIps.isEmpty()) this.linkedConfig.set(
                    lower + ".prompted",
                    false
                );
                saveLinkedConfigAsync();
                p.sendMessage(
                    Utils.getMessage(
                        this.cfg,
                        "messages.premium_revoke",
                        "&cBypass has been removed."
                    )
                );
            } else {
                p.sendMessage(
                    Utils.getMessage(
                        this.cfg,
                        "messages.ip_not_in_list",
                        "&eThis IP address is not in the list."
                    )
                );
            }
            return true;
        }

        String opt = args[1].toLowerCase(Locale.ROOT);
        if (opt.equals("all")) {
            this.linkedConfig.set(lower + ".ips", new ArrayList<>());
            this.linkedConfig.set(lower + ".prompted", false);
            saveLinkedConfigAsync();
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.premium_revoke_all",
                    "&cAll addresses have been removed."
                )
            );
            return true;
        }

        String ipToRemove = args[1];
        boolean removed = false;
        for (int i = 0; i < linkedIps.size(); i++) {
            if (ipToRemove.equals(linkedIps.get(i))) {
                linkedIps.remove(i);
                removed = true;
                break;
            }
        }

        if (removed) {
            this.linkedConfig.set(lower + ".ips", linkedIps);
            if (linkedIps.isEmpty()) this.linkedConfig.set(
                lower + ".prompted",
                false
            );
            saveLinkedConfigAsync();
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.premium_revoke_specific",
                    "&cThe IP has been removed."
                )
            );
        } else {
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.ip_not_in_list",
                    "&eThe IP address is not in the list."
                )
            );
        }
        return true;
    }

    private boolean handleList(Player p, String lower, String[] args) {
        if (args.length == 1) {
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.list_confirm",
                    "&eAre you sure you want to list your IPs?"
                )
            );
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.list_howto",
                    "&eUse /amp list sure"
                )
            );
            return true;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("sure")) {
            List<String> linkedIps = this.linkedConfig.getStringList(
                lower + ".ips"
            );
            if (linkedIps == null || linkedIps.isEmpty()) {
                p.sendMessage(
                    Utils.getMessage(
                        this.cfg,
                        "messages.list_empty",
                        "&eNo allowed IPs."
                    )
                );
            } else {
                p.sendMessage(
                    Utils.getMessage(
                        this.cfg,
                        "messages.list_header",
                        "&aLinked IPs:"
                    )
                );
                for (String linkedIp : linkedIps) {
                    String format = Utils.getMessage(
                        this.cfg,
                        "messages.list_format",
                        "&b - %ip%"
                    );
                    p.sendMessage(format.replace("%ip%", linkedIp));
                }
            }
        } else {
            p.sendMessage(
                Utils.getMessage(
                    this.cfg,
                    "messages.unknown_command",
                    "&eUnknown command."
                )
            );
        }
        return true;
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
