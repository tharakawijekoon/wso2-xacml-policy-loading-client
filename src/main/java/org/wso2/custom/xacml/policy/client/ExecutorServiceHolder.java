package org.wso2.custom.xacml.policy.client;

import java.util.concurrent.ExecutorService;

public class ExecutorServiceHolder {

    private static ExecutorServiceHolder instance = new ExecutorServiceHolder();

    private ExecutorService threadPool;

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    private ExecutorServiceHolder() {

    }

    public static ExecutorServiceHolder getInstance() {

        return instance;
    }


}