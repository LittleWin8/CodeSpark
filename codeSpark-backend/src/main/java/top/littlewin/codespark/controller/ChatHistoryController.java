package top.littlewin.codespark.controller;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import top.littlewin.codespark.annotation.AuthCheck;
import top.littlewin.codespark.common.BaseResponse;
import top.littlewin.codespark.common.ResultUtils;
import top.littlewin.codespark.constant.UserConstant;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.dto.chathistory.ChatHistoryQueryRequest;
import top.littlewin.codespark.model.entity.ChatHistory;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.service.ChatHistoryService;
import top.littlewin.codespark.service.UserService;

import java.time.LocalDateTime;

/**
 * 对话历史 控制层。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

    /**
     * 查询应用对话历史
     *
     * @param appId App ID
     * @param pageSize 对话条数
     * @param lastCreateTime 最后对话时间
     * @param request 请求
     * @return 对话历史
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(@PathVariable Long appId,
                                                              @RequestParam(defaultValue = "10") int pageSize,
                                                              @RequestParam(required = false)LocalDateTime lastCreateTime,
                                                              HttpServletRequest request){
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistory> result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 管理员分页查询所有对话历史
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 对话历史分页
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listAllChatHistoryByPageForAdmin(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        // 查询数据
        QueryWrapper queryWrapper = chatHistoryService.getQueryWrapper(chatHistoryQueryRequest);
        Page<ChatHistory> result = chatHistoryService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(result);
    }


}
