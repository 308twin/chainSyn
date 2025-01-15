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
import com.fudan.chain.service.ChainService;
import com.fudan.chain.service.DBService;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.MemberSubstitution.Substitution.Chain;

import org.apache.commons.lang3.StringUtils;
import org.chainmaker.pb.common.*;
import org.chainmaker.sdk.ChainClient;
import org.chainmaker.sdk.ChainClientException;
import org.chainmaker.sdk.crypto.ChainMakerCryptoSuiteException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@Slf4j
class ChainApplicationTests {

    @Autowired
    ChainService chainService;
    
    static DataAudit dataAudit;
    static ChainMakerClient client;

    @Test
    void contextLoads() {
        chainService.pullDataFromChain();
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

    @Test
    public void pullDataFromChain() {
        ChainClient chainClient = client.getChainClient();

        // 使用 CountDownLatch 保持主线程等待，这里设置1表示等待完成或错误（订阅结束）
        CountDownLatch latch = new CountDownLatch(1);

        try {
            chainClient.subscribeBlock(53, 73, false, false, new StreamObserver<ResultOuterClass.SubscribeResult>() {
                @Override
                public void onNext(ResultOuterClass.SubscribeResult result) {
                    try {
                        ChainmakerBlock.BlockInfo blockInfo = ChainmakerBlock.BlockInfo.parseFrom(result.getData());
                        ChainmakerBlock.BlockHeader blockHeader = blockInfo.getBlock().getHeader();
                        long blockHeight = blockHeader.getBlockHeight();
                        System.out.println("----------BLOCK " + blockHeight + "----------");

                        List<ChainmakerTransaction.Transaction> transactions = blockInfo.getBlock().getTxsList();
                        int i = 0;
                        for (ChainmakerTransaction.Transaction tx : transactions) {
                            String contractName = tx.getPayload().getContractName();
                            // 判断合同名称是否匹配，这里示例中只处理特定合同
                            if (!StringUtils.equals("SuperviseOnlineVehicle", contractName)) {
                                continue;
                            }
                            // 这里假设 PayloadBaseInfo 构造函数根据 Transaction 实例进行初始化
                            PayloadBaseInfo payloadBaseInfo = PayloadBaseInfo.fromTransaction(tx);
                            payloadBaseInfo.setBlock(blockHeight);
                            payloadBaseInfo.setTxNum(i++);
                            System.out.println(payloadBaseInfo);
                        }
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    System.out.println("订阅出错：" + throwable);
                    // 发生错误时，减少 latch 数量，使主线程可以退出
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    System.out.println("订阅 completed");
                    // 订阅正常完成时，减少 latch 数量
                    latch.countDown();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            // 遇到异常时确保 latch 被释放，否则主线程会一直等待
            latch.countDown();
        }

        // 如果不使用 CountDownLatch.await()，主线程可能先于异步逻辑结束
        // 这里等待 100 秒（或根据业务需求等待足够长的时间），也可以选择无限期等待
        try {
            if (!latch.await(100, TimeUnit.SECONDS)) {
                System.out.println("等待超时，未接收到订阅完成或错误通知");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("主线程等待被中断: " + e.getMessage());
        }

        // 程序退出前可做一些资源释放工作，例如关闭 Channel 等（根据实际实现调用相应方法）
        System.out.println("主线程结束，程序退出");
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
            currentBlockHeight = chainClient.getCurrentBlockHeight(10000);
        } catch (ChainMakerCryptoSuiteException e) {
            throw new RuntimeException(e);
        } catch (ChainClientException e) {
            throw new RuntimeException(e);
        }
        System.out.println(currentBlockHeight);
        //return currentBlockHeight;
    }

    @Test
    void block() {

        ChainClient chainClient = client.getChainClient();

        try {
            chainClient.subscribeBlock(10, 11, false, false, new StreamObserver<ResultOuterClass.SubscribeResult>() {

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
                        byte[] byteArray = tx.toByteArray();
                        String jsonString = JSON.toJSONString(tx);
                        System.out.println(byteArray);
                        System.out.println(jsonString);
                    }
//
                    }

//                }

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
        }

        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
