package cn.bugstack.ai.api.dto;

import lombok.Data;

/**
 * 智能体对话处理请求
 */
@Data
public class ChatRequestDTO {

    private String agentId;
    private String userId;
    private String sessionId;
    private String message;

}
