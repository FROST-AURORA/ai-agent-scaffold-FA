package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IAgentService;
import cn.bugstack.ai.api.dto.*;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.service.IChatService;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import io.reactivex.rxjava3.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 智能体服务
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class AgentServiceController implements IAgentService {

    @Resource
    private IChatService chatService;

    /**
     * 查询智能体配置列表
     * @return
     */
    @RequestMapping(value = "query_ai_agent_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList() {
        try {
            log.info("查询智能体配置列表");

            List<AiAgentConfigTableVO.Agent> agentConfigs = chatService.queryAiAgentConfigList();

            List<AiAgentConfigResponseDTO> responseDTOS = agentConfigs.stream().map(agentConfig -> {
                AiAgentConfigResponseDTO responseDTO = new AiAgentConfigResponseDTO();
                responseDTO.setAgentId(agentConfig.getAgentId());
                responseDTO.setAgentName(agentConfig.getAgentName());
                responseDTO.setAgentDesc(agentConfig.getAgentDesc());
                return responseDTO;
            }).collect(Collectors.toList());

            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOS)
                    .build();

        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("查询智能体配置列表失败", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 创建会话
     * @param requestDTO
     * @return
     */
    @RequestMapping(value = "create_session", method = RequestMethod.POST)
    @Override
    public Response<CreateSessionResponseDTO> createSession(@RequestBody CreateSessionRequestDTO requestDTO) {
        try {
            log.info("创建会话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());

            CreateSessionResponseDTO responseDTO = new CreateSessionResponseDTO();
            responseDTO.setSessionId(sessionId);

            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("创建会话失败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 智能体对话
     * @param requestDTO
     * @return
     */
    @RequestMapping(value = "chat", method = RequestMethod.POST)
    @Override
    public Response<ChatResponseDTO> chat(@RequestBody ChatRequestDTO requestDTO) {
        try {
            log.info("智能体对话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = requestDTO.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId());
            }

            List<String> messages = chatService.handleMessage(requestDTO.getAgentId(), requestDTO.getUserId(), sessionId, requestDTO.getMessage());

            ChatResponseDTO responseDTO = new ChatResponseDTO();
            try {
                // 尝试获取最后一条消息并解析
                String result = messages.stream().reduce((first, second) -> second).orElse("");
                ChatResponseDTO parsed = JSON.parseObject(result, ChatResponseDTO.class);
                if (null != parsed) {
                    responseDTO = parsed;
                    // 如果解析后的对象 type 为空，则默认为 user
                    if (null == responseDTO.getType()) {
                        responseDTO.setType("user");
                    }
                } else {
                    responseDTO.setType("user");
                    responseDTO.setContent(String.join("\n", messages));
                }
            } catch (Exception e) {
                responseDTO.setType("user");
                responseDTO.setContent(String.join("\n", messages));
            }

            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.error("智能体对话异常", e);
            return Response.<ChatResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("智能体对话败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 智能体流式对话
     * ResponseBodyEmitter：它是 Spring MVC 提供的一个响应发射器，专门用于实现服务端向客户端持续推送数据
     * @param requestDTO
     * @return
     */
    @RequestMapping(value = "chat_stream", method = RequestMethod.POST, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Override
    public ResponseBodyEmitter chatStream(@RequestBody ChatRequestDTO requestDTO) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(10 * 60 * 1000L);//指定超时时间
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();
        Runnable disposeStream = () -> {
            Disposable disposable = disposableRef.get();
            if (null != disposable && !disposable.isDisposed()) {
                disposable.dispose();
            }
        };
        emitter.onCompletion(() -> {
            emitterClosed.set(true);
            disposeStream.run();
        });
        emitter.onTimeout(() -> {
            emitterClosed.set(true);
            disposeStream.run();
        });
        emitter.onError(error -> {
            emitterClosed.set(true);
            disposeStream.run();
        });
        try {
            sendSseEvent(emitter, "status", "正在分析需求");
            log.info("流式对话 agentId:{} userId:{} sessionId:{} message:{}", requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getSessionId(), requestDTO.getMessage());
            Disposable disposable = chatService.handleMessageStream(requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getSessionId(), requestDTO.getMessage())
                    //订阅这个流
                    .subscribe(
                            //.subscribe() 已经帮你把流"拆开"了，这里event 就是这个流中的当前数据，每当流里收到一条正常数据时执行。
                            event -> {
                                if (emitterClosed.get()) {
                                    return;
                                }
                                try {
                                    //使用 emitter 发送数据
                                    sendSseEvent(emitter, "delta", event.stringifyContent());
                                } catch (Exception e) {
                                    if (emitterClosed.compareAndSet(false, true)) {
                                        log.error("流式对话发送失败", e);
                                        disposeStream.run();
                                        emitter.completeWithError(e);
                                    }
                                }
                            },
                            // 如果整个流式过程出现异常，就调用这个方法通知前端"出错了"，如果流式过程中发生异常时执行。
                            e -> {
                                if (emitterClosed.compareAndSet(false, true)) {
                                    try {
                                        log.error("stream chat failed", e);
                                        sendSseEvent(emitter, "error", e.getMessage());
                                    } catch (Exception sendError) {
                                        log.error("stream error event send failed", sendError);
                                    } finally {
                                        disposeStream.run();
                                        emitter.completeWithError(e);
                                    }
                                }
                            },
                            // 当AI完整回答完毕，没有更多内容时，调用这个方法告诉前端"结束了"，流正常结束时执行。
                            () -> {
                                if (emitterClosed.compareAndSet(false, true)) {
                                    try {
                                        sendSseEvent(emitter, "done", "");
                                    } catch (Exception e) {
                                        log.error("stream done event send failed", e);
                                    } finally {
                                        disposeStream.run();
                                        emitter.complete();
                                    }
                                }
                            }
                    );
            disposableRef.set(disposable);
            if (emitterClosed.get()) {
                disposeStream.run();
            }
        } catch (Exception e) {
            log.error("流式对话失败", e);
            if (emitterClosed.compareAndSet(false, true)) {
                disposeStream.run();
                emitter.completeWithError(e);
            }
        }
        return emitter;
    }

    private void sendSseEvent(ResponseBodyEmitter emitter, String type, String content) throws Exception {
        emitter.send("data: " + JSON.toJSONString(Map.of("type", type, "content", content)) + "\n\n", MediaType.TEXT_EVENT_STREAM);
    }

}
