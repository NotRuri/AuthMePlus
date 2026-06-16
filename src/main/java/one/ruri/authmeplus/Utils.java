package one.ruri.authmeplus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public final class Utils {

    private Utils() {}

    public static String getMessage(
        FileConfiguration cfg,
        String path,
        String def
    ) {
        return ChatColor.translateAlternateColorCodes(
            '&',
            cfg.getString(path, def)
        );
    }

    public static boolean isIpSafe(InetAddress address, FileConfiguration cfg) {
        if (address == null) return false;

        if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
            return false;
        }

        if (
            address.isSiteLocalAddress() &&
            !cfg.getBoolean("settings.allow_local_ips", false)
        ) {
            return false;
        }

        return true;
    }

    public static int checkUsernameIsPremium(Logger logger, String username) {
        HttpURLConnection con = null;
        try {
            String url =
                "https://api.mojang.com/users/profiles/minecraft/" + username;
            con = (HttpURLConnection) (new URL(url)).openConnection();
            con.setConnectTimeout(3000);
            con.setReadTimeout(3000);
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "AuthMePlus/1.0");
            int code = con.getResponseCode();
            if (code == 200) {
                try (
                    BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                            con.getInputStream(),
                            StandardCharsets.UTF_8
                        )
                    )
                ) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) sb.append(line);
                    return sb.length() > 0 ? 1 : 0;
                }
            }
            return 0;
        } catch (Exception e) {
            logger.warning(
                "Error checking Mojang API for " +
                    username +
                    ": " +
                    e.getMessage()
            );
            return -1;
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
