package Monitors;

import Configurations.AppConfig;
import Models.SteamLocations;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Steam/Unturned 环境探测：
 *   注册表 SteamPath(HKCU) / InstallPath(HKLM) → steamapps/libraryfolders.vdf 全部库
 *   → 游戏 steamapps/common/Unturned（以 Bundles 或 Unturned.exe 校验）
 *   → 各库 steamapps/workshop/content/{AppID}（游戏所在库优先）。
 * 不硬编码盘符：Steam 装在任何位置都能命中。
 */
public final class SteamDetector {

    private static final Pattern VDF_PATH_LINE = Pattern.compile("^\\s*\"path\"\\s+\"(.+)\"\\s*$");

    private SteamDetector() {
    }

    public static SteamLocations detect() {
        Path steamRoot = findSteamRoot();
        if (steamRoot == null) {
            return new SteamLocations(null, List.of());
        }
        List<Path> libraries = findLibraries(steamRoot);

        Path gameDir = null;
        for (Path library : libraries) {
            Path candidate = library.resolve("steamapps").resolve("common").resolve("Unturned");
            if (isGameDir(candidate)) {
                gameDir = candidate;
                break;
            }
        }

        List<Path> workshopRoots = new ArrayList<>();
        if (gameDir != null) {
            Path ownLibrary = libraryOf(gameDir, libraries);
            if (ownLibrary != null) {
                addWorkshopRoot(ownLibrary, workshopRoots);
            }
        }
        for (Path library : libraries) {
            addWorkshopRoot(library, workshopRoots);
        }
        return new SteamLocations(gameDir, workshopRoots);
    }

    public static boolean isGameDir(Path path) {
        return path != null && Files.isDirectory(path)
                && (Files.isDirectory(path.resolve("Bundles")) || Files.isRegularFile(path.resolve("Unturned.exe")));
    }

    private static void addWorkshopRoot(Path library, List<Path> out) {
        Path root = library.resolve("steamapps").resolve("workshop")
                .resolve("content").resolve(String.valueOf(AppConfig.WORKSHOP_APP_ID));
        if (Files.isDirectory(root) && out.stream().noneMatch(existing -> samePath(existing, root))) {
            out.add(root);
        }
    }

    private static Path libraryOf(Path gameDir, List<Path> libraries) {
        Path normalized = gameDir.toAbsolutePath().normalize();
        for (Path library : libraries) {
            if (normalized.startsWith(library.toAbsolutePath().normalize())) {
                return library;
            }
        }
        return null;
    }

    private static boolean samePath(Path a, Path b) {
        return a.toAbsolutePath().normalize().toString()
                .equalsIgnoreCase(b.toAbsolutePath().normalize().toString());
    }

    private static Path findSteamRoot() {
        String value = regQuery("HKCU\\Software\\Valve\\Steam", "SteamPath");
        if (value == null || value.isBlank()) {
            value = regQuery("HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam", "InstallPath");
        }
        if (value != null && !value.isBlank()) {
            Path path = Path.of(value.strip());
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        for (String candidate : List.of(
                "C:/Program Files (x86)/Steam", "C:/Program Files/Steam",
                "D:/Steam", "D:/Program Files (x86)/Steam",
                "E:/Steam", "E:/Program Files (x86)/Steam")) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path) && Files.isDirectory(path.resolve("steamapps"))) {
                return path;
            }
        }
        return null;
    }

    /** 读取注册表值；失败返回 null（不抛异常，探测失败时 GUI 会提示手动选择）。 */
    private static String regQuery(String key, String valueName) {
        Process process = null;
        try {
            process = new ProcessBuilder("reg", "query", key, "/v", valueName)
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                return null; // 超时，finally 中强制销毁
            }
            for (String line : output.split("\n")) {
                int idx = line.indexOf("REG_SZ");
                if (idx >= 0) {
                    return line.substring(idx + "REG_SZ".length()).strip();
                }
            }
        } catch (Exception ignored) {
        } finally {
            // 无论成功、超时还是异常都确保不留悬挂进程
            if (process != null) {
                process.destroyForcibly();
            }
        }
        return null;
    }

    private static List<Path> findLibraries(Path steamRoot) {
        LinkedHashSet<Path> libraries = new LinkedHashSet<>();
        libraries.add(steamRoot);
        Path vdf = steamRoot.resolve("steamapps").resolve("libraryfolders.vdf");
        if (Files.isRegularFile(vdf)) {
            try {
                for (String line : Files.readAllLines(vdf, StandardCharsets.UTF_8)) {
                    Matcher matcher = VDF_PATH_LINE.matcher(line);
                    if (matcher.matches()) {
                        // VDF 中反斜杠被转义为 \\
                        Path library = Path.of(matcher.group(1).replace("\\\\", "\\"));
                        if (Files.isDirectory(library)) {
                            libraries.add(library);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return List.copyOf(libraries);
    }
}
