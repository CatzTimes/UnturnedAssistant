package Models;

import java.nio.file.Path;
import java.util.List;

/** 一次扫描的规划结果：资产根、工坊根与官方游戏目录（用于来源判定）。 */
public record ScanPlan(List<Path> assetRoots, List<Path> workshopRoots, Path vanillaGameDir) {
    public ScanPlan {
        assetRoots = List.copyOf(assetRoots);
        workshopRoots = List.copyOf(workshopRoots);
    }
}
