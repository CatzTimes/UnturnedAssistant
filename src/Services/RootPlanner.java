package Services;

import Models.ScanPlan;
import Models.SteamLocations;
import Monitors.SteamDetector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import Configurations.AppConfig;

/**
 * 扫描规划服务：根据用户选择的目录与勾选项，规划实际扫描的资产根目录：
 *  - 所选目录始终作为基础扫描根（支持任意模组合集文件夹）；
 *  - 勾选创意工坊时叠加检测到的工坊 content/{AppID}（可多库）；
 *  - 检测失败但所选目录确实是游戏目录时，从父链推导工坊位置。
 */
public final class RootPlanner {

    private RootPlanner() {
    }

    public static ScanPlan plan(Path selected, boolean includeWorkshop, SteamLocations detected) {
        Path gameDir = SteamDetector.isGameDir(selected) ? selected : null;
        List<Path> workshopRoots = new ArrayList<>();
        if (includeWorkshop) {
            if (detected != null) {
                for (Path root : detected.workshopRoots()) {
                    if (Files.isDirectory(root) && !samePath(root, selected)) {
                        workshopRoots.add(root);
                    }
                }
            }
            if (workshopRoots.isEmpty() && gameDir != null) {
                for (Path parent = selected.getParent(); parent != null; parent = parent.getParent()) {
                    Path root = parent.resolve("steamapps").resolve("workshop").resolve("content")
                            .resolve(String.valueOf(AppConfig.WORKSHOP_APP_ID));
                    if (Files.isDirectory(root)) {
                        workshopRoots.add(root);
                        break;
                    }
                }
            }
        }
        List<Path> assetRoots = new ArrayList<>();
        assetRoots.add(selected);
        assetRoots.addAll(workshopRoots);
        return new ScanPlan(assetRoots, workshopRoots, gameDir);
    }

    private static boolean samePath(Path a, Path b) {
        return a.toAbsolutePath().normalize().toString()
                .equalsIgnoreCase(b.toAbsolutePath().normalize().toString());
    }
}
