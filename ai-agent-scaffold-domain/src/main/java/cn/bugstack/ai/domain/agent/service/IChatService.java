package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.entity.ChatCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;

import java.util.List;

/**
 * 对话接口
 */
public interface IChatService {

    /**
     * 查询所有可用的AI智能体列表
     */
    List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList();

    /**
     * 为用户创建一个新会话（或返回已有会话ID）
     */
    String createSession(String agentId, String userId);

    /**
     * 处理用户的文本消息（自动管理会话）
     * 获取会话id后
     * 调用handleMessage(String agentId, String userId, String sessionId, String message)
     */
    List<String> handleMessage(String agentId, String userId, String message);

    /**
     * 处理用户的文本消息（指定会话ID）
     */
    List<String> handleMessage(String agentId, String userId, String sessionId, String message);

    /**
     * 以流式方式处理消息
     */
    Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message);

    /**
     * 处理复杂消息（支持文本、文件、图片等多种类型）
     */
    List<String> handleMessage(ChatCommandEntity chatCommandEntity);

}
