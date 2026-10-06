package Services;

import Models.AssetRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CSV 导出服务：列序对齐官方 AssetIdListExporter
 * （Name,GUID,Type,Origin,Legacy ID,Legacy Category），另附资产路径与备注；
 * UTF-8 带 BOM 以便 Excel 直接打开中文不乱码。
 */
public final class CsvExporter {

    private CsvExporter() {
    }

    public static void export(List<AssetRecord> records, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writer.write("Name,GUID,Type,Origin,Legacy ID,Legacy Category,Asset Path,Notes");
            writer.write("\r\n");
            for (AssetRecord record : records) {
                writeRow(writer, record);
            }
        }
    }

    private static void writeRow(BufferedWriter writer, AssetRecord record) throws IOException {
        StringBuilder row = new StringBuilder();
        addField(row, record.displayName());
        addField(row, record.getGuid());
        addField(row, record.getRawType());
        addField(row, record.getOrigin());
        addField(row, record.getId() > 0 ? String.valueOf(record.getId()) : record.getRawId());
        addField(row, record.getCategory().name());
        addField(row, record.getAssetPath().toString());
        addField(row, String.join("; ", record.getNotes()));
        writer.write(row.toString());
        writer.write("\r\n");
    }

    private static void addField(StringBuilder row, String value) {
        if (row.length() > 0) {
            row.append(',');
        }
        String field = value == null ? "" : value;
        // CSV 公式注入防护（OWASP）：Excel 会把以 = + - @ 开头的单元格当公式执行
        if (!field.isEmpty() && "=+-@".indexOf(field.charAt(0)) >= 0) {
            field = "'" + field;
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            field = '"' + field.replace("\"", "\"\"") + '"';
        }
        row.append(field);
    }
}
