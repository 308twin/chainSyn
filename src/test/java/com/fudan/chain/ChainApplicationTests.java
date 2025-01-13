package com.fudan.chain;

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
import cn.dreamdt.audit.util.LoggerUtil;
import cn.dreamdt.audit.util.ThreadPoolUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fudan.chain.DO.CommonData;
import com.fudan.chain.DO.PayloadBaseInfo;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chainmaker.pb.common.*;
import org.chainmaker.sdk.ChainClient;
import org.chainmaker.sdk.ChainClientException;
import org.chainmaker.sdk.crypto.ChainMakerCryptoSuiteException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@Slf4j
class ChainApplicationTests {

    static DataAudit dataAudit;
    static ChainMakerClient client;

    @Test
    void contextLoads() {
    }

    @BeforeAll
    static void init() {
        dataAudit = DataAudit.create(AuditConfig.builder()
                .appCode("wycfdyjxm")
                .appKey("250107152633yaP2ytaqbSUJmS6lyoH")
                .serverUrl("http://10.91.255.86:8090/DreamWeb")
                .logFilePath("/Users/lihaodong/Documents")
                .chainConsumer(new ChainMessageImpl() )
                .build());
        dataAudit.subscribeChain("ZW_C8458A2_004", null, null, false);
        client = DataAuditConfigure.getClient("ZW_C8458A2_004", "");
    }

    static class ChainMessageImpl extends ChainConsumer {

        @Override
        public MessageBean onMessage(ChainMessage chainMessage) {
            log.info(JSON.toJSONString(chainMessage));
            return AuditUtil.success(); //注意：v1.6.1及以上版本新增返回
        }
    }

    @Test
    void sdkTest() throws InterruptedException {
        dataAudit.subscribeChain(false);
        Thread.sleep(10000000);
//        dataAudit.getAuditRule();
        System.out.println();
    }

    @Test
    void carGetTest() {
        String contractName = "SuperviseOnlineVehicle";
        String methodName = "GetHistoryDriverIDsByTime";
        JSONObject param = new JSONObject();
        param.put("RegisterDate", "2022-01-01 00:00:00");

        MessageBean query = dataAudit.invoke(contractName, methodName, param, "1111");
        System.out.println(JSON.toJSONString(query));
    }

