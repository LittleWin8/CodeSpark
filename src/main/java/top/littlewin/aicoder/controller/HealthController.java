package top.littlewin.aicoder.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.littlewin.aicoder.common.BaseResponse;
import top.littlewin.aicoder.common.ResultUtils;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/")
    public BaseResponse<String> healthCheck(){
        return ResultUtils.success("ok");
    }
}
