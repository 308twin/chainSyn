package com.fudan.chain.listener;

import cn.dreamdt.audit.main.DataAudit;
import cn.dreamdt.audit.rule.domain.AuditRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;

public class ChainListener implements CommandLineRunner {

    @Autowired
    DataAudit dataAudit;

    @Override
    public void run(String... args) throws Exception {
        dataAudit.subscribeChain(null,null,null,false);
    }
}
