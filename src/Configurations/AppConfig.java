package Configurations;

import Models.AssetCategory;

/** 全局配置：版本、Steam 常量、扫描器参数与官方保留 ID 区间。 */
public final class AppConfig {

    public static final String VERSION = "4.0";
    public static final String VERSION_LABEL = "V" + VERSION;
    /** Unturned 的 Steam AppID（游戏目录与工坊内容的定位锚点）。 */
    public static final int WORKSHOP_APP_ID = 304930;
    /** 扫描线程数上下限（按 CPU 核数在区间内取值）。 */
    public static final int SCANNER_MIN_THREADS = 2;
    public static final int SCANNER_MAX_THREADS = 8;

    private AppConfig() {
    }

    /** 官方保留的 legacy ID 上限（对齐 AssetIdListExporter 的 Reserved for Vanilla 区间）。 */
    public static int reservedLegacyIdLimit(AssetCategory category) {
        return switch (category) {
            case ITEM, SKIN -> 2000;
            case EFFECT -> 200;
            case RESOURCE, ANIMAL -> 50;
            case MYTHIC -> 500;
            case DIALOGUE, QUEST, VENDOR -> 2000;
            case SPAWN -> 1000;
            case VEHICLE -> 2000;
            default -> 0;
        };
    }
}
