package me.titlekeeper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Minecraft inventory UI for the TitleKeeper 1.8.0-beta Sigma Control Panel. */
public final class TitleGui implements Listener {
    private final TitleKeeperPlugin plugin;
    private final Map<UUID, DraftTitle> drafts = new HashMap<>();
    private final Map<UUID, DraftField> pendingInput = new HashMap<>();

    public TitleGui(TitleKeeperPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMain(Player player) {
        MenuHolder holder = holder(Menu.MAIN, null);
        Inventory inventory = create(holder, Component.text("TitleKeeper | Sigma Control Panel", NamedTextColor.DARK_GRAY));
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        inventory.setItem(11, item(Material.BOOK, "Active Titles", NamedTextColor.GOLD,
                "See every configured title.", "Click to open the title list."));
        inventory.setItem(13, item(Material.ANVIL, "Create Title", NamedTextColor.GREEN,
                "Create a title with a name, permission and duration.", "Click to start a title draft."));
        inventory.setItem(15, item(Material.NAME_TAG, "Give a Title", NamedTextColor.AQUA,
                "Choose an online player, then grant a configured title."));
        inventory.setItem(31, item(Material.NETHER_STAR, "Sigma Settings", NamedTextColor.LIGHT_PURPLE,
                "TitleKeeper 1.8.0-beta", "Minecraft-style title control panel."));
        player.openInventory(inventory);
    }

    private void openActive(Player player) {
        MenuHolder holder = holder(Menu.ACTIVE, null);
        Inventory inventory = create(holder, Component.text("TitleKeeper | Active Titles", NamedTextColor.GOLD));
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        ConfigurationSection titles = plugin.getCustomTitlesConfig().getConfigurationSection("titles");
        if (titles == null || titles.getKeys(false).isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, "No titles configured", NamedTextColor.RED,
                    "Use Create Title to make the first one."));
        } else {
            int slot = 0;
            for (String key : titles.getKeys(false)) {
                if (slot >= 45) break;
                String path = "titles." + key;
                String title = plugin.getCustomTitlesConfig().getString(path + ".title", key);
                String permission = plugin.getCustomTitlesConfig().getString(path + ".permission", "<unset>");
                String duration = plugin.getCustomTitlesConfig().getString(path + ".duration", "perm");
                String description = plugin.getCustomTitlesConfig().getString(path + ".description", "No description.");
                inventory.setItem(slot++, item(Material.NAME_TAG, title,
                        color(plugin.getCustomTitlesConfig().getString(path + ".color", "#FFFFFF")),
                        "Key: " + key, "Permission: " + permission, "Duration: " + duration, description));
            }
        }
        inventory.setItem(49, item(Material.ARROW, "Back", NamedTextColor.YELLOW, "Return to the Sigma Control Panel."));
        player.openInventory(inventory);
    }

    private void openCreate(Player player) {
        DraftTitle draft = drafts.computeIfAbsent(player.getUniqueId(), ignored -> new DraftTitle());
        MenuHolder holder = holder(Menu.CREATE, null);
        Inventory inventory = create(holder, Component.text("TitleKeeper | Create Title", NamedTextColor.GREEN));
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        inventory.setItem(10, item(Material.NAME_TAG, "Title Name", NamedTextColor.GOLD,
                "Current: " + value(draft.title, "<click to name>"), "Click, then type the name in chat."));
        inventory.setItem(12, item(Material.TRIPWIRE_HOOK, "Permission Node", NamedTextColor.AQUA,
                "Current: " + value(draft.permission, "<automatic>"), "Example: titlekeeper.title.legend"));
        inventory.setItem(14, item(Material.CLOCK, "Time Override", NamedTextColor.LIGHT_PURPLE,
                "Current: " + draft.duration, "Use perm, 7d, 4h, 30m or 10s."));
        inventory.setItem(16, item(Material.FEATHER, "Badge Character", NamedTextColor.WHITE,
            "Current: " + (draft.badge.isEmpty() ? "None" : draft.badge), "Click to select a badge."));
        inventory.setItem(28, item(Material.LIME_DYE, "Title Colour", NamedTextColor.GREEN,
                "Current: " + draft.color, "Use a hex colour like #55FFFF."));
        inventory.setItem(30, item(Material.WRITABLE_BOOK, "Hover Description", NamedTextColor.YELLOW,
                "Current: " + value(draft.description, "<none>"), "Shown when players hover the title."));
        inventory.setItem(32, item(Material.DIAMOND_BLOCK, "Block Trigger", NamedTextColor.BLUE,
                "Current: " + value(draft.block, "<optional>"), "Enter a Minecraft block ID, or 'none'."));
        inventory.setItem(34, item(Material.EMERALD, "CREATE TITLE", NamedTextColor.GREEN,
                "Save this title into custom-titles.yml."));
        inventory.setItem(49, item(Material.ARROW, "Back", NamedTextColor.YELLOW, "Keep this draft and return."));
        player.openInventory(inventory);
    }

    private void openBadges(Player player, int page) {
        MenuHolder holder = holder(Menu.BADGES, null, page);
        Inventory inventory = create(holder, Component.text("TitleKeeper | Select Badge", NamedTextColor.AQUA));
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        int start = page * 45;
        int end = Math.min(start + 45, 100);
        for (int index = start; index < end; index++) {
            String badge = new String(Character.toChars(0xE000 + index));
            ItemStack icon = new ItemStack(Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(badge).font(Key.key("titlekeeper", "badges")));
            meta.lore(List.of(Component.text("Badge " + (index + 1), NamedTextColor.GRAY)));
            icon.setItemMeta(meta);
            inventory.setItem(index - start, icon);
        }
        if (page > 0) inventory.setItem(45, item(Material.ARROW, "Previous Page", NamedTextColor.YELLOW));
        inventory.setItem(48, item(Material.BARRIER, "Back to Draft", NamedTextColor.YELLOW));
        inventory.setItem(49, item(Material.MAP, "No Badge", NamedTextColor.RED, "Use no badge for this title."));
        if (end < 100) inventory.setItem(53, item(Material.ARROW, "Next Page", NamedTextColor.YELLOW));
        player.openInventory(inventory);
    }

    private void openPlayers(Player player) {
        MenuHolder holder = holder(Menu.PLAYERS, null);
        Inventory inventory = create(holder, Component.text("TitleKeeper | Choose Player", NamedTextColor.AQUA));
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            ItemStack head = item(Material.PLAYER_HEAD, target.getName(), NamedTextColor.WHITE, "Click to choose this player.");
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
        }
        inventory.setItem(49, item(Material.ARROW, "Back", NamedTextColor.YELLOW, "Return to the Sigma Control Panel."));
        player.openInventory(inventory);
    }

    private void openGive(Player player, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) { openPlayers(player); return; }
        MenuHolder holder = holder(Menu.GIVE, targetId);
        Inventory inventory = create(holder, Component.text("TitleKeeper | Give to " + target.getName(), NamedTextColor.AQUA));
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        ConfigurationSection titles = plugin.getCustomTitlesConfig().getConfigurationSection("titles");
        if (titles != null) {
            int slot = 0;
            for (String key : titles.getKeys(false)) {
                if (slot >= 45) break;
                String path = "titles." + key;
                String title = plugin.getCustomTitlesConfig().getString(path + ".title", key);
                String duration = plugin.getCustomTitlesConfig().getString(path + ".duration", "perm");
                inventory.setItem(slot++, item(Material.NAME_TAG, title,
                        color(plugin.getCustomTitlesConfig().getString(path + ".color", "#FFFFFF")),
                        "Give to " + target.getName(), "Duration: " + duration, "Click to grant."));
            }
        }
        inventory.setItem(49, item(Material.ARROW, "Back", NamedTextColor.YELLOW, "Choose another player."));
        player.openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();
        switch (holder.menu) {
            case MAIN -> { if (slot == 11) openActive(player); else if (slot == 13) openCreate(player); else if (slot == 15) openPlayers(player); }
            case ACTIVE -> { if (slot == 49) openMain(player); }
            case BADGES -> handleBadgeClick(player, holder, slot);
            case PLAYERS -> {
                if (slot == 49) { openMain(player); return; }
                ItemStack selected = event.getCurrentItem();
                if (selected == null || selected.getType() != Material.PLAYER_HEAD || !selected.hasItemMeta()) return;
                OfflinePlayer target = ((SkullMeta) selected.getItemMeta()).getOwningPlayer();
                if (target != null) openGive(player, target.getUniqueId());
            }
            case GIVE -> {
                if (slot == 49) { openPlayers(player); return; }
                ItemStack selected = event.getCurrentItem();
                if (selected == null || selected.getType() != Material.NAME_TAG || holder.target == null) return;
                Player target = Bukkit.getPlayer(holder.target);
                if (target == null) { player.sendMessage(Component.text("That player is no longer online.", NamedTextColor.RED)); openPlayers(player); return; }
                String titleKey = keyAt(slot);
                if (titleKey != null && plugin.grantConfiguredTitle(target, titleKey)) {
                    player.sendMessage(Component.text("Granted " + titleKey + " to " + target.getName() + ".", NamedTextColor.GREEN));
                    target.sendMessage(Component.text("You received the " + titleKey + " title!", NamedTextColor.GOLD));
                }
                openGive(player, holder.target);
            }
            case CREATE -> handleCreateClick(player, slot);
        }
    }

    private void handleCreateClick(Player player, int slot) {
        if (slot == 49) { openMain(player); return; }
        if (slot == 16) { openBadges(player, 0); return; }
        DraftField field = switch (slot) { case 10 -> DraftField.NAME; case 12 -> DraftField.PERMISSION; case 14 -> DraftField.DURATION; case 28 -> DraftField.COLOR; case 30 -> DraftField.DESCRIPTION; case 32 -> DraftField.BLOCK; default -> null; };
        if (field != null) {
            pendingInput.put(player.getUniqueId(), field);
            player.closeInventory();
            player.sendMessage(Component.text("TitleKeeper: type the " + field.label + " in chat. Type 'cancel' to stop.", NamedTextColor.YELLOW));
            return;
        }
        if (slot == 34) createTitle(player);
    }

    private void handleBadgeClick(Player player, MenuHolder holder, int slot) {
        DraftTitle draft = drafts.computeIfAbsent(player.getUniqueId(), ignored -> new DraftTitle());
        if (slot == 48) { openCreate(player); return; }
        if (slot == 49) { draft.badge = ""; openCreate(player); return; }
        if (slot == 45 && holder.page > 0) { openBadges(player, holder.page - 1); return; }
        if (slot == 53 && (holder.page + 1) * 45 < 100) { openBadges(player, holder.page + 1); return; }
        if (slot < 0 || slot >= 45) return;
        int index = holder.page * 45 + slot;
        if (index >= 100) return;
        draft.badge = new String(Character.toChars(0xE000 + index));
        openCreate(player);
    }

    public boolean isAwaitingInput(Player player) { return pendingInput.containsKey(player.getUniqueId()); }

    public void acceptInput(Player player, String raw) {
        DraftField field = pendingInput.remove(player.getUniqueId());
        if (field == null) return;
        String input = raw.trim();
        if (input.equalsIgnoreCase("cancel")) { player.sendMessage(Component.text("Input cancelled.", NamedTextColor.GRAY)); openCreate(player); return; }
        DraftTitle draft = drafts.computeIfAbsent(player.getUniqueId(), ignored -> new DraftTitle());
        switch (field) {
            case NAME -> draft.title = input;
            case PERMISSION -> draft.permission = input;
            case DURATION -> draft.duration = input.toLowerCase(Locale.ROOT);
            case COLOR -> draft.color = input.startsWith("#") ? input.toUpperCase(Locale.ROOT) : "#" + input.toUpperCase(Locale.ROOT);
            case DESCRIPTION -> draft.description = input;
            case BLOCK -> draft.block = input.equalsIgnoreCase("none") ? "" : input.toLowerCase(Locale.ROOT);
        }
        player.sendMessage(Component.text(field.label + " updated.", NamedTextColor.GREEN));
        openCreate(player);
    }

    private void createTitle(Player player) {
        DraftTitle draft = drafts.computeIfAbsent(player.getUniqueId(), ignored -> new DraftTitle());
        String key = key(draft.title);
        if (key.isEmpty()) { player.sendMessage(Component.text("Set a title name first.", NamedTextColor.RED)); return; }
        if (plugin.getCustomTitlesConfig().contains("titles." + key)) { player.sendMessage(Component.text("A title with that key already exists.", NamedTextColor.RED)); return; }
        if (color(draft.color) == null) { player.sendMessage(Component.text("Use a hex colour like #55FFFF.", NamedTextColor.RED)); return; }
        if (draft.permission.isBlank()) draft.permission = "titlekeeper.title." + key;
        String base = "titles." + key;
        plugin.getCustomTitlesConfig().set(base + ".permission", draft.permission);
        plugin.getCustomTitlesConfig().set(base + ".title", draft.title);
        plugin.getCustomTitlesConfig().set(base + ".badge", draft.badge);
        plugin.getCustomTitlesConfig().set(base + ".color", draft.color);
        plugin.getCustomTitlesConfig().set(base + ".duration", draft.duration);
        plugin.getCustomTitlesConfig().set(base + ".description", draft.description);
        if (!draft.block.isBlank()) {
            plugin.getCustomTitlesConfig().set(base + ".settings.when_block.block", draft.block);
            plugin.getCustomTitlesConfig().set(base + ".settings.when_block.touched", true);
            plugin.getCustomTitlesConfig().set(base + ".settings.when_block.picked", false);
        }
        plugin.saveCustomTitlesConfig();
        drafts.remove(player.getUniqueId());
        player.sendMessage(Component.text("Created '" + draft.title + "' with permission " + draft.permission + ".", NamedTextColor.GREEN));
        openActive(player);
    }

    private String keyAt(int slot) { ConfigurationSection titles = plugin.getCustomTitlesConfig().getConfigurationSection("titles"); return titles == null || slot < 0 || slot >= titles.getKeys(false).size() ? null : new ArrayList<>(titles.getKeys(false)).get(slot); }
    private MenuHolder holder(Menu menu, UUID target) { return new MenuHolder(menu, target, 0); }
    private MenuHolder holder(Menu menu, UUID target, int page) { return new MenuHolder(menu, target, page); }
    private Inventory create(MenuHolder holder, Component title) { Inventory inventory = Bukkit.createInventory(holder, 54, title); holder.inventory = inventory; return inventory; }
    private void fill(Inventory inventory, Material type, Component name) { for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, item(type, name)); }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String key(String text) { return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_").replaceAll("_+", "_").replaceAll("^_|_$", ""); }
    private TextColor color(String raw) { return raw != null && raw.startsWith("#") ? TextColor.fromHexString(raw) : null; }
    private ItemStack item(Material material, String name, TextColor color, String... lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.displayName(Component.text(name, color)); List<Component> lines = new ArrayList<>(); for (String line : lore) lines.add(Component.text(line, NamedTextColor.GRAY)); meta.lore(lines); item.setItemMeta(meta); return item; }
    private ItemStack item(Material material, Component name) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.displayName(name); item.setItemMeta(meta); return item; }
    private ItemStack item(Material material, String name, NamedTextColor color, String... lore) { return item(material, name, (TextColor) color, lore); }
    private enum Menu { MAIN, ACTIVE, CREATE, BADGES, PLAYERS, GIVE }
    private enum DraftField { NAME("title name"), PERMISSION("permission node"), DURATION("time override"), COLOR("hex colour"), DESCRIPTION("description"), BLOCK("block trigger"); final String label; DraftField(String label) { this.label = label; } }
    private static final class DraftTitle { String title = ""; String permission = ""; String duration = "perm"; String badge = "\ue000"; String color = "#55FFFF"; String description = ""; String block = ""; }
    private static final class MenuHolder implements InventoryHolder { final Menu menu; final UUID target; final int page; Inventory inventory; MenuHolder(Menu menu, UUID target, int page) { this.menu = menu; this.target = target; this.page = page; } @Override public Inventory getInventory() { return inventory; } }
}
