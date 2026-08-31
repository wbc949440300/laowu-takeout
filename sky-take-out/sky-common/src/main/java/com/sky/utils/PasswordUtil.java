package com.sky.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码哈希工具：BCrypt 加盐（盐随机内置于哈希结果中，无需单独存盐字段）
 * 兼容存量 MD5 数据的识别，用于登录时的透明升级判断
 */
public class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 对明文密码做 BCrypt 加盐哈希
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希（$2a$ 开头，60 位）
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码与 BCrypt 哈希是否匹配
     *
     * @param rawPassword  明文密码
     * @param encodedPassword BCrypt 哈希
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 判断存量密码是否为旧版 MD5 哈希（32 位十六进制）
     */
    public static boolean isLegacyMd5(String stored) {
        return stored != null && stored.matches("^[a-fA-F0-9]{32}$");
    }
}
