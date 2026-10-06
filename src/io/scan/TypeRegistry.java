package io.scan;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Type 字符串 → 资产大类。
 * 清单对齐官方源码 UnturnedNexus.assetTypes 注册表与 EItemType 枚举（50 个物品子类）。
 */
public final class TypeRegistry {

    private static final Map<String, AssetCategory> TYPES = new HashMap<>();

    static {
        String[] items = {
                "Hat", "Pants", "Shirt", "Backpack", "Vest", "Mask", "Glasses",
                "Gun", "Sight", "Tactical", "Grip", "Barrel", "Magazine",
                "Food", "Water", "Medical", "Melee", "Fuel", "Tool",
                "Vehicle_Repair_Tool", "Barricade", "Storage", "Tank", "Generator",
                "Beacon", "Farm", "Trap", "Structure", "Supply", "Throwable",
                "Grower", "Optic", "Refill", "Fisher", "Cloud", "Map", "Compass",
                "Key", "Box", "Arrest_Start", "Arrest_End", "Detonator", "Charge",
                "Library", "Filter", "Sentry", "Tire", "Oil_Pump",
                "Vehicle_Paint_Tool", "Vehicle_Lockpick_Tool"
        };
        for (String type : items) {
            TYPES.put(type.toLowerCase(Locale.ROOT), AssetCategory.ITEM);
        }
        for (String type : new String[]{"Large", "Medium", "Small", "Decal"}) {
            TYPES.put(type.toLowerCase(Locale.ROOT), AssetCategory.OBJECT);
        }
        for (String[] pair : new String[][]{
                {"NPC", AssetCategory.NPC.name()},
                {"Resource", AssetCategory.RESOURCE.name()},
                {"Vehicle", AssetCategory.VEHICLE.name()},
                {"Animal", AssetCategory.ANIMAL.name()},
                {"Mythic", AssetCategory.MYTHIC.name()},
                {"Skin", AssetCategory.SKIN.name()},
                {"Spawn", AssetCategory.SPAWN.name()},
                {"Dialogue", AssetCategory.DIALOGUE.name()},
                {"Quest", AssetCategory.QUEST.name()},
                {"Vendor", AssetCategory.VENDOR.name()},
                {"Effect", AssetCategory.EFFECT.name()},
                {"Redirector", AssetCategory.REDIRECTOR.name()},
                {"Tag", AssetCategory.OTHER.name()},
                {"Road", AssetCategory.OTHER.name()},
                {"RewardsList", AssetCategory.OTHER.name()},
                {"ServerCuration", AssetCategory.OTHER.name()}
        }) {
            TYPES.put(pair[0].toLowerCase(Locale.ROOT), AssetCategory.valueOf(pair[1]));
        }
    }

    private TypeRegistry() {
    }

    /** 未识别的 Type（含官方完全限定类名资产）归入 OTHER，保留原始字符串用于展示。 */
    public static AssetCategory resolve(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return AssetCategory.OTHER;
        }
        String normalized = rawType.strip().toLowerCase(Locale.ROOT);
        // 重定向资产：官方 "Redirector" 与完全限定类名（如 SDG.Unturned.VehicleRedirectorAsset）
        if (normalized.contains("redirector")) {
            return AssetCategory.REDIRECTOR;
        }
        return TYPES.getOrDefault(normalized, AssetCategory.OTHER);
    }

    /** 通用 Redirector 资产声明的目标类别（AssetCategory 字段）→ 本工具类别。 */
    public static AssetCategory parseDeclaredCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "item" -> AssetCategory.ITEM;
            case "vehicle" -> AssetCategory.VEHICLE;
            case "object" -> AssetCategory.OBJECT;
            case "effect" -> AssetCategory.EFFECT;
            case "resource" -> AssetCategory.RESOURCE;
            case "animal" -> AssetCategory.ANIMAL;
            case "mythic" -> AssetCategory.MYTHIC;
            case "skin" -> AssetCategory.SKIN;
            case "spawn" -> AssetCategory.SPAWN;
            case "npc" -> AssetCategory.NPC;
            default -> null;
        };
    }
}
