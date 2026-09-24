package com.earth2me.essentials.adventure;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.utils.NumberUtil;
import com.earth2me.essentials.utils.VersionUtil;
import net.ess3.provider.AbstractChatEvent;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class SpigotAdventureFacet implements AdventureFacet {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER_URLS;
    private static final MiniMessage MINI_MESSAGE_NO_TAGS;
    /**
     * Abaixo da 1.16 as mensagens vão como texto legado (§) direto no sendMessage.
     * No iSpigot 1.8 os facets do adventure-platform 4.4 falham por dentro e o Knob só registra com
     * -Dnet.kyori.adventure.debug=true: toda mensagem do Essentials sumia sem erro nenhum.
     * Sem hex nessas versões, o texto legado não perde cor — só clique e hover.
     */
    private static final boolean LEGACY_TEXT_ONLY = !VersionUtil.getServerBukkitVersion().isHigherThanOrEqualTo(VersionUtil.v1_16_1_R01);

    static {
        final LegacyComponentSerializer.Builder builder = LegacyComponentSerializer.builder()
                .flattener(ComponentFlattener.basic())
                .useUnusualXRepeatedCharacterHexFormat();
        if (VersionUtil.getServerBukkitVersion().isHigherThanOrEqualTo(VersionUtil.v1_16_1_R01)) {
            builder.hexColors();
        }
        LEGACY_SERIALIZER = builder.build();
        LEGACY_SERIALIZER_URLS = builder.extractUrls(AbstractChatEvent.URL_PATTERN).build();

        MINI_MESSAGE_NO_TAGS = MiniMessage.builder().strict(true).build();
    }

    private final Essentials ess;
    private final BukkitAudiences bukkitAudiences;
    private final MiniMessage miniMessageInstance;

    public SpigotAdventureFacet(final Essentials ess) {
        this.ess = ess.getSettings() == null ? null : ess;
        bukkitAudiences = BukkitAudiences.create(ess);

        miniMessageInstance = MiniMessage.builder()
                .tags(TagResolver.builder()
                        .resolvers(TagResolver.standard())
                        .resolver(TagResolver.resolver("primary", supplyTag(true)))
                        .resolver(TagResolver.resolver("secondary", supplyTag(false)))
                        .build())
                .build();
    }

    private Tag supplyTag(final boolean primary) {
        final String color = primary ? (ess != null ? ess.getSettings().getPrimaryColor() : "gold") : (ess != null ? ess.getSettings().getSecondaryColor() : "red");

        final TextColor textColor;
        if (color.startsWith("#") && color.length() == 7 && NumberUtil.isHexadecimal(color.substring(1))) {
            textColor = TextColor.color(Color.fromRGB(Integer.decode(color)).asRGB());
        } else if (color.length() == 1) {
            textColor = AdventureUtil.fromChar(color.charAt(0));
        } else {
            textColor = NamedTextColor.NAMES.value(color.toLowerCase(Locale.ENGLISH));
        }

        return ess != null && textColor != null ? Tag.styling(textColor) : Tag.styling(primary ? NamedTextColor.GOLD : NamedTextColor.RED);
    }

    @Override
    public ComponentHolder deserializeMiniMessage(String message) {
        return new ComponentHolder(miniMessageInstance.deserialize(message));
    }

    @Override
    public String legacyToMini(String message) {
        return legacyToMini(message, false);
    }

    @Override
    public void send(CommandSender sender, ComponentHolder component) {
        if (LEGACY_TEXT_ONLY) {
            sendLegacy(sender, (Component) component.getComponent());
            return;
        }
        bukkitAudiences.sender(sender).sendMessage((Component) component.getComponent());
    }

    @Override
    public void send(Player player, ComponentHolder component) {
        if (LEGACY_TEXT_ONLY) {
            sendLegacy(player, (Component) component.getComponent());
            return;
        }
        bukkitAudiences.player(player).sendMessage((Component) component.getComponent());
    }

    private void sendLegacy(final CommandSender sender, final Component component) {
        final String legacy = LEGACY_SERIALIZER.serialize(component);
        if (!legacy.isEmpty()) {
            sender.sendMessage(legacy);
        }
    }

    @Override
    public String adventureToLegacy(ComponentHolder component) {
        return adventureToLegacy((Component) component.getComponent());
    }

    private String adventureToLegacy(Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }

    @Override
    public ComponentHolder legacyToAdventure(String message) {
        return new ComponentHolder(LEGACY_SERIALIZER.deserialize(message));
    }

    @Override
    public ComponentHolder text(String message) {
        return new ComponentHolder(Component.text(message));
    }

    @Override
    public String legacyToMini(String message, boolean useCustomTags) {
        final Component deserializedText = LEGACY_SERIALIZER.deserialize(message);
        if (useCustomTags) {
            return miniMessageInstance.serialize(deserializedText);
        } else {
            return MINI_MESSAGE_NO_TAGS.serialize(deserializedText);
        }
    }

    @Override
    public String miniToLegacy(String message) {
        return adventureToLegacy(miniMessageInstance.deserialize(message));
    }

    @Override
    public String stripTags(String input) {
        return miniMessageInstance.stripTags(input);
    }

    @Override
    public String legacyToMiniWithUrls(String message) {
        return miniMessageInstance.serialize(LEGACY_SERIALIZER_URLS.deserialize(message));
    }

    @Override
    public String escapeTags(String input) {
        return miniMessageInstance.escapeTags(input);
    }

    @Override
    public ComponentHolder append(ComponentHolder base, ComponentHolder... addition) {
        Component baseComponent = (Component) base.getComponent();
        for (ComponentHolder holder : addition) {
            final Component additionComponent = (Component) holder.getComponent();
            baseComponent = baseComponent.append(additionComponent);
        }
        return new ComponentHolder(baseComponent);
    }

    @Override
    public void close() {
        bukkitAudiences.close();
    }
}
