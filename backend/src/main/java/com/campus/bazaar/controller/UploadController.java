package com.campus.bazaar.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    /** 上传根目录（默认 nginx 站点 imgs 目录） */
    @Resource
    private org.springframework.core.env.Environment environment;

    private String uploadDir() {
        return environment.getProperty("bazaar.upload-dir", "imgs");
    }

    @PostMapping("post")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        // 必须登录
        if (UserHolder.getUserId() == null) {
            return Result.fail("请先登录");
        }
        if (image == null || image.isEmpty() || image.getSize() > 5L * 1024 * 1024) {
            return Result.fail("请选择不超过 5MB 的图片");
        }
        try {
            String originalFilename = image.getOriginalFilename();
            if (StrUtil.isBlank(originalFilename) || !originalFilename.contains(".")) {
                return Result.fail("非法的文件名称");
            }
            String suffix = StrUtil.subAfter(originalFilename, ".", true).toLowerCase();
            if (!Arrays.asList("jpg", "jpeg", "png", "gif", "webp").contains(suffix)
                    || image.getContentType() == null || !image.getContentType().startsWith("image/")) {
                return Result.fail("仅支持 JPG、PNG、GIF 或 WebP 图片");
            }
            String fileName = createNewFileName(originalFilename);
            File target = new File(uploadDir(), fileName.substring(1).replace("/", File.separator));
            FileUtil.mkdir(target.getParentFile());
            image.transferTo(target);
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
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
