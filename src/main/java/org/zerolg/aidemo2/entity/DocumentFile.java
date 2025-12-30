// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.entity;

// 导入MyBatis Plus相关注解，用于ORM映射
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
// 导入Lombok注解，用于自动生成getter/setter等方法
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 导入Java时间类
import java.time.LocalDateTime;

/**
 * 已处理文档记录表实体类
 *
 * 这个实体类用于记录已经处理过的文档文件信息，主要用于文件级别的去重检测
 * 对应数据库表名: document_file
 *
 * 核心功能：
 * 1. 文件去重：通过MD5哈希值避免重复处理相同的文件
 * 2. 处理状态跟踪：记录文件的处理状态（成功、失败等）
 * 3. 处理历史：保存文件处理的历史记录
 * 4. 性能优化：避免重复处理大文件，提高系统效率
 *
 * 设计原理：
 * 当用户上传文件时，系统首先计算文件的MD5哈希值，然后检查这个哈希值
 * 是否已经存在于document_file表中。如果存在且状态为COMPLETED，
 * 则跳过处理直接返回之前的结果；如果不存在或状态为FAILED，
 * 则进行文件处理并记录结果。
 *
 * MD5哈希的作用：
 * - 唯一标识：相同内容的文件会产生相同的MD5值
 * - 快速比较：MD5比较比文件内容比较快得多
 * - 去重检测：避免处理重复的文件内容
 * - 完整性校验：确保文件在传输过程中没有损坏
 *
 * 处理状态说明：
 * - COMPLETED: 文件处理成功，已经完成向量化和索引
 * - FAILED: 文件处理失败，可能是格式不支持或内容有问题
 * - PROCESSING: 文件正在处理中（如果需要异步处理）
 *
 * 使用场景：
 * - 文档上传前的去重检查
 * - 批量文档处理的进度跟踪
 * - 失败文档的重新处理
 * - 系统性能监控和统计
 */
@Data // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
@Builder // Lombok注解：自动生成Builder模式的构建器
@NoArgsConstructor // Lombok注解：自动生成无参构造函数
@AllArgsConstructor // Lombok注解：自动生成全参构造函数
@TableName("document_file") // MyBatis Plus注解：指定对应的数据库表名
public class DocumentFile {

    /**
     * 记录唯一标识符
     * <p>
     * 使用UUID作为主键，确保全局唯一性
     * ASSIGN_UUID策略会自动生成UUID字符串作为主键值
     */
    @TableId(type = IdType.ASSIGN_UUID) // MyBatis Plus注解：主键策略为自动分配UUID
    private String id;

    /**
     * 文件哈希值（MD5）
     * <p>
     * 这是文件内容的MD5哈希值，用于文件去重检测
     * <p>
     * MD5特点：
     * - 固定长度：总是32个十六进制字符
     * - 确定性：相同内容总是产生相同的MD5值
     * - 快速计算：相比文件内容比较，MD5比较非常快
     * - 冲突概率低：虽然理论上存在冲突，但实际应用中概率极低
     * <p>
     * 使用方式：
     * 1. 文件上传时计算MD5值
     * 2. 查询数据库检查是否已存在相同MD5的记录
     * 3. 如果存在且状态为COMPLETED，则跳过处理
     * 4. 如果不存在，则处理文件并保存记录
     */
    private String fileHash;

    /**
     * 原始文件名
     *
     * 保存用户上传时的原始文件名，用于：
     * - 用户界面显示：向用户显示友好的文件名
     * - 日志记录：便于问题排查和审计
     * - 文件类型识别：通过扩展名判断文件类型
     * - 下载功能：用户下载时使用原始文件名
     *
     * 注意事项：
     * - 可能包含特殊字符，需要适当处理
     * - 不同用户可能上传同名但内容不同的文件
     * - 文件名可能很长，需要考虑数据库字段长度限制
     */
    private String filename;

    /**
     * 处理状态
     * <p>
     * 记录文件的处理状态，可能的值：
     * - COMPLETED: 处理完成，文件已成功解析并建立索引
     * - FAILED: 处理失败，可能是文件格式不支持或内容有问题
     * - PROCESSING: 正在处理中（用于异步处理场景）
     * - PENDING: 等待处理（用于队列处理场景）
     * <p>
     * 状态流转：
     * PENDING -> PROCESSING -> COMPLETED/FAILED
     * <p>
     * 使用场景：
     * - 去重检查：只有COMPLETED状态的文件才能跳过重复处理
     * - 错误重试：FAILED状态的文件可以重新处理
     * - 进度监控：统计各种状态的文件数量
     * - 清理任务：清理长时间处于PROCESSING状态的记录
     */
    private String status;

    /**
     * 记录创建时间
     *
     * 记录这条记录被创建的时间，用于：
     * - 审计追踪：了解文件处理的时间线
     * - 性能分析：分析文件处理的耗时分布
     * - 数据清理：清理过期的处理记录
     * - 统计报表：按时间维度统计处理量
     *
     * 使用LocalDateTime的原因：
     * - 不包含时区信息，适合单一时区的应用
     * - 与数据库的DATETIME类型对应
     * - Java 8+的现代时间API，比Date更好用
     */
    private LocalDateTime createTime;
}