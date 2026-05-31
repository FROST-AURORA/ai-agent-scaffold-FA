package cn.bugstack.ai.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话命令，实体对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatCommandEntity {

    private String agentId; // 智能体ID

    private String userId; // 用户ID

    private String sessionId; // 会话ID

    private List<Content.Text> texts; // 文本消息列表

    private List<Content.File> files; // 文件列表（如图片、文档）

    private List<Content.InlineData> inlineDatas; // 内联数据列表（如直接嵌入的图片字节）

    @Data
    public static class Content {

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        /**
         * 纯文本消息
         * 用户输入文字聊天
         */
        public static class Text {
            private String message;
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        /**
         * 文件引用
         * 用户上传了一个文件，系统通过URL访问
         */
        public static class File {
            private String fileUri; // 文件的URL地址
            private String mimeType; // 文件类型
        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        /**
         * 内联数据
         * 直接把文件内容嵌入到消息中（不通过URL）
         */
        public static class InlineData {
            private byte[] bytes; // 文件的二进制数据
            private String mimeType; // 文件类型
        }

    }

    /**
     * 构建会话命令
     * 创建一个只包含 agentId 和 userId 的空对话对象
     * @param agentId
     * @param userId
     * @return
     */
    public ChatCommandEntity buildSessionCommand(String agentId, String userId) {
        ChatCommandEntity chatCommandEntity = new ChatCommandEntity();
        chatCommandEntity.setAgentId(agentId);
        chatCommandEntity.setUserId(userId);
        return chatCommandEntity;
    }

    /**
     * 构建对话命令
     * 创建一个包含文本消息的完整对话对象
     * @param agentId
     * @param userId
     * @param message
     * @return
     */
    public ChatCommandEntity buildChatCommand(String agentId, String userId, String message) {
        ChatCommandEntity chatCommandEntity = new ChatCommandEntity();
        chatCommandEntity.setAgentId(agentId);
        chatCommandEntity.setUserId(userId);

        List<Content.Text> texts = new ArrayList<>();
        texts.add(new Content.Text(message));

        chatCommandEntity.setTexts(texts);

        return chatCommandEntity;
    }

}
