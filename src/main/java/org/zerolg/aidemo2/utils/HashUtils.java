// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.utils;

// 导入字符编码相关类
import java.nio.charset.StandardCharsets;
// 导入安全哈希算法相关类
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 哈希计算工具类
 *
 * 这个工具类提供各种哈希算法的计算功能，主要用于数据完整性校验、去重、安全存储等场景
 *
 * 核心功能：
 * 1. SHA-256 哈希计算：提供安全的哈希算法
 * 2. 十六进制转换：将字节数组转换为可读的十六进制字符串
 * 3. 统一编码：使用 UTF-8 编码确保一致性
 *
 * 应用场景：
 * - 文档内容去重：计算文档内容的哈希值，避免重复存储
 * - 数据完整性校验：验证数据在传输过程中是否被篡改
 * - 缓存键生成：为复杂对象生成唯一的缓存键
 * - 密码存储：对敏感信息进行哈希处理（需要加盐）
 * - 文件指纹：为上传的文件生成唯一标识
 *
 * SHA-256 算法特点：
 * - 输出长度：256 位（32 字节）
 * - 安全性：目前被认为是安全的哈希算法
 * - 确定性：相同输入总是产生相同输出
 * - 雪崩效应：输入的微小变化会导致输出的巨大变化
 * - 不可逆：从哈希值无法推导出原始输入
 *
 * 设计原则：
 * - 静态方法：工具类不需要实例化
 * - 异常处理：将检查异常转换为运行时异常
 * - 标准编码：统一使用 UTF-8 编码
 * - 性能优化：使用 StringBuilder 进行字符串拼接
 */
public class HashUtils {

    /**
     * 计算字符串的 SHA-256 哈希值
     *
     * SHA-256 (Secure Hash Algorithm 256-bit) 是一种密码学哈希函数，
     * 属于 SHA-2 系列，由美国国家安全局 (NSA) 设计
     *
     * 算法流程：
     * 1. 输入预处理：将字符串转换为 UTF-8 字节数组
     * 2. 哈希计算：使用 SHA-256 算法计算哈希值
     * 3. 格式转换：将字节数组转换为十六进制字符串
     *
     * 为什么选择 SHA-256：
     * - 安全性高：目前没有已知的有效攻击方法
     * - 标准化：被广泛采用的国际标准
     * - 性能好：计算速度相对较快
     * - 输出固定：总是产生 256 位（64 个十六进制字符）的输出
     *
     * 使用场景：
     * - 文档去重：document_hash = getSha256(document_content)
     * - 缓存键：cache_key = "user_profile_" + getSha256(userId + timestamp)
     * - 数据校验：expected_hash.equals(getSha256(received_data))
     *
     * 注意事项：
     * - 不适合密码存储：密码应该使用 bcrypt、scrypt 等专门的密码哈希算法
     * - 需要加盐：如果用于安全目的，应该添加随机盐值
     * - 编码一致性：确保输入字符串的编码一致（这里统一使用 UTF-8）
     *
     * @param input 要计算哈希的输入字符串，不能为 null
     * @return 64 位十六进制字符串表示的 SHA-256 哈希值
     * @throws RuntimeException 如果 SHA-256 算法不可用（理论上不会发生）
     */
    public static String getSha256(String input) {
        try {
            // 1. 获取 SHA-256 消息摘要实例
            // MessageDigest 是 Java 提供的哈希算法抽象类
            // "SHA-256" 是算法名称，由 Java 安全提供者实现
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 2. 计算哈希值
            // 将输入字符串转换为 UTF-8 字节数组，然后计算哈希
            // StandardCharsets.UTF_8 确保编码的一致性，避免平台差异
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // 3. 转换为十六进制字符串
            // 创建 StringBuilder，预分配容量为字节数组长度的两倍
            // 因为每个字节需要两个十六进制字符表示
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);

            // 遍历每个字节，转换为十六进制表示
            for (byte b : encodedhash) {
                // 将字节转换为十六进制字符串
                // 0xff & b 确保字节被当作无符号数处理（避免负数问题）
                String hex = Integer.toHexString(0xff & b);

                // 如果十六进制字符串只有一位，前面补 0
                // 确保每个字节都用两位十六进制数表示
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            // 返回最终的十六进制字符串
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 标准算法，理论上不会抛出此异常
            // 但为了代码的健壮性，将检查异常转换为运行时异常
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}