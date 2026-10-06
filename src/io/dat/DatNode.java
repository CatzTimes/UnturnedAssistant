package io.dat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unturned .dat/.asset 文件解析后的树节点（字典）。
 * 官方语法约定键不区分大小写，这里统一以小写存储。
 */
public final class DatNode {

    private final Map<String, Object> entries = new LinkedHashMap<>();

    void put(String key, Object value) {
        entries.put(key.toLowerCase(Locale.ROOT), value);
    }

    public String getString(String key) {
        Object value = entries.get(key.toLowerCase(Locale.ROOT));
        return value instanceof String text ? text : null;
    }

    public String getString(String key, String fallback) {
        String value = getString(key);
        return value == null ? fallback : value;
    }

    public DatNode getDict(String key) {
        Object value = entries.get(key.toLowerCase(Locale.ROOT));
        return value instanceof DatNode dict ? dict : null;
    }

    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        Object value = entries.get(key.toLowerCase(Locale.ROOT));
        return value instanceof List<?> list ? (List<Object>) list : null;
    }

    public boolean has(String key) {
        return entries.containsKey(key.toLowerCase(Locale.ROOT));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
