package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Ai Agent 智能体配置表值对象
 */
@Data
public class AiAgentConfigTableVO {

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 智能体配置
     */
    private Agent agent;

    /**
     * 智能体模块
     */
    private Module module;

    @Data
    public static class Agent {
        /**
         * 智能体ID
         */
        private String agentId;
        /**
         * 智能体名称
         */
        private String agentName;
        /**
         * 智能体描述
         */
        private String agentDesc;

    }

    @Data
    public static class Module {
        //AI API 连接配置
        private AiApi aiApi;
        //智能体模型与tool工具
        private ChatModel chatModel;
        //智能体列表：定义多个专门的"小助手"，每个负责不同的任务
        private List<Agent> agents;
        //智能体工作流：定义多个小助手如何协作完成任务的流程
        private List<AgentWorkflow> agentWorkflows;

        @Data
        public static class AiApi {
            private String baseUrl;
            private String apiKey;
            private String completionsPath = "/v1/chat/completions";
            private String embeddingsPath = "/v1/embeddings";

        }

        @Data
        public static class ChatModel {

            private String model;
            /**
             * MCP工具列表
             */
            private List<ToolMcp> toolMcpList;

            @Data
            public static class ToolMcp {

                private SSEServerParameters sse;

                private StdioServerParameters stdio;

                //HTTP 网络请求（云端 API、第三方服务）
                @Data
                public static class SSEServerParameters {
                    private String name;
                    private String baseUri;
                    private String sseEndpoint;
                    private Integer requestTimeout = 3000;

                }
                //本地进程的标准输入输出（本地脚本、文件系统操作）
                @Data
                public static class StdioServerParameters {
                    private String name;
                    private Integer requestTimeout = 3000;
                    private ServerParameters serverParameters;

                    @Data
                    public static class ServerParameters {
                        private String command; // 要执行的命令
                        private List<String> args; // 脚本路径
                        private Map<String, String> env; // 环境变量

                    }
                }

            }
        }

        @Data
        public static class Agent {
            private String name; // 助手名称
            private String instruction; // 助手的指令
            private String description; // 助手的描述
            private String outputKey; // 输出结果的键名

        }

        @Data
        public static class AgentWorkflow {
            /**
             * 类型；loop、parallel、sequential
             */
            private String type; // 工作流类型
            private String name; // 工作流名称
            private List<String> subAgents; // 参与的小助手列表
            private String description; // 工作流描述
            private Integer maxIterations = 3; // 最大迭代次数

        }
    }

}
