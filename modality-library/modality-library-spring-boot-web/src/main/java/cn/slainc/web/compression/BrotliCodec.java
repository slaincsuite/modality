package cn.slainc.web.compression;

public interface BrotliCodec {

    boolean available();

    byte[] compress(byte[] source);
}
