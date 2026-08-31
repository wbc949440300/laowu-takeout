package com.sky.controller.user;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 用户端公共资源接口：图片代理下载
 * 兼容老版本小程序：小程序将图片完整 URL 传给后端，由后端代理拉取后回传字节流
 */
@RestController("userCommonController")
@RequestMapping("/user/common")
@Slf4j
public class CommonController {

    /**
     * 图片代理下载：仅允许 http/https 地址（防 SSRF 任意读取），拉取失败返回对应状态码
     *
     * @param name 图片完整 URL（如 OSS 地址）
     */
    @GetMapping("/download")
    public void download(@RequestParam String name, HttpServletResponse response) {
        if (name == null || !(name.startsWith("http://") || name.startsWith("https://"))) {
            response.setStatus(400);
            return;
        }
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(name);
            try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
                int status = httpResponse.getStatusLine().getStatusCode();
                if (status != 200) {
                    log.warn("图片代理失败：{}，状态：{}", name, status);
                    response.setStatus(status);
                    return;
                }
                HttpEntity entity = httpResponse.getEntity();
                String contentType = entity.getContentType() != null
                        ? entity.getContentType().getValue() : "image/png";
                response.setContentType(contentType);
                try (InputStream in = entity.getContent(); OutputStream out = response.getOutputStream()) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                    out.flush();
                }
            }
        } catch (Exception e) {
            log.error("图片代理异常：{}", name, e);
            response.setStatus(500);
        }
    }
}
