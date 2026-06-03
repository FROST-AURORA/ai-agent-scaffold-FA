package cn.bugstack.ai.domain.agent.service.armory.matter.mcp.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 拼团项目本地 MCP 工具。
 *
 * <p>这些工具只读取 group-buy-market-FA 的代码、文档和接口结构，
 * 用于给大模型绘图提供真实项目上下文，不执行锁单、结算、退单等交易操作。</p>
 */
@Slf4j
@Service
public class GroupBuyProjectMcpService {

    private static final String DEFAULT_PROJECT_ROOT = "D:/Java/frostAurora/group-buy-market-FA";
    private static final int MAX_SNIPPET_CHARS = 12000;

    private static final String TRADE_CONTROLLER = "group-buy-market-FA-trigger/src/main/java/cn/bugstack/trigger/http/MarketTradeController.java";
    private static final String INDEX_CONTROLLER = "group-buy-market-FA-trigger/src/main/java/cn/bugstack/trigger/http/MarketIndexController.java";
    private static final String LOCK_SERVICE = "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/lock/TradeLockOrderService.java";
    private static final String LOCK_FACTORY = "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/lock/factory/TradeLockRuleFilterFactory.java";
    private static final String LOCK_ACTIVITY_FILTER = "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/lock/filter/ActivityUsabilityRuleFilter.java";
    private static final String LOCK_USER_TAKE_FILTER = "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/lock/filter/UserTakeLimitRuleFilter.java";
    private static final String SETTLEMENT_SERVICE = "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/settlement/TradeSettlementOrderService.java";
    private static final String SETTLEMENT_FACTORY = "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/settlement/factory/TradeSettlementRuleFilterFactory.java";
    private static final String REFUND_SERVICE = "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/refund/TradeRefundOrderService.java";
    private static final String REFUND_FACTORY = "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/refund/factory/TradeRefundRuleFilterFactory.java";
    private static final String TRADE_REPOSITORY = "group-buy-market-FA-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/TradeRepository.java";
    private static final String TRADE_PORT = "group-buy-market-FA-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/TradePort.java";
    private static final String NOTIFY_JOB = "group-buy-market-FA-trigger/src/main/java/cn/bugstack/trigger/job/GroupBuyNotifyJob.java";
    private static final String LOCK_FLOW_DOC = "docs/lock-settlement-flow.md";

    @Tool(name = "gbm_query_trade_flow", description = "查询拼团业务流程说明，用于绘制 Draw.io 图。只读取项目代码和文档，不执行真实锁单、结算或退单。scene 可取 lock_market_pay_order、settlement_market_pay_order、refund_market_pay_order。")
    public TradeFlowResponse gbmQueryTradeFlow(TradeFlowRequest request) {
        String scene = normalizeScene(request == null ? null : request.getScene());
        Path root = projectRoot();

        TradeFlowResponse response = new TradeFlowResponse();
        response.setScene(scene);
        response.setProjectRoot(root.toString());
        response.setReadOnly(true);
        response.setSideEffectPolicy("本工具只做代码和文档检索，不会调用真实交易接口。锁单、结算、退单等有副作用操作必须由独立执行工具处理，并要求用户确认。");

        if ("settlement_market_pay_order".equals(scene)) {
            fillSettlementFlow(root, response);
        } else if ("refund_market_pay_order".equals(scene)) {
            fillRefundFlow(root, response);
        } else {
            fillLockFlow(root, response);
        }

        return response;
    }

