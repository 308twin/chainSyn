package com.fudan.chain.service;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import com.fudan.chain.DO.PayloadBaseInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@Service
public class DBService {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    // 用于缓存SQL语句的线程安全队列
    private final Queue<String> sqlCache = new ConcurrentLinkedQueue<>();
    // 用于存储执行失败的SQL语句的线程安全队列
    private final Queue<FailedSQL> failedSqlCache = new ConcurrentLinkedQueue<>();
    private final int BATCH_SIZE = 10000;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Log logger = LogFactory.getLog(DBService.class);

    public static Map<String, String> contractTableMap = new HashMap<>();
    static {
        contractTableMap.put("SuperviseOnlineVehicle", "supervise_online_vehicle");
    }

    // 执行查询来获取所有的表名
    public List<String> getAllTableNames() {
        String sql = "SHOW TABLES";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    // 初始化调度程序，每隔5秒执行一次批量操作
    public DBService() {
        scheduler.scheduleAtFixedRate(this::executeBatch, 5, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::retryFailedBatch, 5, 5, TimeUnit.MINUTES);
    }

    // 添加SQL语句到缓存中
    public void addSQL(String sql) {
        sqlCache.add(sql);
        // 如果缓存达到批量大小，立即执行批处理
        if (sqlCache.size() >= BATCH_SIZE) {
            executeBatch();
        }
    }

    // 批量执行缓存的SQL语句
    private void executeBatch() {
        List<String> batch = new ArrayList<>();
        while (!sqlCache.isEmpty() && batch.size() < BATCH_SIZE) {
            batch.add(sqlCache.poll());
        }
        if (!batch.isEmpty()) {
            try {
                jdbcTemplate.batchUpdate(batch.toArray(new String[0]));
                logger.info("Batch executed with " + batch.size() + " statements.");
            } catch (Exception e) {
                logger.info("Error executing batch: " + e.getMessage());
                // 将失败的SQL添加到失败缓存中并记录失败次数
                for (String sql : batch) {
                    failedSqlCache.add(new FailedSQL(sql));
                }
            }
        }

    }

    // 重试执行失败的SQL语句
    private void retryFailedBatch() {
        List<FailedSQL> batch = new ArrayList<>();
        while (!failedSqlCache.isEmpty() && batch.size() < BATCH_SIZE) {
            batch.add(failedSqlCache.poll());
        }
        if (!batch.isEmpty()) {
            List<String> sqlStatements = new ArrayList<>();
            for (FailedSQL failedSQL : batch) {
                if (failedSQL.getRetryCount() < 10) {
                    sqlStatements.add(failedSQL.getSql());
                }
            }
            if (!sqlStatements.isEmpty()) {
                try {
                    jdbcTemplate.batchUpdate(sqlStatements.toArray(new String[0]));
                    logger.info("Retry batch executed with " + sqlStatements.size() + " statements.");
                } catch (Exception e) {
                    logger.info("Error retrying batch: " + e.getMessage());
                    // 记录重试失败的SQL语句，增加其重试次数
                    for (FailedSQL failedSQL : batch) {
                        failedSQL.incrementRetryCount();
                        if (failedSQL.getRetryCount() < 10) {
                            failedSqlCache.add(failedSQL);
                        } else {
                            logger.error("Max retry limit reached for SQL: " + failedSQL.getSql());
                        }
                    }
                }
            }
        }
    }

    // public static String buildSuperviseOnlineVehicleSQL(PayloadBaseInfo
    // payLoadInfo) {
    // return constructInsertQuery(payLoadInfo,"supervise_online_vehicle");
    // }

    public String buildInsertSQL(PayloadBaseInfo payloadBaseInfo, String contractName) {
        return constructInsertQuery(payloadBaseInfo, contractTableMap.get(contractName));
    }

    public Map<String, String> getAllNewestHash() {
        List<String> tableNames = getAllTableNames();
        Map<String, String> newestHashs = new HashMap<>();
        for (String table : tableNames) {
            String newestHash = getNewestHashFromTable(table);
            newestHashs.put(table, newestHash);
        }
        return newestHashs;
    }

    public String getNewestHashFromTable(String tableName) {
        try {
            String sql = "SELECT verify_hash FROM " + tableName + " ORDER BY block DESC, tx_num DESC LIMIT 1";
            return jdbcTemplate.queryForObject(sql, String.class);
        } catch (Exception e) {
            return "";
        }

    }

    public String constructInsertQuery(Object obj, String tableName) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        StringBuilder columns = new StringBuilder("(");
        StringBuilder values = new StringBuilder("VALUES (");

        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                if (value != null) { // 只处理非空字段
                    // 获取字段对应的数据库列名，并用反引号包裹
                    String columnName = "`" + getColumnName(field) + "`";
                    columns.append(columnName).append(", ");
                    if (value instanceof String || value instanceof Character) {
                        values.append("'").append(value).append("', ");
                    } else {
                        values.append(value).append(", ");
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Error accessing field: " + field.getName(), e);
            }
        }

        // 移除最后的多余逗号和空格
        columns.setLength(columns.length() - 2);
        values.setLength(values.length() - 2);

        sql.append(tableName).append(" ")
                .append(columns).append(") ")
                .append(values).append(")");

        return sql.toString();
    }

    private static String getColumnName(Field field) {
        JsonProperty annotation = field.getAnnotation(JsonProperty.class);
        return (annotation != null) ? annotation.value() : field.getName();
    }

    public void upsertChainParsedCount(String chainId, long parsedCount) {
        // String sql = "INSERT INTO chain_parsed_count (chain_id, parsed_count) " +
                // "VALUES (?, ?) " +
                // "ON DUPLICATE KEY UPDATE parsed_count = ?";
        String sql = "INSERT INTO chain_parsed_count (chain_id, parsed_count) " +
                "VALUES ('" + chainId + "', " + parsedCount + ") " +
                "ON DUPLICATE KEY UPDATE parsed_count = " + parsedCount;
        //System.out.println(sql);
        sqlCache.add(sql);

        //jdbcTemplate.update(sql, chainId, parsedCount, parsedCount);

    }

    public Map<String, Long> getChainParsedCount() {
        String sql = "SELECT chain_id, parsed_count FROM chain_parsed_count";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        Map<String, Long> chainParsedCount = new HashMap<>();
        for (Map<String, Object> row : rows) {
            chainParsedCount.put((String) row.get("chain_id"), (Long) row.get("parsed_count"));
        }
        return chainParsedCount;
    }

    private static class FailedSQL {
        private final String sql;
        private int retryCount;

        public FailedSQL(String sql) {
            this.sql = sql;
            this.retryCount = 0;
        }

        public String getSql() {
            return sql;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void incrementRetryCount() {
            this.retryCount++;
        }
    }
}
