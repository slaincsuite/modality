package cn.slainc.storage.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.IIOByteBuffer;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExifReader {

    private static final Logger log = LoggerFactory.getLogger(ExifReader.class);

    private static final String JPEG_FORMAT = "javax_imageio_jpeg_image_1.0";

    private static final int APP1_MARKER = 0xE1;

    private static final Map<Integer, String> TAGS = Map.ofEntries(
            Map.entry(0x010F, "Make"),
            Map.entry(0x0110, "Model"),
            Map.entry(0x0112, "Orientation"),
            Map.entry(0x0132, "DateTime"),
            Map.entry(0x829A, "ExposureTime"),
            Map.entry(0x829D, "FNumber"),
            Map.entry(0x8827, "ISOSpeedRatings"),
            Map.entry(0x9003, "DateTimeOriginal"),
            Map.entry(0x920A, "FocalLength"),
            Map.entry(0xA002, "PixelXDimension"),
            Map.entry(0xA003, "PixelYDimension"),
            Map.entry(0xA434, "LensModel"));

    private static final int[] RATIONAL_TAGS = {0x829A, 0x829D, 0x920A};

    private ExifReader() {
    }

    public static Map<String, String> read(byte[] data) {
        byte[] app1 = app1Segment(data);
        if (app1 == null) {
            return Map.of();
        }
        try {
            return parse(app1);
        } catch (RuntimeException ex) {
            log.warn("解析 EXIF 信息失败，已跳过：{}", ex.getMessage());
            return Map.of();
        }
    }

    private static byte[] app1Segment(byte[] data) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (stream == null) {
                return null;
            }
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                IIOMetadata metadata = reader.getImageMetadata(0);
                if (metadata == null || !metadata.isStandardMetadataFormatSupported()) {
                    log.debug("图片未提供标准元数据，EXIF 解析跳过");
                }
                if (metadata == null) {
                    return null;
                }
                var root = metadata.getAsTree(JPEG_FORMAT);
                return findApp1(root);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            log.warn("读取 JPEG 元数据失败，已跳过 EXIF：{}", ex.getMessage());
            return null;
        }
    }

    private static byte[] findApp1(org.w3c.dom.Node node) {
        if (node instanceof IIOMetadataNode element) {
            String marker = element.getAttribute("MarkerTag");
            if (marker != null && !marker.isEmpty() && Integer.decode(marker) == APP1_MARKER) {
                Object userObject = element.getUserObject();
                if (userObject instanceof IIOByteBuffer buffer) {
                    byte[] copy = new byte[buffer.getLength()];
                    System.arraycopy(buffer.getData(), buffer.getOffset(), copy, 0, buffer.getLength());
                    return copy;
                }
                if (userObject instanceof byte[] bytes) {
                    return bytes;
                }
            }
        }
        var children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            byte[] found = findApp1(children.item(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Map<String, String> parse(byte[] app1) {
        // JDK 返回的 APP1 载荷可能带有 JPEG 段长度前缀，这里按 Exif 标识定位真实起点
        int offset = -1;
        for (int index = 0; index + 6 <= app1.length && index < 8; index++) {
            if (app1[index] == 'E' && app1[index + 1] == 'x' && app1[index + 2] == 'i'
                    && app1[index + 3] == 'f' && app1[index + 4] == 0 && app1[index + 5] == 0) {
                offset = index + 6;
                break;
            }
        }
        if (offset < 0) {
            return Map.of();
        }
        if (app1.length < offset + 8) {
            return Map.of();
        }
        boolean littleEndian = app1[offset] == 'I' && app1[offset + 1] == 'I';
        int ifd0 = offset + (int) readUnsignedInt(app1, offset + 4, littleEndian);
        Map<String, String> values = new LinkedHashMap<>();
        int exifIfd = readIfd(app1, ifd0, offset, littleEndian, values, false);
        if (exifIfd > 0 && exifIfd < app1.length) {
            readIfd(app1, exifIfd, offset, littleEndian, values, true);
        }
        return values;
    }

    private static int readIfd(byte[] data, int ifdOffset, int base, boolean littleEndian,
                               Map<String, String> values, boolean nested) {
        if (ifdOffset + 2 > data.length) {
            return -1;
        }
        int count = readUnsignedShort(data, ifdOffset, littleEndian);
        int exifIfdPointer = -1;
        for (int index = 0; index < count; index++) {
            int entry = ifdOffset + 2 + index * 12;
            if (entry + 12 > data.length) {
                break;
            }
            int tag = readUnsignedShort(data, entry, littleEndian);
            int type = readUnsignedShort(data, entry + 2, littleEndian);
            long length = readUnsignedInt(data, entry + 4, littleEndian);
            int valueOffset = entry + 8;
            int typeSize = typeSize(type);
            if (typeSize == 0) {
                continue;
            }
            long total = typeSize * length;
            if (total > 4) {
                valueOffset = base + (int) readUnsignedInt(data, entry + 8, littleEndian);
            }
            if (tag == 0x8769 && !nested) {
                exifIfdPointer = base + (int) readUnsignedInt(data, entry + 8, littleEndian);
                continue;
            }
            String name = TAGS.get(tag);
            if (name == null || valueOffset < 0 || valueOffset + total > data.length) {
                continue;
            }
            String value = readValue(data, valueOffset, type, (int) length, littleEndian, tag);
            if (value != null && !value.isBlank()) {
                values.putIfAbsent(name, value);
            }
        }
        return exifIfdPointer;
    }

    private static String readValue(byte[] data, int offset, int type, int count, boolean littleEndian, int tag) {
        return switch (type) {
            case 2 -> new String(data, offset, count, StandardCharsets.US_ASCII).replace("\0", "").trim();
            case 1, 7 -> String.valueOf(data[offset] & 0xFF);
            case 3 -> String.valueOf(readUnsignedShort(data, offset, littleEndian));
            case 4 -> String.valueOf(readUnsignedInt(data, offset, littleEndian));
            case 5 -> formatRational(readUnsignedInt(data, offset, littleEndian),
                    readUnsignedInt(data, offset + 4, littleEndian), tag);
            case 10 -> formatRational(readSignedInt(data, offset, littleEndian),
                    readSignedInt(data, offset + 4, littleEndian), tag);
            default -> null;
        };
    }

    private static String formatRational(long numerator, long denominator, int tag) {
        if (denominator == 0) {
            return numerator + "/0";
        }
        double value = (double) numerator / (double) denominator;
        if (tag == 0x829D) {
            return "f/" + String.format(java.util.Locale.ROOT, "%.1f", value);
        }
        if (tag == 0x920A) {
            return String.format(java.util.Locale.ROOT, "%.1fmm", value);
        }
        if (tag == 0x829A) {
            return value >= 1 ? String.format(java.util.Locale.ROOT, "%.1fs", value)
                    : "1/" + Math.round(1 / value) + "s";
        }
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static int typeSize(int type) {
        return switch (type) {
            case 1, 2, 7 -> 1;
            case 3 -> 2;
            case 4, 5, 10 -> 4;
            default -> 0;
        };
    }

    private static int readUnsignedShort(byte[] data, int offset, boolean littleEndian) {
        int first = data[offset] & 0xFF;
        int second = data[offset + 1] & 0xFF;
        return littleEndian ? (second << 8) | first : (first << 8) | second;
    }

    private static long readUnsignedInt(byte[] data, int offset, boolean littleEndian) {
        long first = data[offset] & 0xFFL;
        long second = data[offset + 1] & 0xFFL;
        long third = data[offset + 2] & 0xFFL;
        long fourth = data[offset + 3] & 0xFFL;
        return littleEndian ? (fourth << 24) | (third << 16) | (second << 8) | first
                : (first << 24) | (second << 16) | (third << 8) | fourth;
    }

    private static int readSignedInt(byte[] data, int offset, boolean littleEndian) {
        return (int) readUnsignedInt(data, offset, littleEndian);
    }
}