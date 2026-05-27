package cn.bugstack.ai.domain.agent.service.armory.node;

import cn.bugstack.ai.domain.agent.model.entity.ArmoryCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.bugstack.ai.domain.agent.service.armory.AbstractArmorySupport;
import cn.bugstack.ai.domain.agent.service.armory.factory.DefaultArmoryFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatModelNode extends AbstractArmorySupport {

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - ChatModelNode");
        // 从上下文中获取api配置信息
        OpenAiApi openAiApi = dynamicContext.getOpenAiApi();

        // 从参数中获取智能体配置中的模型信息
        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        AiAgentConfigTableVO.Module.ChatModel chatModelConfig = aiAgentConfigTableVO.getModule().getChatModel();

        // 封装 MCP工具列表 集合
        List<McpSyncClient> mcpSyncClients = new ArrayList<>();
        List<AiAgentConfigTableVO.Module.ChatModel.ToolMcp> toolMcpList = chatModelConfig.getToolMcpList();
        //创建MCP客户端列表
        for (AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp : toolMcpList) {
            mcpSyncClients.add(createMcpSyncClient(toolMcp));
        }

        // 创建并封装 ChatModel 对象
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi) // 设置API连接
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chatModelConfig.getModel()) // 设置模型名称
                        .toolCallbacks(SyncMcpToolCallbackProvider.builder()
                                .mcpClients(mcpSyncClients).build()
                                .getToolCallbacks()) // 绑定所有MCP工具
                        .build())
                .build();

        dynamicContext.setChatModel(chatModel);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return defaultStrategyHandler;
    }

    /**
     * 创建MCP同步客户端
     * 这个方法负责创建一个MCP工具的客户端连接，支持两种连接方式
     * @param toolMcp
     * @throws Exception
     */
    private McpSyncClient createMcpSyncClient(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception {

        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.SSEServerParameters sseConfig = toolMcp.getSse();
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StdioServerParameters stdioConfig = toolMcp.getStdio();

        //方式1：SSE连接
        if (null != sseConfig) {
            // http://appbuilder.baidu.com/v2/ai_search/mcp/sse?api_key=bce-v3/ALTAK-JFZXXLpfxhAutDQvJ32Ei/4492c1879b8c2f0df4612ef5b4a52df1c1fba9f7

            String originalBaseUri = sseConfig.getBaseUri(); // 基础URL
            String baseUri = originalBaseUri;
            String sseEndpoint = sseConfig.getSseEndpoint(); // SSE端点路径

            //如果配置中没有单独指定 sseEndpoint，就从完整的URL中拆分出来
            if (StringUtils.isBlank(sseEndpoint)) {
                //把字符串形式的URL转换成Java的URL对象
                URL url = new URL(originalBaseUri);

                String protocol = url.getProtocol(); // 协议：http
                String host = url.getHost();  // 主机：appbuilder.baidu.com
                int port = url.getPort(); // 端口：-1（表示使用默认端口80）

                String baseUrl = port == -1 ? protocol + "://" + host : protocol + "://" + host + ":" + port;
                //找到基础地址在完整URL中的位置
                int index = originalBaseUri.indexOf(baseUrl);
                if (index != -1) {
                    //截取路径部分
                    sseEndpoint = originalBaseUri.substring(index + baseUrl.length());
                }
                //把原来的完整URL替换成只有基础地址的部分
                baseUri = baseUrl;
            }

            //设置默认值
            sseEndpoint = StringUtils.isBlank(sseEndpoint) ? "/sse" : sseEndpoint;

            //创建传输层和客户端
            HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                    .builder(baseUri)
                    .sseEndpoint(sseEndpoint)
                    .build();
            McpSyncClient mcpSyncClient = McpClient
                    .sync(sseClientTransport) //传输通道
                    .requestTimeout(Duration.ofMillis(sseConfig.getRequestTimeout())) //请求超时时间
                    .build();
            McpSchema.InitializeResult initialize = mcpSyncClient.initialize(); // 初始化连接

            log.info("tool sse mcp initialize {}", initialize);

            return mcpSyncClient;
        }

        //方式2：Stdio连接
        if (null != stdioConfig) {
            AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StdioServerParameters.ServerParameters serverParameters = stdioConfig.getServerParameters();

            ServerParameters stdioParams = ServerParameters.builder(serverParameters.getCommand())
                    .args(serverParameters.getArgs()) // 命令行参数
                    .env(serverParameters.getEnv()) // 环境变量
                    .build();

            McpSyncClient mcpSyncClient = McpClient
                    .sync(new StdioClientTransport(stdioParams, new JacksonMcpJsonMapper(new ObjectMapper())))
                    .requestTimeout(Duration.ofSeconds(stdioConfig.getRequestTimeout())).build();

            McpSchema.InitializeResult initialize = mcpSyncClient.initialize();

            log.info("tool stdio mcp initialize {}", initialize);

        }

        throw new RuntimeException("tool mcp sse and stdio is null!");
    }

}
