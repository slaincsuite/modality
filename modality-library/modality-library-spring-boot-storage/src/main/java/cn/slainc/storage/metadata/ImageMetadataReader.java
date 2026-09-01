package cn.slainc.storage.metadata;

import cn.slainc.storage.StorageException;
import cn.slainc.storage.model.ImageMetadata;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public final class ImageMetadataReader {

    private ImageMetadataReader() {
    }

    public static ImageMetadata read(byte[] data) {
        if (data == null || data.length == 0) {
            throw new StorageException("图片内容为空，无法读取元数据");
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (stream == null) {
                throw new StorageException("无法创建图片输入流");
            }
            Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new StorageException("无法识别的图片格式，读取元数据失败");
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                String format = reader.getFormatName();
                Map<String, String> exif = ExifReader.read(data);
                return new ImageMetadata(width, height, format, exif);
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new StorageException("读取图片元数据失败", ex);
        }
    }
}
