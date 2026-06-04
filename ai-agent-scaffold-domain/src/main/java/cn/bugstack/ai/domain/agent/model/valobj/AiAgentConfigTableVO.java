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
     * 应用名称：一个应用可以有多个智能体
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
        //运行器：运行哪个“小助手”
        private Runner runner;

        @Data
        public static class AiApi {
            private String baseUrl;
            private String apiKey;
            private String completionsPath = "/v1/chat/completions";
            private String embeddingsPath = "/v1/embeddings";
            private Integer requestTimeout = 300000;

        }

        @Data
        public static class ChatModel {

            /**
             * 模型名称
             */
            private String model;
            /**
             * MCP工具列表
             */
            private List<ToolMcp> toolMcpList;

            /**
             * Skills工具列表
             */
            private List<ToolSkills> toolSkillsList;

            @Data
            public static class ToolMcp {

                //通过 HTTP + SSE 长连接访问远程 MCP 服务：你的 Agent 通过 URL 去连一个已经部署好的 MCP Server。
                private SSEServerParameters sse;

                //本地进程的标准输入输出：Agent 启动一个脚本或命令
                private StdioServerParameters stdio;

                //直接调用 Spring 容器里的本地 Bean：不走网络，也不启动外部进程，直接从 Spring 容器里拿一个 Bean 来提供 MCP 工具能力
                private LocalParameters local;


                @Data
                public static class SSEServerParameters {
                    private String name;
                    private String baseUri; // 基础URL
                    private String sseEndpoint; // SSE端点路径
                    private Integer requestTimeout = 3000;

                }
                @Data
                public static class StdioServerParameters {
                    private String name;
                    private Integer requestTimeout = 3000;
                    private ServerParameters serverParameters; // 服务器参数

                    @Data
                    public static class ServerParameters {
                        private String command; // 要执行的命令
                        private List<String> args; // 脚本路径
                        private Map<String, String> env; // 环境变量

                    }
                }
                @Data
                public static class LocalParameters {
                    private String name;
                }

            }

            @Data
            public static class ToolSkills {

                /**
                 * 类型；directory（用户配置的，映射进来的）、resource（放到工程下的）
                 */
                private String type = "directory";

                /**
                 * 路径；
                 */
                private String path;

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

        @Data
        public static class Runner {
            //运行器名称
            private String agentName;
            //运行器插件集合
            private List<String> pluginNameList;
        }
    }

}
