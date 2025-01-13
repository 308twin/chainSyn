package com.fudan.chain.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectMapperFactory {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ObjectMapperFactory() {
        // 防止实例化
    }

    public static ObjectMapper getInstance() {
        return OBJECT_MAPPER;
    }
}