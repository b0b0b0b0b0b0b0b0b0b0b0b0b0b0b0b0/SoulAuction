package bm.b0b0b0.soulAuction.config;

import java.nio.file.Path;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class SerializedConfigReloader {

    private SerializedConfigReloader() {
    }

    public static <T extends YamlSerializable> T reload(T settings, Path path) {
        settings.reload(path);
        return settings;
    }
}
