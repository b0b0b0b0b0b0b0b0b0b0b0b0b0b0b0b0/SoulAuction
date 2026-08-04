package bm.b0b0b0.soulAuction.integration.worldguard;

import bm.b0b0b0.soulAuction.model.region.RegionBounds;
import bm.b0b0b0.soulAuction.model.region.RegionInfo;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

    public List<String> tabCompleteOwnedRegions(Player player, String partial, boolean hideWorldName) {
        String needle = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        for (RegionRef ref : listOwnedRegions(player.getUniqueId())) {
            String key = hideWorldName ? ref.regionId() : ref.displayKey();
            if (needle.isEmpty() || key.toLowerCase(Locale.ROOT).startsWith(needle)) {
                suggestions.add(key);
            }
        }
        return new ArrayList<>(suggestions);
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

    public boolean isInAllowedTradeRegions(Player player, List<String> allowedEntries) {
        if (!pluginPresent || allowedEntries == null || allowedEntries.isEmpty()) {
            return true;
        }
        if (player == null) {
            return false;
        }
        List<String> atLocation = regionIdsAtLocation(player);
        if (atLocation.isEmpty()) {
            return false;
        }
        String worldName = player.getWorld().getName();
        for (String entry : allowedEntries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            RegionRef allowed = parseTradeRegionEntry(entry.trim(), worldName);
            if (allowed == null) {
                continue;
            }
            if (!allowed.worldName().equalsIgnoreCase(worldName)) {
                continue;
            }
            for (String regionId : atLocation) {
                if (regionId.equalsIgnoreCase(allowed.regionId())) {
                    return true;
                }
            }
        }
        return false;
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

    public RegionBounds regionBounds(RegionRef ref) {
        Object region = resolveRegion(ref);
        if (region == null) {
            return null;
        }
        try {
            Object min = invoke(region, "getMinimumPoint");
            Object max = invoke(region, "getMaximumPoint");
            if (min == null || max == null) {
                return null;
            }
            return new RegionBounds(
                    blockX(min),
                    blockY(min),
                    blockZ(min),
                    blockX(max),
                    blockY(max),
                    blockZ(max)
            );
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    public RegionInfo regionInfo(RegionRef ref) {
        Object region = resolveRegion(ref);
        if (region == null) {
            return RegionInfo.empty();
        }
        RegionBounds bounds = regionBounds(ref);
        if (bounds == null) {
            return RegionInfo.empty();
        }
        try {
            long volume = resolveVolume(region, bounds);
            int priority = intValue(invoke(region, "getPriority"));
            String parentId = parentId(region);
            int ownersCount = domainSize(invoke(region, "getOwners"));
            int membersCount = domainSize(invoke(region, "getMembers"));
            String flagsSummary = formatFlags(invokeMap(region, "getFlags"));
            return new RegionInfo(bounds, volume, priority, parentId, ownersCount, membersCount, flagsSummary);
        } catch (ReflectiveOperationException exception) {
            return new RegionInfo(bounds, bounds.blockVolume(), 0, "", 0, 0, "");
        }
    }

    private long resolveVolume(Object region, RegionBounds bounds) throws ReflectiveOperationException {
        Object volume = invoke(region, "getVolume");
        if (volume instanceof Number number && number.longValue() > 0L) {
            return number.longValue();
        }
        return bounds.blockVolume();
    }

    private static String parentId(Object region) throws ReflectiveOperationException {
        Object parent = region.getClass().getMethod("getParent").invoke(region);
        if (parent == null) {
            return "";
        }
        Object id = parent.getClass().getMethod("getId").invoke(parent);
        return id == null ? "" : String.valueOf(id);
    }

    private static int domainSize(Object domain) throws ReflectiveOperationException {
        if (domain == null) {
            return 0;
        }
        Object size = domain.getClass().getMethod("size").invoke(domain);
        return size instanceof Number number ? number.intValue() : 0;
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String formatFlags(Map<?, ?> flags) {
        if (flags == null || flags.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<?, ?> entry : flags.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String name = flagName(entry.getKey());
            if (name.isBlank()) {
                continue;
            }
            String value = flagValue(entry.getValue());
            parts.add(name.toLowerCase(Locale.ROOT) + "=" + value);
        }
        parts.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", parts);
    }

    private static String flagName(Object flag) {
        try {
            Object name = flag.getClass().getMethod("getName").invoke(flag);
            return name == null ? "" : String.valueOf(name);
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    private static String flagValue(Object value) {
        if (value == null) {
            return "?";
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name().toLowerCase(Locale.ROOT);
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT);
    }

    public Optional<Location> findSafeVisitLocation(RegionRef ref) {
        RegionBounds bounds = regionBounds(ref);
        if (bounds == null) {
            return Optional.empty();
        }
        World world = worldByName(ref.worldName());
        if (world == null) {
            return Optional.empty();
        }
        int checks = 0;
        int maxChecks = 512;
        for (int y = bounds.maxY(); y >= bounds.minY() && checks < maxChecks; y--) {
            for (int x = bounds.centerX(); x <= bounds.maxX() && checks < maxChecks; x += 2) {
                checks++;
                Optional<Location> found = safeStand(world, x, y, bounds.centerZ(), bounds);
                if (found.isPresent()) {
                    return found;
                }
            }
            for (int x = bounds.centerX() - 2; x >= bounds.minX() && checks < maxChecks; x -= 2) {
                checks++;
                Optional<Location> found = safeStand(world, x, y, bounds.centerZ(), bounds);
                if (found.isPresent()) {
                    return found;
                }
            }
            for (int z = bounds.centerZ() + 2; z <= bounds.maxZ() && checks < maxChecks; z += 2) {
                checks++;
                Optional<Location> found = safeStand(world, bounds.centerX(), y, z, bounds);
                if (found.isPresent()) {
                    return found;
                }
            }
            for (int z = bounds.centerZ() - 2; z >= bounds.minZ() && checks < maxChecks; z -= 2) {
                checks++;
                Optional<Location> found = safeStand(world, bounds.centerX(), y, z, bounds);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Location> safeStand(World world, int x, int y, int z, RegionBounds bounds) {
        if (!bounds.contains(x, y, z)) {
            return Optional.empty();
        }
        Location feet = new Location(world, x, y, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        Block lower = feet.getBlock();
        Block upper = world.getBlockAt(x, y + 1, z);
        if (!ground.getType().isSolid() || isDangerous(ground.getType())) {
            return Optional.empty();
        }
        if (!isPassable(lower.getType()) || !isPassable(upper.getType())) {
            return Optional.empty();
        }
        if (isDangerous(lower.getType()) || isDangerous(upper.getType())) {
            return Optional.empty();
        }
        Block aboveHead = world.getBlockAt(x, y + 2, z);
        if (!isPassable(aboveHead.getType()) || isDangerous(aboveHead.getType())) {
            return Optional.empty();
        }
        return Optional.of(feet.add(0.5D, 0.0D, 0.5D));
    }

    private static boolean isPassable(Material material) {
        return material.isAir() || !material.isSolid();
    }

    private static boolean isDangerous(Material material) {
        return material == Material.LAVA
                || material == Material.WATER
                || material == Material.FIRE
                || material == Material.SOUL_FIRE
                || material == Material.MAGMA_BLOCK
                || material == Material.CACTUS
                || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE
                || material == Material.SWEET_BERRY_BUSH
                || material == Material.WITHER_ROSE
                || material == Material.POWDER_SNOW;
    }

    private static int blockX(Object vector) throws ReflectiveOperationException {
        Object value = vector.getClass().getMethod("getBlockX").invoke(vector);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int blockY(Object vector) throws ReflectiveOperationException {
        Object value = vector.getClass().getMethod("getBlockY").invoke(vector);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int blockZ(Object vector) throws ReflectiveOperationException {
        Object value = vector.getClass().getMethod("getBlockZ").invoke(vector);
        return value instanceof Number number ? number.intValue() : 0;
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

    private List<String> regionIdsAtLocation(Player player) {
        if (!pluginPresent || player == null) {
            return List.of();
        }
        try {
            Location location = player.getLocation();
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object blockVector = invokeStatic(adapterClass, "asBlockVector", location);
            Object container = regionContainer();
            if (container == null) {
                return List.of();
            }
            Object query = invoke(container, "createQuery");
            if (query == null) {
                return List.of();
            }
            Object applicableSet = invoke(query, "getApplicableRegions", blockVector);
            if (applicableSet == null) {
                return List.of();
            }
            Object regions = invoke(applicableSet, "getRegions");
            if (!(regions instanceof Iterable<?> iterable)) {
                return List.of();
            }
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (Object region : iterable) {
                if (region == null) {
                    continue;
                }
                Object id = invoke(region, "getId");
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            }
            return List.copyOf(ids);
        } catch (ReflectiveOperationException exception) {
            return List.of();
        }
    }

    private RegionRef parseTradeRegionEntry(String entry, String currentWorld) {
        if (entry == null || entry.isBlank() || currentWorld == null || currentWorld.isBlank()) {
            return null;
        }
        int colon = entry.indexOf(':');
        if (colon > 0 && colon < entry.length() - 1) {
            return new RegionRef(entry.substring(0, colon), entry.substring(colon + 1));
        }
        return new RegionRef(currentWorld, entry);
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
