package cn.slainc.crypto.hash;

public record Argon2Params(int memoryKb, int iterations, int parallelism, int hashLength, byte[] secret, byte[] additional) {

    public static final int DEFAULT_MEMORY_KB = 65536;

    public static final int DEFAULT_ITERATIONS = 3;

    public static final int DEFAULT_PARALLELISM = 1;

    public static final int DEFAULT_HASH_LENGTH = 32;

    public static Argon2Params defaults() {
        return new Argon2Params(DEFAULT_MEMORY_KB, DEFAULT_ITERATIONS, DEFAULT_PARALLELISM, DEFAULT_HASH_LENGTH, null, null);
    }

    public static Argon2Params of(int memoryKb, int iterations, int parallelism, int hashLength) {
        return new Argon2Params(memoryKb, iterations, parallelism, hashLength, null, null);
    }

    public Argon2Params withSecret(byte[] value) {
        return new Argon2Params(memoryKb, iterations, parallelism, hashLength, value, additional);
    }

    public Argon2Params withAdditional(byte[] value) {
        return new Argon2Params(memoryKb, iterations, parallelism, hashLength, secret, value);
    }

    public Argon2Params withHashLength(int value) {
        return new Argon2Params(memoryKb, iterations, parallelism, value, secret, additional);
    }

    public Argon2Params withIterations(int value) {
        return new Argon2Params(memoryKb, value, parallelism, hashLength, secret, additional);
    }

    public Argon2Params withMemoryKb(int value) {
        return new Argon2Params(value, iterations, parallelism, hashLength, secret, additional);
    }

    public Argon2Params withParallelism(int value) {
        return new Argon2Params(memoryKb, iterations, value, hashLength, secret, additional);
    }
}
