package com.fudan.chain.DO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fudan.chain.utils.ObjectMapperFactory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import cn.dreamdt.audit.domain.AuditConfig;
import cn.dreamdt.audit.domain.ChainMessage;
import cn.dreamdt.audit.domain.DataAuditServerConfig;
import cn.dreamdt.audit.domain.MessageBean;
import cn.dreamdt.audit.logger.LoggerService;
import cn.dreamdt.audit.main.DataAudit;
import cn.dreamdt.audit.main.DataAuditConfigure;
import cn.dreamdt.audit.maker.chain.ChainConsumer;
import cn.dreamdt.audit.maker.chain.ChainMakerClient;
import cn.dreamdt.audit.rule.domain.AuditRule;
import cn.dreamdt.audit.util.AuditUtil;
import org.chainmaker.pb.common.ChainmakerTransaction;
import org.chainmaker.pb.common.Request;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PayloadBaseInfo {
    @JsonProperty("chain_id")
    private String chainId;

    @JsonProperty("tx_id")
    private String txId;

    @JsonProperty("operate_time")
    private Long operateTime;

    @JsonProperty("contract_name")
    private String contractName;

    private String version;

    @JsonProperty("user_id")
    private String userId;

    private String method;

    @JsonProperty("app_code")
    private String appCode;

    @JsonProperty("rule_id")
    private String ruleId;

    @JsonProperty("raw_data")
    private String rawData;

    public PayloadBaseInfo(ChainmakerTransaction.Transaction tx){
        Request.Payload payload = tx.getPayload();
        ObjectMapper objectMapper = ObjectMapperFactory.getInstance();
        JsonNode rootNode = null;
        CommonData commonData = null;
        String rawData = null;

        try {
            String commonString = payload.getParametersList().get(0).getValue().toStringUtf8();
            rootNode = objectMapper.readTree(commonString);
            JsonNode commonDataNode = rootNode.get("commonData");
            JsonNode rawDataNode = rootNode.get("rawData");
            commonData = objectMapper.treeToValue(commonDataNode, CommonData.class);
            rawData = rawDataNode.toString();

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        this.chainId = payload.getChainId();
        this.txId = payload.getTxId();
        this.operateTime = payload.getTimestamp();
        this.contractName = payload.getContractName();
        this.version = commonData.getRuleVersion();
        this.userId = commonData.getUserId();
        this.method = payload.getMethod();
        this.appCode = commonData.getAppCode();
        this.rawData = rawData;

    }
}
