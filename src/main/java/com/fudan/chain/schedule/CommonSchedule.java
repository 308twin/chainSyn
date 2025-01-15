/*
 * @Author: LHD
 * @Date: 2025-01-15 10:55:37
 * @LastEditors: 308twin 790816436@qq.com
 * @LastEditTime: 2025-01-15 14:56:19
 * @Description: 
 * 
 * Copyright (c) 2025 by 308twin@790816436@qq.com, All Rights Reserved. 
 */
package com.fudan.chain.schedule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fudan.chain.service.ChainService;

@Component
public class CommonSchedule {

    private final ChainService chainService;

    public CommonSchedule(ChainService chainService) {
        this.chainService = chainService;
    }


    @Scheduled(fixedRate = 5000)
    public void pullFromChain(){
        chainService.pullDataFromChain();
    }
}
