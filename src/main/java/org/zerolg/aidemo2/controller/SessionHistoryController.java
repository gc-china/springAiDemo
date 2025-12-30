// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.controller;

// 导入MyBatis Plus分页相关类
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// 导入Lombok注解，用于自动生成构造函数
import lombok.RequiredArgsConstructor;
// 导入Spring框架HTTP响应相关类
import org.springframework.http.ResponseEntity;
// 导入Spring Web相关注解
import org.springframework.web.bind.annotation.*;
// 导入项目自定义的实体类
import org.zerolg.aidemo2.entity.SessionArchive;
import org.zerolg.aidemo2.entity.SessionArchiveIndex;
// 导入项目自定义的服务层接口
import org.zerolg.aidemo2.service.memory.SessionArchiveService;

// 导入Java标准库
import java.util.Map;

/**
 * 会话历史查询控制器
 *
 * 这是会话历史管理系统的REST API控制器，提供会话历史的查询和管理功能
 *
 * 主要功能：
 * 1. 历史会话列表查询
 *    - 支持按用户ID查询历史会话
 *    - 支持分页查询，避免一次性加载过多数据
 *    - 返回会话索引信息，包含会话摘要和基本信息
 *
 * 2. 会话详情查询
 *    - 根据会话ID获取完整的会话内容
 *    - 包含所有消息的详细信息
 *    - 支持会话重放和内容回顾
 *
 * 3. 数据管理特点
 *    - 归档存储：历史会话存储在专门的归档表中
 *    - 索引优化：通过索引表提供快速的列表查询
 *    - 分离设计：索引和详情分离，提升查询性能
 *
 * 技术特点：
 * - RESTful设计：遵循REST API设计规范
 * - 分页支持：使用MyBatis Plus的分页功能
 * - 响应式设计：使用Optional和ResponseEntity提供优雅的错误处理
 * - 参数验证：支持默认参数和参数验证
 *
 * 使用场景：
 * - 用户查看历史对话记录
 * - 会话内容的搜索和回顾
 * - 数据分析和用户行为统计
 * - 客服系统的历史记录查询
 */
@RestController // Spring注解：标记这是一个REST控制器，自动序列化返回值为JSON
@RequestMapping("/api/history") // 统一的API基础路径，所有历史查询相关接口都以此开头
@RequiredArgsConstructor // Lombok注解：自动生成包含所有final字段的构造函数，用于依赖注入
public class SessionHistoryController {

    // 会话归档服务，提供历史会话的查询和管理功能
    // 使用final确保依赖注入后不可变，提高代码安全性
    private final SessionArchiveService archiveService;

    /**
     * 获取历史会话列表接口
     *
     * 提供分页的历史会话列表查询功能，支持按用户ID过滤
     *
     * 功能特点：
     * - 分页查询：避免一次性加载过多数据，提升性能
     * - 用户隔离：每个用户只能查看自己的历史会话
     * - 索引优化：使用会话索引表提供快速查询
     * - 默认参数：提供合理的默认分页参数
     *
     * 返回数据包含：
     * - 会话ID和标题
     * - 会话创建时间和最后更新时间
     * - 消息数量统计
     * - 会话摘要信息
     * - 分页信息（总数、当前页、总页数等）
     *
     * 请求示例：
     * GET /api/history?userId=user123&page=1&size=10
     *
     * @param userId 用户ID，必填参数，用于过滤该用户的历史会话
     * @param page 页码，可选参数，默认为1（第一页）
     * @param size 每页大小，可选参数，默认为10条记录
     * @return 分页的会话索引列表，包含分页信息和会话摘要
     */
    @GetMapping // HTTP GET请求映射，路径为 /api/history
    public ResponseEntity<Page<SessionArchiveIndex>> getHistoryList(
            @RequestParam String userId, // 从查询参数中获取用户ID
            @RequestParam(defaultValue = "1") int page, // 从查询参数中获取页码，默认为1
            @RequestParam(defaultValue = "10") int size) { // 从查询参数中获取每页大小，默认为10

        // 调用归档服务获取用户的历史会话列表
        // 返回MyBatis Plus的Page对象，包含分页信息和数据列表
        return ResponseEntity.ok(archiveService.getUserHistory(userId, page, size));
    }

    /**
     * 获取特定会话的完整详情接口
     *
     * 根据会话ID获取完整的会话内容，包含所有消息的详细信息
     *
     * 功能特点：
     * - 完整内容：返回会话中的所有消息和元数据
     * - 安全检查：验证会话是否存在，不存在时返回404
     * - 详细信息：包含消息内容、时间戳、角色信息等
     * - 格式化数据：返回结构化的会话数据，便于前端展示
     *
     * 返回数据包含：
     * - 会话基本信息（ID、标题、创建时间等）
     * - 完整的消息列表
     * - 每条消息的详细信息（内容、角色、时间戳等）
     * - 会话的元数据和配置信息
     *
     * 请求示例：
     * GET /api/history/conv_123456789
     *
     * @param conversationId 会话ID，路径参数，用于标识要查询的具体会话
     * @return 完整的会话详情，如果会话不存在则返回404 Not Found
     */
    @GetMapping("/{conversationId}") // HTTP GET请求映射，支持路径变量
    public ResponseEntity<SessionArchive> getHistoryDetail(@PathVariable String conversationId) {
        // 调用归档服务获取会话详情
        // 使用Optional处理可能为空的结果
        return archiveService.getSessionDetail(conversationId)
                .map(ResponseEntity::ok) // 如果会话存在，返回200 OK和会话详情
                .orElse(ResponseEntity.notFound().build()); // 如果会话不存在，返回404 Not Found
    }
}