    @Tool(name = "gbm_query_api_schema", description = "查询拼团接口路径、请求 DTO 字段和响应 DTO 字段，用于绘制接口链路图或补充流程图节点。只读，不调用真实接口。")
    public ApiSchemaResponse gbmQueryApiSchema(ApiSchemaRequest request) {
        String apiName = normalizeApiName(request == null ? null : request.getApiName());
        Path root = projectRoot();

        ApiSchemaResponse response = new ApiSchemaResponse();
        response.setApiName(apiName);
        response.setProjectRoot(root.toString());
        response.setReadOnly(true);

        if ("query_group_buy_market_config".equals(apiName)) {
            response.setHttpMethod("POST");
            response.setPath("/api/v1/gbm/index/query_group_buy_market_config");
            response.setController("MarketIndexController.queryGroupBuyMarketConfig");
            response.setRequestDto("GoodsMarketRequestDTO");
            response.setResponseDto("GoodsMarketResponseDTO");
            response.setRequiredFields(Arrays.asList("userId", "source", "channel", "goodsId"));
            addApiSnippets(root, response,
                    INDEX_CONTROLLER,
                    "group-buy-market-FA-api/src/main/java/cn/bugstack/api/dto/GoodsMarketRequestDTO.java",
                    "group-buy-market-FA-api/src/main/java/cn/bugstack/api/dto/GoodsMarketResponseDTO.java");
        } else if ("settlement_market_pay_order".equals(apiName)) {
            response.setHttpMethod("POST");
            response.setPath("/api/v1/gbm/trade/settlement_market_pay_order");
            response.setController("MarketTradeController.settlementMarketPayOrder");
            response.setRequestDto("SettlementMarketPayOrderRequestDTO");
            response.setResponseDto("SettlementMarketPayOrderResponseDTO");
            response.setRequiredFields(Arrays.asList("userId", "source", "channel", "outTradeNo", "outTradeTime"));
            addApiSnippets(root, response,
                    TRADE_CONTROLLER,
                    "group-buy-market-FA-api/src/main/java/cn/bugstack/api/dto/SettlementMarketPayOrderRequestDTO.java",
                    "group-buy-market-FA-api/src/main/java/cn/bugstack/api/dto/SettlementMarketPayOrderResponseDTO.java");
        } else if ("refund_market_pay_order".equals(apiName)) {
            response.setHttpMethod("POST");
            response.setPath("/api/v1/gbm/trade/refund_market_pay_order");
            response.setController("MarketTradeController.refundMarketPayOrder");
            response.setRequestDto("RefundMarketPayOrderRequestDTO");
            response.setResponseDto("RefundMarketPayOrderResponseDTO");
            response.setRequiredFields(Arrays.asList("userId", "source", "channel", "outTradeNo"));
            addApiSnippets(root, response,
                    TRADE_CONTROLLER,
                    "group-buy-market-FA-api/src/main/java/cn/bugstack/api/dto/RefundMarketPayOrderRequestDTO.java",
                    "group-buy-market-FA-api/src/main/java/cn/bugstack/api/dto/RefundMarketPayOrderResponseDTO.java");
        } else {
            response.setHttpMethod("POST");
            response.setPath("/api/v1/gbm/trade/lock_market_pay_order");
            response.setController("MarketTradeController.lockMarketPayOrder");
            response.setRequestDto("LockMarketPayOrderRequestDTO");
            response.setResponseDto("LockMarketPayOrderResponseDTO");
            response.setRequiredFields(Arrays.asList("userId", "source", "channel", "goodsId", "activityId", "outTradeNo", "notifyConfigVO"));
            response.setOptionalFields(Collections.singletonList("teamId"));
            addApiSnippets(root, response,
                    TRADE_CONTROLLER,
                    "group-buy-market-FA-api/src/main/java/cn/bugstack/api/dto/LockMarketPayOrderRequestDTO.java",
                    "group-buy-market-FA-api/src/main/java/cn/bugstack/api/dto/LockMarketPayOrderResponseDTO.java");
        }

        return response;
    }

