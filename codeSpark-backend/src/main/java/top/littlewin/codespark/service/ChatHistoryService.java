package top.littlewin.codespark.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import top.littlewin.codespark.model.dto.chathistory.ChatHistoryQueryRequest;
import top.littlewin.codespark.model.entity.ChatHistory;
import top.littlewin.codespark.model.entity.User;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话历史
     *
     * @param appId 应用 ID
     * @param message 消息
     * @param messageType 消息类型
     * @param userId 用户 ID
     * @return 是否添加成功
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 删除对话历史
     *
     * @param appId 应用 ID
     * @return 是否删除成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 判断应用是否已有 AI 对话历史（用于区分"创建应用"与"修改应用"）
     *
     * @param appId 应用 ID
     * @return 是否有 AI 历史
     */
    boolean hasAiChatMessage(Long appId);

    /**
     * 翻页查询 App 的对话历史
     *
     * @param appId 应用 ID
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    /**
     * 将历史对话添加到模型记忆
     *
     * @param appId 应用 ID
     * @param chatMemory 对话记忆
     * @param maxCount 获取最大记忆数量
     * @return 加载的到记忆的历史对话条数
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 构造查询条件
     *
     * @param chatHistoryQueryRequest 查询条件
     * @return 查询结果
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
