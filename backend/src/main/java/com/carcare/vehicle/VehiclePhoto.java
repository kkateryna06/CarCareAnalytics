package com.carcare.vehicle;

import com.carcare.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class VehiclePhoto {
    private static final int MAX_BYTES = 384 * 1024;
    static final int MAX_BASE64 = MAX_BYTES / 3 * 4;
    private static final int MAX_EDGE = 1280;

    static byte[] decode(String encoded) {
        if (encoded == null) return null;
        if (encoded.isEmpty() || encoded.length() > MAX_BASE64) throw invalid();
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            if (data.length < 4 || data.length > MAX_BYTES || data[0] != (byte) 0xff || data[1] != (byte) 0xd8 ||
                data[data.length - 2] != (byte) 0xff || data[data.length - 1] != (byte) 0xd9) throw invalid();
            var reader = ImageIO.getImageReadersByFormatName("JPEG").next();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) > MAX_EDGE || reader.getHeight(0) > MAX_EDGE || reader.read(0) == null) throw invalid();
            } finally {
                reader.dispose();
            }
            return data;
        } catch (IllegalArgumentException | IOException ex) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return ApiException.invalid(Map.of("photoBase64", "Некорректный JPEG или превышен допустимый размер фотографии"));
    }

    private VehiclePhoto() {}
}
