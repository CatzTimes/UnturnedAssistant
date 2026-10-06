package Models;

import java.nio.file.Path;
import java.util.List;

/** Steam 环境探测结果：游戏目录与全部工坊 content 根（可多库）。 */
public record SteamLocations(Path gameDir, List<Path> workshopRoots) {
    public SteamLocations {
        workshopRoots = List.copyOf(workshopRoots);
    }
}
