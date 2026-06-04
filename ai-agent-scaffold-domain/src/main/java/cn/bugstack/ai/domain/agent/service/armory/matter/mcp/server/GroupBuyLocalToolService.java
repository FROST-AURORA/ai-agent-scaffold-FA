package cn.bugstack.ai.domain.agent.service.armory.matter.mcp.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class GroupBuyLocalToolService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gbm.api.base-url:http://127.0.0.1:8091}")
    private String gbmBaseUrl;

    @Tool(name = "gbm_query_market_config", description = "Query group-buy market config. Read-only. Required: userId, source, channel, goodsId.")
    public Map<String, Object> gbmQueryMarketConfig(GoodsMarketRequest request) {
        validateGoodsMarketRequest(request);
        String url = normalizeBaseUrl() + "/api/v1/gbm/index/query_group_buy_market_config";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(list(MediaType.APPLICATION_JSON));
        HttpEntity<GoodsMarketRequest> entity = new HttpEntity<>(request, headers);
        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
        return response == null ? new LinkedHashMap<>() : response;
    }

    @Tool(name = "gbm_recommend_team", description = "Recommend whether the user should join an existing team or create a new team. Read-only. Required: userId, source, channel, goodsId.")
    public RecommendTeamResponse gbmRecommendTeam(GoodsMarketRequest request) {
        Map<String, Object> marketConfig = gbmQueryMarketConfig(request);
        return recommendTeam(request, marketConfig);
    }

    @Tool(name = "gbm_generate_share_context", description = "Generate share-card and Draw.io diagram context for the recommended group-buy action. Read-only. Required: userId, source, channel, goodsId.")
    public ShareContextResponse gbmGenerateShareContext(GoodsMarketRequest request) {
        Map<String, Object> marketConfig = gbmQueryMarketConfig(request);
        RecommendTeamResponse recommendation = recommendTeam(request, marketConfig);

        ShareContextResponse response = new ShareContextResponse();
        response.setRequest(request);
        response.setRecommendation(recommendation);
        response.setDiagramType("group_buy_share_card");
        response.setDiagramTitle("拼团推荐分享图");
        response.setShareText(recommendation.getShareText());
        response.setDrawioPrompt(buildDrawioPrompt(recommendation));
        response.setMarketConfig(marketConfig);
        return response;
    }

    @Tool(name = "gbm_query_api_schema", description = "Query group-buy API schema for agent planning. Read-only.")
    public Map<String, Object> gbmQueryApiSchema(ApiSchemaRequest request) {
        String topic = request == null ? null : request.getTopic();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", topic == null || topic.trim().isEmpty() ? "all" : topic);

        List<Map<String, Object>> schemas = new ArrayList<>();
        schemas.add(schema("gbm_query_market_config", "POST", "/api/v1/gbm/index/query_group_buy_market_config",
                "Read-only query for product price, activity id, available teams and activity team statistics.",
                list("userId", "source", "channel", "goodsId"),
                list("activityId", "goods", "teamList", "teamStatistic")));
        schemas.add(schema("gbm_recommend_team", "LOCAL_TOOL", "local MCP tool",
                "Read-only recommendation based on market config. It does not lock order, settle order or refund order.",
                list("userId", "source", "channel", "goodsId"),
                list("recommendAction", "teamId", "reason", "missingCount", "progressText", "shareText")));
        schemas.add(schema("gbm_generate_share_context", "LOCAL_TOOL", "local MCP tool",
                "Read-only share-card context for Draw.io XML generation.",
                list("userId", "source", "channel", "goodsId"),
                list("diagramType", "diagramTitle", "shareText", "drawioPrompt", "recommendation")));
        result.put("schemas", schemas);
        result.put("sideEffectPolicy", "Only read-only tools are exposed for AI recommendation and share diagram generation. Trade actions must stay behind the group-buy HTTP API and explicit user confirmation.");
        return result;
    }

    @Tool(name = "gbm_query_trade_flow", description = "Query group-buy business flow notes for diagram planning. Read-only.")
    public Map<String, Object> gbmQueryTradeFlow(TradeFlowRequest request) {
        String scene = request == null ? null : request.getScene();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scene", scene == null || scene.trim().isEmpty() ? "recommend_share" : scene);
        result.put("recommendShareFlow", list(
                "User opens product detail page.",
                "Agent calls gbm_query_market_config with userId, source, channel and goodsId.",
                "Agent filters available teams by targetCount, lockCount and countdown.",
                "Agent recommends JOIN_TEAM when an existing team has remaining slots; otherwise CREATE_TEAM.",
                "Agent builds a share-card context with price saving, progress and countdown.",
                "Draw.io agent generates XML for a ToC share image. No trade-side-effect API is executed."
        ));
        result.put("domainBoundary", list(
                "MCP tools call public group-buy HTTP APIs or local read-only composition only.",
                "MCP tools must not call infrastructure repository classes directly.",
                "Lock, settlement and refund stay in the group-buy trade API and require explicit confirmation."
        ));
        return result;
    }

    private RecommendTeamResponse recommendTeam(GoodsMarketRequest request, Map<String, Object> marketConfig) {
        Map<String, Object> data = asMap(marketConfig.get("data"));
        Map<String, Object> goods = asMap(data.get("goods"));
        Map<String, Object> teamStatistic = asMap(data.get("teamStatistic"));
        List<Map<String, Object>> teamList = asMapList(data.get("teamList"));

        List<TeamCandidate> candidates = new ArrayList<>();
        for (Map<String, Object> team : teamList) {
            Integer targetCount = asInteger(team.get("targetCount"));
            Integer lockCount = asInteger(team.get("lockCount"));
            if (targetCount == null || lockCount == null) {
                continue;
            }

            int missingCount = Math.max(targetCount - lockCount, 0);
            if (missingCount <= 0) {
                continue;
            }

            TeamCandidate candidate = new TeamCandidate();
            candidate.team = team;
            candidate.missingCount = missingCount;
            candidate.targetCount = targetCount;
            candidate.lockCount = lockCount;
            candidate.countdownSeconds = parseCountdownSeconds(asString(team.get("validTimeCountdown")));
            candidates.add(candidate);
        }

        candidates.sort(Comparator
                .comparingInt((TeamCandidate candidate) -> candidate.missingCount)
                .thenComparingInt(candidate -> candidate.countdownSeconds < 0 ? Integer.MAX_VALUE : candidate.countdownSeconds)
                .thenComparing((TeamCandidate candidate) -> candidate.lockCount, Comparator.reverseOrder()));

        RecommendTeamResponse response = new RecommendTeamResponse();
        response.setRequest(request);
        response.setActivityId(asLong(data.get("activityId")));
        response.setOriginalPrice(asBigDecimal(goods.get("originalPrice")));
        response.setPayPrice(asBigDecimal(goods.get("payPrice")));
        response.setDeductionPrice(asBigDecimal(goods.get("deductionPrice")));
        response.setAllTeamCount(asInteger(teamStatistic.get("allTeamCount")));
        response.setAllTeamCompleteCount(asInteger(teamStatistic.get("allTeamCompleteCount")));
        response.setAllTeamUserCount(asInteger(teamStatistic.get("allTeamUserCount")));

        if (candidates.isEmpty()) {
            response.setRecommendAction("CREATE_TEAM");
            response.setReason("当前没有可直接加入的未满团队，建议用户发起新团，并使用省钱利益点邀请好友参团。");
            response.setProgressText("发起新团");
            response.setShareText(buildCreateTeamShareText(response));
            response.setRawMarketConfig(marketConfig);
            return response;
        }

        TeamCandidate selected = candidates.get(0);
        Map<String, Object> team = selected.team;
        response.setRecommendAction("JOIN_TEAM");
        response.setTeamId(asString(team.get("teamId")));
        response.setTeamOwnerUserId(asString(team.get("userId")));
        response.setTargetCount(selected.targetCount);
        response.setLockCount(selected.lockCount);
        response.setMissingCount(selected.missingCount);
        response.setCountdown(asString(team.get("validTimeCountdown")));
        response.setProgressText(selected.lockCount + "/" + selected.targetCount);
        response.setReason("推荐加入该团：还差 " + selected.missingCount + " 人，进度 " + response.getProgressText() + "，在可参团队伍中成团优先级最高。");
        response.setShareText(buildJoinTeamShareText(response));
        response.setRawMarketConfig(marketConfig);
        return response;
    }

    private String buildDrawioPrompt(RecommendTeamResponse recommendation) {
        StringBuilder builder = new StringBuilder();
        builder.append("请生成一张可导入 Draw.io 的 ToC 拼团营销分享图，最终只输出 mxfile XML。");
        builder.append("画布建议 420x720，移动端海报比例，整体像商品详情页里的分享卡，不要像后台报表。");
        builder.append("使用中文文案，不要使用英文，不要使用 emoji；图标请用 Draw.io 基础形状或小圆点模拟。");
        builder.append("视觉风格：白色圆角主卡片，珊瑚红作为主行动色，青绿色表示进度，金黄色表示省钱利益点，文字层级清晰，留白充足。");
        builder.append("顶部主标题：");
        if ("JOIN_TEAM".equals(recommendation.getRecommendAction())) {
            builder.append("还差 ").append(nullToText(recommendation.getMissingCount())).append(" 人，马上成团。");
        } else {
            builder.append("发起拼团，邀请好友一起省。");
        }
        builder.append("副标题：").append(recommendation.getReason()).append("。");
        builder.append("第一模块是价格利益点：原价 ").append(money(recommendation.getOriginalPrice()))
                .append("，拼团价 ").append(money(recommendation.getPayPrice()))
                .append("，立省 ").append(money(recommendation.getDeductionPrice()))
                .append("，拼团价要最大最醒目。");
        builder.append("第二模块是成团进度：推荐动作 ").append(actionText(recommendation.getRecommendAction()))
                .append("，进度 ").append(nullToText(recommendation.getProgressText()));
        if (recommendation.getTeamId() != null) {
            builder.append("，团队ID ").append(recommendation.getTeamId());
        }
        if (recommendation.getCountdown() != null) {
            builder.append("，倒计时 ").append(recommendation.getCountdown());
        }
        builder.append("。用横向进度条表达 lockCount/targetCount，不要只写普通文本。");
        builder.append("第三模块是活动热度：累计开团 ").append(nullToText(recommendation.getAllTeamCount()))
                .append("，已成团 ").append(nullToText(recommendation.getAllTeamCompleteCount()))
                .append("，参团人次 ").append(nullToText(recommendation.getAllTeamUserCount()))
                .append("，用三个小统计胶囊横向排列。");
        builder.append("第四模块是分享文案：").append(nullToText(recommendation.getShareText()))
                .append("，文案区要像用户可直接转发的话术。");
        builder.append("底部放一个醒目的圆角按钮，文案为 ")
                .append("JOIN_TEAM".equals(recommendation.getRecommendAction()) ? "立即参团" : "立即发起拼团")
                .append("。");
        builder.append("底部弱化展示商品ID ").append(recommendation.getRequest() == null ? "unknown" : nullToText(recommendation.getRequest().getGoodsId()))
                .append(" 和活动ID ").append(nullToText(recommendation.getActivityId()))
                .append("。");
        builder.append("布局要求：避免多层卡片嵌套，避免连线，避免复杂表格；所有文字必须在元素内完整可见。");
        return builder.toString();
    }

    private String buildJoinTeamShareText(RecommendTeamResponse response) {
        return "这个团还差 " + response.getMissingCount() + " 人就能成，拼团价 " + money(response.getPayPrice())
                + "，比原价省 " + money(response.getDeductionPrice()) + "，一起拼更划算。";
    }

    private String buildCreateTeamShareText(RecommendTeamResponse response) {
        return "我发起了一个拼团，拼团价 " + money(response.getPayPrice())
                + "，比原价省 " + money(response.getDeductionPrice()) + "，邀请你一起拼。";
    }

    private String actionText(String action) {
        if ("JOIN_TEAM".equals(action)) {
            return "加入推荐团";
        }
        if ("CREATE_TEAM".equals(action)) {
            return "创建新团";
        }
        return nullToText(action);
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "未知";
        }
        return "¥" + value.stripTrailingZeros().toPlainString();
    }

    private void validateGoodsMarketRequest(GoodsMarketRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        requireText(request.getUserId(), "userId");
        requireText(request.getSource(), "source");
        requireText(request.getChannel(), "channel");
        requireText(request.getGoodsId(), "goodsId");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
    }

    private String normalizeBaseUrl() {
        if (gbmBaseUrl == null || gbmBaseUrl.trim().isEmpty()) {
            return "http://127.0.0.1:8091";
        }
        return gbmBaseUrl.endsWith("/") ? gbmBaseUrl.substring(0, gbmBaseUrl.length() - 1) : gbmBaseUrl;
    }

    private static Map<String, Object> schema(String name, String method, String path, String description, List<String> requiredInputs, List<String> outputs) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", name);
        schema.put("method", method);
        schema.put("path", path);
        schema.put("description", description);
        schema.put("requiredInputs", requiredInputs);
        schema.put("outputs", outputs);
        return schema;
    }

    private static <T> List<T> list(T... values) {
        List<T> list = new ArrayList<>();
        for (T value : values) {
            list.add(value);
        }
        return list;
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    private static List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number || value instanceof String) {
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int parseCountdownSeconds(String countdown) {
        if (countdown == null || countdown.trim().isEmpty()) {
            return -1;
        }
        String[] parts = countdown.trim().split(":");
        if (parts.length != 3) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String nullToText(Object value) {
        return Objects.toString(value, "unknown");
    }

    private static class TeamCandidate {
        private Map<String, Object> team;
        private Integer targetCount;
        private Integer lockCount;
        private int missingCount;
        private int countdownSeconds;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GoodsMarketRequest {
        @JsonProperty(required = true, value = "userId")
        @JsonPropertyDescription("Current user id.")
        private String userId;

        @JsonProperty(required = true, value = "source")
        @JsonPropertyDescription("Business source, for example s01.")
        private String source;

        @JsonProperty(required = true, value = "channel")
        @JsonPropertyDescription("Business channel, for example c01.")
        private String channel;

        @JsonProperty(required = true, value = "goodsId")
        @JsonPropertyDescription("Product goods id.")
        private String goodsId;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiSchemaRequest {
        @JsonPropertyDescription("Optional schema topic, for example market, recommend or share.")
        private String topic;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TradeFlowRequest {
        @JsonPropertyDescription("Optional business scene, for example recommend_share, lock, settlement or refund.")
        private String scene;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecommendTeamResponse {
        private GoodsMarketRequest request;
        private String recommendAction;
        private Long activityId;
        private String teamId;
        private String teamOwnerUserId;
        private Integer targetCount;
        private Integer lockCount;
        private Integer missingCount;
        private String countdown;
        private String progressText;
        private BigDecimal originalPrice;
        private BigDecimal deductionPrice;
        private BigDecimal payPrice;
        private Integer allTeamCount;
        private Integer allTeamCompleteCount;
        private Integer allTeamUserCount;
        private String reason;
        private String shareText;
        private Map<String, Object> rawMarketConfig;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShareContextResponse {
        private GoodsMarketRequest request;
        private RecommendTeamResponse recommendation;
        private String diagramType;
        private String diagramTitle;
        private String shareText;
        private String drawioPrompt;
        private Map<String, Object> marketConfig;
    }
}
