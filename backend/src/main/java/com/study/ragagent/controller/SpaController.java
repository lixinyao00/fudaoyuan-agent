package com.study.ragagent.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA 路由兜底：前端 history 模式下刷新 /chat、/admin 等路径不 404
 */
@Controller
public class SpaController {

    @RequestMapping(value = {"/chat", "/login", "/admin", "/admin/**"})
    public String spaFallback() {
        return "forward:/index.html";
    }
}