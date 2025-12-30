// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.entity;

// 导入MyBatis Plus相关注解，用于ORM映射
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
// 导入Lombok注解，用于自动生成getter/setter等方法
import lombok.Data;

// 导入Java时间类
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 文档元数据实体类
 *
 * 这是知识库系统的核心实体之一，代表一个完整的文档记录
 * 对应数据库表: document
 *
 * 主要功能：
 * 1. 文档基本信息存储 - 标题、路径、MIME类型等
 * 2. 文档统计信息 - token数量、切片数量等
 * 3. 元数据管理 - 灵活的JSON格式元数据存储
 * 4. 生命周期管理 - 创建时间、更新时间、软删除等
 *
 * 设计特点：
 * - 软删除支持：使用isDeleted字段实现逻辑删除
 * - 元数据扩展：使用JSON字段存储灵活的元数据信息
 * - 时间戳管理：记录文档的创建和更新时间
 * - UUID主键：使用UUID作为主键，支持分布式环境
 *
 * 与其他实体的关系：
 * - 一对多关系：一个Document对应多个DocumentChunk（文档切片）
 * - 关联向量存储：通过documentId关联向量数据库中的向量记录
 */
@Data // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
@TableName(value = "document", autoResultMap = true) // MyBatis Plus注解：指定对应的数据库表名，启用自动结果映射
public class Document {

    /**
     * 文档唯一标识符
     * <p>
     * 使用UUID作为主键，确保在分布式环境下的唯一性
     * 自动分配UUID，无需手动设置
     */
    @TableId(type = IdType.ASSIGN_UUID) // MyBatis Plus注解：主键策略为自动分配UUID
    private String id;

    /**
     * 文档标题
     *
     * 通常来源于：
     * - 文件名（对于上传的文件）
     * - 文档内部的标题元数据
     * - 用户手动指定的标题
     */
    private String title;

    /**
     * 文档来源URL
     *
     * 用于记录文档的原始来源地址，如：
     * - 网页URL（爬取的网页文档）
     * - 文件下载地址
     * - API接口地址等
     *
     * 注意：对于本地上传的文件，此字段可能为空
     */
    private String sourceUrl;

    /**
     * 文件存储路径
     *
     * 记录文档文件在服务器上的存储位置
     * - 对于上传文件：存储本地文件系统路径
     * - 对于纯文本摄入：此字段为空
     * - 支持相对路径和绝对路径
     */
    private String filePath;

    /**
     * MIME类型
     *
     * 标识文档的媒体类型，如：
     * - application/pdf（PDF文件）
     * - application/vnd.openxmlformats-officedocument.wordprocessingml.document（Word文档）
     * - text/plain（纯文本）
     * - text/html（HTML文档）
     */
    private String mimeType;

    /**
     * 文档总token数量
     *
     * 记录文档包含的总字符数或token数量
     * 用于：
     * - 统计分析
     * - 成本计算（AI处理成本通常按token计费）
     * - 性能优化（大文档的特殊处理）
     */
    private Integer totalTokens;

    /**
     * 文档切片数量
     *
     * 记录文档被分割成多少个切片（chunk）
     * 用于：
     * - 统计分析
     * - 验证数据完整性
     * - 性能监控
     */
    private Integer chunkCount;

    /**
     * 文档元数据
     * <p>
     * 使用JSON格式存储灵活的元数据信息，可能包含：
     * - 文件信息：文件大小、创建时间、修改时间
     * - 文档属性：作者、创建者、关键词、摘要
     * - 处理信息：解析器类型、编码格式、处理时间
     * - 业务信息：分类、标签、权限等
     * <p>
     * 使用JacksonTypeHandler自动处理JSON序列化和反序列化
     */
    @TableField(typeHandler = JacksonTypeHandler.class) // MyBatis Plus注解：指定JSON类型处理器
    private Map<String, Object> metadata;

    /**
     * 创建时间
     *
     * 记录文档记录的创建时间
     * 使用OffsetDateTime支持时区信息
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     *
     * 记录文档记录的最后更新时间
     * 每次修改文档信息时都会更新此字段
     */
    private OffsetDateTime updatedAt;

    /**
     * 软删除标志
     * <p>
     * 实现逻辑删除功能：
     * - false: 正常状态（默认值）
     * - true: 已删除状态
     * <p>
     * 使用软删除的优势：
     * - 数据安全：避免误删除造成的数据丢失
     * - 审计追踪：保留删除记录用于审计
     * - 性能优化：避免级联删除的复杂操作
     * - 数据恢复：支持删除后的数据恢复
     */
    @TableLogic(value = "false", delval = "true") // MyBatis Plus注解：逻辑删除配置
    private Boolean isDeleted;
}
