package org.example.aispingboot.controller;

import org.example.aispingboot.common.Result;
import org.example.aispingboot.service.DataAnalyticsService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/data-analytics")
public class DataAnalytics {

    @Resource
    private DataAnalyticsService dataAnalyticsService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(dataAnalyticsService.getOverview());
    }
}
