// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.utils;

// 导入日志相关类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 导入文件和编码处理相关类
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 编码处理工具类
 *
 * 这个工具类专门用于处理文件编码检测和转换问题，特别是中文文档的编码处理
 *
 * 核心功能：
 * 1. 自动检测文件编码：支持 UTF-8、GBK、GB2312、Big5 等常见中文编码
 * 2. 乱码检测：识别各种类型的乱码字符和编码问题
 * 3. 内容清理：移除 BOM 标记和其他编码问题字符
 * 4. 智能读取：自动选择正确的编码读取文件内容
 *
 * 应用场景：
 * - 文档上传时的编码检测和转换
 * - 历史文档的编码修复
 * - 跨平台文件编码兼容性处理
 * - 中文文档的正确显示
 *
 * 编码问题的常见原因：
 * 1. 历史遗留：老系统使用 GBK/GB2312 编码
 * 2. 平台差异：Windows 默认 GBK，Linux/Mac 默认 UTF-8
 * 3. 工具差异：不同编辑器的默认编码不同
 * 4. 传输问题：网络传输过程中编码信息丢失
 *
 * 设计原则：
 * - 优先 UTF-8：现代标准，支持所有字符
 * - 渐进尝试：从最可能的编码开始尝试
 * - 乱码检测：通过多种策略识别编码错误
 * - 容错处理：即使检测失败也能提供可用结果
 */
public class EncodingUtils {

    // 创建日志记录器，用于记录编码处理过程
    private static final Logger logger = LoggerFactory.getLogger(EncodingUtils.class);

    /**
     * 常见的中文编码列表
     * <p>
     * 按优先级排序，UTF-8 优先，然后是各种中文编码
     * <p>
     * 编码说明：
     * - UTF-8: Unicode 的 8 位编码，现代标准，支持全球所有字符
     * - GBK: 中国国家标准，扩展 GB2312，支持简繁体中文
     * - GB2312: 中国国家标准，支持简体中文和常用符号
     * - Big5: 台湾地区标准，主要支持繁体中文
     * - ISO_8859_1: 西欧字符集，有时用作二进制数据的容器
     */
    private static final List<Charset> COMMON_CHARSETS = Arrays.asList(
            StandardCharsets.UTF_8,        // 现代标准编码，优先尝试
            Charset.forName("GBK"),        // 中国大陆常用编码，兼容 GB2312
            Charset.forName("GB2312"),     // 中国国家标准，简体中文
            Charset.forName("Big5"),       // 台湾地区标准，繁体中文
            StandardCharsets.ISO_8859_1    // 西欧字符集，最后尝试
    );

