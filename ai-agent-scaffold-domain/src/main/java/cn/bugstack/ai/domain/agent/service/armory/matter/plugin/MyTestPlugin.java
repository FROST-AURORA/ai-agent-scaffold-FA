package cn.bugstack.ai.domain.agent.service.armory.matter.plugin;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 自定义插件
 * 是一个测试用生命周期插件，分别在“收到用户消息前后链路入口”“Agent 执行前”“模型调用前”打印运行信息，用来验证 Plugin 机制是否能拿到 Runner 执行过程中的上下文
 * 这个类本质上是一个 Google ADK Runner 生命周期插件
 */
@Slf4j
@Service("myTestPlugin")
public class MyTestPlugin extends BasePlugin {

    public MyTestPlugin(String name) {
        super(name);
    }

    public MyTestPlugin() {
        super("MyTestPlugin");
    }

    /**
     * 用户输入信息
     * @param invocationContext：本次调用上下文，里面一般有 session、runner、事件流等运行信息
     * @param userMessage：用户输入内容
     * @return
     */
    @Override
    public Maybe<Content> onUserMessageCallback(InvocationContext invocationContext, Content userMessage) {
        log.info("用户输入信息:{}", userMessage.text());
        return super.onUserMessageCallback(invocationContext, userMessage);
    }

    /**
     * 智能体执行前
     * 从 BaseAgent 里取出 Agent 名称，打印当前即将执行的 Agent
     */
    @Override
    public Maybe<Content> beforeAgentCallback(BaseAgent agent, CallbackContext callbackContext) {
        String agentName = agent.name();
        log.info("智能体名称:{}", agentName);
        return super.beforeAgentCallback(agent, callbackContext);
    }

    /**
     * 模型执行前
     * 从 LlmRequest 里取出模型名称，打印当前即将执行的模型名称
     */
    @Override
    public Maybe<LlmResponse> beforeModelCallback(CallbackContext callbackContext, LlmRequest.Builder llmRequest) {
        Optional<String> model = llmRequest.build().model();
        log.info("ai 模型:{}", model.orElse(""));
        return super.beforeModelCallback(callbackContext, llmRequest);
    }

}
