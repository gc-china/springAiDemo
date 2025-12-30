// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.entity;

// 导入MyBatis Plus相关注解，用于ORM映射
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
// 导入Lombok注解，用于自动生成getter/setter等方法
import lombok.Data;

// 导入Java时间类
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 文档切片实体类
 *
 * 这是知识库系统的核心实体之一，代表文档的一个文本切片
 * 对应关系型数据库表: document_chunk
 *
 * 主要功能：
 * 1. 文档切片存储 - 存储文档分割后的文本片段
 * 2. 关系维护 - 维护切片与原文档的关联关系
 * 3. 索引支持 - 支持基于SQL的全文检索
 * 4. 管理界面 - 为后台管理系统提供数据支持
 *
 * 设计原理：
 * RAG（检索增强生成）系统需要将长文档分割成小的文本片段，
 * 每个片段都会被向量化并存储到向量数据库中。同时，为了
 * 支持传统的关键词检索和后台管理，我们在关系型数据库中
 * 也保存一份切片数据。
 *
 * 双存储架构：
 * - 向量数据库：存储文本的向量表示，用于语义检索
 * - 关系型数据库：存储文本内容和元数据，用于关键词检索和管理
 *
 * 与其他实体的关系：
 * - 多对一关系：多个DocumentChunk属于一个Document
 * - ID一致性：与向量存储中的记录保持相同的ID
 */
@Data // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
@TableName(value = "document_chunk", autoResultMap = true) // MyBatis Plus注解：指定对应的数据库表名，启用自动结果映射
public class DocumentChunk {

    /**
     * 切片唯一标识符
     *
     * 重要特性：与向量存储（vector_store）中的ID保持一致
     * 这样可以确保关系型数据库和向量数据库中的记录能够准确对应
     *
     * 使用INPUT类型表示ID由程序手动设置，而不是数据库自动生成
     */
    @TableId(type = IdType.INPUT) // MyBatis Plus注解：主键策略为手动输入
    private String id;

    /**
     * 所属文档ID
     *
     * 外键字段，关联到Document表的id字段
     * 建立切片与原文档的关联关系
     *
     * 用途：
     * - 数据完整性：确保每个切片都有明确的归属
     * - 查询优化：支持按文档查询所有切片
     * - 级联操作：删除文档时可以同时删除相关切片
     */
    private String documentId;

    /**
     * 切片在文档中的序号
     *
     * 表示当前切片在原文档中的位置顺序
     * 从0开始编号，按照文档的自然顺序递增
     *
     * 用途：
     * - 顺序重建：可以按序号重新组装完整文档
     * - 上下文定位：帮助理解切片在文档中的位置
     * - 相邻检索：查找相邻的文本片段
     */
    private Integer chunkIndex;

    /**
     * 切片文本内容
     * <p>
     * 存储文档切片的实际文本内容
     * 这是进行全文检索和内容展示的核心数据
     * <p>
     * 内容特点：
     * - 长度适中：通常控制在几百到几千字符
     * - 语义完整：尽量保持语义的完整性
     * - 重叠处理：可能与相邻切片有少量重叠
     */
    private String content;

    /**
     * 切片token数量
     *
     * 记录当前切片包含的token数量（通常等于字符数）
     *
     * 用途：
     * - 成本计算：AI处理成本通常按token计费
     * - 性能优化：控制单次处理的数据量
     * - 统计分析：分析文档的复杂度和规模
     */
    private Integer tokenCount;

    /**
     * 切片创建时间
     *
     * 记录切片记录的创建时间
     * 使用OffsetDateTime支持时区信息
     *
     * 用途：
     * - 审计追踪：记录数据处理的时间线
     * - 性能分析：分析文档处理的耗时
     * - 数据管理：支持按时间范围查询和清理
     */
    private OffsetDateTime createdAt;

    /**
     * 切片元数据
     * <p>
     * 使用JSON格式存储切片的扩展信息，可能包含：
     * - 切片哈希值：用于去重检测
     * - 向量信息：向量维度、相似度阈值等
     * - 处理信息：分割算法、处理参数等
     * - 业务信息：重要性评分、分类标签等
     * <p>
     * 使用JacksonTypeHandler自动处理JSON序列化和反序列化
     */
    @TableField(typeHandler = JacksonTypeHandler.class) // MyBatis Plus注解：指定JSON类型处理器
    private Map<String, Object> metadata;
}