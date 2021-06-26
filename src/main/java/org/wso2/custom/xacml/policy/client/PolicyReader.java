package org.wso2.custom.xacml.policy.client;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.wso2.carbon.identity.entitlement.stub.dto.PolicyDTO;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PolicyReader {

    private static final Log log = LogFactory.getLog(PolicyReader.class);

    private static File policyFolder = new File("/Users/tharaka/Documents/issues/sample/xacml");
    private static List<String> policyIdList = new ArrayList<>();;
    private static ArrayList<PolicyDTO> policyDTOs = new ArrayList<>();;
    private static PolicyDistributionTask policyDistributionTask = new PolicyDistributionTask(10);;

    public static void main(String args[]) throws InterruptedException {
        BasicConfigurator.configure();
        LogManager.getLogger("org.apache.axis2").setLevel(Level.INFO);
        LogManager.getLogger("org.apache.axiom").setLevel(Level.INFO);
        System.setProperty("javax.net.ssl.trustStore","/Users/tharaka/Documents/issues/sample/wso2is-5.8.0/repository/resources/security/client-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword","wso2carbon");
        File[] fileList;
        if (policyFolder.exists() && ArrayUtils.isNotEmpty(fileList = policyFolder.listFiles())) {
            for (File policyFile : fileList) {
                log.info("filename : " + policyFile.getName());
                if (policyFile.isFile()) {
                    PolicyDTO policyDTO = new PolicyDTO();
                    try {
                        policyDTO.setPolicy(FileUtils.readFileToString(policyFile));
                    } catch (IOException e) {
                        log.error("Error reading from file", e);
                    }
                    policyDTOs.add(policyDTO);
                    if(policyDTOs.size() % 10 == 0) {
                        policyDistributionTask.addPolicyDtoToQueue(new ArrayList<PolicyDTO>(policyDTOs));
                        policyDTOs.clear();
                        log.info("policy set added to queue");
                    }
                }
            }
            if(policyDTOs.size() % 10 > 0) {
                policyDistributionTask.addPolicyDtoToQueue(new ArrayList<PolicyDTO>(policyDTOs));
                policyDTOs.clear();
                log.info("remaining policy set added to queue");
            }
            new Thread(policyDistributionTask).start();
        }
        TimeUnit.MILLISECONDS.sleep(20000);
        log.info("stopped");
    }
}
