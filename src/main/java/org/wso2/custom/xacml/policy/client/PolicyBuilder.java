package org.wso2.custom.xacml.policy.client;

import org.apache.axis2.AxisFault;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.transport.http.HttpTransportProperties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.wso2.carbon.identity.entitlement.stub.EntitlementPolicyAdminServiceEntitlementException;
import org.wso2.carbon.identity.entitlement.stub.EntitlementPolicyAdminServiceStub;
import org.wso2.carbon.identity.entitlement.stub.dto.PolicyDTO;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.Gson;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PolicyBuilder {

    private static final Log log = LogFactory.getLog(PolicyBuilder.class);

    public static void main(String args[]) {

        BasicConfigurator.configure();
        LogManager.getLogger("org.apache.axis2").setLevel(Level.INFO);
        LogManager.getLogger("org.apache.axiom").setLevel(Level.INFO);
        System.setProperty("javax.net.ssl.trustStore","/Users/tharakaw/Documents/issues/sample/wso2is-5.10.0/repository/resources/security/client-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword","wso2carbon");

        String str = "{\"version\":\"1.0\",\"xacmlPolicy\":[{\"role\":\"clinician\",\"resource\":\"/dcs/surveillance/sessionRange\",\"action\":\"GET\"},{\"role\":\"clinician\",\"resource\":\"/dcs/surveillance/sessionRange\",\"action\":\"POST\"},{\"role\":\"clinician\",\"resource\":\"/dcs/surveillance/sessionRange\",\"action\":\"PUT\"},{\"role\":\"hospitaladmin\",\"resource\":\"/dcs/surveillance/sessionRange\",\"action\":\"GET\"},{\"role\":\"hospitaladmin\",\"resource\":\"/dcs/surveillance/sessionRange\",\"action\":\"POST\"},{\"role\":\"hospitaladmin\",\"resource\":\"/dcs/surveillance/sessionRange\",\"action\":\"PUT\"},{\"role\":\"clinician\",\"resource\":\"/dcs/annotationbuilder\",\"action\":\"PUT\"},{\"role\":\"clinician\",\"resource\":\"/dcs/annotationbuilder\",\"action\":\"DELETE\"}]}";

        GsonBuilder builder = new GsonBuilder();
        builder.setPrettyPrinting();
        Gson gson = builder.create();
        Policies policies = gson.fromJson(str,Policies.class);

        EntitlementPolicyAdminServiceStub policyAdminStub;
        try {
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
            addAllPolicies(policyAdminStub,policies);
            client.cleanupTransport();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    class Policies {

        public List<XacmlPolicy> xacmlPolicy;

        public List<XacmlPolicy> getXacmlPolicy() {
            return xacmlPolicy;
        }

        class XacmlPolicy {
            public String role;

            public String action;
            public String resource;

            public String getRole() {
                return role;
            }

            public String getAction() {
                return action;
            }

            public void setAction(String action) {
                this.action = action;
            }

            public String getResource() {
                return resource;
            }
        }

    }
    public static void addAllPolicies(EntitlementPolicyAdminServiceStub policyAdminStub, Policies policies) throws Exception {
        ArrayList<String> failedPolicies = new ArrayList<String>();
        ArrayList<PolicyDTO> policyDTOs = new ArrayList<>();

        List<String> existingPolicyIds  = Arrays.asList(policyAdminStub.getAllPolicyIds("*"));
        int numberOfPolicies = 0;
        String policyId = null;
        boolean isPolicyInvalid = false;
        if (null != policies && null != policies.getXacmlPolicy()) {
            numberOfPolicies = policies.getXacmlPolicy().size();
            for (int i = 0; i < numberOfPolicies; i++) {
                isPolicyInvalid = false;
                Policies.XacmlPolicy policy = policies.getXacmlPolicy().get(i);
                try {
                    policyId = constructPolicyID(policy.getRole(), policy.getAction(), policy.getResource());
                    log.info(policyId + " After constructPolicyID ");
                } catch (Exception e) {
                    isPolicyInvalid = true;
                    failedPolicies.add(policy.getRole() + "_" + "_" + policy.getAction() + "_" + policy.getResource()
                            + " : " + e.getMessage());
                }
                if (null != policy.getAction() && policy.getAction().equalsIgnoreCase("ANY")) {
                    policy.setAction("[\\s\\S]*");
                }
                if (!isPolicyInvalid && !existingPolicyIds.contains(policyId)) {
                    PolicyDTO policyDTO = new PolicyDTO();
                    policyDTO.setPolicy(readPolicy().replace("SamplePolicy", policyId)
                            .replace("REPLACE_ROLE", policy.getRole()).replace("REPLACE_ACTION", policy.getAction())
                            .replace("REPLACE_RESOURCE", policy.getResource()));
                    policyDTO.setPromote(true);
                    policyDTO.setActive(true);
                    policyDTOs.add(policyDTO);
                    log.info(policyId + " Policy is added to policyDTOs in method addAllPolicies ");
                }
            }
            publishAllPoliciesToPDP(policyAdminStub, new ArrayList<PolicyDTO>(policyDTOs), failedPolicies);
        } else {
            throw new Exception("Error");
        }
    }


    public static String constructPolicyID(String role, String action, String resource) throws Exception {
        if (null == role || "".equalsIgnoreCase(role) || null == action || "".equalsIgnoreCase(action)
                || null == resource || "".equalsIgnoreCase(resource)) {
            throw new Exception("Error");
        }
        StringBuilder sb = new StringBuilder();
        if (role.contains(":")) {
            sb.append(role.replace(":", "."));
        } else {
            sb.append(role);
        }
        sb.append("__");
        sb.append(action);
        sb.append("__");
        if (resource.contains("/")) {
            sb.append(resource.replace("/", "_"));
        } else {
            sb.append(resource);
        }
        return sb.toString();
    }

    public static String readPolicy() {
        return "<Policy xmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\"  PolicyId=\"SamplePolicy\""
                + " RuleCombiningAlgId=\"urn:oasis:names:tc:xacml:1.0:rule-combining-algorithm:first-applicable\" Version=\"1.0\"> <Target> <AnyOf> <AllOf> "
                + " <Match MatchId=\"urn:oasis:names:tc:xacml:1.0:function:string-regexp-match\"> "
                + " <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">^REPLACE_RESOURCE//*</AttributeValue> "
                + " <AttributeDesignator AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:resource-id\" "
                + " Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:resource\" DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"> "
                + " </AttributeDesignator> </Match> </AllOf> </AnyOf> <AnyOf> <AllOf> <Match MatchId=\"urn:oasis:names:tc:xacml:1.0:function:string-regexp-match\"> "
                + " <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">REPLACE_ACTION</AttributeValue> "
                + " <AttributeDesignator AttributeId=\"urn:oasis:names:tc:xacml:1.0:action:action-id\" Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:action\""
                + " DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"> </AttributeDesignator> </Match> </AllOf> </AnyOf> <AnyOf> <AllOf> "
                + " <Match MatchId=\"urn:oasis:names:tc:xacml:1.0:function:string-equal\"> <AttributeValue "
                + " DataType=\"http://www.w3.org/2001/XMLSchema#string\">REPLACE_ROLE</AttributeValue> <AttributeDesignator "
                + " AttributeId=\"http://wso2.org/claims/role\" Category=\"urn:oasis:names:tc:xacml:1.0:subject-category:access-subject\" "
                + " DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"> </AttributeDesignator> </Match> </AllOf> </AnyOf> </Target> "
                + " <Rule Effect=\"Permit\" RuleId=\"PERMIT_RULE\"> <Target> <AnyOf> <AllOf> <Match MatchId=\"urn:oasis:names:tc:xacml:1.0:function:string-regexp-match\"> "
                + " <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">^REPLACE_RESOURCE//*</AttributeValue> <AttributeDesignator"
                + " AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:resource-id\" Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:resource\""
                + " DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"> </AttributeDesignator> </Match> </AllOf> </AnyOf> <AnyOf> <AllOf> "
                + " <Match MatchId=\"urn:oasis:names:tc:xacml:1.0:function:string-regexp-match\"> <AttributeValue"
                + " DataType=\"http://www.w3.org/2001/XMLSchema#string\">REPLACE_ACTION</AttributeValue> <AttributeDesignator "
                + " AttributeId=\"urn:oasis:names:tc:xacml:1.0:action:action-id\" Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:action\""
                + " DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"> </AttributeDesignator> </Match> </AllOf> </AnyOf> </Target> <Condition> "
                + " <Apply FunctionId=\"urn:oasis:names:tc:xacml:1.0:function:any-of\"> <Function FunctionId=\"urn:oasis:names:tc:xacml:1.0:function:string-equal\"> "
                + " </Function> <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">REPLACE_ROLE</AttributeValue> <AttributeDesignator"
                + " AttributeId=\"http://wso2.org/claims/role\" Category=\"urn:oasis:names:tc:xacml:1.0:subject-category:access-subject\" "
                + " DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"> </AttributeDesignator> </Apply> </Condition> </Rule> </Policy>";
    }


    private static void publishAllPoliciesToPDP(EntitlementPolicyAdminServiceStub policyAdminStub,
                                                ArrayList<PolicyDTO> policyDTOs, ArrayList<String> failedPolicies) throws Exception {
        try {
            PolicyDTO[] arrDTO = policyDTOs.toArray(new PolicyDTO[policyDTOs.size()]);
            log.info("INFO : BEFORE policyAdminStub.addPolicies -" + arrDTO.length);
            for (PolicyDTO policyDTO : arrDTO) {
                if (policyDTO == null) {
                    log.info("INFO : PolicyDTO is null");
                } else {
                    log.info("INFO :policy is not null");
                }
                log.info("INFO : policy.getPolicyId() -" + policyDTO.getPolicyId());

                if (policyDTO.getPolicy() == null || policyDTO.getPolicy().isEmpty()) {
                    log.info("INFO : policy.getPolicy() is null");
                } else {
                    log.info("INFO :policy.getPolicy() is not null and getPolicy() is = " + policyDTO.getPolicy());
                }

            }
            policyAdminStub.addPolicies(arrDTO);
            policyAdminStub.getAllPolicies( )
            log.info("INFO : AFTER policyAdminStub.addPolicies");
        } catch (RemoteException | EntitlementPolicyAdminServiceEntitlementException e) {
            if (e instanceof EntitlementPolicyAdminServiceEntitlementException) {
                EntitlementPolicyAdminServiceEntitlementException entitlementException = (EntitlementPolicyAdminServiceEntitlementException) e;
                if (entitlementException.getFaultMessage().getEntitlementException() != null) {
                    log.info("INFO : Exception occured while adding Bulk policies, now trying to add one by one "
                            + entitlementException.getFaultMessage().getEntitlementException().getMessage());
                }
            } else {

                log.info("INFO :Remote Exception occured while adding Bulk policies, now trying to add one by one "
                        + e.getMessage());
            }

        } catch (Exception e) {
            log.info("INFO : Exception occured " + e.getMessage());
        }

        log.info("INFO : All Policies published successfully");
        if (!failedPolicies.isEmpty()) {

            StringBuilder policies = new StringBuilder();
            for (int j = 0; j < failedPolicies.size(); j++) {
                policies.append(failedPolicies.get(j) + "\n");
                log.info(failedPolicies.get(j) + " policy FAILED... ");
            }
            throw new Exception(
                    "Xacml policies creation failed for below " + failedPolicies.size() + " policies \n\n" + policies);
        }
    }
}
