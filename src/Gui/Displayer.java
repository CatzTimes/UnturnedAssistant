package Gui;

import Configurations.AppConfig;
import Models.AssetCategory;
import Models.AssetRecord;
import Models.SteamLocations;
import Monitors.ScanProgress;
import Monitors.SteamDetector;
import Services.AssetScanner;
import Services.CsvExporter;
import Services.RootPlanner;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.plaf.FontUIResource;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static Configurations.LanguageManager.getI18nText;
import static javax.swing.JFileChooser.DIRECTORIES_ONLY;

/**
 * 主界面：启动即后台自动检测 Steam/Unturned 目录并填入（不再默认 "."），
 * 手动输入的路径做了去引号/容错校验，扫描在后台并行执行，支持真实进度与取消，
 * 结果可导出 CSV。
 */
public class Displayer extends JFrame {

    private static final String VERSION = AppConfig.VERSION_LABEL;

    private final JTextField pathField = new JTextField(34);
    private final JTextArea out = new JTextArea();
    private final JCheckBox workshopCheck = new JCheckBox(getI18nText("gui.checkbox.workshop"));
    private final JPanel selectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final Map<AssetCategory, JCheckBox> categoryBoxes = new EnumMap<>(AssetCategory.class);
    private final JButton selectButton = new JButton(getI18nText("gui.button.filechoose"));
    private final JButton startButton = new JButton(getI18nText("gui.button.start"));
    private final JButton csvButton = new JButton(getI18nText("gui.button.csv"));
    private final JFileChooser pathChooser = new JFileChooser();

    private volatile SteamLocations detected;
    private ProgressMonitor progress;
    private List<AssetRecord> lastResult = List.of();

    public Displayer() {
        initComponents();
        setUpComponents();
        addComponents();
        detectSteamAsync();
        pack();
    }

