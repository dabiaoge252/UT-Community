package com.quanxiaoha.xiaohashu.oss.biz.controller;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.oss.biz.service.FileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author: 犬小哈
 * @date: 2024/4/4 13:22
 * @version: v1.0.0
 * @description: 文件控制器类，处理文件上传相关的HTTP请求
 **/
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private FileService fileService;

/**
 * 处理文件上传的POST请求接口
 * 该接口接收multipart/form-data格式的文件数据
 *
 * @param file 通过@RequestPart注解接收的文件对象，参数名为"file"
 * @return 返回一个Response对象，包含文件上传的结果信息
 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> uploadFile(@RequestPart(value = "file") MultipartFile file) {
    // 调用fileService的uploadFile方法处理文件上传逻辑
        return fileService.uploadFile(file);
    }

}
