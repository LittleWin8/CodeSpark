package top.littlewin.codespark.ai.tools;

import cn.hutool.json.JSONObject;

/**
 * 工具基类
 * 定义所有工具的通用接口，统一各工具的名称、展示与历史格式化逻辑。
 */
public abstract class BaseTool {

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的显示名称
     * 默认由英文名推导（驼峰转空格并首字母大写，如 modifyFile -> Modify File）；
     * 子类可覆写为本地化名称（如"修改文件"），用于 [选择工具] 等用户可见标记
     *
     * @return 工具显示名称
     */
    public String getDisplayName() {
        String spaced = getToolName().replaceAll("([a-z])([A-Z])", "$1 $2");
        if (spaced.isEmpty()) {
            return spaced;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库/历史）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);

    /**
     * 从工具参数中提取前端展示用的文件路径（用于 SSE 工具事件 path 字段）；无路径返回 null
     *
     * @param arguments 工具调用参数
     * @return 文件相对路径，无路径返回 null
     */
    public abstract String getToolPath(JSONObject arguments);
}