    @Tool(name = "gbm_query_project_context", description = "查询拼团项目架构上下文，用于绘制架构图、业务流程图、接口调用链路图。只读，不执行真实交易。")
    public ProjectContextResponse gbmQueryProjectContext(ProjectContextRequest request) {
        Path root = projectRoot();

        ProjectContextResponse response = new ProjectContextResponse();
        response.setProjectRoot(root.toString());
        response.setReadOnly(true);
        response.setArchitecture("DDD 分层：api 定义契约，trigger 承接 HTTP/Job/MQ 入口，domain 承载活动/交易/标签业务规则，infrastructure 负责 MyBatis、Redis、MQ、HTTP 回调等外部能力，app 负责 Spring Boot 组装和资源配置。");
        response.setImportantModules(Arrays.asList(
                "group-buy-market-FA-api：IMarketIndexService、IMarketTradeService、请求/响应 DTO",
                "group-buy-market-FA-trigger：MarketIndexController、MarketTradeController、GroupBuyNotifyJob、RefundSuccessTopicListener",
                "group-buy-market-FA-domain：activity 试算域、trade 锁单/结算/退单域、规则链和聚合",
                "group-buy-market-FA-infrastructure：TradeRepository、ActivityRepository、TradePort、MyBatis DAO",
                "group-buy-market-FA-app：application 配置、MyBatis XML、DCC 配置、测试"));
        response.setRecommendedDrawNodes(Arrays.asList(
                "外部商城/前端",
                "MarketTradeController",
                "IIndexGroupBuyMarketService",
                "ITradeLockOrderService / ITradeSettlementOrderService / ITradeRefundOrderService",
                "规则链 Factory + Filter",
                "TradeRepository",
                "MySQL: group_buy_order / group_buy_order_list / notify_task",
                "Redis/DCC",
                "TradePort",
                "HTTP 回调 / MQ",
                "补偿任务 GroupBuyNotifyJob"));

        LinkedHashMap<String, String> snippets = new LinkedHashMap<>();
        snippets.put(LOCK_FLOW_DOC, readSection(root, LOCK_FLOW_DOC, "# 锁单与结算流程复习图", "## 8."));
        snippets.put(TRADE_CONTROLLER, readSnippet(root, TRADE_CONTROLLER, MAX_SNIPPET_CHARS));
        snippets.put(TRADE_REPOSITORY, readWindow(root, TRADE_REPOSITORY, Arrays.asList("lockMarketPayOrder", "settlementMarketPayOrder", "refund"), MAX_SNIPPET_CHARS));
        response.setSourceSnippets(snippets);

        return response;
    }

    private void fillLockFlow(Path root, TradeFlowResponse response) {
        response.setApiPath("/api/v1/gbm/trade/lock_market_pay_order");
        response.setEntryPoint("MarketTradeController.lockMarketPayOrder");
        response.setSummary("锁单是支付前锁定拼团名额和营销价格的流程。入口先做参数校验和 outTradeNo 幂等判断；指定 teamId 时校验团队是否已满；随后调用活动域试算，校验人群可见/可用；最后调用交易域锁单服务，经过锁单规则链后由 TradeRepository 事务落库。");
        response.setMainSteps(Arrays.asList(
                "接收 POST /api/v1/gbm/trade/lock_market_pay_order 请求",
                "校验 userId、source、channel、goodsId、activityId、outTradeNo、notifyConfigVO",
                "按 userId + outTradeNo 查询未支付锁单，存在则直接返回原 orderId 和金额",
                "如果传入 teamId，查询拼团进度，lockCount 达到 targetCount 时拦截",
                "调用 IIndexGroupBuyMarketService.indexMarketTrial 做营销试算",
                "校验试算结果 isVisible / isEnable",
                "组装 UserEntity、PayActivityEntity、PayDiscountEntity",
                "调用 ITradeLockOrderService.lockMarketPayOrder",
                "执行锁单规则链：ActivityUsabilityRuleFilter -> UserTakeLimitRuleFilter / TeamStockOccupyRuleFilter",
                "TradeRepository.lockMarketPayOrder 事务写 group_buy_order 和 group_buy_order_list",
                "返回 orderId、teamId、originalPrice、deductionPrice、payPrice、tradeOrderStatus"));
        response.setDomainNodes(Arrays.asList(
                "IIndexGroupBuyMarketService.indexMarketTrial",
                "ITradeLockOrderService.queryNoPayMarketPayOrderByOutTradeNo",
                "ITradeLockOrderService.queryGroupBuyProgress",
                "ITradeLockOrderService.lockMarketPayOrder",
                "TradeLockRuleFilterFactory",
                "ActivityUsabilityRuleFilter",
                "UserTakeLimitRuleFilter",
                "TeamStockOccupyRuleFilter"));
        response.setPersistenceNodes(Arrays.asList(
                "TradeRepository.lockMarketPayOrder",
                "group_buy_order：新团插入主单，老团 lock_count + 1",
                "group_buy_order_list：插入用户锁单明细，status=CREATE(0)",
                "group_buy_order_mapper.xml",
                "group_buy_order_list_mapper.xml"));
        response.setRelatedFiles(Arrays.asList(
                TRADE_CONTROLLER, LOCK_SERVICE, LOCK_FACTORY, LOCK_ACTIVITY_FILTER, LOCK_USER_TAKE_FILTER,
                TRADE_REPOSITORY, "group-buy-market-FA-app/src/main/resources/mybatis/mapper/group_buy_order_mapper.xml",
                "group-buy-market-FA-app/src/main/resources/mybatis/mapper/group_buy_order_list_mapper.xml", LOCK_FLOW_DOC));

        LinkedHashMap<String, String> snippets = new LinkedHashMap<>();
        snippets.put(LOCK_FLOW_DOC, readSection(root, LOCK_FLOW_DOC, "## 3. 锁单流程", "## 4."));
        snippets.put(TRADE_CONTROLLER, readWindow(root, TRADE_CONTROLLER, Collections.singletonList("lockMarketPayOrder"), MAX_SNIPPET_CHARS));
        snippets.put(LOCK_SERVICE, readSnippet(root, LOCK_SERVICE, MAX_SNIPPET_CHARS));
        snippets.put(LOCK_FACTORY, readSnippet(root, LOCK_FACTORY, MAX_SNIPPET_CHARS));
        snippets.put(TRADE_REPOSITORY, readWindow(root, TRADE_REPOSITORY, Arrays.asList("lockMarketPayOrder", "queryGroupBuyProgress"), MAX_SNIPPET_CHARS));
        response.setSourceSnippets(snippets);
    }

