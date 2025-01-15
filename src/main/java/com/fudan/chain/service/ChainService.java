/*
 * @Author: LHD
 * @Date: 2025-01-14 14:24:58
 * @LastEditors: 308twin 790816436@qq.com
 * @LastEditTime: 2025-01-15 15:21:51
 * @Description: 
 * 
 * Copyright (c) 2025 by 308twin@790816436@qq.com, All Rights Reserved. 
 */
package com.fudan.chain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import cn.dreamdt.audit.domain.AuditConfig;
import cn.dreamdt.audit.main.DataAudit;
import cn.dreamdt.audit.main.DataAuditConfigure;
import cn.dreamdt.audit.maker.chain.ChainMakerClient;
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
import com.fudan.chain.service.ChainService.ChainMessageImpl;
import com.fudan.chain.service.DBService;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.chainmaker.pb.common.*;
import org.chainmaker.sdk.ChainClient;
import org.chainmaker.sdk.ChainClientException;
import org.chainmaker.sdk.crypto.ChainMakerCryptoSuiteException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;

@Service
@Slf4j
public class ChainService {
    private Map<String, Long> parsedMap = new HashMap<>();
    private final DBService dbService;
    private final HashService hashService;
    private static DataAudit dataAudit;
    private static ChainMakerClient client;
    // private final ReentrantLock pullLock = new ReentrantLock();
    private final AtomicBoolean pullRunning = new AtomicBoolean(false);

    public ChainService(DBService dbService, HashService hashService) {
        this.dbService = dbService;
        this.hashService = hashService;
        this.parsedMap = dbService.getChainParsedCount();
        this.init();
    }

    // 后续可能支持多个链
    public void init() {
        dataAudit = DataAudit.create(AuditConfig.builder()
                .appCode("wycfdyjxm")
                .appKey("250107152633yaP2ytaqbSUJmS6lyoH")
                .serverUrl("http://10.91.255.86:8090/DreamWeb")
                .logFilePath("/Users/lihaodong/Documents")
                .chainConsumer(new ChainMessageImpl())
                .build());
        dataAudit.subscribeChain("ZW_C8458A2_004", null, null, false);
        client = DataAuditConfigure.getClient("ZW_C8458A2_004", "");
    }

    public long getBlockHeight() {
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
        return currentBlockHeight;
    }