    /**
     * 检测文件编码并读取内容
     *
     * 这是核心方法，通过多种编码尝试读取文件，自动选择最合适的编码
     *
     * 检测策略：
     * 1. 优先尝试 UTF-8：现代文件大多使用 UTF-8 编码
     * 2. 乱码检测：读取后检查是否包含乱码字符
     * 3. 逐一尝试：如果 UTF-8 失败，尝试其他常见编码
     * 4. 降级处理：如果所有编码都失败，使用 UTF-8 强制读取
     *
     * 乱码检测原理：
     * - 替换字符检测：查找 Unicode 替换字符 (U+FFFD)
     * - BOM 乱码检测：检测字节顺序标记的乱码
     * - 控制字符检测：查找不应出现的控制字符
     * - 模式匹配：检测常见的编码转换错误模式
     *
     * @param filePath 文件路径，支持各种文件类型
     * @return 正确编码的文件内容字符串
     * @throws IOException 如果文件读取失败（文件不存在、权限不足等）
     */
    public static String readFileWithCorrectEncoding(Path filePath) throws IOException {
        // 第一步：优先尝试 UTF-8 编码
        // UTF-8 是现代标准，大多数新文件都使用这种编码
        try {
            // 使用 UTF-8 编码读取文件内容
            String content = Files.readString(filePath, StandardCharsets.UTF_8);

            // 检查读取的内容是否包含乱码
            if (!containsGarbledText(content)) {
                // 如果没有乱码，说明 UTF-8 编码正确
                logger.debug("文件使用 UTF-8 编码: {}", filePath);
                return content;
            }
        } catch (Exception e) {
            // UTF-8 读取失败，可能是编码不匹配或文件损坏
            logger.debug("UTF-8 读取失败，尝试其他编码: {}", filePath);
        }

        // 第二步：尝试其他常见编码
        // 如果 UTF-8 失败，逐一尝试其他可能的编码
        for (Charset charset : COMMON_CHARSETS) {
            // 跳过已经尝试过的 UTF-8
            if (charset.equals(StandardCharsets.UTF_8)) {
                continue;
            }

            try {
                // 使用当前编码尝试读取文件
                String content = Files.readString(filePath, charset);

                // 检查是否包含乱码
                if (!containsGarbledText(content)) {
                    // 找到正确的编码，记录并返回
                    logger.info("文件使用 {} 编码: {}", charset.name(), filePath);
                    return content;
                }
            } catch (Exception e) {
                // 当前编码读取失败，尝试下一个
                logger.debug("编码 {} 读取失败: {}", charset.name(), filePath);
            }
        }

        // 第三步：降级处理
        // 如果所有编码都失败，使用 UTF-8 强制读取
        // 虽然可能有乱码，但至少能读取到内容
        logger.warn("无法确定正确编码，使用 UTF-8: {}", filePath);
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 检测文本是否包含乱码
     *
     * 通过多种策略检测文本中的乱码字符，判断编码是否正确
     *
     * 检测策略：
     * 1. 替换字符检测：Unicode 标准的替换字符 (U+FFFD)
     * 2. BOM 乱码检测：字节顺序标记的错误显示
     * 3. 控制字符检测：不应出现在文本中的控制字符
     * 4. 模式匹配检测：常见的编码转换错误模式
     *
     * 乱码产生的原因：
     * - 编码不匹配：用错误的编码解析文件
     * - 编码转换错误：在不同编码间转换时出错
     * - 传输损坏：网络传输或存储过程中数据损坏
     * - BOM 处理错误：字节顺序标记处理不当
     *
     * @param content 要检测的文本内容
     * @return true 如果包含乱码，false 如果编码正确
     */
    public static boolean containsGarbledText(String content) {
        // 空值检查：空内容不算乱码
        if (content == null || content.isEmpty()) {
            return false;
        }

        // 检测 1：Unicode 替换字符 (U+FFFD)
        // 当解码器遇到无法识别的字节序列时，会插入这个字符
        if (content.contains("\uFFFD")) {
            return true;
        }

        // 检测 2：BOM 乱码
        // BOM (Byte Order Mark) 在某些编码转换中会显示为乱码
        if (content.startsWith("\uFEFF") || content.contains("锘�")) {
            return true;
        }

        // 检测 3：控制字符
        // 检查是否包含不应出现在普通文本中的控制字符
        for (char c : content.toCharArray()) {
            // ASCII 控制字符范围（除了常见的换行、制表符等）
            if (c >= 0x00 && c <= 0x08 ||     // NULL 到 BACKSPACE
                    c >= 0x0B && c <= 0x0C ||     // 垂直制表符到换页符
                    c >= 0x0E && c <= 0x1F) {     // 其他控制字符
                return true;
            }
        }

        // 检测 4：常见的 GBK 转 UTF-8 乱码模式
        // 这些是 GBK 编码的中文在 UTF-8 环境下的典型乱码表现
        if (content.contains("涓枃") ||      // "中文" 的 GBK->UTF-8 乱码
                content.contains("鏂囨。")) {    // "文档" 的 GBK->UTF-8 乱码
            return true;
        }

        // 如果所有检测都通过，认为没有乱码
        return false;
    }

    /**
     * 清理 BOM 和其他编码问题
     *
     * 移除文本中的各种编码问题字符，提供干净的文本内容
     *
     * 清理内容：
     * 1. BOM 标记：字节顺序标记，在文本显示时不需要
     * 2. 乱码字符：各种编码转换产生的错误字符
     * 3. 替换字符：Unicode 替换字符
     *
     * BOM (Byte Order Mark) 说明：
     * - UTF-8 BOM: EF BB BF，显示为 U+FEFF
     * - 用于标识文件的字节顺序和编码类型
     * - 在文本处理时通常需要移除
     * - 某些编辑器会自动添加，某些会自动移除
     *
     * @param content 原始文本内容，可能包含编码问题
     * @return 清理后的干净文本内容
     */
    public static String cleanContent(String content) {
        // 空值检查：null 内容直接返回
        if (content == null) {
            return null;
        }

        // 清理 1：移除 UTF-8 BOM 标记
        // BOM 在文件开头，显示为 U+FEFF 字符
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        // 清理 2：移除其他常见的编码问题字符
        content = content.replace("锘�", "");      // 常见的 BOM 乱码显示
        content = content.replace("\uFFFD", "");  // Unicode 替换字符

        // 返回清理后的内容
        return content;
    }
}