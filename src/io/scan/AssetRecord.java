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

    private final AssetCategory category;
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
    private final boolean bypassIdLimit;
    private final List<String> notes = new ArrayList<>();

    public AssetRecord(AssetCategory category, String rawType, int id, boolean idValid, String rawId,
                       String guid, String internalName, Map<String, String> names,
                       String origin, String originKind, Path assetPath,
                       String targetGuid, boolean bypassIdLimit) {
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
        this.bypassIdLimit = bypassIdLimit;
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
        String joined = joinedNames();
        return joined.isEmpty() ? internalName : joined;
    }

    public String joinedNames() {
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
        return String.join("|", ordered);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(id > 0 ? id : "?").append("  ").append(displayName());
        if (!rawType.isEmpty()) {
            sb.append("  [").append(rawType).append(']');
        }
        sb.append("  (").append(origin).append(')');
        if (!guid.isEmpty()) {
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
