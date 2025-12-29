package org.zerolg.aidemo2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 审计监控面板控制器
 */
@Controller
@RequestMapping("/audit")
public class AuditDashboardController {

    /**
     * 审计监控面板首页
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/audit-dashboard.html";
    }

    /**
     * 直接访问监控面板
     */
    @GetMapping("")
    public String auditHome() {
        return "redirect:/audit-dashboard.html";
    }
}