package Services;

import Models.DatNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Unturned .dat(v1) / .asset(v2) 兼容解析服务。
 * 语法要点（官方 assets/data-file-format 文档）：
 *  - 每行 "键 值"，键不区分大小写；键与值都可用引号包裹；
 *  - '{' 开子字典、'[' 开列表，可另起一行（键单独成行）或直接跟在键后；
 *  - '//' 开头的整行是注释；带引号的值可携带行内注释；值内 \n 表示换行。
 */
public final class DatParser {

    private DatParser() {
    }

    public static DatNode parseFile(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        // 部分模组的 English.dat 带 UTF-8 BOM（如记事本保存），不剥离会污染首个键
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return parse(text);
    }

    public static DatNode parse(String text) {
        Frame root = new Frame(new DatNode());
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(root);

        for (String line : text.split("\r\n|\r|\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }

            Frame current = stack.peek();

            switch (trimmed) {
                case "{" -> {
                    DatNode child = new DatNode();
                    attachPending(current, child);
                    stack.push(new Frame(child));
                    continue;
                }
                case "[" -> {
                    List<Object> child = new ArrayList<>();
                    attachPending(current, child);
                    stack.push(new Frame(child));
                    continue;
                }
                case "}", "]" -> {
                    if (stack.size() > 1) {
                        stack.pop();
                    }
                    continue;
                }
                default -> {
                }
            }

            String[] pair = splitKey(trimmed);
            String key = pair[0];
            String rest = pair[1];

            if (rest.isEmpty()) {
                // 键单独成行：默认存空串占位；若下一行是 { / [ 则由 attachPending 覆盖为子结构
                putOrAdd(current, key, "");
                current.pendingKey = key;
                continue;
            }
            if (rest.equals("{")) {
                DatNode child = new DatNode();
                putOrAdd(current, key, child);
                current.pendingKey = null;
                stack.push(new Frame(child));
                continue;
            }
            if (rest.equals("[")) {
                List<Object> child = new ArrayList<>();
                putOrAdd(current, key, child);
                current.pendingKey = null;
                stack.push(new Frame(child));
                continue;
            }
            putOrAdd(current, key, unquote(rest));
            current.pendingKey = key;
        }
        return root.dict();
    }

    /** 栈帧：容器（字典或列表）+ 等待挂子结构的键。 */
    private static final class Frame {
        private final Object container;
        private String pendingKey;

        private Frame(Object container) {
            this.container = container;
        }

        private DatNode dict() {
            return (DatNode) container;
        }
    }

    /** 把子结构挂到帧的 pendingKey（字典）或直接追加（列表）。 */
    private static void attachPending(Frame frame, Object child) {
        if (frame.container instanceof List<?> list) {
            asList(list).add(child);
            return;
        }
        if (frame.pendingKey != null) {
            frame.dict().put(frame.pendingKey, child);
            frame.pendingKey = null;
        }
        // pendingKey 为空说明文件结构畸形（无键开括号），丢弃该子块
    }

    private static void putOrAdd(Frame frame, String key, Object value) {
        if (frame.container instanceof DatNode dict) {
            dict.put(key, value);
        } else if (frame.container instanceof List<?> list) {
            asList(list).add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object list) {
        return (List<Object>) list;
    }

    /** 拆出键与剩余部分；支持引号键。 */
    private static String[] splitKey(String line) {
        if (line.charAt(0) == '"') {
            for (int i = 1; i < line.length(); i++) {
                if (line.charAt(i) == '\\' && i + 1 < line.length()) {
                    i++;
                } else if (line.charAt(i) == '"') {
                    String key = unquote(line.substring(0, i + 1));
                    String rest = line.substring(i + 1).strip();
                    return new String[]{key, rest};
                }
            }
        }
        int split = firstWhitespace(line);
        if (split < 0) {
            return new String[]{line, ""};
        }
        return new String[]{line.substring(0, split), line.substring(split + 1).strip()};
    }

    private static int firstWhitespace(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') {
                return i;
            }
        }
        return -1;
    }

    /** 去掉值两侧引号并处理 \n、\t、\\、\" 转义；闭引号后的行内注释忽略。 */
    private static String unquote(String value) {
        if (value.isEmpty() || value.charAt(0) != '"') {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(next);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
