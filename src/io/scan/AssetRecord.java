package io.scan;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 一条资产记录：ID、GUID、名称、来源、备注。
 * 命名对齐官方 AssetIdListExporter 的导出字段（Name/GUID/Type/Origin/Legacy ID/Legacy Category）。
 */
public final class AssetRecord {

    private AssetCategory category;
    private final String rawType;
    private final int id;
    private final boolean idValid;
    private final String rawId;
    private final String guid;
    private final String internalName;
    private final Map<String, String> names;
    private final String origin;
    private final String originKind;
    private final Path assetPath;
    private final String targetGuid;
    private final AssetCategory targetCategory;
    private final boolean bypassIdLimit;
    /** 构造时即判定的"是否重定向资产"，与后续类别覆盖无关。 */
    private final boolean redirector;
    /** 已解析的重定向目标名；null = 非重定向或目标未解析。 */
    private String redirectTarget;
    private final List<String> notes = new ArrayList<>();

    public AssetRecord(AssetCategory category, String rawType, int id, boolean idValid, String rawId,
                       String guid, String internalName, Map<String, String> names,
                       String origin, String originKind, Path assetPath,
                       String targetGuid, AssetCategory targetCategory, boolean bypassIdLimit) {
        this.category = category;
        this.rawType = rawType == null ? "" : rawType;
        this.id = id;
        this.idValid = idValid;
        this.rawId = rawId == null ? "" : rawId;
        this.guid = guid == null ? "" : guid;
        this.internalName = internalName;
        this.names = names;
        this.origin = origin;
        this.originKind = originKind;
        this.assetPath = assetPath;
        this.targetGuid = targetGuid == null ? "" : targetGuid;
        this.targetCategory = targetCategory;
        this.bypassIdLimit = bypassIdLimit;
        this.redirector = category == AssetCategory.REDIRECTOR;
    }

    public AssetCategory getCategory() {
        return category;
    }

    public String getRawType() {
        return rawType;
    }

    public int getId() {
        return id;
    }

    public boolean isIdValid() {
        return idValid;
    }

    public String getRawId() {
        return rawId;
    }

    public String getGuid() {
        return guid;
    }

    public String getInternalName() {
        return internalName;
    }

    public Map<String, String> getNames() {
        return names;
    }

    public String getOrigin() {
        return origin;
    }

    public String getOriginKind() {
        return originKind;
    }

    public Path getAssetPath() {
        return assetPath;
    }

    public String getTargetGuid() {
        return targetGuid;
    }

    /** 重定向资产声明的目标类别（VehicleRedirectorAsset 恒为载具，通用 Redirector 读 AssetCategory 字段）。 */
    public AssetCategory getTargetCategory() {
        return targetCategory;
    }

    /** 仅供扫描后处理：把重定向资产归入其目标类别分区（如载具）。 */
    public void overrideCategory(AssetCategory category) {
        this.category = category;
    }

    public boolean isRedirector() {
        return redirector;
    }

    /** 已解析的重定向目标名；设置后展示为 "内部名 → 目标名"。 */
    public void setRedirectTarget(String targetName) {
        this.redirectTarget = targetName;
    }

    public boolean isBypassIdLimit() {
        return bypassIdLimit;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void addNote(String note) {
        if (!notes.contains(note)) {
            notes.add(note);
        }
    }

    public boolean hasLocalizedName() {
        return !names.isEmpty();
    }

    /** 英文名优先，其余语言按字母序以 | 连接；无任何语言文件时回退内部名。 */
    public String displayName() {
        return joinNames(names, internalName);
    }

    /** 静态版名称拼接：英文优先，其余语言按字母序以 | 连接，空时回退 fallback。 */
    public static String joinNames(Map<String, String> names, String fallback) {
        List<String> ordered = new ArrayList<>();
        names.entrySet().stream()
                .filter(entry -> "english".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .ifPresent(ordered::add);
        names.entrySet().stream()
                .filter(entry -> !"english".equalsIgnoreCase(entry.getKey()))
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(Map.Entry::getValue)
                .forEach(ordered::add);
        String joined = String.join("|", ordered);
        return joined.isEmpty() ? fallback : joined;
    }

    @Override
    public String toString() {
        return toDisplayString(true);
    }

    /** 类型名与分区同名的类别（如载具区的 [Vehicle]）：展示时属冗余，省略。 */
    private static final Map<AssetCategory, String> GENERIC_TYPE_NAMES = Map.ofEntries(
            Map.entry(AssetCategory.VEHICLE, "vehicle"),
            Map.entry(AssetCategory.ANIMAL, "animal"),
            Map.entry(AssetCategory.RESOURCE, "resource"),
            Map.entry(AssetCategory.EFFECT, "effect"),
            Map.entry(AssetCategory.SKIN, "skin"),
            Map.entry(AssetCategory.MYTHIC, "mythic"),
            Map.entry(AssetCategory.SPAWN, "spawn"),
            Map.entry(AssetCategory.DIALOGUE, "dialogue"),
            Map.entry(AssetCategory.QUEST, "quest"),
            Map.entry(AssetCategory.VENDOR, "vendor"),
            Map.entry(AssetCategory.NPC, "npc"));

    /**
     * 单行展示：ID 名称 [Type] (来源) [GUID] 备注。
     * includeOrigin=false 时省略来源；有短 ID 时 GUID 冗余省略（仅无 ID 资产展示）；
     * 类型名与所属分区同名时省略（如载具区的 [Vehicle]）；
     * 重定向行展示为 "内部名 → 目标名"，不显示长类型名与无本地化备注。
     */
    public String toDisplayString(boolean includeOrigin) {
        StringBuilder sb = new StringBuilder();
        sb.append(id > 0 ? id : "?").append("  ");
        if (redirector) {
            sb.append(internalName);
            if (redirectTarget != null) {
                sb.append(" → ").append(redirectTarget);
            }
        } else {
            sb.append(displayName());
            String generic = GENERIC_TYPE_NAMES.get(category);
            boolean redundantType = generic != null && generic.equalsIgnoreCase(rawType);
            if (!rawType.isEmpty() && !redundantType) {
                sb.append("  [").append(rawType).append(']');
            }
        }
        if (includeOrigin) {
            sb.append("  (").append(origin).append(')');
        }
        // 物体以 GUID 为准（legacy ID 是无意义占位），始终显示 GUID；其他类别仅无 ID 时显示
        if ((!guid.isEmpty() && category == AssetCategory.OBJECT)
                || (id <= 0 && !guid.isEmpty())) {
            sb.append("  GUID:").append(guid);
        }
        for (String note : notes) {
            sb.append("  ").append(note);
        }
        return sb.toString();
    }

    String sortKey() {
        return internalName.toLowerCase(Locale.ROOT);
    }
}
