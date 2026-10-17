package me.titlekeeper;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

final class LuckPermsIntegration {
    private LuckPermsIntegration() {
    }

    static boolean grant(Player player, String permissionNode, int seconds) {
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;

        PermissionNode.Builder builder = PermissionNode.builder(permissionNode).value(true);
        if (seconds > 0) {
            builder.expiry(Duration.ofSeconds(seconds));
        }

        user.data().add(builder.build());
        api.getUserManager().saveUser(user);
        player.recalculatePermissions();
        return true;
    }

    static void remove(JavaPlugin plugin, Player player, List<String> permissions) {
        LuckPerms api = LuckPermsProvider.get();
        UUID id = player.getUniqueId();
        User online = api.getUserManager().getUser(id);
        if (online != null) {
            stripTitleNodes(api, online, permissions);
            return;
        }

        api.getUserManager().loadUser(id).thenAccept(user -> {
            if (user != null) {
                stripTitleNodes(api, user, permissions);
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getLogger().info("Cleared TitleKeeper titles for offline player " + player.getName()));
            }
        });
    }

    private static void stripTitleNodes(LuckPerms api, User user, List<String> permissions) {
        for (String permission : permissions) {
            user.data().remove(PermissionNode.builder(permission).build());
        }
        api.getUserManager().saveUser(user);
    }
}