package io.scan;

import io.dat.DatNode;
import io.dat.DatParser;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;

import static Language.LanguageManager.getI18nText;

/**
 * 资产扫描器：目录驱动的发现规则与游戏运行时（AssetsWorker.FindAssets）一致：
 *   &lt;目录名&gt;.asset → &lt;目录名&gt;.dat → Asset.dat → 目录内所有 *.asset；
 * 同目录的其余 .dat 作为语言文件（English 优先兜底）。
 * 解析阶段按目录粒度并行；冲突/保留区间标注在扫描完成后统一进行。
 */
public final class AssetScanner {

    /** progress/done 由后台线程调用；cancelled 供其轮询。 */
    public interface Progress {
        void progress(int done, int total, String currentDir);

        boolean cancelled();
    }

    private final List<Path> assetRoots;
    private final List<Path> workshopRoots;
    private final Path vanillaGameDir;
    /** guid(小写) → 显示名；收录全部解析到的资产（含被排除的），供重定向解析目标名。 */
    private final Map<String, String> guidToName = new ConcurrentHashMap<>();

    public AssetScanner(List<Path> assetRoots, List<Path> workshopRoots, Path vanillaGameDir) {
        this.assetRoots = List.copyOf(assetRoots);
        this.workshopRoots = List.copyOf(workshopRoots);
        this.vanillaGameDir = vanillaGameDir;
    }

    public List<AssetRecord> scan(Progress progress) {
        guidToName.clear();
        List<Path> dirs = new ArrayList<>();
        for (Path root : assetRoots) {
            if (Files.isDirectory(root)) {
                collectDirs(root, dirs, progress);
                if (progress.cancelled()) {
                    return List.of();
                }
            }
        }
        int total = dirs.size();
        progress.progress(0, total, null);
        if (total == 0) {
            return List.of();
        }

        List<AssetRecord> records = new ArrayList<>();
        int threads = Math.min(Math.max(Runtime.getRuntime().availableProcessors(), 2), 8);
        ExecutorService pool = Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task, "asset-scanner");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<List<AssetRecord>>> futures = new ArrayList<>(total);
            for (Path dir : dirs) {
                futures.add(pool.submit(() -> processDirectory(dir)));
            }
            int done = 0;
            for (Future<List<AssetRecord>> future : futures) {
                if (progress.cancelled()) {
                    futures.forEach(other -> other.cancel(true));
                    return records;
                }
                try {
                    records.addAll(future.get());
                } catch (ExecutionException e) {
                    // 单个目录损坏不影响整体扫描
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return records;
                }
                done++;
                if (done % 16 == 0 || done == total) {
                    progress.progress(done, total, null);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        postProcess(records);
        return records;
    }

    private static void collectDirs(Path root, List<Path> dirs, Progress progress) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (progress.cancelled()) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    // 与游戏运行时一致：工坊的地图包与 UI 本地化包不承载资产
                    if (isSkippableWorkshopPackage(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    dirs.add(dir);
                    if (dirs.size() % 256 == 0) {
                        progress.progress(0, 0, dir.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // 无法进入的子树直接跳过
        }
    }

    /** dir 是否为工坊 content/304930 下的直接子包，且类型为地图/UI 本地化（应剪枝）。 */
    private static boolean isSkippableWorkshopPackage(Path dir) {
        Path parent = dir.getParent();
        if (parent == null || !"304930".equalsIgnoreCase(parent.getFileName().toString())) {
            return false;
        }
        Path content = parent.getParent();
        if (content == null || !"content".equalsIgnoreCase(content.getFileName().toString())) {
            return false;
        }
        Path workshop = content.getParent();
        if (workshop == null || !"workshop".equalsIgnoreCase(workshop.getFileName().toString())) {
            return false;
        }
        return Files.exists(dir.resolve("Map.meta")) || Files.exists(dir.resolve("Localization.meta"));
    }

    private List<AssetRecord> processDirectory(Path dir) {
        List<Path> assetFiles = findAssetFiles(dir);
        if (assetFiles.isEmpty()) {
            return List.of();
        }
        Map<String, String> localization = loadLocalization(dir, assetFiles);
        List<AssetRecord> out = new ArrayList<>(assetFiles.size());
        for (Path assetFile : assetFiles) {
            try {
                AssetRecord record = parseAsset(dir, assetFile, localization);
                if (record != null) {
                    out.add(record);
                }
            } catch (Exception e) {
                // 损坏的 .dat 不应中断整体扫描
            }
        }
        return out;
    }

    /** 游戏同款发现优先级；命中即止。 */
    private static List<Path> findAssetFiles(Path dir) {
        String dirName = dir.getFileName() != null ? dir.getFileName().toString() : dir.toString();
        Path sameNameAsset = dir.resolve(dirName + ".asset");
        if (Files.isRegularFile(sameNameAsset)) {
            return List.of(sameNameAsset);
        }
        Path sameNameDat = dir.resolve(dirName + ".dat");
        if (Files.isRegularFile(sameNameDat)
                && !"masterbundle.dat".equalsIgnoreCase(sameNameDat.getFileName().toString())) {
            return List.of(sameNameDat);
        }
        Path assetDat = dir.resolve("Asset.dat");
        if (Files.isRegularFile(assetDat)) {
            return List.of(assetDat);
        }
        List<Path> found = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.asset")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    found.add(file);
                }
            }
        } catch (IOException ignored) {
        }
        found.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        return found;
    }

