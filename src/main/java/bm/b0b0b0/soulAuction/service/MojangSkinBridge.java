package bm.b0b0b0.soulAuction.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public final class MojangSkinBridge {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private MojangSkinBridge() {
    }

    public static Optional<SkinTexture> fetch(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }
        try {
            String uuid = fetchUuid(playerName.trim());
            if (uuid == null || uuid.isBlank()) {
                return Optional.empty();
            }
            return fetchTextures(uuid);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String fetchUuid(String playerName) throws Exception {
        URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/" + encodeName(playerName));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
            return null;
        }
        JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!profile.has("id")) {
            return null;
        }
        return profile.get("id").getAsString();
    }

    private static Optional<SkinTexture> fetchTextures(String uuid) throws Exception {
        URI uri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/"
                + uuid
                + "?unsigned=false");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
            return Optional.empty();
        }
        JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!profile.has("properties") || !profile.get("properties").isJsonArray()) {
            return Optional.empty();
        }
        JsonArray properties = profile.getAsJsonArray("properties");
        for (int index = 0; index < properties.size(); index++) {
            if (!properties.get(index).isJsonObject()) {
                continue;
            }
            JsonObject property = properties.get(index).getAsJsonObject();
            if (!property.has("name") || !"textures".equals(property.get("name").getAsString())) {
                continue;
            }
            String value = property.has("value") ? property.get("value").getAsString() : "";
            String signature = property.has("signature") ? property.get("signature").getAsString() : "";
            if (value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new SkinTexture(value, signature));
        }
        return Optional.empty();
    }

    private static String encodeName(String playerName) {
        return URI.create("https://local/" + playerName).getRawPath().substring(1);
    }
}
