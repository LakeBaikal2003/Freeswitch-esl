package link.thingscloud.freeswitch.esl.pool;

import link.thingscloud.freeswitch.esl.IEslEventListener;
import link.thingscloud.freeswitch.esl.InboundClient;
import link.thingscloud.freeswitch.esl.inbound.option.InboundClientOption;
import link.thingscloud.freeswitch.esl.inbound.option.ServerOption;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import link.thingscloud.freeswitch.esl.transport.event.EslEventHeaderNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EslConnectionDetail  implements IEslEventListener {
    private final static Logger logger = LoggerFactory.getLogger(EslConnectionDetail.class);
    private InboundClientOption inboundOption = null;
    private ServerOption serverOption  = null;
    private InboundClient conn  = null;
    private  final static ConcurrentHashMap<String, IEslEventListener> LISTENTERS = new ConcurrentHashMap<>(1000);
    private static List<String> eventSubscriptionsForDefaultConn = new ArrayList<>();
    /**
     *  上次心跳时间
     */
    private volatile Long lastBeatTime = System.currentTimeMillis();
    /**
     * 当前通话数;
     */
    private  int callSessionCount = 0;
    /**
     * 当前CPU使用情况
     */
    private   String cpuIdle = "";
    /**
     * 每秒允许的呼叫数
     */
    private   int sessionPerSec = 30;
    /**
     * Freeswitch启动至今的毫秒数;
     */
    private   Long uptimeMsec = 0L;

    public EslConnectionDetail(InboundClientOption inboundOption,
                               ServerOption serverOption,
                               InboundClient conn
                               ){
        this.inboundOption = inboundOption;
        this.serverOption = serverOption;
        this.conn = conn;
    }

    public void addListener(String uuid, IEslEventListener listener ){
        LISTENTERS.put(uuid, listener);
    }

    public  void removeListener(String uuid) {
        LISTENTERS.remove(uuid);
    }

    /**
     *  获取真正的esl的socket连接对象;
     * @return
     */
    public InboundClient getRealConn(){
        return  conn;
    }

    @Override
    public void eventReceived(String addr, EslEvent event) {
        String uuid = event.getEventHeaders().get("Unique-ID");
        String heartbeat = EslEventHeaderNames.EVENT_HEARTBEAT;
        String eventName = event.getEventName();
        Map<String, String> headers = event.getEventHeaders();
        if (eventName.equalsIgnoreCase(heartbeat)) {
            callSessionCount = Integer.parseInt(headers.get("Session-Count"));
            cpuIdle = headers.get("Idle-CPU");
            sessionPerSec = Integer.parseInt(headers.get("Session-Per-Sec"));
            uptimeMsec = Long.parseLong(headers.get("Uptime-msec"));
            lastBeatTime = System.currentTimeMillis();
            logger.info("RECV fs EVENT_HEARTBEAT, callSessionCount:{},cpuIdle:{},sessionPerSec:{},uptimeMsec:{},lastBeatTime:{}",
                    callSessionCount,
                    cpuIdle,
                    sessionPerSec,
                    uptimeMsec,
                    lastBeatTime
            );
            return;
        }
        logger.info("eventReceived:{}, uuid:{}", event.getEventName(), uuid);
        IEslEventListener msgHandle = LISTENTERS.get(uuid);
        if(null != msgHandle){
            msgHandle.eventReceived(addr, event);
        }else{
            logger.debug("eventReceived 放弃esl消息, 找不到指定的  handle.  callUuid:{}, msg: {}...",
                    uuid, event.toString());
        }
    }

    @Override
    public void backgroundJobResultReceived(String addr, EslEvent event) {
        String jobUuid = event.getEventHeaders().get("Job-UUID");
        IEslEventListener msgHandle = LISTENTERS.get(jobUuid);
        if(null != msgHandle){
            msgHandle.backgroundJobResultReceived(addr, event);
        }else{
            logger.debug("backgroundJobResultReceived 放弃esl消息, 找不到指定的  handle.  jobUUID:{}, msg: {}...",
                    jobUuid, event.toString());
        }
    }

    public Long getLastBeatTime() {
        return lastBeatTime;
    }

    public void setLastBeatTime(Long lastBeatTime) {
        this.lastBeatTime = lastBeatTime;
    }

    public int getCallSessionCount() {
        return callSessionCount;
    }

    public void setCallSessionCount(int callSessionCount) {
        this.callSessionCount = callSessionCount;
    }

    public String getCpuIdle() {
        return cpuIdle;
    }

    public void setCpuIdle(String cpuIdle) {
        this.cpuIdle = cpuIdle;
    }

    public int getSessionPerSec() {
        return sessionPerSec;
    }

    public void setSessionPerSec(int sessionPerSec) {
        this.sessionPerSec = sessionPerSec;
    }

    public Long getUptimeMsec() {
        return uptimeMsec;
    }

    public void setUptimeMsec(Long uptimeMsec) {
        this.uptimeMsec = uptimeMsec;
    }

    public static List<String> getEventSubscriptionsForDefaultConn() {
        return eventSubscriptionsForDefaultConn;
    }

    public static void setEventSubscriptionsForDefaultConn(List<String> eventSubscriptionsForDefaultConn) {
        EslConnectionDetail.eventSubscriptionsForDefaultConn = eventSubscriptionsForDefaultConn;
    }
}
