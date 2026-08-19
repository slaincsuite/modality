package cn.slainc.web.compression;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Brotli4jCodec implements BrotliCodec {

    private static final Logger log = LoggerFactory.getLogger(Brotli4jCodec.class);

    private final Encoder.Parameters parameters;

    private final boolean available;

    public Brotli4jCodec(int quality) {
        this.parameters = new Encoder.Parameters().setQuality(Math.clamp(quality, 0, 11));
        boolean loaded;
        try {
            Brotli4jLoader.ensureAvailability();
            loaded = Brotli4jLoader.isAvailable();
        } catch (Throwable throwable) {
            loaded = false;
            log.warn("brotli4j 原生库加载失败，Brotli 压缩已降级为不压缩：{}", throwable.getMessage());
        }
        this.available = loaded;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public byte[] compress(byte[] source) {
        try {
            return Encoder.compress(source, parameters);
        } catch (IOException exception) {
            throw new IllegalStateException("Brotli 压缩失败：" + exception.getMessage(), exception);
        }
    }
}
