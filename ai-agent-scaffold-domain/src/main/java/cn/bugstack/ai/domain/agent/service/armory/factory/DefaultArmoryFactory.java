package cn.bugstack.ai.domain.agent.service.armory.factory;

import cn.bugstack.ai.domain.agent.model.entity.ArmoryCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentConfigTableVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentRegisterVO;
import cn.bugstack.ai.domain.agent.service.armory.node.RootNode;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.SequentialAgent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认的装配工厂
 */
@Service
public class DefaultArmoryFactory {

    @Resource
    private RootNode rootNode;
    @Resource
    private ApplicationContext applicationContext;

    // 获取根节点
    public StrategyHandler<ArmoryCommandEntity, DynamicContext, AiAgentRegisterVO> armoryStrategyHandler() {
        return rootNode;
    }

    // 获取智能体注册对象
    public AiAgentRegisterVO getAiAgentRegisterVO(String agentId) {
        return applicationContext.getBean(agentId, AiAgentRegisterVO.class);
    }

    /**
     * 定义一个上下文对象，用于各个节点串联的时候，写入数据和使用数据
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {

        /**
         * LLM API
         */
        private OpenAiApi openAiApi;

        /**
         * LLM ChatModel
         */
        private ChatModel chatModel;


        /**
         * 智能体配置组
         * 可以存储任何类型的智能体
         */
        private Map<String, BaseAgent> agentGroup = new HashMap<>();



        /**
         * 当前工作流
         */
        private AiAgentConfigTableVO.Module.AgentWorkflow currentAgentWorkflow;

        /**
         * 智能体数据对象
         */
        private Map<String, Object> dataObjects = new HashMap<>();
        public <T> void setValue(String key, T value) {
            dataObjects.put(key, value);
        }
        public <T> T getValue(String key) {
            return (T) dataObjects.get(key);
        }

        /**
         * 根据名称查询智能体
         * @param agentNames
         * @return
         */
        public List<BaseAgent> queryAgentList(List<String> agentNames) {
            if (agentNames == null || agentNames.isEmpty() || agentGroup == null) {
                return Collections.emptyList();
            }

            List<BaseAgent> agents = new ArrayList<>();
            for (String name : agentNames) {
                BaseAgent agent = agentGroup.get(name);
                if (agent!=null){
                    agents.add(agent);
                }
            }

            return agents;
        }

        //AtomicInteger 是一个线程安全的整数计数器。
        private AtomicInteger currentStepIndex = new AtomicInteger(0);
        //加1
        public void addCurrentStepIndex(){
            currentStepIndex.incrementAndGet();
        }
        //获取当前值
        public int getCurrentStepIndex(){
            return currentStepIndex.get();
        }
    }

}
