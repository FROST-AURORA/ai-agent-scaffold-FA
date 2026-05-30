package cn.bugstack.ai.domain.agent.service.armory.matter.mcp.client.impl;

import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.service.armory.matter.mcp.client.TooMcpCreateService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;

@Slf4j
@Service
public class SSEToolMcpCreateService implements TooMcpCreateService {

    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.SSEServerParameters sseConfig = toolMcp.getSse();

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

        return SyncMcpToolCallbackProvider.builder() //创建一个工具提供者的建造者
                .mcpClients(mcpSyncClient).build() //把 MCP 客户端传进去（已经连接到了远程服务器）
                .getToolCallbacks(); //从 MCP 服务器获取所有可用的工具，返回一个 ToolCallback[] 数组
    }

}