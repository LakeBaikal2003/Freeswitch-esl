package link.thingscloud.freeswitch.esl.pool;

import link.thingscloud.freeswitch.esl.InboundClient;
import link.thingscloud.freeswitch.esl.inbound.NettyInboundClient;
import link.thingscloud.freeswitch.esl.inbound.option.ConnectState;
import link.thingscloud.freeswitch.esl.inbound.option.InboundClientOption;
import link.thingscloud.freeswitch.esl.inbound.option.ServerOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;

public class EslConnectionPool {
    protected static final Logger logger = LoggerFactory.getLogger(EslConnectionPool.class);

    /**
     * 当前节点的连接对象列表;
     */
    private final LinkedBlockingDeque<EslConnectionDetail> eslConnectionPool = new LinkedBlockingDeque<>(1000);

    /**
     * 当前节点的默认连接;
     * 所谓默认连接是指： 仅用作接收指定节点EventSocket消息的连接; 不用于发送指令;
     */
    private EslConnectionDetail defaultEslConnection = null;

    private Semaphore semaphore = null;
    private int connCount = 0;
    private String host;
    private int port;
    private String pass;

    private EslConnectionPool(int connCount, String host, int port, String pass) {
        this.connCount = connCount;
        this.semaphore = new Semaphore(connCount);
        this.host = host;
        this.port = port;
        this.pass = pass;
    }

    public String getEslAddr() {
        return String.format("%s:%d", host, port);
    }

    /**
     * 获取连接池连接数量;
     *
     * @return
     */
    public int getPoolSize() {
        return connCount;
    }

    private static EslConnectionDetail createOneConn(
            String host,
            int port,
            String pass,
            boolean subscribeEvents) {
        String hostKey = String.format("%s:%d", host, port);
        InboundClientOption inboundOption = new InboundClientOption();
        ServerOption serverOption = new ServerOption(host, port);
        inboundOption.defaultPassword(pass).addServerOption(serverOption);
        if (subscribeEvents) {
            inboundOption.addEvents(EslConnectionDetail.getEventSubscriptionsForDefaultConn());
        }
        InboundClient conn = new NettyInboundClient(inboundOption);
        conn.start();
        EslConnectionDetail eslConnectionDetail = new EslConnectionDetail(inboundOption, serverOption, conn);
        inboundOption.addListener(eslConnectionDetail);
        String addr = serverOption.addr().intern();
        synchronized (addr.intern()) {
            try {
                addr.wait(3000);
            } catch (Exception e) {
                logger.error("error occurs on waiting for esl connection..." + e.toString());
            }
        }
        if (serverOption.state() != ConnectState.AUTHED) {
            logger.error("无法连接fs服务器:{}", hostKey);
            return null;
        }
        return eslConnectionDetail;
    }

    /**
     * 为指定的host:port创建默认的esl对象，用于接收全部消息;
     */
    public EslConnectionDetail getDefaultEslConn() {
        String hostKey = String.format("%s:%d", host, port);
        if (null == defaultEslConnection) {
            synchronized (hostKey.intern()) {
                if (null == defaultEslConnection) {
                    // 设置连接的通道相关信息
                    defaultEslConnection = createOneConn(host, port, pass, true);
                }
            }
        }
        return defaultEslConnection;
    }

    private void setConnections(List<EslConnectionDetail> connList) {
        if (connList.size() != connCount) {
            throw new RuntimeException("connList包含的连接数量大于esl连接池设置定的数量，这将导致信号量失效!");
        }
        eslConnectionPool.clear();
        eslConnectionPool.addAll(connList);
    }

    public static EslConnectionPool createPool(int poolSize, String host, int port, String pass) {
        ArrayList<EslConnectionDetail> connList = new ArrayList<>(100);
        for (int i = 0; i <= poolSize - 1; i++) {
            EslConnectionDetail conn = EslConnectionPool.createOneConn(
                    host,
                    port,
                    pass,
                    false);
            if (null != conn) {
                connList.add(conn);
            }
        }
        EslConnectionPool connectionPool = new EslConnectionPool(poolSize, host, port, pass);
        connectionPool.setConnections(connList);
        //创建默认连接
        connectionPool.getDefaultEslConn();
        return connectionPool;
    }

    /**
     * 获取一个连接;
     *
     * @return
     */
    public EslConnectionDetail getConnection() {
        try {
            semaphore.acquire();
            return eslConnectionPool.poll();
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * 释放一个连接;
     *
     * @param conn
     */
    public void releaseOneConn(EslConnectionDetail conn) {
        try {
            eslConnectionPool.add(conn);
            semaphore.release();
        } catch (Exception e) {
        }
    }

}