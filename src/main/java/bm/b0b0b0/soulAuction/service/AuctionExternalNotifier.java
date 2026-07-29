package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.MinecraftAvatarUrls;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionExternalNotifier {

    private final JavaPlugin plugin;
    private final Supplier<AuctionSettings> settingsSupplier;
    private final Gson gson;
    private final HttpClient httpClient;

    public AuctionExternalNotifier(JavaPlugin plugin, Supplier<AuctionSettings> settingsSupplier) {
        this.plugin = plugin;
        this.settingsSupplier = settingsSupplier;
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public void listingCreated(AuctionListing listing, ItemStack item) {
        AuctionSettings.NotificationsSettings settings = settingsSupplier.get().notifications;
        if (!settings.notifyListed) {
            return;
        }
        if (listing.price() < settings.minPrice) {
            return;
        }
        String itemLine = describeItem(item);
        String body = "Новый лот • " + listing.auctionId() + "\n"
                + "Продавец: " + listing.sellerName() + "\n"
                + "Цена: " + listing.price() + "\n"
                + "Предмет: " + itemLine;
        dispatch(
                "LISTED",
                body,
                "Новый лот на аукционе",
                listing.auctionId(),
                listing.sellerId(),
                listing.sellerName(),
                null,
                null,
                listing.price(),
                itemLine,
                item == null || item.isEmpty() ? null : item.getType().name()
        );
    }

    public void sold(AuctionListing listing, String buyerName, UUID buyerId, String priceFormatted) {
        AuctionSettings.NotificationsSettings settings = settingsSupplier.get().notifications;
        if (!settings.notifySold) {
            return;
        }
        if (listing.price() < settings.minPrice) {
            return;
        }
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        String itemLine = describeItem(item);
        String body = "Продажа • " + listing.auctionId() + "\n"
                + "Продавец: " + listing.sellerName() + "\n"
                + "Покупатель: " + buyerName + "\n"
                + "Цена: " + priceFormatted + "\n"
                + "Предмет: " + itemLine;
        dispatch(
                "SOLD",
                body,
                "Сделка на аукционе",
                listing.auctionId(),
                listing.sellerId(),
                listing.sellerName(),
                buyerId,
                buyerName,
                listing.price(),
                itemLine,
                item.getType().name()
        );
    }

    public void expired(AuctionListing listing) {
        AuctionSettings.NotificationsSettings settings = settingsSupplier.get().notifications;
        if (!settings.notifyExpired) {
            return;
        }
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        String itemLine = describeItem(item);
        String body = "Истёк срок • " + listing.auctionId() + "\n"
                + "Продавец: " + listing.sellerName() + "\n"
                + "Цена: " + listing.price() + "\n"
                + "Предмет: " + itemLine;
        dispatch(
                "EXPIRED",
                body,
                "Лот просрочен",
                listing.auctionId(),
                listing.sellerId(),
                listing.sellerName(),
                null,
                null,
                listing.price(),
                itemLine,
                item.getType().name()
        );
    }

    private void dispatch(
            String eventType,
            String plainText,
            String discordTitle,
            String auctionId,
            UUID sellerId,
            String sellerName,
            UUID buyerId,
            String buyerName,
            int priceValue,
            String itemLine,
            String itemMaterial
    ) {
        PluginSchedulers.runAsync(plugin, () -> {
            AuctionSettings settings = settingsSupplier.get();
            sendDiscord(
                    settings.notifications.discord,
                    discordTitle,
                    auctionId,
                    sellerId,
                    sellerName,
                    buyerId,
                    buyerName,
                    priceValue,
                    itemLine,
                    eventType,
                    itemMaterial
            );
            sendTelegram(settings.notifications.telegram, plainText);
        });
    }

    private void sendDiscord(
            AuctionSettings.DiscordNotificationSettings discord,
            String title,
            String auctionId,
            UUID sellerId,
            String sellerName,
            UUID buyerId,
            String buyerName,
            int price,
            String itemLine,
            String eventType,
            String itemMaterial
    ) {
        if (!discord.enabled || discord.webhookUrl == null || discord.webhookUrl.isBlank()) {
            return;
        }
        try {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", title);
            embed.addProperty("color", colorFor(eventType));
            if (discord.showPlayerAvatars) {
                applyAvatars(embed, discord, sellerId, sellerName, buyerId, buyerName);
            }
            JsonArray fields = new JsonArray();
            fields.add(field("Аукцион", auctionId, true));
            fields.add(field("Продавец", sellerName, true));
            if (buyerName != null && !buyerName.isBlank()) {
                fields.add(field("Покупатель", buyerName, true));
            }
            fields.add(field("Цена", String.valueOf(price), true));
            fields.add(field("Предмет", truncate(itemLine, 256), false));
            embed.add("fields", fields);
            if (itemMaterial != null && !itemMaterial.isBlank()) {
                JsonObject image = new JsonObject();
                image.addProperty("url", "https://mc-heads.net/minecraft/item/" + itemMaterial.toLowerCase());
                embed.add("image", image);
            }
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            JsonObject payload = new JsonObject();
            payload.add("embeds", embeds);
            postJson(discord.webhookUrl.trim(), gson.toJson(payload));
        } catch (Exception exception) {
            plugin.getLogger().warning("Discord notify failed: " + exception.getMessage());
        }
    }

    private void applyAvatars(
            JsonObject embed,
            AuctionSettings.DiscordNotificationSettings discord,
            UUID sellerId,
            String sellerName,
            UUID buyerId,
            String buyerName
    ) {
        String sellerAvatar = MinecraftAvatarUrls.resolve(discord, sellerId, sellerName);
        if (sellerName != null && !sellerName.isBlank()) {
            JsonObject author = new JsonObject();
            author.addProperty("name", sellerName);
            if (sellerAvatar != null) {
                author.addProperty("icon_url", sellerAvatar);
            }
            embed.add("author", author);
        }
        String thumbnailUrl = null;
        if (buyerId != null || (buyerName != null && !buyerName.isBlank())) {
            thumbnailUrl = MinecraftAvatarUrls.resolve(discord, buyerId, buyerName);
        } else if (sellerAvatar != null) {
            thumbnailUrl = sellerAvatar;
        }
        if (thumbnailUrl != null) {
            JsonObject thumbnail = new JsonObject();
            thumbnail.addProperty("url", thumbnailUrl);
            embed.add("thumbnail", thumbnail);
        }
    }

    private void sendTelegram(AuctionSettings.TelegramNotificationSettings telegram, String text) {
        if (!telegram.enabled || telegram.botToken == null || telegram.botToken.isBlank()) {
            return;
        }
        if (telegram.chatId == null || telegram.chatId.isBlank()) {
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + telegram.botToken.trim() + "/sendMessage";
            JsonObject payload = new JsonObject();
            payload.addProperty("chat_id", telegram.chatId.trim());
            payload.addProperty("text", truncate(text, 3900));
            payload.addProperty("disable_web_page_preview", true);
            postJson(url, gson.toJson(payload));
        } catch (Exception exception) {
            plugin.getLogger().warning("Telegram notify failed: " + exception.getMessage());
        }
    }

    private void postJson(String url, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            plugin.getLogger().warning("External notify HTTP " + response.statusCode());
        }
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value == null || value.isBlank() ? "-" : truncate(value, 128));
        field.addProperty("inline", inline);
        return field;
    }

    private static int colorFor(String eventType) {
        return switch (eventType) {
            case "SOLD" -> 0x22C55E;
            case "LISTED" -> 0x8B5CF6;
            case "EXPIRED" -> 0xF97316;
            default -> 0x6B7280;
        };
    }

    private static String describeItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return "unknown";
        }
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        if (item.getAmount() > 1) {
            return item.getAmount() + "x " + name;
        }
        return name;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }
}
