package org.zerolg.aidemo2.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore; // ✅ 正确路径
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 向量数据库配置类
 * <p>
 * 功能说明：
 * 1. 配置PgVector向量数据库，用于存储文档向量
 * 2. 配置文本分割器，将长文档切分为小块
 * 3. 支持语义搜索和RAG（检索增强生成）功能
 * <p>
 * 什么是向量数据库：
 * - 专门存储和检索高维向量的数据库
 * - 支持相似性搜索（找到语义相似的文档）
 * - 是RAG系统的核心组件
 * <p>
 * PgVector选择原因：
 * - 基于PostgreSQL，稳定可靠
 * - 支持多种距离算法（余弦、欧几里得等）
 * - 支持HNSW索引，查询性能优秀
 * - 与Spring AI深度集成
 * <p>
 * RAG工作流程：
 * 1. 文档切分 → 2. 向量化 → 3. 存储到向量数据库
 * 4. 用户提问 → 5. 问题向量化 → 6. 相似性搜索
 * 7. 检索相关文档 → 8. 结合问题生成答案
 *
 * @author zerolg
 */
@Configuration
public class VectorStoreConfig {

    /**
     * 向量维度：决定向量的精度和存储空间
     * 1536是OpenAI text-embedding-ada-002模型的标准维度
     * 不同的嵌入模型有不同的维度要求
     */
    @Value("${spring.ai.vectorstore.pgvector.dimension:1536}")
    private int dimension;

    /**
     * 索引类型：影响查询性能和精度
     * HNSW (Hierarchical Navigable Small World)：
     * - 高性能近似最近邻搜索算法
     * - 查询速度快，适合大规模数据
     * - 内存占用相对较高
     */
    @Value("${spring.ai.vectorstore.pgvector.index-type:HNSW}")
    private PgVectorStore.PgIndexType indexType;

    /**
     * 是否初始化数据库模式
     * true：自动创建必要的表和索引
     * false：假设表已存在，适合生产环境
     */
    @Value("${spring.ai.vectorstore.pgvector.initialize-schema:true}")
    private boolean initializeSchema;

    /**
     * 配置文本分割器
     *
     * 为什么需要分割文本：
     * 1. 长文档包含多个主题，整体向量化会丢失细节
     * 2. AI模型有输入长度限制
     * 3. 小块文档更容易匹配用户问题
     *
     * TokenTextSplitter特点：
     * - 按token数量分割，而不是字符数
     * - 考虑AI模型的token限制
     * - 保持语义完整性
     *
     * @return 配置好的文本分割器
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    /**
     * 配置向量存储
     *
     * VectorStore是Spring AI的抽象接口，提供统一的向量操作API：
     * - add(): 添加文档向量
     * - delete(): 删除文档向量  
     * - similaritySearch(): 相似性搜索
     *
     * PgVectorStore实现特点：
     * - 基于PostgreSQL + pgvector扩展
     * - 支持多种距离算法
     * - 支持元数据过滤
     * - 事务安全
     *
     * 构建参数说明：
     * - jdbcTemplate: 数据库连接模板
     * - embeddingModel: 向量化模型（将文本转为向量）
     * - dimensions: 向量维度
     * - indexType: 索引类型（影响查询性能）
     * - distanceType: 距离算法（COSINE适合文本相似性）
     * - initializeSchema: 是否自动建表
     *
     * @param jdbcTemplate Spring提供的JDBC模板
     * @param embeddingModel 嵌入模型（由Spring AI自动配置）
     * @return 配置好的向量存储实例
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        // 使用建造者模式构建PgVectorStore
        // 注意：不同版本的Spring AI构造函数可能略有不同
        // 如果API有变化，IDE会提示使用PgVectorStoreOptions或类似的Builder
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                // 设置向量维度：必须与嵌入模型的输出维度一致
                .dimensions(dimension)
                // 设置索引类型：HNSW提供高性能近似搜索
                .indexType(indexType)
                // 设置距离算法：余弦距离适合文本语义相似性计算
                // 余弦距离关注向量方向，忽略长度，适合文本比较
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                // 是否初始化数据库模式：开发环境建议true，生产环境建议false
                .initializeSchema(initializeSchema)
                .build();
    }
}