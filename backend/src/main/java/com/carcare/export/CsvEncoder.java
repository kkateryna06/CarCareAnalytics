package com.carcare.export;

import com.carcare.exception.ApiException;
import org.springframework.http.HttpStatus;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvEncoder {
    private CsvEncoder() {}
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static ApiException tooLarge() { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EXPORT_TOO_LARGE", "Слишком большой экспорт. Выберите меньший период или категорию."); }
    /** Quote every cell, escape quotes, and neutralize formula-like user text for spreadsheet readers. */
    public static String cell(String value) {
        String text = value == null ? "" : value;
        String leading = text.stripLeading();
        if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0 || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    public static byte[] encode(List<List<String>> rows) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
        for (var row : rows) {
            byte[] line = (row.stream().map(CsvEncoder::cell).collect(java.util.stream.Collectors.joining(",")) + "\r\n").getBytes(StandardCharsets.UTF_8);
            if (out.size() + line.length > MAX_BYTES) throw tooLarge();
            out.writeBytes(line);
        }
        return out.toByteArray();
    }
}
