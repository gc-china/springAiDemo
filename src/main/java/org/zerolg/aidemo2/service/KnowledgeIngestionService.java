// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.service;

// 导入Jackson JSON处理相关类
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
// 导入日志相关类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// 导入Spring框架注解和配置
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 导入Jakarta注解
import jakarta.annotation.PostConstruct;
// 导入项目自定义的模型类
import org.zerolg.aidemo2.model.IngestionStatus;
import org.zerolg.aidemo2.model.IngestionTask;

// 导入Java标准库
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识摄入服务
 * <p>
 * 这是知识库系统的核心服务之一，负责管理文档摄入的完整生命周期
 * <p>
 * 主要功能：
 * 1. 文件上传管理 - 处理用户上传的各种格式文档文件
 * 2. 任务队列管理 - 使用Redis Stream实现异步任务处理
 * 3. 状态跟踪 - 实时跟踪文档处理进度和状态
 * 4. 实时通知 - 通过SSE (Server-Sent Events) 推送处理进度
 * 5. 文件存储 - 安全地存储上传的文件到指定目录
 * <p>
 * 技术架构：
 * - 异步处理：使用Redis Stream实现任务队列，支持高并发
 * - 状态管理：使用Redis Hash存储任务状态，支持分布式部署
 * - 实时通信：使用SSE技术实现服务器到客户端的实时推送
 * - 文件管理：支持配置化的文件存储路径和安全检查
 * <p>
 * 处理流程：
 * 1. 接收文件上传请求
 * 2. 生成唯一的摄入任务ID
 * 3. 保存文件到本地存储
 * 4. 创建任务记录并发送到Redis Stream
 * 5. 初始化任务状态到Redis
 * 6. 返回任务ID给客户端
 * 7. 后台异步处理文档解析和向量化
 * 8. 通过SSE实时推送处理进度
 * <p>
 * 状态管理：
 * - PENDING: 等待处理
 * - PROCESSING: 正在处理
 * - COMPLETED: 处理完成
 * - FAILED: 处理失败
 */
@Service // Spring注解：标记这是一个服务层组件
public class KnowledgeIngestionService {

    // 创建日志记录器，用于记录摄入服务的运行过程
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    // Redis Stream和状态存储的键名常量
    private static final String STREAM_KEY = "ingestion:stream"; // 任务队列的Stream键
    private static final String STATUS_KEY_PREFIX = "ingestion:status:"; // 状态存储的键前缀
    // 依赖注入的核心组件
    private final StringRedisTemplate stringRedisTemplate; // Redis操作模板，用于Stream和Hash操作
    private final ObjectMapper objectMapper; // JSON对象映射器，用于序列化任务数据
    // 用于存储活跃的SSE连接，支持并发访问
    // Key: 摄入任务ID, Value: SSE发射器对象
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    // 文件上传目录配置，可通过配置文件自定义，默认为ragFiles目录
    @Value("${file.upload-dir:ragFiles}")
    private String uploadDir;

