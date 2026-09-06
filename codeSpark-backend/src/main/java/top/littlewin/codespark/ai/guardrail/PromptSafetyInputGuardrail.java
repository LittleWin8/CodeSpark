package top.littlewin.codespark.ai.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 输入护轨：拦截"改写系统指令"型提示注入与超长/空白输入。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>不做内容词(破解/hack/越狱/绕过等)的子串匹配：代码生成场景里这些词常出现在合法需求中
 *       （如"做一个绕过验证码的登录演示页"），误杀率高于防护价值；</li>
 *   <li>只拦截高置信的指令覆盖模式：试图让模型忽略既定指令 / 冒充系统角色 / 注入新的系统指令。
 *       英文带词边界且大小写不敏感，中文用高精度句式，避免匹配普通对话；</li>
 *   <li>规则直接写死在类内（不提供配置开关/参数）：护轨始终全量执行，
 *       调整规则即改此处常量后发版；</li>
 *   <li>拒绝时记录 warn 日志（原因 + 截断的输入预览），便于上线后校准规则。</li>
 * </ul>
 */
@Slf4j
@Component
public class PromptSafetyInputGuardrail implements InputGuardrail {

    /** 单条输入最大长度（字符），超过直接拒绝 */
    private static final int MAX_INPUT_LENGTH = 4000;

    /** 内置高置信指令注入模式 */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // 英文：忽略/放弃前置指令
            Pattern.compile("(?i)\\bignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?|rules?)"),
            Pattern.compile("(?i)\\b(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before|previous)"),
            // 英文：注入 system 角色 / 新指令
            Pattern.compile("(?i)\\bsystem\\s*[:：]\\s*(?:you\\s+are|now|prompt)"),
            Pattern.compile("(?i)\\bnew\\s+(?:instructions?|commands?|prompts?)\\s*[:：]"),
            // 中文：忽略/无视既定指令
            Pattern.compile("(?:忽略|无视)(?:之前|上面|以上)?(?:的)?(?:所有|一切)?(?:指令|规则|要求|系统提示|提示词)"),
            Pattern.compile("(?:忽略|无视).{0,8}(?:系统提示|既定指令|原始设定)"),
            // 中文：冒充系统角色
            Pattern.compile("(?:你现在是|假装你是|扮演|你被设定为)\\s*系统")
    );

    /** 拒绝原因文案（会透传给前端，需用户可读） */
    private static final String BLANK_MESSAGE = "请输入内容后再发送";
    private static final String INJECTION_MESSAGE = "检测到试图改写系统指令的内容，请求已拦截；请勿要求模型忽略既定指令或冒充系统角色";

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();
        if (input == null || input.trim().isEmpty()) {
            return reject("blank", BLANK_MESSAGE, input);
        }
        String trimmed = input.trim();
        if (trimmed.length() > MAX_INPUT_LENGTH) {
            return reject("too-long", "内容过长（最多 " + MAX_INPUT_LENGTH + " 字），请精简后再发送", input);
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return reject("injection:" + pattern.pattern(), INJECTION_MESSAGE, input);
            }
        }
        return success();
    }

    private InputGuardrailResult reject(String reason, String message, String input) {
        log.warn("输入护轨拦截, reason={}, inputPreview={}", reason, preview(input));
        return fatal(message);
    }

    /** 输入预览：只截取前 120 字符，避免把超长/敏感内容整段打进日志 */
    private String preview(String input) {
        if (input == null) {
            return "";
        }
        String compact = input.replaceAll("\\s+", " ");
        return compact.length() <= 120 ? compact : compact.substring(0, 120) + "...";
    }
}
