package cn.slainc.crypto.hash;

public record ScryptParams(int cost, int blockSize, int parallelization, int keyLength) {

    public static final int DEFAULT_COST = 16384;

    public static final int DEFAULT_BLOCK_SIZE = 8;

    public static final int DEFAULT_PARALLELIZATION = 1;

    public static final int DEFAULT_KEY_LENGTH = 32;

    public static ScryptParams defaults() {
        return new ScryptParams(DEFAULT_COST, DEFAULT_BLOCK_SIZE, DEFAULT_PARALLELIZATION, DEFAULT_KEY_LENGTH);
    }

    public static ScryptParams of(int cost, int blockSize, int parallelization, int keyLength) {
        return new ScryptParams(cost, blockSize, parallelization, keyLength);
    }

    public ScryptParams withKeyLength(int value) {
        return new ScryptParams(cost, blockSize, parallelization, value);
    }
}
