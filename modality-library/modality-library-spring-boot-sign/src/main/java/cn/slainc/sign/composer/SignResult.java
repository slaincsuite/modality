package cn.slainc.sign.composer;

public record SignResult(String canonical, String signature, String timestamp, String nonce) {
}
