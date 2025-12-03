package org.zerolg.aidemo2.service;

import org.zerolg.aidemo2.model.SessionEvent;

/**
 * 会话归档服务接口
 * 
 * 职责：
 * 定义归档操作的标准接口。具体的实现可以是：
 * 1. 打印日志 (开发/调试模式)
 * 2. 写入 PostgreSQL/PGVector (生产模式)
 * 3. 上传到 S3 (冷存储模式)
 */
public interface SessionArchiver {

    /**
     * 归档会话事件
     * 
     * @param event 需要归档的会话事件
     */
    void archive(SessionEvent event);
}
