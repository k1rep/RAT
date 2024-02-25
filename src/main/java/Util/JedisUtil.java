package Util;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ResourceBundle;

public class JedisUtil {
    private static JedisPool jedisPool = null;
    static {
        ResourceBundle bundle = ResourceBundle.getBundle("redis");
        int maxTotal = Integer.parseInt(bundle.getString("redis.maxTotal"));
        int maxIdle = Integer.parseInt(bundle.getString("redis.maxIdle"));
        String host = bundle.getString("host");
        int port = Integer.parseInt(bundle.getString("port"));
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        jedisPool = new JedisPool(config,host,port);
    }
    public static Jedis getJedis(){
        return jedisPool.getResource();
    }
}
