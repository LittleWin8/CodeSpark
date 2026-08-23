package top.littlewin.codespark.service;

import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {

    /**
     * 下载项目源码压缩包
     *
     * @param projectPath 项目路径
     * @param downloadFileName 下载文件名
     * @param response 自定义响应内容
     * @return
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName,  HttpServletResponse response);
}
