package Persistence;

import Model.CodeBlock;
import Util.JedisUtil;
import Util.sqlite.SqliteHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MappingSaver {
    private static final Logger logger = LoggerFactory.getLogger(MappingSaver.class);
    private SqliteHelper helper;

    public MappingSaver(String dbFilePath) {
        try {
            helper = new SqliteHelper(dbFilePath);
        } catch (SQLException | ClassNotFoundException e) {
            logger.error(e.getMessage());
        }
    }
    public void close(){
        helper.destroyed();
    }

    public void save() {
        try {
            PreparedStatement preparedStatement = helper.getPreparedStatement("insert into Mapping values(?,?);");
            Jedis jedis = JedisUtil.getJedis();
            Set<String> keys = jedis.keys("*");
            ObjectMapper objectMapper = new ObjectMapper();
            for(String key : keys) {
                CodeBlock codeBlock = objectMapper.readValue(jedis.get(key), CodeBlock.class);
                preparedStatement.setString(1,key);
                preparedStatement.setInt(2,codeBlock.getCodeBlockID());
                preparedStatement.addBatch();
            }
            helper.executePreparedStatement(preparedStatement);
            helper.destroyed();  // 手动关闭连接
        } catch (SQLException | ClassNotFoundException | JsonProcessingException e) {
            logger.error(e.toString());
        }
    }

    // todo: query
//    public void query() {
//        Map<String,String> mapping = null;
//        try {
//            mapping = helper.executeQuery("select * from Mapping", (resultSet, index) -> resultSet.getString("commitId"));
//        } catch (SQLException | ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//        System.out.println(mapping);
//    }
}
