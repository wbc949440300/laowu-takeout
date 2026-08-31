package com.sky.test;

import com.sky.utils.PasswordUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * PasswordUtil 单元测试：覆盖 BCrypt 加盐、校验、旧版 MD5 识别（登录透明升级的判断依据）
 */
public class PasswordUtilTest {

    @Test
    public void encodeShouldGenerateBcryptHash() {
        String hash = PasswordUtil.encode("123456");
        //BCrypt 哈希以 $2a$ 开头，长度 60
        Assertions.assertTrue(hash.startsWith("$2a$"));
        Assertions.assertEquals(60, hash.length());
    }

    @Test
    public void encodeShouldUseRandomSalt() {
        //同一明文两次加密结果必须不同（盐随机），这是加盐的核心意义
        Assertions.assertNotEquals(PasswordUtil.encode("123456"), PasswordUtil.encode("123456"));
    }

    @Test
    public void matchesShouldVerifyCorrectPassword() {
        String hash = PasswordUtil.encode("abc@2024");
        Assertions.assertTrue(PasswordUtil.matches("abc@2024", hash));
        Assertions.assertFalse(PasswordUtil.matches("wrong", hash));
    }

    @Test
    public void matchesShouldHandleNullSafely() {
        Assertions.assertFalse(PasswordUtil.matches(null, PasswordUtil.encode("123456")));
        Assertions.assertFalse(PasswordUtil.matches("123456", null));
    }

    @Test
    public void isLegacyMd5ShouldRecognizeOldFormat() {
        //32 位十六进制 = 旧版 MD5；BCrypt 哈希不应被误判
        Assertions.assertTrue(PasswordUtil.isLegacyMd5("e10adc3949ba59abbe56e057f20f883e"));
        Assertions.assertFalse(PasswordUtil.isLegacyMd5(PasswordUtil.encode("123456")));
        Assertions.assertFalse(PasswordUtil.isLegacyMd5(null));
        Assertions.assertFalse(PasswordUtil.isLegacyMd5("short"));
    }
}
