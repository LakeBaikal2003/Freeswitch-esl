package link.thingscloud.freeswitch.esl.util;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *  获取当前系统时间戳;
 */
public class CurrentTimeMillisClock {
    private volatile long now;
    private volatile long lastFixedTime;
    private static final CurrentTimeMillisClock instance = new CurrentTimeMillisClock();

    private CurrentTimeMillisClock() {
        this.now = System.currentTimeMillis();
        lastFixedTime = this.now;
        scheduleTick();
    }

    private void scheduleTick() {
        new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "current-time-millis");
            thread.setDaemon(true);
            return thread;
        }).scheduleAtFixedRate(() -> {
            now += 1;
            long passedMills = this.now - lastFixedTime;
            //每隔1秒钟矫正下时间戳
            if(passedMills > 1000){
                this.now = System.currentTimeMillis();
                lastFixedTime = this.now;
            }
        }, 1, 1, TimeUnit.MILLISECONDS);
    }

    public static long now() {
        return getInstance().now;
    }


    private static CurrentTimeMillisClock getInstance() {
        return instance;
    }





}
