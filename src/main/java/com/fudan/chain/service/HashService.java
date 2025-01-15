package com.fudan.chain.service;

import com.fudan.chain.DO.PayloadBaseInfo;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class HashService {
    public Map<String,String> newestHashs;
    private final DBService dbService;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;


    public HashService(DBService dbService){
        this.dbService = dbService;
        newestHashs = dbService.getAllNewestHash();
    }

    /* 当前结构的hash和之前的hash计算出新的hash */
    public String calPayLoadHash(PayloadBaseInfo payloadBaseInfo,String tableName){
        String newestHash = newestHashs.get(tableName);
        newestHash = fnv1aHash(newestHash+payloadBaseInfo.calHash());
        newestHashs.put(tableName,newestHash);
        return newestHash;
    }

    public static String fnv1aHash(String input) {
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < input.length(); i++) {
            hash ^= input.charAt(i);
            hash *= FNV_PRIME;
        }
        return Long.toHexString(hash);
    }

}