    /**
     * 构造函数 - 依赖注入
     *
     * @param stringRedisTemplate Redis操作模板
     * @param objectMapper JSON对象映射器
     */
    public KnowledgeIngestionService(StringRedisTemplate stringRedisTemplate,
                                     ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化上传目录
     *
     * 在服务启动后自动执行，确保上传目录存在
     * 使用@PostConstruct注解确保在依赖注入完成后执行
     */
    @PostConstruct
    private void initUploadDirectory() {
        try {
            // 创建上传目录（如果不存在），支持多级目录创建
            Files.createDirectories(Paths.get(uploadDir).toAbsolutePath());
            logger.info("上传目录已创建: {}", Paths.get(uploadDir).toAbsolutePath());
        } catch (IOException e) {
            // 目录创建失败时记录错误，但不中断服务启动
            logger.error("无法创建上传目录: {}", uploadDir, e);
        }
    }

    /**
     * 提交文档摄入任务
     *
     * 这是文档摄入的入口方法，处理完整的任务提交流程：
     * 1. 生成唯一的摄入任务ID
     * 2. 保存上传的文件到本地存储
     * 3. 初始化任务状态到Redis
     * 4. 发送任务到Redis Stream队列
     *
     * @param file 上传的文件对象，包含文件内容和元信息
     * @return 摄入任务ID，客户端可用此ID查询处理进度
     * @throws IOException 文件保存失败时抛出异常
     */
    public String submitTask(MultipartFile file) throws IOException {
        // 1. 生成唯一的摄入任务ID
        String ingestionId = UUID.randomUUID().toString();

        // 获取文件的基本信息
        String originalFilename = file.getOriginalFilename(); // 原始文件名
        String mimeType = file.getContentType(); // MIME类型

        // 2. 保存文件到本地存储
        // 使用"摄入ID_原始文件名"的格式避免文件名冲突
        Path filePath = Paths.get(uploadDir, ingestionId + "_" + originalFilename).toAbsolutePath();
        file.transferTo(filePath.toFile()); // 将上传文件保存到指定路径
        logger.info("文件已保存: {}", filePath);

        // 3. 初始化Redis状态记录
        String statusKey = STATUS_KEY_PREFIX + ingestionId;
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("status", IngestionStatus.PENDING.name()); // 初始状态为等待处理
        statusMap.put("progress", "0"); // 初始进度为0%
        statusMap.put("message", "Waiting for processing"); // 状态描述
        statusMap.put("fileName", originalFilename); // 保存原始文件名

        // 将状态信息保存到Redis Hash，并设置24小时过期时间
        stringRedisTemplate.opsForHash().putAll(statusKey, statusMap);
        stringRedisTemplate.expire(statusKey, java.time.Duration.ofHours(24));

        // 4. 发送任务到Redis Stream队列
        // 创建摄入任务对象
        IngestionTask task = new IngestionTask(ingestionId, filePath.toString(), originalFilename, mimeType);
        // 将任务对象转换为Map格式，便于Redis Stream存储
        Map<String, String> taskMap = objectMapper.convertValue(task, new TypeReference<Map<String, String>>() {
        });

        // 添加任务到Redis Stream，返回记录ID
        RecordId recordId = stringRedisTemplate.opsForStream().add(STREAM_KEY, taskMap);
        logger.info("任务已提交到 Stream: ingestionId={}, recordId={}", ingestionId, recordId);

        // 返回摄入任务ID给客户端
        return ingestionId;
    }

    /**
     * 获取任务状态
     *
     * 根据摄入任务ID查询当前的处理状态和进度
     *
     * @param ingestionId 摄入任务ID
     * @return 状态信息Map，包含status、progress、message等字段
     */
    public Map<Object, Object> getStatus(String ingestionId) {
        String statusKey = STATUS_KEY_PREFIX + ingestionId;
        // 从Redis Hash中获取所有状态字段
        return stringRedisTemplate.opsForHash().entries(statusKey);
    }

    /**
     * 订阅状态流 - 创建SSE连接
     *
     * 为指定的摄入任务创建Server-Sent Events连接，
     * 客户端可以通过此连接实时接收处理进度更新
     *
     * @param ingestionId 摄入任务ID
     * @return SSE发射器对象，用于向客户端推送事件
     */
    public SseEmitter subscribeStatus(String ingestionId) {
        // 创建SSE发射器，设置5分钟超时时间
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        // 将发射器存储到活跃连接映射中
        emitters.put(ingestionId, emitter);

        // 定义清理逻辑，在连接结束时移除发射器
        Runnable cleanup = () -> emitters.remove(ingestionId);

        // 注册各种事件的清理回调
        emitter.onCompletion(cleanup); // 正常完成时清理
        emitter.onTimeout(cleanup); // 超时时清理
        emitter.onError((e) -> cleanup.run()); // 错误时清理

        return emitter;
    }

    /**
     * 更新状态并推送到SSE
     *
     * 这是状态更新的核心方法，同时更新Redis存储和推送SSE事件
     *
     * @param ingestionId 摄入任务ID
     * @param status 新的处理状态
     * @param progress 处理进度（0-100）
     * @param message 状态描述信息
     */
    public void updateStatus(String ingestionId, IngestionStatus status, int progress, String message) {
        // 1. 更新Redis中的状态信息
        String statusKey = STATUS_KEY_PREFIX + ingestionId;
        Map<String, String> updates = new HashMap<>();
        updates.put("status", status.name()); // 状态枚举转字符串
        updates.put("progress", String.valueOf(progress)); // 进度转字符串
        if (message != null) {
            updates.put("message", message); // 更新消息（如果提供）
        }
        // 批量更新Redis Hash中的字段
        stringRedisTemplate.opsForHash().putAll(statusKey, updates);

        // 2. 推送SSE事件到客户端
        SseEmitter emitter = emitters.get(ingestionId);
        if (emitter != null) {
            try {
                // 构建要发送的事件数据
                Map<String, Object> event = new HashMap<>();
                event.put("status", status.name());
                event.put("progress", progress);
                event.put("message", message);

                // 发送事件到客户端
                emitter.send(event);

                // 如果任务已完成或失败，关闭SSE连接
                if (status == IngestionStatus.COMPLETED || status == IngestionStatus.FAILED) {
                    emitter.complete(); // 完成SSE连接
                    emitters.remove(ingestionId); // 从活跃连接中移除
                }
            } catch (IOException e) {
                // SSE发送失败时，移除连接（客户端可能已断开）
                emitters.remove(ingestionId);
            }
        }
    }
}