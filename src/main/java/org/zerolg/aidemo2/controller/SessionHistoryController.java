package org.zerolg.aidemo2.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zerolg.aidemo2.entity.SessionArchive;
import org.zerolg.aidemo2.entity.SessionArchiveIndex;
import org.zerolg.aidemo2.service.memory.SessionArchiveService;

import java.util.Map;

/**
 * 会话历史查询接口
 */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class SessionHistoryController {

    private final SessionArchiveService archiveService;

    /**
     * 获取历史会话列表
     * GET /api/history?userId=123&page=1&size=10
     */
    @GetMapping
    public ResponseEntity<Page<SessionArchiveIndex>> getHistoryList(
            @RequestParam String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(archiveService.getUserHistory(userId, page, size));
    }

    /**
     * 获取特定会话的完整详情
     * GET /api/history/{conversationId}
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<SessionArchive> getHistoryDetail(@PathVariable String conversationId) {
        return archiveService.getSessionDetail(conversationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}