    /** 同目录所有非资产 .dat 均视为语言文件（不再只认 English.dat，修复无语言文件资产的漏项）。 */
    private static Map<String, String> loadLocalization(Path dir, List<Path> assetFiles) {
        Set<String> reserved = new HashSet<>();
        for (Path file : assetFiles) {
            reserved.add(file.getFileName().toString().toLowerCase(Locale.ROOT));
        }
        reserved.add("asset.dat");
        reserved.add("masterbundle.dat");
        Map<String, String> names = new LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.dat")) {
            for (Path file : stream) {
                if (reserved.contains(file.getFileName().toString().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                try {
                    String name = DatParser.parseFile(file).getString("Name", "");
                    if (!name.isBlank()) {
                        names.put(baseName(file), name.strip());
                    }
                } catch (Exception e) {
                    // 单个语言文件损坏不影响资产本身
                }
            }
        } catch (IOException ignored) {
        }
        return names;
    }

    private AssetRecord parseAsset(Path dir, Path assetFile, Map<String, String> localization) throws IOException {
        DatNode data = DatParser.parseFile(assetFile);
        if (data.isEmpty()) {
            return null;
        }
        DatNode metadata = data.getDict("Metadata");
        String typeStr = metadata != null ? metadata.getString("Type") : null;
        String guid = metadata != null ? metadata.getString("GUID") : null;
        if (typeStr == null) {
            typeStr = data.getString("Type");
        }
        if (guid == null) {
            guid = data.getString("GUID");
        }
        // v2 资产正文可整体放在 Asset {} 子字典
        DatNode body = data.getDict("Asset");
        if (body == null) {
            body = data;
        }
        String rawId = body.getString("ID");
        if (rawId == null) {
            rawId = data.getString("ID");
        }
        String targetGuid = data.getString("TargetAsset");
        if (targetGuid == null && metadata != null) {
            targetGuid = metadata.getString("TargetAsset");
        }
        // 载具重定向（VehicleRedirectorAsset）用 TargetVehicle 指向目标载具
        if (targetGuid == null) {
            targetGuid = data.getString("TargetVehicle");
        }
        boolean bypass = data.has("Bypass_ID_Limit") || body.has("Bypass_ID_Limit");
        boolean pro = data.has("Pro") || body.has("Pro");

        AssetCategory category = TypeRegistry.resolve(typeStr);
        String internalName = internalNameOf(dir, assetFile);

        // 先收录 GUID→名称（含将被排除的资产），重定向目标可能本身没有 ID
        if (guid != null && !guid.isEmpty()) {
            guidToName.putIfAbsent(guid.toLowerCase(Locale.ROOT),
                    AssetRecord.joinNames(localization, internalName));
        }

        // Pro 标记 = 官方 Steam 经济（皮肤基底）物品，玩家视角不是可用物品
        if (pro && category == AssetCategory.ITEM) {
            return null;
        }

        int[] id = parseId(rawId);
        // 无 legacy ID 的资产（皮肤基底等 GUID-only 物品）无法按 ID 查找，不属于 ID 表范畴
        if (id[0] == 0) {
            return null;
        }

        // 重定向目标类别：载具重定向恒为载具；通用 Redirector 读 AssetCategory 声明
        AssetCategory targetCategory = null;
        if (category == AssetCategory.REDIRECTOR) {
            if (typeStr != null && typeStr.toLowerCase(Locale.ROOT).contains("vehicleredirector")) {
                targetCategory = AssetCategory.VEHICLE;
            } else {
                targetCategory = TypeRegistry.parseDeclaredCategory(data.getString("AssetCategory"));
            }
        }

        String[] origin = originOf(dir);
        // 完全限定类名（如 "...VehicleRedirectorAsset, Assembly-CSharp, ..."）截掉程序集后缀便于展示
        String displayType = typeStr == null ? "" : typeStr.strip();
        int assembly = displayType.indexOf(',');
        if (assembly > 0) {
            displayType = displayType.substring(0, assembly);
        }
        return new AssetRecord(
                category,
                displayType,
                id[0], id[1] == 1, rawId == null ? "" : rawId.strip(),
                guid, internalName,
                localization, origin[1], origin[0], assetFile,
                targetGuid, targetCategory, bypass);
    }

    /** [值, 是否有效]：容错解析（trim、ushort 范围），与游戏 ParseUInt16 语义对齐但保留非法痕迹。 */
    private static int[] parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[]{0, 0};
        }
        try {
            int value = Integer.parseInt(raw.strip());
            if (value >= 0 && value <= 65535) {
                return new int[]{value, 1};
            }
        } catch (NumberFormatException ignored) {
        }
        return new int[]{0, 0};
    }

    private static String internalNameOf(Path dir, Path assetFile) {
        String fileName = assetFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        // 游戏规则：Asset.dat 形态的资产显示名取父目录名
        if (base.equalsIgnoreCase("Asset")) {
            return dir.getFileName() != null ? dir.getFileName().toString() : base;
        }
        return base;
    }

    private static String baseName(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** [kind, 显示名]：官方本体 / 工坊 FileID / 自定义目录的顶层文件夹。 */
    private String[] originOf(Path dir) {
        if (vanillaGameDir != null && startsWith(dir, vanillaGameDir)) {
            return new String[]{"vanilla", getI18nText("origin.vanilla")};
        }
        for (Path workshopRoot : workshopRoots) {
            if (startsWith(dir, workshopRoot)) {
                return new String[]{"workshop", getI18nText("origin.workshop") + " " + topSegment(workshopRoot, dir)};
            }
        }
        for (Path root : assetRoots) {
            if (startsWith(dir, root)) {
                if (isWorkshopContentRoot(root)) {
                    return new String[]{"workshop", getI18nText("origin.workshop") + " " + topSegment(root, dir)};
                }
                return new String[]{"custom", topSegment(root, dir)};
            }
        }
        return new String[]{"custom", dir.getFileName() != null ? dir.getFileName().toString() : dir.toString()};
    }

    private static String topSegment(Path root, Path dir) {
        Path rel = root.toAbsolutePath().normalize().relativize(dir.toAbsolutePath().normalize());
        return rel.getNameCount() > 0 ? rel.getName(0).toString()
                : root.getFileName() != null ? root.getFileName().toString() : root.toString();
    }

    private static boolean isWorkshopContentRoot(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (!"304930".equalsIgnoreCase(normalized.getFileName().toString())) {
            return false;
        }
        Path content = normalized.getParent();
        Path workshop = content != null ? content.getParent() : null;
        return content != null && "content".equalsIgnoreCase(content.getFileName().toString())
                && workshop != null && "workshop".equalsIgnoreCase(workshop.getFileName().toString());
    }

    private static boolean startsWith(Path child, Path parent) {
        return child.toAbsolutePath().normalize().startsWith(parent.toAbsolutePath().normalize());
    }

    /** 扫描后统一标注：ID/GUID 冲突（官方优先→工坊先到先得）、官方保留区间、重定向归入目标类别、非法 ID。 */
    private void postProcess(List<AssetRecord> records) {
        Map<String, AssetRecord> activeById = new HashMap<>();
        Map<String, AssetRecord> activeByGuid = new HashMap<>();
        for (AssetRecord record : records) {
            if (record.getId() > 0) {
                String key = record.getCategory() + "#" + record.getId();
                AssetRecord current = activeById.get(key);
                if (current == null || rankOf(record) < rankOf(current)) {
                    activeById.put(key, record);
                }
            }
            if (!record.getGuid().isEmpty()) {
                String key = record.getGuid().toLowerCase(Locale.ROOT);
                AssetRecord current = activeByGuid.get(key);
                if (current == null || rankOf(record) < rankOf(current)) {
                    activeByGuid.put(key, record);
                }
            }
        }

        for (AssetRecord record : records) {
            if (record.getId() > 0) {
                AssetRecord winner = activeById.get(record.getCategory() + "#" + record.getId());
                if (winner != null && winner != record) {
                    record.addNote(getI18nText("gui.note.conflict") + winner.getOrigin() + ")");
                }
                if (!"vanilla".equals(record.getOriginKind())) {
                    int limit = reservedLimit(record.getCategory());
                    if (limit > 0 && record.getId() < limit) {
                        record.addNote(record.isBypassIdLimit()
                                ? getI18nText("gui.note.reserved") + "(Bypass_ID_Limit)"
                                : getI18nText("gui.note.reserved") + "(<" + limit + ")");
                    }
                }
            } else if (!record.isIdValid() && !record.getRawId().isEmpty()) {
                record.addNote(getI18nText("gui.note.invalidid") + "(" + record.getRawId() + ")");
            }
            if (!record.getGuid().isEmpty()) {
                AssetRecord winner = activeByGuid.get(record.getGuid().toLowerCase(Locale.ROOT));
                if (winner != null && winner != record) {
                    record.addNote(getI18nText("gui.note.guidconflict"));
                }
            }
            // 重定向：解析目标名并归入目标类别分区（游戏内 /v <旧ID> 即命中目标）；
            // 解析成功时展示为 "内部名 → 目标名"，不追加备注
            if (record.isRedirector() && !record.getTargetGuid().isEmpty()) {
                String target = guidToName.get(record.getTargetGuid().toLowerCase(Locale.ROOT));
                if (target != null) {
                    record.setRedirectTarget(target);
                } else {
                    record.addNote(getI18nText("gui.note.redirect") + record.getTargetGuid());
                }
                AssetCategory targetCategory = record.getTargetCategory();
                if (targetCategory != null && targetCategory != AssetCategory.REDIRECTOR) {
                    record.overrideCategory(targetCategory);
                }
            }
            if (!record.hasLocalizedName() && !record.isRedirector()) {
                record.addNote(getI18nText("gui.note.nolocal"));
            }
        }

        records.sort(Comparator
                .comparingInt((AssetRecord record) -> record.getCategory().ordinal())
                .thenComparingInt(AssetRecord::getId)
                .thenComparing(AssetRecord::sortKey));
    }

    private static int rankOf(AssetRecord record) {
        return switch (record.getOriginKind()) {
            case "vanilla" -> 0;
            case "workshop" -> 1;
            default -> 2;
        };
    }

    /** 全部条目来源相同时（如只扫游戏本体），来源列无信息量，可不显示。 */
    public static boolean originsDiffer(List<AssetRecord> records) {
        if (records.isEmpty()) {
            return false;
        }
        String first = records.get(0).getOrigin();
        for (AssetRecord record : records) {
            if (!record.getOrigin().equals(first)) {
                return true;
            }
        }
        return false;
    }

    /** 官方保留的 legacy ID 上限（对齐 AssetIdListExporter 的 Reserved for Vanilla 区间）。 */
    private static int reservedLimit(AssetCategory category) {
        return switch (category) {
            case ITEM -> 2000;
            case EFFECT -> 200;
            case RESOURCE, ANIMAL -> 50;
            case MYTHIC -> 500;
            case SKIN -> 2000;
            case DIALOGUE, QUEST, VENDOR -> 2000;
            case SPAWN -> 1000;
            case VEHICLE -> 2000;
            default -> 0;
        };
    }
}
