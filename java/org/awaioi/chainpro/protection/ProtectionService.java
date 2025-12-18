package org.awaioi.chainpro.protection;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ProtectionService {

    private final List<Checker> checkers;

    public ProtectionService(Plugin plugin) {
        this.checkers = buildCheckers(plugin.getServer().getPluginManager());
    }

    public boolean canBreak(Player player, Block block) {
        for (Checker checker : checkers) {
            if (!checker.canBreak(player, block)) {
                return false;
            }
        }
        return true;
    }

    private static List<Checker> buildCheckers(PluginManager pluginManager) {
        List<Checker> result = new ArrayList<>();

        Plugin worldGuard = pluginManager.getPlugin("WorldGuard");
        if (worldGuard != null && worldGuard.isEnabled()) {
            Checker checker = createWorldGuardChecker(worldGuard);
            if (checker != null) result.add(checker);
        }

        Plugin griefPrevention = pluginManager.getPlugin("GriefPrevention");
        if (griefPrevention != null && griefPrevention.isEnabled()) {
            Checker checker = createGriefPreventionChecker(griefPrevention);
            if (checker != null) result.add(checker);
        }

        Plugin residence = pluginManager.getPlugin("Residence");
        if (residence != null && residence.isEnabled()) {
            Checker checker = createResidenceChecker();
            if (checker != null) result.add(checker);
        }

        return result;
    }

    private static Checker createWorldGuardChecker(Plugin worldGuardPluginInstance) {
        try {
            Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            if (!wgPluginClass.isInstance(worldGuardPluginInstance)) return null;
            Method canBuild = wgPluginClass.getMethod("canBuild", Player.class, Location.class);

            return (player, block) -> {
                try {
                    Object allowed = canBuild.invoke(worldGuardPluginInstance, player, block.getLocation());
                    return allowed instanceof Boolean && (Boolean) allowed;
                } catch (Throwable ignored) {
                    return true;
                }
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Checker createGriefPreventionChecker(Plugin griefPreventionPluginInstance) {
        try {
            Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            FieldAccessor instanceField = FieldAccessor.tryCreate(gpClass, "instance");
            if (instanceField == null) return null;
            Object gpInstance = instanceField.get(null);
            if (gpInstance == null) return null;

            Method allowBreak4;
            try {
                allowBreak4 = gpClass.getMethod("allowBreak", Player.class, Block.class, Location.class, BlockBreakEvent.class);
            } catch (NoSuchMethodException e) {
                allowBreak4 = null;
            }

            Method allowBreak3;
            try {
                allowBreak3 = gpClass.getMethod("allowBreak", Player.class, Block.class, Location.class);
            } catch (NoSuchMethodException e) {
                allowBreak3 = null;
            }

            Method allowBreak = allowBreak4 != null ? allowBreak4 : allowBreak3;
            if (allowBreak == null) return null;

            return (player, block) -> {
                try {
                    Object reason;
                    if (allowBreak.getParameterCount() == 4) {
                        reason = allowBreak.invoke(gpInstance, player, block, block.getLocation(), new BlockBreakEvent(block, player));
                    } else {
                        reason = allowBreak.invoke(gpInstance, player, block, block.getLocation());
                    }
                    return reason == null;
                } catch (Throwable ignored) {
                    return true;
                }
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Checker createResidenceChecker() {
        try {
            Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.Residence");
            Method permsByLoc = resolveMethod(residenceClass, "getPermsByLoc", Location.class);

            if (permsByLoc != null) {
                final Method getPermsByLoc = permsByLoc;
                return (player, block) -> {
                    try {
                        Object perms = getPermsByLoc.invoke(null, block.getLocation());
                        if (perms == null) return true;
                        Method playerHas = findPlayerHas(perms.getClass());
                        if (playerHas == null) return true;
                        Object allowed = playerHas.invoke(perms, player.getName(), "build", true);
                        return allowed instanceof Boolean && (Boolean) allowed;
                    } catch (Throwable ignored) {
                        return true;
                    }
                };
            }

            Method getResidenceManager = resolveMethod(residenceClass, "getResidenceManager");
            if (getResidenceManager == null) return null;
            final Method finalGetResidenceManager = getResidenceManager;

            return (player, block) -> {
                try {
                    Object manager = finalGetResidenceManager.invoke(null);
                    if (manager == null) return true;
                    Method getByLoc;
                    try {
                        getByLoc = manager.getClass().getMethod("getByLoc", Location.class);
                    } catch (NoSuchMethodException e) {
                        return true;
                    }
                    Object res = getByLoc.invoke(manager, block.getLocation());
                    if (res == null) return true;
                    Method getPermisssions;
                    try {
                        getPermisssions = res.getClass().getMethod("getPermisssions");
                    } catch (NoSuchMethodException e) {
                        getPermisssions = null;
                    }
                    Object perms = getPermisssions != null ? getPermisssions.invoke(res) : null;
                    if (perms == null) {
                        Method getPermissions = res.getClass().getMethod("getPermissions");
                        perms = getPermissions.invoke(res);
                    }
                    if (perms == null) return true;
                    Method playerHas = findPlayerHas(perms.getClass());
                    if (playerHas == null) return true;
                    Object allowed = playerHas.invoke(perms, player.getName(), "build", true);
                    return allowed instanceof Boolean && (Boolean) allowed;
                } catch (Throwable ignored) {
                    return true;
                }
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveMethod(Class<?> clazz, String name, Class<?>... parameters) {
        try {
            return clazz.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method findPlayerHas(Class<?> permsClass) {
        try {
            return permsClass.getMethod("playerHas", String.class, String.class, boolean.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private interface Checker {
        boolean canBreak(Player player, Block block);
    }

    private static final class FieldAccessor {
        private final java.lang.reflect.Field field;

        private FieldAccessor(java.lang.reflect.Field field) {
            this.field = field;
            this.field.setAccessible(true);
        }

        static FieldAccessor tryCreate(Class<?> clazz, String fieldName) {
            try {
                return new FieldAccessor(clazz.getField(fieldName));
            } catch (Throwable ignored) {
                try {
                    return new FieldAccessor(clazz.getDeclaredField(fieldName));
                } catch (Throwable ignored2) {
                    return null;
                }
            }
        }

        Object get(Object instance) throws IllegalAccessException {
            return field.get(instance);
        }
    }
}