    //@PostConstruct
    void block() {

        ChainClient chainClient = client.getChainClient();

        try {
            chainClient.subscribeBlock(11, 11, false, false,
//                chainClient.subscribeBlock(parsedBlockHeight+1, Math.min(parsedBlockHeight + 100,currentBlockHeight), false, false,
                    new StreamObserver<ResultOuterClass.SubscribeResult>() {

                        @Override
                        public void onNext(ResultOuterClass.SubscribeResult result) {
                            ChainmakerBlock.BlockInfo blockInfo = null;

                            try {
                                blockInfo = ChainmakerBlock.BlockInfo.parseFrom(result.getData());
                            } catch (InvalidProtocolBufferException var20) {
                                var20.printStackTrace();
                            }

                            ChainmakerBlock.BlockHeader blockHeader = blockInfo.getBlock().getHeader();
                            long blockHeight = blockHeader.getBlockHeight();

                            System.out.println("----------BLOCK" + blockHeight + "----------");

                            List<ChainmakerTransaction.Transaction> transactions = blockInfo.getBlock()
                                    .getTxsList();
                            int i = 1;
                            for (ChainmakerTransaction.Transaction tx : transactions) {
                                System.out.println(i);
                                Request.Payload payload = tx.getPayload();
                                String contractName = payload.getContractName();
                                String tableName = DBService.contractTableMap.get(contractName);
                                if (!StringUtils.equals("SuperviseOnlineVehicle", contractName)) {
                                    continue;
                                }
                                System.out.println(i);
                                // byte[] byteArray = tx.toByteArray();
                                // PayloadBaseInfo payloadBaseInfo = new PayloadBaseInfo(tx);
//                                payloadBaseInfo.setBlock(blockHeight);
//                                payloadBaseInfo.setTxNum(i++);
//                                payloadBaseInfo
//                                        .setVerifyHash(hashService.calPayLoadHash(payloadBaseInfo, tableName));
//                                System.out.println(payloadBaseInfo);
//                                String insertSQL = dbService.constructInsertQuery(payloadBaseInfo, tableName);
                                //dbService.addSQL(insertSQL);
                                //dbService.upsertChainParsedCount(chainID, blockHeight);

                            }
                            //parsedMap.put(chainID, blockHeight);

                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.info(throwable.toString());
                        }

                        @Override
                        public void onCompleted() {
                            pullRunning.set(false);
                            System.out.println("completed");

                        }

            });
        } catch (ChainMakerCryptoSuiteException | ChainClientException e) {
            e.printStackTrace();
            log.info(e.toString());
            throw new RuntimeException(e);
        }

        try {
            Thread.sleep(100000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    //@PostConstruct
    public void pullDataFromChain() {
        // 后续可能支持多个链
        String chainID = "ZW_C8458A2_004";
        if (pullRunning.compareAndSet(false, true)) {
            long currentBlockHeight = getBlockHeight();
            long parsedBlockHeight = parsedMap.getOrDefault(chainID, 0L);
            ChainClient chainClient = client.getChainClient();
            try {
                // 一次性订阅100个
                //  chainClient.subscribeBlock(89, Math.min(89,currentBlockHeight), false, false,
               chainClient.subscribeBlock(parsedBlockHeight+1, Math.min(parsedBlockHeight + 100,currentBlockHeight), false, false,
                        new StreamObserver<ResultOuterClass.SubscribeResult>() {

                            @Override
                            public void onNext(ResultOuterClass.SubscribeResult result) {
                                ChainmakerBlock.BlockInfo blockInfo = null;

                                try {
                                    blockInfo = ChainmakerBlock.BlockInfo.parseFrom(result.getData());
                                } catch (InvalidProtocolBufferException var20) {
                                    var20.printStackTrace();
                                }

                                ChainmakerBlock.BlockHeader blockHeader = blockInfo.getBlock().getHeader();
                                long blockHeight = blockHeader.getBlockHeight();

                                System.out.println("----------BLOCK" + blockHeight + "----------");

                                List<ChainmakerTransaction.Transaction> transactions = blockInfo.getBlock()
                                        .getTxsList();
                                int i = 1;
                                for (ChainmakerTransaction.Transaction tx : transactions) {
                                    Request.Payload payload = tx.getPayload();
                                    String contractName = payload.getContractName();
                                    String tableName = DBService.contractTableMap.get(contractName);
                                    if (!StringUtils.equals("SuperviseOnlineVehicle", contractName)) {
                                        continue;
                                    }
                                    PayloadBaseInfo payloadBaseInfo;
                                    try {
                                        payloadBaseInfo = PayloadBaseInfo.fromTransaction(tx);
                                    } catch (Exception e) {
                                        continue;
                                    }
                                    if(payloadBaseInfo == null){
                                        continue;
                                    }
                                    payloadBaseInfo.setBlock(blockHeight);
                                    payloadBaseInfo.setTxNum(i++);
                                    payloadBaseInfo
                                            .setVerifyHash(hashService.calPayLoadHash(payloadBaseInfo, tableName));
                                    //System.out.println(payloadBaseInfo);
                                    String insertSQL = dbService.constructInsertQuery(payloadBaseInfo, tableName);
                                    dbService.addSQL(insertSQL);
                                    dbService.upsertChainParsedCount(chainID, blockHeight);

                                }
                                parsedMap.put(chainID, blockHeight);
                                
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                pullRunning.set(false); // 出错时重置状态
                                log.info(throwable.toString());
                                System.out.println("on error");
                                System.out.println(throwable);
                            }

                            @Override
                            public void onCompleted() {                                
                                pullRunning.set(false);
                                System.out.println("completed");

                            }
                        });

            } catch (Exception e) {
                pullRunning.set(false);
                throw new RuntimeException(e);
            }

        } else {
            log.info("pullDataFromChain is running, skip this time");
        }

    }

    static class ChainMessageImpl extends ChainConsumer {

        @Override
        public MessageBean onMessage(ChainMessage chainMessage) {
            //log.info(JSON.toJSONString(chainMessage));
            return AuditUtil.success(); // 注意：v1.6.1及以上版本新增返回
        }
    }

}
