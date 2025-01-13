package com.fudan.chain.DO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommonData {
    private String ruleVersion;
    private String appCode;
    private String ruleId;
    private String moduleId;
    private String userId;
    private String credibleTime;

    public CommonData(String commonData){

    }
}