    private void fillSettlementFlow(Path root, TradeFlowResponse response) {
        response.setApiPath("/api/v1/gbm/trade/settlement_market_pay_order");
        response.setEntryPoint("MarketTradeController.settlementMarketPayOrder");
        response.setSummary("结算是外部支付成功后把锁单明细从未支付推进到已完成，并在团队达到目标人数时写通知任务、触发 HTTP/MQ 通知和定时补偿。");
        response.setMainSteps(Arrays.asList(
                "接收 POST /api/v1/gbm/trade/settlement_market_pay_order 请求",
                "校验 userId、source、channel、outTradeNo、outTradeTime",
                "调用 ITradeSettlementOrderService.settlementMarketPayOrder",
                "执行结算规则链：SCRuleFilter -> OutTradeNoRuleFilter -> SettableRuleFilter -> EndRuleFilter",
                "TradeRepository.settlementMarketPayOrder 更新明细 status=1、团队 complete_count+1",
                "如果当前支付者是最后一人，更新团队 status=1 并插入 notify_task",
                "TradeSettlementOrderService 线程池触发 TradePort.groupBuyNotify",
                "GroupBuyNotifyJob 定时扫描 notify_task 做补偿重试",
                "返回 teamId、activityId、outTradeNo"));
        response.setDomainNodes(Arrays.asList(
                "ITradeSettlementOrderService.settlementMarketPayOrder",
                "TradeSettlementRuleFilterFactory",
                "SCRuleFilter",
                "OutTradeNoRuleFilter",
                "SettableRuleFilter",
                "EndRuleFilter",
                "ITradePort.groupBuyNotify"));
        response.setPersistenceNodes(Arrays.asList(
                "TradeRepository.settlementMarketPayOrder",
                "group_buy_order_list：status 0 -> 1",
                "group_buy_order：complete_count + 1，成团时 status 0 -> 1",
                "notify_task：成团后写通知任务",
                "nofify_task_mapper.xml"));
        response.setRelatedFiles(Arrays.asList(
                TRADE_CONTROLLER, SETTLEMENT_SERVICE, SETTLEMENT_FACTORY, TRADE_REPOSITORY,
                TRADE_PORT, NOTIFY_JOB, LOCK_FLOW_DOC));

        LinkedHashMap<String, String> snippets = new LinkedHashMap<>();
        snippets.put(LOCK_FLOW_DOC, readSection(root, LOCK_FLOW_DOC, "## 4. 结算流程", "## 5."));
        snippets.put(TRADE_CONTROLLER, readWindow(root, TRADE_CONTROLLER, Collections.singletonList("settlementMarketPayOrder"), MAX_SNIPPET_CHARS));
        snippets.put(SETTLEMENT_SERVICE, readSnippet(root, SETTLEMENT_SERVICE, MAX_SNIPPET_CHARS));
        snippets.put(SETTLEMENT_FACTORY, readSnippet(root, SETTLEMENT_FACTORY, MAX_SNIPPET_CHARS));
        snippets.put(TRADE_REPOSITORY, readWindow(root, TRADE_REPOSITORY, Collections.singletonList("settlementMarketPayOrder"), MAX_SNIPPET_CHARS));
        snippets.put(TRADE_PORT, readSnippet(root, TRADE_PORT, MAX_SNIPPET_CHARS));
        response.setSourceSnippets(snippets);
    }

