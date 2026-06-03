package cn.bugstack.ai.domain.agent.service.armory.matter.mcp.server;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class GroupBuyLocalToolService {
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GBM_BASE_URL = "http://127.0.0.1:8090";

    @Tool(description = "查询拼团营销配置，需要 userId、source、channel、goodsId")
    public Object gbmQueryMarketConfig(GoodsMarketRequest request) {
        requireText(request.getUserId(), "userId");
        requireText(request.getSource(), "source");
        requireText(request.getChannel(), "channel");
        requireText(request.getGoodsId(), "goodsId");

        String url = GBM_BASE_URL + "/api/v1/gbm/index/query_group_buy_market_config";
        return restTemplate.postForObject(url, request, Object.class);
    }

    @Tool(description = "营销锁单。该操作有副作用，必须 confirmed=true 才允许调用")
    public Object gbmLockMarketPayOrder(LockOrderRequest request) {
        if (!Boolean.TRUE.equals(request.getConfirmed())) {
            throw new IllegalArgumentException("锁单会创建预支付订单，必须用户明确确认后才能执行。");
        }

        requireText(request.getUserId(), "userId");
        requireText(request.getSource(), "source");
        requireText(request.getChannel(), "channel");
        requireText(request.getGoodsId(), "goodsId");
        requireText(request.getOutTradeNo(), "outTradeNo");

        if (request.getActivityId() == null) {
            throw new IllegalArgumentException("activityId 不能为空");
        }

        String url = GBM_BASE_URL + "/api/v1/gbm/trade/lock_market_pay_order";
        return restTemplate.postForObject(url, request, Object.class);
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    @Data
    public static class GoodsMarketRequest {
        private String userId;
        private String source;
        private String channel;
        private String goodsId;
    }

    @Data
    public static class LockOrderRequest {
        private Boolean confirmed;
        private String userId;
        private String teamId;
        private Long activityId;
        private String goodsId;
        private String source;
        private String channel;
        private String outTradeNo;
        private NotifyConfigVO notifyConfigVO;
    }

    @Data
    public static class NotifyConfigVO {
        private String notifyType;
        private String notifyMQ;
        private String notifyUrl;
    }
}
