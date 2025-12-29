package org.zerolg.aidemo2.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 编码处理工具类
 * 用于检测和处理文件编码问题
 */
public class EncodingUtils {

    private static final Logger logger = LoggerFactory.getLogger(EncodingUtils.class);

    // 常见的中文编码
    private static final List<Charset> COMMON_CHARSETS = Arrays.asList(
            StandardCharsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB2312"),
            Charset.forName("Big5"),
            StandardCharsets.ISO_8859_1
    );

    /**
     * 检测文件编码并读取内容
     *
     * @param filePath 文件路径
     * @return 正确编码的文件内容
     */
    public static String readFileWithCorrectEncoding(Path filePath) throws IOException {
        // 首先尝试 UTF-8
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            if (!containsGarbledText(content)) {
                logger.debug("文件使用 UTF-8 编码: {}", filePath);
                return content;
            }
        } catch (Exception e) {
            logger.debug("UTF-8 读取失败，尝试其他编码: {}", filePath);
        }

        // 尝试其他编码
        for (Charset charset : COMMON_CHARSETS) {
            if (charset.equals(StandardCharsets.UTF_8)) {
                continue; // 已经尝试过了
            }

            try {
                String content = Files.readString(filePath, charset);
                if (!containsGarbledText(content)) {
                    logger.info("文件使用 {} 编码: {}", charset.name(), filePath);
                    return content;
                }
            } catch (Exception e) {
                logger.debug("编码 {} 读取失败: {}", charset.name(), filePath);
            }
        }

        // 如果所有编码都失败，使用默认 UTF-8
        logger.warn("无法确定正确编码，使用 UTF-8: {}", filePath);
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 检测文本是否包含乱码
     *
     * @param content 文本内容
     * @return 是否包含乱码
     */
    public static boolean containsGarbledText(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        // 检查替换字符 (�)
        if (content.contains("\uFFFD")) {
            return true;
        }

        // 检查 BOM 乱码
        if (content.startsWith("\uFEFF") || content.contains("锘�")) {
            return true;
        }

        // 检查控制字符
        for (char c : content.toCharArray()) {
            if (c >= 0x00 && c <= 0x08 || c >= 0x0B && c <= 0x0C || c >= 0x0E && c <= 0x1F) {
                return true;
            }
        }

        // 检查常见的 GBK 转 UTF-8 乱码模式
        if (content.contains("涓枃") || content.contains("鏂囨。")) {
            return true;
        }

        return false;
    }

    /**
     * 清理 BOM 和其他编码问题
     *
     * @param content 原始内容
     * @return 清理后的内容
     */
    public static String cleanContent(String content) {
        if (content == null) {
            return null;
        }

        // 移除 BOM
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        // 移除其他常见的编码问题字符
        content = content.replace("锘�", "");
        content = content.replace("\uFFFD", "");

        return content;
    }
}