    private void fillRefundFlow(Path root, TradeFlowResponse response) {
        response.setApiPath("/api/v1/gbm/trade/refund_market_pay_order");
        response.setEntryPoint("MarketTradeController.refundMarketPayOrder");
        response.setSummary("退单流程接收外部退单请求，校验 userId、source、channel、outTradeNo 后进入交易退单领域服务。退单规则链查询订单和团队状态，选择未支付退单、已支付未成团退单、已支付已成团退单等策略，并执行库存/团队状态回滚。");
        response.setMainSteps(Arrays.asList(
                "接收 POST /api/v1/gbm/trade/refund_market_pay_order 请求",
                "校验 userId、source、channel、outTradeNo",
                "调用 ITradeRefundOrderService.refundOrder",
                "执行退单规则链：DataNodeFilter -> UniqueRefundNodeFilter -> RefundOrderNodeFilter",
                "按订单和团队状态选择 IRefundOrderStrategy",
                "执行退单落库、锁单库存/团队计数回滚或成团退款行为",
                "返回 userId、orderId、teamId、退单行为 code/info"));
        response.setDomainNodes(Arrays.asList(
                "ITradeRefundOrderService.refundOrder",
                "TradeRefundRuleFilterFactory",
                "DataNodeFilter",
                "UniqueRefundNodeFilter",
                "RefundOrderNodeFilter",
                "Unpaid2RefundStrategy",
                "Paid2RefundStrategy",
                "PaidTeam2RefundStrategy"));
        response.setPersistenceNodes(Arrays.asList(
                "TradeRepository 查询订单和团队状态",
                "group_buy_order_list：退单状态更新",
                "group_buy_order：团队锁定数/完成数回滚",
                "refund 相关策略落库"));
        response.setRelatedFiles(Arrays.asList(
                TRADE_CONTROLLER, REFUND_SERVICE, REFUND_FACTORY, TRADE_REPOSITORY,
                "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/refund/business/impl/Unpaid2RefundStrategy.java",
                "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/refund/business/impl/Paid2RefundStrategy.java",
                "group-buy-market-FA-domain/src/main/java/cn/bugstack/domain/trade/service/refund/business/impl/PaidTeam2RefundStrategy.java"));

        LinkedHashMap<String, String> snippets = new LinkedHashMap<>();
        snippets.put(TRADE_CONTROLLER, readWindow(root, TRADE_CONTROLLER, Collections.singletonList("refundMarketPayOrder"), MAX_SNIPPET_CHARS));
        snippets.put(REFUND_SERVICE, readSnippet(root, REFUND_SERVICE, MAX_SNIPPET_CHARS));
        snippets.put(REFUND_FACTORY, readSnippet(root, REFUND_FACTORY, MAX_SNIPPET_CHARS));
        snippets.put(TRADE_REPOSITORY, readWindow(root, TRADE_REPOSITORY, Arrays.asList("refund", "restore", "query"), MAX_SNIPPET_CHARS));
        response.setSourceSnippets(snippets);
    }

    private void addApiSnippets(Path root, ApiSchemaResponse response, String... files) {
        LinkedHashMap<String, String> snippets = new LinkedHashMap<>();
        for (String file : files) {
            snippets.put(file, readSnippet(root, file, MAX_SNIPPET_CHARS));
        }
        response.setSourceSnippets(snippets);
    }

    private String normalizeScene(String scene) {
        if (isBlank(scene)) {
            return "lock_market_pay_order";
        }
        String value = scene.toLowerCase(Locale.ROOT).trim();
        if (value.contains("settlement") || value.contains("结算")) {
            return "settlement_market_pay_order";
        }
        if (value.contains("refund") || value.contains("退单") || value.contains("退款")) {
            return "refund_market_pay_order";
        }
        return "lock_market_pay_order";
    }

    private String normalizeApiName(String apiName) {
        if (isBlank(apiName)) {
            return "lock_market_pay_order";
        }
        String value = apiName.toLowerCase(Locale.ROOT).trim();
        if (value.contains("query") || value.contains("config") || value.contains("trial") || value.contains("营销配置") || value.contains("试算")) {
            return "query_group_buy_market_config";
        }
        return normalizeScene(value);
    }