    @Test
    void carToChainTest() {
        String contractName = "SuperviseOnlineVehicle";
        String methodName = "CreateHistoryVehicle";
        JSONObject param = new JSONObject();
        param.put("UuId", "uuid233098");
        param.put("AddTime", "2000-01-01 00:00:00");
        param.put("RegisterDate", "2000-01-01 00:00:00");
        param.put("Ukey", "ukedy9");
        param.put("UpdTime", "2000-01-01 00:00:00");
        param.put("IsRemove", "Y");
        param.put("Status", 0);
        MessageBean query = dataAudit.invoke(contractName, methodName, param, "111431");
        System.out.println(JSON.toJSONString(query));
        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void queryTxById() {
        MessageBean messageBean = dataAudit.queryTransactionByTxId("ZW_C8458A2_004", "1818a26b2bdffdc7caf9b6bef3a1f328ce924ae413af443cb2e57c0c376670b1", true);
        System.out.println(JSON.toJSONString(messageBean));
    }

    static class TxInfo {
        String contratName;
    }

    @Test
    void queryTx() {
        String contractName = "SuperviseOnlineVehicle";
        String methodName = "CreateHistoryDriver";
        MessageBean messageBean = dataAudit.query(contractName, methodName, "1818a6f94b90af8cca0dc91088c69e2808c8ac9ead9641f8913d39be35e3e53b");
        System.out.println(JSON.toJSONString(messageBean));
    }

    @Test
    void getBlockHeight() {
        ChainClient chainClient = client.getChainClient();
        long currentBlockHeight = 0;
        try {
            currentBlockHeight = chainClient.getCurrentBlockHeight(1000);
        } catch (ChainMakerCryptoSuiteException e) {
            throw new RuntimeException(e);
        } catch (ChainClientException e) {
            throw new RuntimeException(e);
        }
        System.out.println(currentBlockHeight);
    }

    @Test
    void block() {

        ChainClient chainClient = client.getChainClient();

        try {
            chainClient.subscribeBlock(30, 100, true, false, new StreamObserver<ResultOuterClass.SubscribeResult>() {

                @Override
                public void onNext(ResultOuterClass.SubscribeResult result) {
                    ChainmakerBlock.BlockInfo blockInfo = null;

                    try {
                        blockInfo = ChainmakerBlock.BlockInfo.parseFrom(result.getData());
                    } catch (InvalidProtocolBufferException var20) {
                        var20.printStackTrace();
                    }



                    ChainmakerBlock.BlockHeader blockHeader = blockInfo.getBlock().getHeader();
                    String chainId = blockHeader.getChainId();
                    long blockHeight = blockHeader.getBlockHeight();

                    System.out.println("----------BLOCK" + blockHeight + "----------");



                    List<ChainmakerTransaction.Transaction> transactions = blockInfo.getBlock().getTxsList();
                    Iterator txIter = transactions.iterator();
//                    System.out.println(JSON.toJSONString(transactions));

                    for (ChainmakerTransaction.Transaction tx: transactions) {
                        Request.Payload payload = tx.getPayload();
                        String contractName = payload.getContractName();
                        if (!StringUtils.equals("SuperviseOnlineVehicle", contractName)) {
                            continue;
                        }
//                        System.out.println(payload.getParametersList());
                        byte[] byteArray = tx.toByteArray();
                        PayloadBaseInfo payloadBaseInfo = new PayloadBaseInfo(tx);
                        System.out.println(payloadBaseInfo);
                        //String jsonString = JSON.toJSONString(tx);
                        //System.out.println(byteArray);
//                        ObjectMapper objectMapper = new ObjectMapper();
                        // 解析 JSON 字符串为树结构
//                        JsonNode rootNode = null;
//                        CommonData commonData = null;
//                        String rawData = null;
//                        try {
//                            String commonString = payload.getParametersList().get(0).getValue().toStringUtf8();
//                            rootNode = objectMapper.readTree(commonString);
//                            JsonNode commonDataNode = rootNode.get("commonData");
//                            JsonNode rawDataNode = rootNode.get("rawData");
//                            commonData = objectMapper.treeToValue(commonDataNode, CommonData.class);
//                            rawData = rawDataNode.toString();
//                            System.out.println(rawData);
//                            System.out.println("Constructed CommonData: " + commonData);
//
//                        } catch (JsonProcessingException e) {
//                            throw new RuntimeException(e);
//                        }
//
//                        // 提取 commonData 节点
//
//                        PayloadBaseInfo payloadBaseInfo = PayloadBaseInfo.builder()
//                                .chainId(payload.getChainId())
//                                .txId(payload.getTxId())
//                                .operateTime(payload.getTimestamp())
//                                .contractName(payload.getContractName())
//                                .version(commonData.getRuleVersion())
//                                .userId(commonData.getUserId())
//                                .method(payload.getMethod())
//                                .appCode(commonData.getAppCode())
//                                .rawData(rawData).build();

//                      String jsonString = JSON.toJSONString(payload);
//                        System.out.println("jsonString");
//                        System.out.println(jsonString);
                    }
                    }


                @Override
                public void onError(Throwable throwable) {
                    System.out.println(throwable);
                }

                @Override
                public void onCompleted() {
                    System.out.println("completed");
                }
            });
        } catch (ChainMakerCryptoSuiteException e) {
            throw new RuntimeException(e);
        } catch (ChainClientException e) {
            throw new RuntimeException(e);
        } catch (Exception e){
            throw new RuntimeException(e);
        }

        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
