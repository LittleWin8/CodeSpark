package top.littlewin.codespark.ai.tools;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具管理器
 * 统一管理所有工具，提供根据名称获取工具的能力，供 AiServices 装配与流式处理层使用。
 * 新增工具只需：继承 {@link BaseTool} 并标注为 Spring Bean，即自动注册，无需改动装配代码。
 */
@Slf4j
@Component
public class ToolManager {

    /**
     * 工具名称到工具实例的映射
     */
    private final Map<String, BaseTool> toolMap = new HashMap<>();

    /**
     * 自动注入所有工具
     */
    @Resource
    private BaseTool[] tools;

    /**
     * 初始化工具映射
     */
    @PostConstruct
    public void initTools() {
        for (BaseTool tool : tools) {
            toolMap.put(tool.getToolName(), tool);
            log.info("注册工具: {} -> {}", tool.getToolName(), tool.getDisplayName());
        }
        log.info("工具管理器初始化完成，共注册 {} 个工具", toolMap.size());
    }

    /**
     * 根据工具名称获取工具实例
     *
     * @param toolName 工具英文名称
     * @return 工具实例，未注册返回 null
     */
    public BaseTool getTool(String toolName) {
        return toolMap.get(toolName);
    }

    /**
     * 获取已注册的工具集合（供 AiServices.tools 装配）
     *
     * @return 工具实例集合
     */
    public BaseTool[] getAllTools() {
        return tools;
    }
}