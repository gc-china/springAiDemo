package org.zerolg.aidemo2.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zerolg.aidemo2.service.AiService;
import reactor.core.publisher.Flux;

@RestController
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 最终优化的混合路由流式接口 (Tool Override + 动态工具注册 + 多轮对话)
     */
    @GetMapping("/three-stage/stream")
    public Flux<String> threeStageHybridChatStream(@RequestParam String msg, 
                                                   @RequestParam(defaultValue = "default") String chatId) {
        return aiService.processQuery(chatId, msg);
    }
}
