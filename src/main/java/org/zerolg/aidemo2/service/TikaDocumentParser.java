package org.zerolg.aidemo2.service;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;
import org.zerolg.aidemo2.model.ParsedDocument;
import org.zerolg.aidemo2.utils.EncodingUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档解析服务 (基于 Apache Tika)
 * 支持解析多种格式：PDF, Word, Excel, PowerPoint, HTML 等
 */
@Service
public class TikaDocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(TikaDocumentParser.class);

    /**
     * 解析文档，提取纯文本内容
     * 
     * @param filePath 文件路径
     * @return 解析结果 (内容 + 元数据)
     * @throws IOException 文件读取异常
     * @throws TikaException Tika 解析异常
     * @throws SAXException SAX 解析异常
     */
    public ParsedDocument parseDocument(String filePath) throws IOException, TikaException, SAXException {
        logger.info("开始解析文档: {}", filePath);

        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString().toLowerCase();

        // 对于纯文本文件，使用编码检测
        if (fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".log")) {
            logger.info("检测到文本文件，使用编码检测: {}", filePath);
            try {
                String content = EncodingUtils.readFileWithCorrectEncoding(path);
                content = EncodingUtils.cleanContent(content);

                // 构建基本元数据
                Map<String, Object> metaMap = new HashMap<>();
                metaMap.put("Content-Type", "text/plain; charset=utf-8");
                metaMap.put("resourceName", fileName);
                metaMap.put("Content-Length", String.valueOf(content.length()));

                logger.info("文本文件解析完成: 字符数={}", content.length());

                return ParsedDocument.builder()
                        .content(content)
                        .metadata(metaMap)
                        .build();
            } catch (Exception e) {
                logger.warn("编码检测失败，回退到 Tika 解析: {}", e.getMessage());
                // 继续使用 Tika 解析
            }
        }

        // 使用 Tika 解析其他格式或文本文件解析失败时的回退
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        
        try (InputStream stream = new FileInputStream(filePath)) {
            parser.parse(stream, handler, metadata, context);

            logger.debug("文档元数据: 标题={}, 作者={}, 创建时间={}, 编码={}", 
                metadata.get("title"), 
                metadata.get("creator"),
                    metadata.get("created"),
                    metadata.get("Content-Encoding"));

            Map<String, Object> metaMap = new HashMap<>();
            for (String name : metadata.names()) {
                metaMap.put(name, metadata.get(name));
            }

            String content = handler.toString();

            // 清理可能的编码问题
            content = EncodingUtils.cleanContent(content);

            if (EncodingUtils.containsGarbledText(content)) {
                logger.warn("检测到可能的编码问题: {}", filePath);
            }

            logger.info("文档解析完成: 字符数={}, 检测到的MIME类型={}",
                    content.length(), metadata.get("Content-Type"));

            return ParsedDocument.builder()
                    .content(content)
                    .metadata(metaMap)
                    .build();
        }
    }
}
