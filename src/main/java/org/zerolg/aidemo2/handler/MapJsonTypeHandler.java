package org.zerolg.aidemo2.handler;

import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * 自定义 JSON TypeHandler
 * 专门用于处理 Map<String, Object> 类型的 JSONB 字段
 * 解决 MyBatis Plus 默认 JacksonTypeHandler 在 autoResultMap 下泛型擦除的问题
 */
@MappedTypes({Map.class})
@MappedJdbcTypes({JdbcType.VARCHAR, JdbcType.OTHER}) // 兼容 Postgres 的 JSONB (OTHER) 和普通 VARCHAR
public class MapJsonTypeHandler extends AbstractJsonTypeHandler<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(MapJsonTypeHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 注册 Java 8 时间模块，防止 LocalDateTime 序列化报错
    static {
        OBJECT_MAPPER.findAndRegisterModules();
    }

    public MapJsonTypeHandler(Class<?> type) {
        super(type);
    }

    @Override
    public Map<String, Object> parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            // 明确指定反序列化为 Map<String, Object>
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            log.error("JSON 反序列化失败: {}", json, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJson(Map<String, Object> obj) {
        if (obj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败: {}", obj, e);
            throw new RuntimeException(e);
        }
    }
}