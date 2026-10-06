package Configurations;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 轻量 i18n：加载失败或键缺失时回退原文，绝不抛异常。 */
public final class LanguageManager {

    private static final Map<String, String> MAPPINGS = new HashMap<>();

    static {
        String locale = Locale.getDefault().toString();
        load(locale);
        if (MAPPINGS.isEmpty()) {
            load("zh_CN");
        }
        if (MAPPINGS.isEmpty()) {
            load("en_US");
        }
    }

    private LanguageManager() {
    }

    public static String getI18nText(String key) {
        return MAPPINGS.getOrDefault(key, key);
    }

    private static void load(String locale) {
        var stream = LanguageManager.class.getResourceAsStream("/assets/lang/" + locale + ".lang");
        if (stream == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int split = line.indexOf('=');
                if (split > 0) {
                    MAPPINGS.putIfAbsent(line.substring(0, split).strip(), line.substring(split + 1));
                }
            }
        } catch (IOException ignored) {
        }
    }
}
