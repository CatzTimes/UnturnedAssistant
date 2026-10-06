import gui.Displayer;
import io.detect.SteamDetector;
import io.scan.AssetCategory;
import io.scan.AssetRecord;
import io.scan.AssetScanner;
import io.scan.RootPlanner;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 入口：默认启动 GUI；--scan <目录> [workshop] 提供无界面扫描（用于自动化验证）。 */
public class Start {

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equalsIgnoreCase("--scan")) {
            runHeadless(args);
            return;
        }
        java.awt.EventQueue.invokeLater(() -> new Displayer().setVisible(true));
    }

    private static void runHeadless(String[] args) throws Exception {
        Path root = Path.of(args[1]);
        boolean workshop = args.length > 2 && args[2].equalsIgnoreCase("workshop");

        SteamDetector.Locations detected = workshop ? SteamDetector.detect() : null;
        RootPlanner.Plan plan = RootPlanner.plan(root, workshop, detected);
        System.err.println("[根目录] " + plan.assetRoots());
        System.err.println("[工坊根] " + plan.workshopRoots());

        List<AssetRecord> records = new AssetScanner(plan.assetRoots(), plan.workshopRoots(), plan.vanillaGameDir())
                .scan(new AssetScanner.Progress() {
                    @Override
                    public void progress(int done, int total, String currentDir) {
                    }

                    @Override
                    public boolean cancelled() {
                        return false;
                    }
                });

        Map<AssetCategory, Integer> counts = new EnumMap<>(AssetCategory.class);
        for (AssetRecord record : records) {
            counts.merge(record.getCategory(), 1, Integer::sum);
        }
        System.err.println("[分类统计] " + counts);
        boolean showOrigin = AssetScanner.originsDiffer(records);
        for (AssetRecord record : records) {
            System.out.println(record.toDisplayString(showOrigin));
        }
    }
}
