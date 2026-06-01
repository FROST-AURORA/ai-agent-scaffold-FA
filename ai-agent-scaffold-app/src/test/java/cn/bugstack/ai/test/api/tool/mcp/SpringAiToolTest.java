package cn.bugstack.ai.test.api.tool.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

/**
 * Spring Ai Tool
 */
@Slf4j
public class SpringAiToolTest {

    public static void main(String[] args) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .apiKey("sk-a5c9b22c65a54fe6a6ac218f0690355e")
                //.completionsPath("v1/chat/completions")
                //.embeddingsPath("v1/embeddings")
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen3.6-plus")
                        // 添加MCP工具能力
                        .toolCallbacks(SyncMcpToolCallbackProvider.builder()
                                .mcpClients(sseMcpClient()).build()
                                .getToolCallbacks())
                        .build())
                .build();

        String call = chatModel.call("你哪有哪些工具能力");

        log.info("测试结果:{}", call);
    }

    /**
     * 这个方法创建了一个 MCP 客户端，用于连接百度的 AI 搜索服务，让 AI 能够使用百度搜索工具
     * 百度搜索MCP服务(url)；https://sai.baidu.com/zh/detail/e014c6ffd555697deabf00d058baf388
     * 百度搜索MCP服务(key)；https://console.bce.baidu.com/iam/?_=1753597622044#/iam/apikey/list
     */
    public static McpSyncClient sseMcpClient() {

        //创建 HTTP 传输通道
        // 自己申请 api_key
        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                .builder("http://appbuilder.baidu.com/v2/ai_search/mcp/")
                .sseEndpoint("sse?api_key=Bearer+bce-v3/ALTAK-VNHFDDhxotqBJEQtPgDJv/2c043efe186253a2d6195cb95a1ec2be70857256")
                .build();

        //创建 MCP 客户端
        McpSyncClient mcpSyncClient = McpClient
                .sync(sseClientTransport) // 使用刚才创建的传输通道
                .requestTimeout(Duration.ofMinutes(360)) // 设置请求超时时间为 1 分钟
                .build();
        var init_sse = mcpSyncClient.initialize(); // 初始化连接
        log.info("Tool SSE MCP Initialized {}", init_sse);

        return mcpSyncClient;
    }


    @Test
    public void test_url() throws MalformedURLException {
        String fullUrl = "http://appbuilder.baidu.com/v2/ai_search/mcp/sse?api_key=bce-v3/ALTAK-JFZXXLpfxhAutDQvJ32Ei/4492c1879b8c2f0df4612ef5b4a52df1c1fba9f7";

        fullUrl = "http://127.0.0.1:9999/sse?apiKey=xxxx";

        URL url = new URL(fullUrl);

        String protocol = url.getProtocol();
        String host = url.getHost();
        int port = url.getPort();

        String baseUrl = port == -1 ? protocol + "://" + host : protocol + "://" + host + ":" + port;
        String endpoint = "";

        int index = fullUrl.indexOf(baseUrl);
        if (index != -1) {
            endpoint = fullUrl.substring(index + baseUrl.length());
        }

        log.info("baseUrl:{}", baseUrl);
        log.info("endpoint:{}", endpoint);
    }
}
