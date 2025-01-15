package com.fudan.chain.config;

import cn.dreamdt.audit.domain.AuditConfig;
import cn.dreamdt.audit.domain.ChainMessage;
import cn.dreamdt.audit.domain.MessageBean;
import cn.dreamdt.audit.main.DataAudit;
import cn.dreamdt.audit.maker.chain.ChainConsumer;
import cn.dreamdt.audit.util.AuditUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
@Slf4j
public class DataAuditConfiguration {

    //@Bean
    DataAudit initDataAudit() {
        return DataAudit.create(
                    AuditConfig.builder()
                    .appCode("wycfdyjxm")
                    .appKey("250107152633yaP2ytaqbSUJmS6lyoH")
                    .serverUrl("http://10.91.255.86:8090/DreamWeb")
                    .logFilePath("D:\\log")
                    .chainConsumer(new ChainConsumer() {
                            @Override
                            public MessageBean onMessage(ChainMessage chainMessage) {
                                log.info(JSON.toJSONString(chainMessage));
                                return AuditUtil.success(); //注意：v1.6.1及以上版本新增返回
                            }
                        })
                    .build()
        );
    }
}
