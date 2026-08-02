package bm.b0b0b0.soulAuction.integration.worldguard;

import bm.b0b0b0.soulAuction.model.region.RegionRef;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class WorldGuardBridge {

    private final boolean pluginPresent;

    public WorldGuardBridge() {
        this.pluginPresent = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public boolean available() {
        return pluginPresent;
    }

    public List<RegionRef> listOwnedRegions(UUID playerId) {
        List<RegionRef> owned = new ArrayList<>();
        if (!pluginPresent) {
            return owned;
        }
        try {
            Object container = regionContainer();
            if (container == null) {
                return owned;
            }
            for (World world : Bukkit.getWorlds()) {
                Object manager = regionManager(container, world);
                if (manager == null) {
                    continue;
                }
                Map<?, ?> regions = invokeMap(manager, "getRegions");
                if (regions == null) {
                    continue;
                }
                for (Map.Entry<?, ?> entry : regions.entrySet()) {
                    Object region = entry.getValue();
                    if (region == null || !isOwner(region, playerId)) {
                        continue;
                    }
                    owned.add(new RegionRef(world.getName(), String.valueOf(entry.getKey())));
                }
            }
        } catch (ReflectiveOperationException exception) {
            return List.of();
        }
        owned.sort(Comparator.comparing(RegionRef::worldName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RegionRef::regionId, String.CASE_INSENSITIVE_ORDER));
        return owned;
    }

    public List<String> tabCompleteOwnedRegions(Player player, String partial) {
        String needle = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (RegionRef ref : listOwnedRegions(player.getUniqueId())) {
            String key = ref.displayKey();
            if (needle.isEmpty() || key.toLowerCase(Locale.ROOT).startsWith(needle)) {
                suggestions.add(key);
            }
        }
        return suggestions;
    }

    public boolean regionExists(RegionRef ref) {
        return resolveRegion(ref) != null;
    }

    public boolean isOwner(UUID playerId, RegionRef ref) {
        Object region = resolveRegion(ref);
        if (region == null) {
            return false;
        }
        try {
            return isOwner(region, playerId);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public boolean transferOwnership(RegionRef ref, UUID from, UUID to) {
        Object region = resolveRegion(ref);
        if (region == null) {
            return false;
        }
        try {
            Object owners = invoke(region, "getOwners");
            if (owners == null || !containsPlayer(owners, from)) {
                return false;
            }
            invoke(owners, "removePlayer", from);
            invoke(owners, "addPlayer", to);
            Object manager = regionManager(regionContainer(), worldByName(ref.worldName()));
            if (manager == null) {
                return false;
            }
            invoke(manager, "saveChanges");
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private Object resolveRegion(RegionRef ref) {
        if (ref == null || !pluginPresent) {
            return null;
        }
        try {
            Object manager = regionManager(regionContainer(), worldByName(ref.worldName()));
            if (manager == null) {
                return null;
            }
            Object region = invoke(manager, "getRegion", ref.regionId());
            if (region != null) {
                return region;
            }
            Map<?, ?> regions = invokeMap(manager, "getRegions");
            if (regions == null) {
                return null;
            }
            for (Map.Entry<?, ?> entry : regions.entrySet()) {
                if (String.valueOf(entry.getKey()).equalsIgnoreCase(ref.regionId())) {
                    return entry.getValue();
                }
            }
            return null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Object regionContainer() throws ReflectiveOperationException {
        Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
        Object instance = invokeStatic(worldGuardClass, "getInstance");
        Object platform = invoke(instance, "getPlatform");
        return invoke(platform, "getRegionContainer");
    }

    private Object regionManager(Object container, World world) throws ReflectiveOperationException {
        if (container == null || world == null) {
            return null;
        }
        Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Object adaptedWorld = invokeStatic(adapterClass, "adapt", world);
        return invoke(container, "get", adaptedWorld);
    }

    private World worldByName(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getName().equalsIgnoreCase(worldName)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isOwner(Object region, UUID playerId) throws ReflectiveOperationException {
        Object owners = invoke(region, "getOwners");
        return owners != null && containsPlayer(owners, playerId);
    }

    private boolean containsPlayer(Object owners, UUID playerId) throws ReflectiveOperationException {
        Object result = invoke(owners, "contains", playerId);
        return result instanceof Boolean bool && bool;
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> invokeMap(Object target, String methodName) throws ReflectiveOperationException {
        Object result = invoke(target, methodName);
        if (result instanceof Map<?, ?> map) {
            return map;
        }
        return null;
    }

    private Object invokeStatic(Class<?> type, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(type, methodName, args);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, args);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private Method findMethod(Class<?> type, String methodName, Object... args) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            if (parametersMatch(method.getParameterTypes(), args)) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + methodName);
    }

    private boolean parametersMatch(Class<?>[] parameterTypes, Object[] args) {
        for (int index = 0; index < parameterTypes.length; index++) {
            Object arg = args[index];
            if (arg == null) {
                continue;
            }
            Class<?> parameterType = parameterTypes[index];
            if (!parameterType.isAssignableFrom(arg.getClass())) {
                if (parameterType.isPrimitive()) {
                    continue;
                }
                if (parameterType == UUID.class && arg instanceof UUID) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }
}
