package org.wso2.custom.xacml.policy.client;

import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.transport.http.HttpTransportProperties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.entitlement.stub.dto.PolicyDTO;
import org.wso2.carbon.identity.entitlement.stub.EntitlementPolicyAdminServiceEntitlementException;
import org.wso2.carbon.identity.entitlement.stub.EntitlementPolicyAdminServiceStub;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

public class PolicyDistributionTask implements Runnable{

    private static final Log log = LogFactory.getLog(PolicyDistributionTask.class);

    private BlockingDeque<ArrayList<PolicyDTO>> policyQueue;

    private volatile boolean running;

    PolicyDistributionTask(int threadPoolSize){
        this.policyQueue = new LinkedBlockingDeque<ArrayList<PolicyDTO>>();
        ExecutorServiceHolder.getInstance().setThreadPool(Executors.newFixedThreadPool(threadPoolSize));
    }

    public void addPolicyDtoToQueue(ArrayList<PolicyDTO> policyDTOs) {
        this.policyQueue.add(policyDTOs);
    }

    @Override
    public void run() {
        log.info("queue size :" + this.policyQueue.size());
        running = true;
        // Run forever until stop the bundle. Will stop in eventQueue.take()
        while (running) {
            try {
                final ArrayList<PolicyDTO> policyDTOs = policyQueue.take();
                // Create a runnable and submit to the thread pool for sending message.
                Runnable policyAdder = new Runnable() {
                    @Override
                    public void run() {
                        log.info("policyDTOs : " + policyDTOs.size());
                        try {
                            EntitlementPolicyAdminServiceStub policyAdminStub;
                            policyAdminStub = new EntitlementPolicyAdminServiceStub("https://localhost:9443/services/EntitlementPolicyAdminService");
                            ServiceClient client = policyAdminStub._getServiceClient();
                            Options options = client.getOptions();
                            HttpTransportProperties.Authenticator auth = new HttpTransportProperties.Authenticator();
                            auth.setUsername("admin");
                            auth.setPassword("admin");
                            auth.setPreemptiveAuthentication(true);
                            options.setProperty(org.apache.axis2.transport.http.HTTPConstants.AUTHENTICATE, auth);
                            options.setManageSession(true);
                            options.setProperty(HTTPConstants.REUSE_HTTP_CLIENT , true);
                            policyAdminStub.addPolicies(policyDTOs.toArray(new PolicyDTO[policyDTOs.size()]));
                            log.info("added policy");
                            client.cleanupTransport();
                        } catch (RemoteException | EntitlementPolicyAdminServiceEntitlementException axisFault) {
                            log.error("Axis error : " , axisFault);
                        }
                    }
                };
                ExecutorServiceHolder.getInstance().getThreadPool().submit(policyAdder);
            } catch (InterruptedException e) {
                log.info("Error while picking up from queue");
            }
        }
    }

    public void shutdown() {
        this.running = false;
    }
}
