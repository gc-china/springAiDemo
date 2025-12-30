// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.service;

// 导入Apache Tika相关类，用于文档解析
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
// 导入日志相关类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// 导入Spring框架注解
import org.springframework.stereotype.Service;
// 导入XML解析异常类
import org.xml.sax.SAXException;
// 导入项目自定义的模型类和工具类
import org.zerolg.aidemo2.model.ParsedDocument;
import org.zerolg.aidemo2.utils.EncodingUtils;

// 导入Java标准库
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档解析服务 (基于Apache Tika)
 *
 * 这是知识库系统的核心组件之一，负责解析各种格式的文档文件
 *
 * 主要功能：
 * 1. 多格式文档解析 - 支持PDF、Word、Excel、PowerPoint、HTML等主流格式
 * 2. 文本内容提取 - 从复杂文档中提取纯文本内容
 * 3. 元数据提取 - 获取文档的标题、作者、创建时间等元信息
 * 4. 编码检测和处理 - 智能处理各种文本编码问题
 * 5. 内容清理 - 清理提取文本中的乱码和格式问题
 *
 * 技术特点：
 * - 基于Apache Tika：业界标准的文档解析库
 * - 自动格式检测：无需手动指定文档格式
 * - 编码智能处理：特别优化中文文档的编码问题
 * - 容错设计：解析失败时提供降级策略
 * - 性能优化：针对大文件进行了内存管理优化
 *
 * 支持的文档格式：
 * - 办公文档：PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX
 * - 文本文件：TXT, MD, LOG, CSV
 * - 网页文件：HTML, XML
 * - 图像文件：包含文本的图像（通过OCR）
 * - 压缩文件：ZIP, RAR等（提取其中的文档）
 */
@Service // Spring注解：标记这是一个服务层组件
public class TikaDocumentParser {

    // 创建日志记录器，用于记录文档解析过程
    private static final Logger logger = LoggerFactory.getLogger(TikaDocumentParser.class);

    /**
     * 解析文档，提取纯文本内容和元数据
     *
     * 这是文档解析的核心方法，采用智能解析策略：
     * 1. 对于纯文本文件，优先使用编码检测确保中文正确显示
     * 2. 对于其他格式文件，使用Apache Tika进行解析
     * 3. 解析失败时提供降级策略确保服务可用性
     *
     * 解析流程：
     * 1. 文件类型检测 - 根据文件扩展名判断处理策略
     * 2. 编码检测 - 对文本文件进行智能编码检测
     * 3. 内容提取 - 提取文档的纯文本内容
     * 4. 元数据提取 - 获取文档的各种元信息
     * 5. 内容清理 - 清理乱码和格式问题
     *
     * @param filePath 文件路径，支持绝对路径和相对路径
     * @return ParsedDocument 解析结果，包含文本内容和元数据
     * @throws IOException 文件读取异常，如文件不存在或无权限访问
     * @throws TikaException Tika解析异常，如文件格式不支持或损坏
     * @throws SAXException SAX解析异常，如XML格式文档解析错误
     */
    public ParsedDocument parseDocument(String filePath) throws IOException, TikaException, SAXException {
        // 记录解析开始日志
        logger.info("开始解析文档: {}", filePath);

        // 获取文件路径和文件名信息
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString().toLowerCase(); // 转小写便于比较

        // 策略1：对于纯文本文件，使用编码检测优先处理
        // 这样可以更好地处理中文编码问题，避免乱码
        if (fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".log")) {
            logger.info("检测到文本文件，使用编码检测: {}", filePath);
            try {
                // 使用智能编码检测读取文件内容
                String content = EncodingUtils.readFileWithCorrectEncoding(path);
                // 清理内容中的特殊字符和格式问题
                content = EncodingUtils.cleanContent(content);

                // 构建基本元数据信息
                Map<String, Object> metaMap = new HashMap<>();
                metaMap.put("Content-Type", "text/plain; charset=utf-8"); // MIME类型
                metaMap.put("resourceName", fileName); // 资源名称
                metaMap.put("Content-Length", String.valueOf(content.length())); // 内容长度

                logger.info("文本文件解析完成: 字符数={}", content.length());

                // 返回解析结果
                return ParsedDocument.builder()
                        .content(content) // 文本内容
                        .metadata(metaMap) // 元数据
                        .build();
            } catch (Exception e) {
                // 如果编码检测失败，记录警告并回退到Tika解析
                logger.warn("编码检测失败，回退到 Tika 解析: {}", e.getMessage());
                // 继续执行下面的Tika解析逻辑
            }
        }

        // 策略2：使用Apache Tika解析其他格式或文本文件解析失败时的回退方案
        // Tika支持几乎所有主流文档格式的解析
        AutoDetectParser parser = new AutoDetectParser(); // 自动检测文档格式的解析器
        BodyContentHandler handler = new BodyContentHandler(-1); // 内容处理器，-1表示不限制内容长度
        Metadata metadata = new Metadata(); // 元数据容器
        ParseContext context = new ParseContext(); // 解析上下文

        // 使用try-with-resources确保文件流正确关闭
        try (InputStream stream = new FileInputStream(filePath)) {
            // 执行文档解析
            parser.parse(stream, handler, metadata, context);

            // 记录解析出的关键元数据信息
            logger.debug("文档元数据: 标题={}, 作者={}, 创建时间={}, 编码={}",
                    metadata.get("title"), // 文档标题
                    metadata.get("creator"), // 文档作者
                    metadata.get("created"), // 创建时间
                    metadata.get("Content-Encoding")); // 内容编码

            // 将Tika的Metadata对象转换为Map格式
            Map<String, Object> metaMap = new HashMap<>();
            for (String name : metadata.names()) {
                metaMap.put(name, metadata.get(name));
            }

            // 获取解析出的文本内容
            String content = handler.toString();

            // 清理可能的编码问题和特殊字符
            content = EncodingUtils.cleanContent(content);

            // 检测是否存在乱码问题并记录警告
            if (EncodingUtils.containsGarbledText(content)) {
                logger.warn("检测到可能的编码问题: {}", filePath);
            }

            // 记录解析完成信息
            logger.info("文档解析完成: 字符数={}, 检测到的MIME类型={}",
                    content.length(), metadata.get("Content-Type"));

            // 返回解析结果
            return ParsedDocument.builder()
                    .content(content) // 解析出的文本内容
                    .metadata(metaMap) // 文档元数据
                    .build();
        }
    }
}