    private static void setUIFont() {
        FontUIResource font = new FontUIResource("Sans", Font.PLAIN, 12);
        var keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof FontUIResource) {
                UIManager.put(key, font);
            }
        }
    }

    private void initComponents() {
        // 全局兜底：任何未捕获异常显示在输出区并恢复按钮，而不是无响应
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            out.append(getI18nText("gui.error") + "\n" + thread + "\n");
            throwable.printStackTrace(new PrintWriter(
                    new OutputStreamWriter(new JTextAreaWithInputStream(out), StandardCharsets.UTF_8), true));
        });
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        setUIFont();
        // 窗口/任务栏图标
        var iconUrl = Displayer.class.getResource("/assets/icon.jpg");
        if (iconUrl != null) {
            try {
                setIconImage(ImageIO.read(iconUrl));
            } catch (IOException ignored) {
            }
        }
        for (AssetCategory category : AssetCategory.values()) {
            JCheckBox box = new JCheckBox(category.getName(), true);
            categoryBoxes.put(category, box);
            selectPanel.add(box);
        }
        out.setRows(32);
        out.setEditable(false);
        setTitle(getI18nText("gui.title") + VERSION);
        setLayout(new javax.swing.BoxLayout(getContentPane(), javax.swing.BoxLayout.Y_AXIS));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    private void setUpComponents() {
        selectButton.addActionListener(event -> {
            pathChooser.setFileSelectionMode(DIRECTORIES_ONLY);
            pathChooser.setDialogTitle(getI18nText("gui.button.filechoose.title"));
            pathChooser.setApproveButtonText(getI18nText("gui.button.filechoose.select"));
            String current = sanitizePath(pathField.getText());
            if (!current.isEmpty()) {
                pathChooser.setCurrentDirectory(new File(current));
            }
            int result = pathChooser.showDialog(this, null);
            // 取消对话框时 getSelectedFile 为 null，直接判空即可
            if (result == JFileChooser.APPROVE_OPTION && pathChooser.getSelectedFile() != null
                    && pathChooser.getSelectedFile().isDirectory()) {
                pathField.setText(pathChooser.getSelectedFile().getPath());
            }
        });

        startButton.addActionListener(event -> startScan());

        csvButton.addActionListener(event -> {
            if (lastResult.isEmpty()) {
                return;
            }
            JFileChooser saver = new JFileChooser();
            saver.setSelectedFile(new File("UnturnedIDs_" + LocalDate.now() + ".csv"));
            if (saver.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                Path target = saver.getSelectedFile().toPath();
                CsvExporter.export(lastResult, target);
                out.append(getI18nText("gui.csv.done"));
                out.append(target.toString());
                out.append("\n");
                out.setCaretPosition(out.getDocument().getLength());
            } catch (Exception ex) {
                appendError(ex);
            }
        });
    }

    private void addComponents() {
        JPanel pathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pathPanel.add(pathField);
        pathPanel.add(selectButton);
        JPanel startPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        startPanel.add(workshopCheck);
        startPanel.add(startButton);
        startPanel.add(csvButton);
        add(new JScrollPane(out));
        add(selectPanel);
        add(pathPanel);
        add(startPanel);
    }

    /** 启动即后台探测：注册表 → libraryfolders.vdf → 游戏与工坊目录。 */
    private void detectSteamAsync() {
        out.setText(getI18nText("gui.detecting") + "\n");
        pathField.setText(getI18nText("gui.detecting"));
        new SwingWorker<SteamLocations, Void>() {
            @Override
            protected SteamLocations doInBackground() {
                return SteamDetector.detect();
            }

            @Override
            protected void done() {
                try {
                    detected = get();
                } catch (Exception ex) {
                    detected = null;
                }
                if (detected != null && detected.gameDir() != null) {
                    pathField.setText(detected.gameDir().toString());
                    out.setText("");
                } else {
                    pathField.setText("");
                    out.setText(getI18nText("gui.detect.fail") + "\n");
                }
            }
        }.execute();
    }

    private void startScan() {
        String input = sanitizePath(pathField.getText());
        if (input.isEmpty()) {
            out.setText(getI18nText("gui.path.empty"));
            return;
        }
        Path selected = Path.of(input);
        if (!Files.isDirectory(selected)) {
            out.setText(getI18nText("gui.path.invalid") + "\n" + input);
            return;
        }
        Models.ScanPlan plan = RootPlanner.plan(selected, workshopCheck.isSelected(), detected);

        selectButton.setEnabled(false);
        startButton.setEnabled(false);
        csvButton.setEnabled(false);
        out.setText(getI18nText("gui.processing") + "\n");
        progress = new ProgressMonitor(this, getI18nText("gui.processing.title"), "", 0, 100);

        new SwingWorker<List<AssetRecord>, Void>() {
            final long startNanos = System.nanoTime();

            @Override
            protected List<AssetRecord> doInBackground() {
                AssetScanner scanner = new AssetScanner(plan.assetRoots(), plan.workshopRoots(), plan.vanillaGameDir());
                return scanner.scan(new ScanProgress() {
                    @Override
                    public void progress(int done, int total, String currentDir) {
                        SwingUtilities.invokeLater(() -> {
                            if (progress == null || progress.isCanceled()) {
                                return;
                            }
                            if (total > 0) {
                                progress.setMaximum(total);
                                progress.setProgress(done);
                            }
                            if (currentDir != null) {
                                progress.setNote(currentDir);
                            }
                        });
                    }

                    @Override
                    public boolean cancelled() {
                        return progress != null && progress.isCanceled();
                    }
                });
            }

            @Override
            protected void done() {
                try {
                    List<AssetRecord> records = get();
                    if (progress != null && progress.isCanceled()) {
                        out.setText(getI18nText("gui.cancelled"));
                    } else {
                        lastResult = records;
                        render(records, (System.nanoTime() - startNanos) * 10E-9);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    appendError(ex.getCause() != null ? ex.getCause() : ex);
                } catch (Exception ex) {
                    appendError(ex);
                } finally {
                    if (progress != null) {
                        progress.close();
                        progress = null;
                    }
                    selectButton.setEnabled(true);
                    startButton.setEnabled(true);
                    csvButton.setEnabled(!lastResult.isEmpty());
                }
            }
        }.execute();
    }

    private void render(List<AssetRecord> records, double seconds) {
        StringBuilder sb = new StringBuilder();
        sb.append(getI18nText("gui.result.cost"))
                .append(String.format(Locale.ROOT, "%.2f", seconds)).append(" s\n");
        sb.append(getI18nText("gui.result.date")).append(LocalDate.now()).append("\n");

        Map<AssetCategory, List<AssetRecord>> groups = new EnumMap<>(AssetCategory.class);
        for (AssetRecord record : records) {
            groups.computeIfAbsent(record.getCategory(), key -> new java.util.ArrayList<>()).add(record);
        }
        boolean showOrigin = AssetScanner.originsDiffer(records);
        for (AssetCategory category : AssetCategory.values()) {
            JCheckBox box = categoryBoxes.get(category);
            if (box != null && !box.isSelected()) {
                continue;
            }
            List<AssetRecord> group = groups.get(category);
            if (group == null || group.isEmpty()) {
                continue;
            }
            sb.append("\n===============").append(category.getName()).append("===============\n\n");
            for (AssetRecord record : group) {
                sb.append(record.toDisplayString(showOrigin)).append('\n');
            }
        }
        out.setText(sb.toString());
        out.setCaretPosition(0);
    }

    private void appendError(Throwable throwable) {
        out.append(getI18nText("gui.error") + "\n");
        throwable.printStackTrace(new PrintWriter(
                new OutputStreamWriter(new JTextAreaWithInputStream(out), StandardCharsets.UTF_8), true));
    }

    /** 手动输入容错：去首尾空白与包裹引号，去掉多余的尾部反斜杠。 */
    private static String sanitizePath(String input) {
        String text = input.strip();
        if (text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'")))) {
            text = text.substring(1, text.length() - 1).strip();
        }
        while (text.endsWith("\\") && text.length() > 3) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
