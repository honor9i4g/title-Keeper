package me.titlekeeper;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public record PlayerRecord(
        String title,
        String badgeChar,
        TextColor color,
        String description
) {
    private static final Key BADGE_FONT = Key.key("titlekeeper", "badges");
    public static final PlayerRecord DEFAULT =
            new PlayerRecord("Member", "\ue000", NamedTextColor.WHITE, "");

    public PlayerRecord {
        if (title == null || title.isBlank()) title = "Member";
        if (badgeChar == null) badgeChar = "\ue000";
        if (color == null) color = NamedTextColor.WHITE;
        if (description == null) description = "";
    }

    public Component badgeComponent() {
        if (badgeChar.isEmpty()) return Component.empty();
        return Component.text(badgeChar).font(BADGE_FONT).color(color);
    }

    public Component titleComponent() {
        Component c = Component.text(" [", NamedTextColor.DARK_GRAY)
                .append(Component.text(title, color))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY));

        if (!description.isEmpty()) {
            c = c.hoverEvent(Component.text(description, color));
        }
        return c;
    }

    public Component leftPrefix() {
        return badgeComponent().append(titleComponent());
    }
}