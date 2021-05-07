package link.thingscloud.freeswitch.esl.pool;

import com.alibaba.fastjson.JSON;
import link.thingscloud.freeswitch.esl.transport.CommandResponse;
import link.thingscloud.freeswitch.esl.transport.message.EslMessage;
import link.thingscloud.freeswitch.esl.util.CurrentTimeMillisClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


/**
 *  根据host:port参数创建Esl连接对象;
 *
 */
public class EslConnectionUtil  {

    protected static final Logger logger = LoggerFactory.getLogger(EslConnectionUtil.class);

    private static  final ConcurrentHashMap<String, EslConnectionPool> eslConnectionPools = new ConcurrentHashMap<>(200);

    /**
     *  请求api接口，获取可用节点列表并返回一个随机节点的连接池信息;
     *  callcenter和Freeswitch的关系是一对多的关系， 1个callcenter节点管理数个Freeswitch节点；
     *  每个callcenter节点请求 fs_nodes api接口，获取到的Freeswitch节点是不同的， 在api接口端自动做了负载算法；
     *  （api接口负责对Fs节点进行分组分配，每个callcenter有唯一标识; api接口端通过callcenter的唯一标识进行区分;
     *    针对FS节点的宕机问题，callcenter重新连接FS时，判断当前FS节点是否在fs_nodes列表中，如果不在则放弃重连接：
     *  ）
     * @return
     */
    public synchronized static void initConnPool( List<FreeswitchNodeInfo> nodeList) {
        if (eslConnectionPools.size() != 0 || nodeList == null || nodeList.size() == 0) {
            return;
        }
        try {
            for (FreeswitchNodeInfo node : nodeList) {
                getEslConnectionPoolEx(node);
            }
        } catch (Exception e) {
            logger.error("initConnPool 初始化Freeswitch连接池时发生错误: {}", e.toString());
        }
    }



    /**
     *  获取指定host的esl连接池; 连接池的连接仅用于指令发送，不接收事件；
     * @param host
     * @param port
     * @return
     */
    public static EslConnectionPool getEslConnectionPool(String host, int port){
        String hostKey = String.format("%s:%d", host, port);
        return eslConnectionPools.get(hostKey);
    }




    /**
     *  获取、创建指定host的esl连接池; 连接池的连接仅用于指令发送，不接收事件；
     * @param nodeInfo
     * @return
     */
    private static EslConnectionPool getEslConnectionPoolEx(FreeswitchNodeInfo nodeInfo){
        String host = nodeInfo.getHost();
        int port = nodeInfo.getPort();
        String pass = nodeInfo.getPass();
        int poolSize = nodeInfo.getPoolSize();
        String hostKey = String.format("%s:%d", host, port);
        EslConnectionPool connectionPool = eslConnectionPools.get(hostKey);
        if(null == connectionPool) {
            synchronized (hostKey.intern()) {
                connectionPool = eslConnectionPools.get(hostKey);
                if (null == connectionPool) {
                     connectionPool =  EslConnectionPool.createPool(poolSize, host, port , pass);
                    eslConnectionPools.put(hostKey,  connectionPool);
                }
            }
        }
        return connectionPool;
    }

    /**
     *  执行app指令;
     * @param app
     * @param param
     * @param uuid
     * @param eslConnectionPool
     */
    public static  String sendExecuteCommand(String app, String param, String uuid, EslConnectionPool eslConnectionPool){
        long startTime = CurrentTimeMillisClock.now();
        String traceId = uuid;
        EslConnectionDetail  connection = eslConnectionPool.getConnection();
        long cost = CurrentTimeMillisClock.now() - startTime;
        logger.info("{} successfully borrow a esl connection , cost: {} mills.", traceId, cost);
        String response = "";
        try {
            long cmdStartTime = CurrentTimeMillisClock.now();
            CommandResponse cmdResponse  = connection.getRealConn().execute(
                    eslConnectionPool.getEslAddr(),
                    app,
                    param,
                    uuid
            );
            cost = CurrentTimeMillisClock.now() - cmdStartTime;
            response = JSON.toJSONString(cmdResponse);
            logger.info("{} [sendExecuteCommand] successfully get response:{}, from esl connection, cost: {} mills.", traceId, response, cost);
        }catch ( Exception e )
        {
        }finally {
            eslConnectionPool.releaseOneConn(connection);
            cost = CurrentTimeMillisClock.now() - startTime;
            logger.info("{} successfully return  a esl connection to fs connPool, total cost: {} mills.", traceId, cost);
        }
        return  response;
    }

    /**
     * 执行异步api;
     * @param api
     * @param param
     * @param eslConnectionPool
     * @return
     */
    public static  String sendAsyncApiCommand(String api, String param, EslConnectionPool eslConnectionPool){
        long startTime = CurrentTimeMillisClock.now();
        String traceId = param;
        String spacer = " ";
        if(param.contains(spacer)){
            traceId = param.split(spacer)[0];
        }
        EslConnectionDetail  connection = eslConnectionPool.getConnection();
        long cost = CurrentTimeMillisClock.now() - startTime;
        logger.info("{} successfully borrow a esl connection , cost: {} mills.", traceId, cost);
        String response = null;
        try {
            long cmdStartTime = CurrentTimeMillisClock.now();
            response = connection.getRealConn().sendAsyncApiCommand(
                    eslConnectionPool.getEslAddr(),
                    api,
                    param);
            cost = CurrentTimeMillisClock.now() - cmdStartTime;
                    logger.info("{} [sendAsyncApiCommand] successfully get response:{}, from esl connection, cost: {} mills.", traceId, response, cost);
        }catch ( Exception e )
        {
        }finally {
            eslConnectionPool.releaseOneConn(connection);
            cost = CurrentTimeMillisClock.now() - startTime;
            logger.info("{} successfully return  a esl connection to fs connPool, total cost: {} mills.", traceId, cost);
        }
        return response;
    }

    /**
     * 执行同步api指令;
     * @param api
     * @param param
     * @param eslConnectionPool
     * @return
     */
    public static  EslMessage sendSyncApiCommand(String api, String param, EslConnectionPool eslConnectionPool){
        EslConnectionDetail  connection = eslConnectionPool.getConnection();
        EslMessage response = null;
        try {
            response = connection.getRealConn().sendSyncApiCommand(
                    eslConnectionPool.getEslAddr(),
                    api,
                    param);
        }catch ( Exception e )
        {
        }finally {
            eslConnectionPool.releaseOneConn(connection);
        }
        return response;
    }

}