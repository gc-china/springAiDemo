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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        
        // 1. 创建 Tika 解析器 (自动检测文件类型)
        AutoDetectParser parser = new AutoDetectParser();
        
        // 2. 创建内容处理器 (限制最大字符数，避免 OOM)
        // -1 表示不限制，生产环境建议设置上限，如 100MB
        BodyContentHandler handler = new BodyContentHandler(-1);
        
        // 3. 创建元数据对象 (可选，用于获取文件属性)
        Metadata metadata = new Metadata();
        
        // 4. 创建解析上下文
        ParseContext context = new ParseContext();
        
        // 5. 解析文件
        try (InputStream stream = new FileInputStream(filePath)) {
            parser.parse(stream, handler, metadata, context);
            
            // 记录元数据 (可选)
            logger.debug("文档元数据: 标题={}, 作者={}, 创建时间={}", 
                metadata.get("title"), 
                metadata.get("creator"),
                metadata.get("created"));

            // 提取元数据到 Map
            Map<String, Object> metaMap = new HashMap<>();
            for (String name : metadata.names()) {
                metaMap.put(name, metadata.get(name));
            }

            String content = handler.toString();
            logger.info("文档解析完成: 字符数={}", content.length());

            return ParsedDocument.builder()
                    .content(content)
                    .metadata(metaMap)
                    .build();
        }
    }
}
