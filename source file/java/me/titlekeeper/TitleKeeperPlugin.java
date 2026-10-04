package me.titlekeeper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TitleKeeperPlugin extends JavaPlugin implements Listener, BasicCommand {

    private File customTitlesFile;
    private FileConfiguration customTitlesConfig;

    // Temporary permission attachments (fallback when LuckPerms is absent)
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    // when_block cooldowns: "<uuid>:<material>" -> expiry millis (-1 = permanent)
    private final Map<String, Long> blockCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createCustomTitlesConfig();

        getServer().getPluginManager().registerEvents(this, this);
        this.registerCommand("titlekeeper", this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }

        getLogger().info("TitleKeeper loaded successfully with custom-titles.yml and titlekeeper:badges support!");
    }

    @Override
    public void onDisable() {
        try {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                Scoreboard board = manager.getMainScoreboard();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Team team = board.getTeam(player.getName());
                    if (team != null) {
                        team.unregister();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        attachments.clear();
        blockCooldowns.clear();
    }

    // --- CONFIGURATION MANAGEMENT ---

    private void createCustomTitlesConfig() {
        customTitlesFile = new File(getDataFolder(), "custom-titles.yml");
        if (!customTitlesFile.exists() && getResource("custom-titles.yml") != null) {
            saveResource("custom-titles.yml", false);
        }
        customTitlesConfig = YamlConfiguration.loadConfiguration(customTitlesFile);
    }

    public FileConfiguration getCustomTitlesConfig() {
        return this.customTitlesConfig;
    }

    public void reloadCustomTitlesConfig() {
        if (customTitlesFile == null) {
            customTitlesFile = new File(getDataFolder(), "custom-titles.yml");
        }
        customTitlesConfig = YamlConfiguration.loadConfiguration(customTitlesFile);
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    // --- COMMAND HANDLER (BasicCommand) ---

    @Override
    public boolean canUse(CommandSender sender) {
        return true;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        // /titlekeeper reload
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("titlekeeper.admin")) {
                sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
                return;
            }
            reloadCustomTitlesConfig();
            sender.sendMessage(Component.text("TitleKeeper configuration reloaded!", NamedTextColor.GREEN));
            return;
        }

        // /titlekeeper give <title-name> <player> [duration]
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("titlekeeper.admin")) {
                sender.sendMessage(Component.text("Only admins may grant titles.", NamedTextColor.RED));
                return;
            }

            String titleKey = args[1].toLowerCase();
            Player target = Bukkit.getPlayerExact(args[2]);

            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[2] + "' is not online.", NamedTextColor.RED));
                return;
            }

            String permission = null;
            String configDuration = "perm";
            if (customTitlesConfig.isConfigurationSection("titles")) {
                for (String key : customTitlesConfig.getConfigurationSection("titles").getKeys(false)) {
                    if (key.equalsIgnoreCase(titleKey)) {
                        permission = customTitlesConfig.getString("titles." + key + ".permission");
                        configDuration = customTitlesConfig.getString("titles." + key + ".duration", "perm");
                        break;
                    }
                }
            }

            if (permission == null || permission.isBlank()) {
                sender.sendMessage(Component.text("Unknown title name: '" + titleKey + "'. Check custom-titles.yml.", NamedTextColor.RED));
                return;
            }

            int seconds = parseDuration(configDuration);
            if (args.length >= 4) {
                seconds = parseDuration(args[3]);
            }

            boolean persisted = applySecretLuckPermsGrant(target, permission, seconds);

            String human = seconds > 0 ? formatSeconds(seconds) : "permanent";
            if (!persisted) {
                grantTitle(target, permission);
                sender.sendMessage(Component.text(
                        "Granted '" + titleKey + "' (" + human + ") to " + target.getName()
                                + " - temporary, LuckPerms not found.",
                        NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text(
                        "Granted '" + titleKey + "' (" + human + ") to " + target.getName() + ".",
                        NamedTextColor.GREEN));
            }

            refreshPlayer(target);
            return;
        }

        // /titlekeeper remove <player>
        if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) {
            if (!sender.hasPermission("titlekeeper.admin")) {
                sender.sendMessage(Component.text("Only admins may remove titles.", NamedTextColor.RED));
                return;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[1] + "' is not online.", NamedTextColor.RED));
                return;
            }

            removeTitle(target);
            sender.sendMessage(Component.text("Removed TitleKeeper-granted title from " + target.getName() + ".", NamedTextColor.YELLOW));
            return;
        }

        // Usage
        sender.sendMessage(Component.text("/titlekeeper reload", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/titlekeeper give <title> <player> [duration]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/titlekeeper remove <player>", NamedTextColor.GRAY));
    }

    /**
     * Grants through LuckPerms' API directly, so nothing appears in chat or the command log.
     * When seconds > 0 the node gets an expiry and LuckPerms removes it automatically.
     */
    private boolean applySecretLuckPermsGrant(Player player, String permissionNode, int seconds) {
        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            return false;
        }
        try {
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user =
                    api.getUserManager().getUser(player.getUniqueId());
            if (user == null) return false;

            net.luckperms.api.node.types.PermissionNode.Builder builder =
                    net.luckperms.api.node.types.PermissionNode.builder(permissionNode)
                            .value(true);
            if (seconds > 0) {
                builder.expiry(Duration.ofSeconds(seconds));
            }

            user.data().add(builder.build());
            api.getUserManager().saveUser(user);
            player.recalculatePermissions();
            return true;
        } catch (Throwable t) {
            getLogger().warning("LuckPerms grant failed for " + player.getName() + ": " + t.getMessage());
            return false;
        }
    }

    /** Temporary attachment grant (used when LuckPerms is absent). */
    public void grantTitle(Player player, String permissionNode) {
        UUID uuid = player.getUniqueId();

        PermissionAttachment existing = attachments.remove(uuid);
        if (existing != null) {
            player.removeAttachment(existing);
        }

        PermissionAttachment attachment = player.addAttachment(this);
        attachment.setPermission(permissionNode, true);
        attachments.put(uuid, attachment);

        player.recalculatePermissions();
        refreshPlayer(player);
    }

    public void removeTitle(Player player) {
        PermissionAttachment existing = attachments.remove(player.getUniqueId());
        if (existing != null) {
            player.removeAttachment(existing);
        }

        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            player.recalculatePermissions();
            refreshPlayer(player);
            return;
        }

        try {
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            UUID id = player.getUniqueId();

            net.luckperms.api.model.user.User online = api.getUserManager().getUser(id);
            if (online != null) {
                stripTitleNodes(api, online);
            } else {
                api.getUserManager().loadUser(id).thenAccept(user -> {
                    if (user != null) {
                        stripTitleNodes(api, user);
                        Bukkit.getScheduler().runTask(this, () ->
                                getLogger().info("Cleared TitleKeeper titles for offline player " + player.getName()));
                    }
                });
                return;
            }
        } catch (Throwable t) {
            getLogger().warning("LuckPerms remove failed for " + player.getName() + ": " + t.getMessage());
        }

        player.recalculatePermissions();
        refreshPlayer(player);
    }

    private void stripTitleNodes(net.luckperms.api.LuckPerms api, net.luckperms.api.model.user.User user) {
        if (!customTitlesConfig.isConfigurationSection("titles")) return;
        for (String key : customTitlesConfig.getConfigurationSection("titles").getKeys(false)) {
            String perm = customTitlesConfig.getString("titles." + key + ".permission");
            if (perm != null && !perm.isBlank()) {
                user.data().remove(net.luckperms.api.node.types.PermissionNode.builder(perm).build());
            }
        }
        api.getUserManager().saveUser(user);
    }

    // --- EVENT LISTENERS ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        attachments.remove(event.getPlayer().getUniqueId());

        try {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                Scoreboard board = manager.getMainScoreboard();
                Team team = board.getTeam(event.getPlayer().getName());
                if (team != null) {
                    team.unregister();
                }
            }
        } catch (Exception ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerRecord record = getPlayerRecord(player);

        Component chatPrefix = record.leftPrefix()
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY));

        event.renderer((source, sourceDisplayName, message, viewer) ->
                chatPrefix.append(message)
        );
    }

    // --- BLOCK SETTINGS (per-title settings.when_block) ---

    /** Finds the player's matched title and returns its settings.when_block, or null. */
    private ConfigurationSection getWhenBlock(Player player) {
        if (!customTitlesConfig.isConfigurationSection("titles")) return null;

        for (String key : customTitlesConfig.getConfigurationSection("titles").getKeys(false)) {
            String permission = customTitlesConfig.getString("titles." + key + ".permission");
            if (permission == null || permission.isBlank()) continue;
            if (!player.hasPermission(permission)) continue;

            ConfigurationSection settings =
                    customTitlesConfig.getConfigurationSection("titles." + key + ".settings");
            if (settings == null) return null;
            ConfigurationSection wb = settings.getConfigurationSection("when_block");
            if (wb == null || wb.getString("block") == null) return null;
            return wb;
        }
        return null;
    }

    private Material parseBlock(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String id = raw.trim().toLowerCase();
        if (!id.contains(":")) id = "minecraft:" + id;
        Material mat = Material.matchMaterial(id);
        return (mat != null && mat.isBlock()) ? mat : null;
    }

    /** Parses "1s" "5m" "1h" "1d" "perm". Returns seconds, or -1 for permanent. */
    private int parseDuration(Object raw) {
        if (raw == null) return -1;
        String s = String.valueOf(raw).trim().toLowerCase();
        if (s.isEmpty() || s.equals("0")) return -1;
        if (s.startsWith("perm")) return -1;

        Matcher m = Pattern.compile("^(\\d+)\\s*(s|m|h|d)$").matcher(s);
        if (!m.matches()) return -1;

        int n = Integer.parseInt(m.group(1));
        switch (m.group(2)) {
            case "s": return n;
            case "m": return n * 60;
            case "h": return n * 3600;
            case "d": return n * 86400;
            default: return -1;
        }
    }

    private String formatSeconds(int total) {
        if (total >= 86400) return (total / 86400) + "d";
        if (total >= 3600) return (total / 3600) + "h";
        if (total >= 60) return (total / 60) + "m";
        return total + "s";
    }

    private String cooldownKey(UUID uuid, Material mat) {
        return uuid + ":" + mat.getKey().getKey();
    }

    private boolean canTrigger(UUID uuid, Material mat, int durationSec) {
        String key = cooldownKey(uuid, mat);
        Long expiry = blockCooldowns.get(key);
        if (expiry == null) return true;
        if (durationSec < 0) return true;
        if (System.currentTimeMillis() >= expiry) {
            blockCooldowns.remove(key);
            return true;
        }
        return false;
    }

    private void setCooldown(UUID uuid, Material mat, int durationSec) {
        if (durationSec <= 0) return;
        blockCooldowns.put(cooldownKey(uuid, mat),
                System.currentTimeMillis() + durationSec * 1000L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ConfigurationSection wb = getWhenBlock(player);
        if (wb == null || !wb.getBoolean("touched", false)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Material target = parseBlock(wb.getString("block"));
        if (target == null || block.getType() != target) return;

        int dur = parseDuration(wb.get("duration"));
        if (!canTrigger(player.getUniqueId(), target, dur)) return;

        setCooldown(player.getUniqueId(), target, dur);
        player.sendMessage(Component.text("You touched a ", NamedTextColor.GRAY)
                .append(Component.text(target.getKey().getKey(), NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GRAY)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ConfigurationSection wb = getWhenBlock(player);
        if (wb == null || !wb.getBoolean("picked", false)) return;

        Material target = parseBlock(wb.getString("block"));
        if (target == null || event.getBlockPlaced().getType() != target) return;

        int dur = parseDuration(wb.get("duration"));
        if (!canTrigger(player.getUniqueId(), target, dur)) return;

        setCooldown(player.getUniqueId(), target, dur);
        player.sendMessage(Component.text("You placed a ", NamedTextColor.GRAY)
                .append(Component.text(target.getKey().getKey(), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(".", NamedTextColor.GRAY)));
    }

    // --- NAMETAG & TAB REFRESH ---

    public void refreshPlayer(Player player) {
        PlayerRecord record = getPlayerRecord(player);
        Component leftPrefix = record.leftPrefix();

        try {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                Scoreboard board = manager.getMainScoreboard();
                Team team = board.getTeam(player.getName());
                if (team == null) {
                    team = board.registerNewTeam(player.getName());
                }
                team.addEntry(player.getName());
                team.prefix(leftPrefix);
            }

            Component tabDisplayName = leftPrefix.append(Component.text(player.getName(), NamedTextColor.WHITE));
            player.playerListName(tabDisplayName);
        } catch (Exception ignored) {
        }
    }

    public PlayerRecord getPlayerRecord(Player player) {
        FileConfiguration config = getCustomTitlesConfig();

        if (config.isConfigurationSection("titles")) {
            for (String key : config.getConfigurationSection("titles").getKeys(false)) {
                String permission = config.getString("titles." + key + ".permission");
                if (permission == null || permission.isBlank()) continue;
                if (!player.hasPermission(permission)) continue;

                String title = config.getString("titles." + key + ".title", "Member");
                String badge = config.getString("titles." + key + ".badge", "\ue000");
                String hexColor = config.getString("titles." + key + ".color", "#FFFFFF");
                String description = config.getString("titles." + key + ".description", "");

                return new PlayerRecord(title, badge, parseColor(hexColor), description);
            }
        }

        return PlayerRecord.DEFAULT;
    }

    private TextColor parseColor(String hex) {
        if (hex != null && hex.startsWith("#")) {
            TextColor color = TextColor.fromHexString(hex);
            if (color != null) return color;
        }
        return NamedTextColor.WHITE;
    }
}