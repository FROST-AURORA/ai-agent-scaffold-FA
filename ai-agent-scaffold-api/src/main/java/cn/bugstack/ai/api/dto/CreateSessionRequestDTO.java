package cn.bugstack.ai.api.dto;

import lombok.Data;

/**
 * 创建会话请求对象
 */
@Data
public class CreateSessionRequestDTO {

    private String agentId;

    private String userId;

}
