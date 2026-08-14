package org.example.aispingboot.controller;

import org.example.aispingboot.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/file")
public class FileController {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @PostMapping("/upload")
    public Result<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "businessType", required = false) String businessType,
            @RequestParam(value = "businessId", required = false) String businessId) {
        if (file.isEmpty()) {
            return Result.error("5001", "文件上传失败", "文件为空");
        }
        try {
            File dir = new File(uploadPath);
            if (!dir.exists()) dir.mkdirs();

            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String newFileName = UUID.randomUUID().toString() + ext;

            File dest = new File(dir, newFileName);
            file.transferTo(dest);

            String filePath = "/uploads/" + newFileName;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("filePath", filePath);
            result.put("fileName", originalName);
            result.put("fileSize", file.getSize());
            return Result.ok(result);
        } catch (IOException e) {
            return Result.error("5002", "文件上传失败", e.getMessage());
        }
    }
}