    private Path projectRoot() {
        String root = System.getProperty("gbm.project.root");
        if (isBlank(root)) {
            root = System.getenv("GBM_PROJECT_ROOT");
        }
        if (isBlank(root)) {
            root = DEFAULT_PROJECT_ROOT;
        }
        return Paths.get(root).toAbsolutePath().normalize();
    }

    private String readSnippet(Path root, String relativeFile, int maxChars) {
        Path path = resolveInsideRoot(root, relativeFile);
        if (path == null) {
            return "文件路径不在拼团项目根目录内，已拒绝读取：" + relativeFile;
        }
        if (!Files.exists(path)) {
            return "文件不存在：" + relativeFile;
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return limit(content, maxChars);
        } catch (IOException e) {
            log.warn("读取拼团项目文件失败 relativeFile={}", relativeFile, e);
            return "读取文件失败：" + relativeFile + "，原因：" + e.getMessage();
        }
    }

    private String readWindow(Path root, String relativeFile, List<String> anchors, int maxChars) {
        String content = readSnippet(root, relativeFile, Integer.MAX_VALUE);
        if (content.startsWith("文件") || content.startsWith("读取文件失败")) {
            return content;
        }
        int index = -1;
        for (String anchor : anchors) {
            if (!isBlank(anchor)) {
                index = content.indexOf(anchor);
                if (index >= 0) {
                    break;
                }
            }
        }
        if (index < 0) {
            return limit(content, maxChars);
        }
        int start = Math.max(0, index - maxChars / 4);
        int end = Math.min(content.length(), start + maxChars);
        return content.substring(start, end);
    }

    private String readSection(Path root, String relativeFile, String startMarker, String endMarker) {
        String content = readSnippet(root, relativeFile, Integer.MAX_VALUE);
        if (content.startsWith("文件") || content.startsWith("读取文件失败")) {
            return content;
        }
        int start = content.indexOf(startMarker);
        if (start < 0) {
            return limit(content, MAX_SNIPPET_CHARS);
        }
        int end = content.indexOf(endMarker, start + startMarker.length());
        if (end < 0) {
            end = Math.min(content.length(), start + MAX_SNIPPET_CHARS);
        }
        return limit(content.substring(start, end), MAX_SNIPPET_CHARS);
    }

    private Path resolveInsideRoot(Path root, String relativeFile) {
        Path path = root.resolve(relativeFile).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            return null;
        }
        return path;
    }

    private String limit(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "\n... 已截断，避免工具结果过长 ...";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TradeFlowRequest {
        @JsonProperty(value = "scene")
        @JsonPropertyDescription("业务场景，例如 lock_market_pay_order、settlement_market_pay_order、refund_market_pay_order，也可以用中文：锁单、结算、退单。")
        private String scene;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TradeFlowResponse {
        @JsonProperty(required = true)
        private String scene;
        @JsonProperty(required = true)
        private String projectRoot;
        @JsonProperty(required = true)
        private Boolean readOnly;
        private String sideEffectPolicy;
        private String apiPath;
        private String entryPoint;
        private String summary;
        private List<String> mainSteps;
        private List<String> domainNodes;
        private List<String> persistenceNodes;
        private List<String> relatedFiles;
        private Map<String, String> sourceSnippets;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiSchemaRequest {
        @JsonProperty(value = "apiName")
        @JsonPropertyDescription("接口名称，例如 query_group_buy_market_config、lock_market_pay_order、settlement_market_pay_order、refund_market_pay_order。")
        private String apiName;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiSchemaResponse {
        @JsonProperty(required = true)
        private String apiName;
        @JsonProperty(required = true)
        private String projectRoot;
        @JsonProperty(required = true)
        private Boolean readOnly;
        private String httpMethod;
        private String path;
        private String controller;
        private String requestDto;
        private String responseDto;
        private List<String> requiredFields;
        private List<String> optionalFields = new ArrayList<>();
        private Map<String, String> sourceSnippets;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProjectContextRequest {
        @JsonProperty(value = "topic")
        @JsonPropertyDescription("需要查询的上下文主题，例如 architecture、trade、lock、settlement、refund。")
        private String topic;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProjectContextResponse {
        @JsonProperty(required = true)
        private String projectRoot;
        @JsonProperty(required = true)
        private Boolean readOnly;
        private String architecture;
        private List<String> importantModules;
        private List<String> recommendedDrawNodes;
        private Map<String, String> sourceSnippets;
    }

}
