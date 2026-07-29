package bm.b0b0b0.soulAuction.lang;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageService {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration messages;
    private Supplier<Boolean> respectDisabledMessages = () -> true;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    public void reload() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void setRespectDisabledMessages(Supplier<Boolean> respectDisabledMessages) {
        if (respectDisabledMessages != null) {
            this.respectDisabledMessages = respectDisabledMessages;
        }
    }

    public Component component(String key) {
        return component(key, Collections.emptyMap());
    }

    public Component component(String key, Map<String, String> placeholders) {
        if (isDisabled(key)) {
            return Component.empty();
        }
        String value = template(key);
        value = value.replace("{prefix}", template("prefix"));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return miniMessage.deserialize(value);
    }

    public List<Component> components(String key) {
        return components(key, Collections.emptyMap());
    }

    public List<Component> components(String key, Map<String, String> placeholders) {
        List<String> lines = messages.getStringList(key);
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<Component> output = new ArrayList<>(lines.size());
        for (String line : lines) {
            String formatted = line.replace("{prefix}", template("prefix"));
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            output.add(miniMessage.deserialize(formatted));
        }
        return output;
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (!(sender instanceof Audience audience)) {
            return;
        }
        send(audience, key, placeholders);
    }

    public void send(Player player, String key) {
        send((Audience) player, key, Collections.emptyMap());
    }

    public void send(Player player, String key, Map<String, String> placeholders) {
        send((Audience) player, key, placeholders);
    }

    public void send(Audience audience, String key, Map<String, String> placeholders) {
        if (isDisabled(key)) {
            return;
        }
        List<Component> lines = components(key, placeholders);
        if (!lines.isEmpty()) {
            for (Component line : lines) {
                audience.sendMessage(line);
            }
            return;
        }
        audience.sendMessage(component(key, placeholders));
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        if (isDisabled(key)) {
            return;
        }
        List<Component> lines = components(key, placeholders);
        if (!lines.isEmpty()) {
            for (Component line : lines) {
                plugin.getServer().broadcast(line);
            }
            return;
        }
        plugin.getServer().broadcast(component(key, placeholders));
    }

    public List<Component> componentsFromTemplates(List<String> templates, Map<String, String> placeholders) {
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }
        List<Component> output = new ArrayList<>(templates.size());
        for (String line : templates) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String formatted = line.replace("{prefix}", template("prefix"));
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            output.add(miniMessage.deserialize(formatted));
        }
        return output;
    }

    public String raw(String key) {
        return template(key);
    }

    private String template(String key) {
        return messages.getString(key, key);
    }

    private boolean isDisabled(String key) {
        if (!Boolean.TRUE.equals(respectDisabledMessages.get())) {
            return false;
        }
        return messages.getStringList("disabled-messages").stream().anyMatch(entry -> entry.equalsIgnoreCase(key));
    }
}
