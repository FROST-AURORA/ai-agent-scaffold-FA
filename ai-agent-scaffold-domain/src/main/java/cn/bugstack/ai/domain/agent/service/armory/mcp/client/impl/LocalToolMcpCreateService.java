package cn.bugstack.ai.domain.agent.service.armory.mcp.client.impl;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.service.armory.mcp.client.TooMcpCreateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class LocalToolMcpCreateService  implements TooMcpCreateService {

    @Resource
    protected ApplicationContext applicationContext;

    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.LocalParameters local = toolMcp.getLocal();
        String name = local.getName();

        // 从 Spring 容器中获取名为 "local.getName()" 的 ToolCallbackProvider bean
        ToolCallbackProvider localToolCallbackProvider = (ToolCallbackProvider) applicationContext.getBean(local.getName());
        log.info("tool local mcp initialize {}", name);

        // 从这个提供者那里获取所有的工具回调
        return localToolCallbackProvider.getToolCallbacks();
    }

}