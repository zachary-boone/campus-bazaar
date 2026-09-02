package com.campus.bazaar.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.utils.SystemConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    /** 上传根目录（默认 nginx 站点 imgs 目录） */
    @Resource
    private org.springframework.core.env.Environment environment;

    private String uploadDir() {
        return environment.getProperty("bazaar.upload-dir", SystemConstants.IMAGE_UPLOAD_DIR);
    }

    @PostMapping("post")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        // 必须登录
        if (UserHolder.getUserId() == null) {
            return Result.fail("请先登录");
        }
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            if (StrUtil.isBlank(originalFilename) || !originalFilename.contains(".")) {
                return Result.fail("非法的文件名称");
            }
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(uploadDir(), fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.fail("文件上传失败");
        }
    }

    @DeleteMapping("/post/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        // 必须登录（防止匿名删除服务器文件）
        if (UserHolder.getUserId() == null) {
            return Result.fail("请先登录");
        }
        // 安全校验：只允许删除 uploadDir 内的普通文件，禁止路径穿越/绝对路径
        if (StrUtil.isBlank(filename)
                || filename.contains("..")
                || filename.startsWith("/")
                || filename.startsWith("\\")
                || filename.contains(":")) {
            return Result.fail("错误的文件名称");
        }
        File base = new File(uploadDir()).getAbsoluteFile();
        File file = new File(base, filename);
        try {
            // 规范化后必须仍位于上传根目录内（防御符号链接/多级目录穿越）
            String basePath = base.getCanonicalPath();
            String targetPath = file.getCanonicalPath();
            if (!targetPath.startsWith(basePath + File.separator)) {
                return Result.fail("错误的文件名称");
            }
        } catch (IOException e) {
            return Result.fail("错误的文件名称");
        }
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
