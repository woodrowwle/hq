/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hyperic.plugin.vrealize.automation;

import com.vmware.hyperic.model.relations.CommonModel;
import com.vmware.hyperic.model.relations.Identifier;
import com.vmware.hyperic.model.relations.ObjectFactory;
import com.vmware.hyperic.model.relations.Relation;
import com.vmware.hyperic.model.relations.RelationType;
import com.vmware.hyperic.model.relations.Resource;
import com.vmware.hyperic.model.relations.ResourceSubType;
import com.vmware.hyperic.model.relations.ResourceTier;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.product.DaemonDetector;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ServerResource;
import org.hyperic.util.config.ConfigResponse;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * @author laullon
 */
public class DiscoveryVRAServer extends DaemonDetector {

    private static final Log log = LogFactory.getLog(DiscoveryVRAServer.class);

    @Override
    public List getServerResources(ConfigResponse platformConfig) throws PluginException {
        log.debug("[getServerResources] platformConfig=" + platformConfig);

        List<ServerResource> servers = super.getServerResources(platformConfig);

        Properties props = VRAUtils.configFile();
        String cspHost = props.getProperty("csp.host");
        String websso = props.getProperty("vmidentity.websso.host");

        websso = websso.substring(0, websso.indexOf(":"));

        log.debug("[getServerResources] csp.host=" + cspHost);
        log.debug("[getServerResources] csp.host=" + websso);

        if (cspHost != null) {
            for (ServerResource server : servers) {
                String model = marshallCommonModel(getCommonModel(cspHost, websso, getPlatformName()));
                server.getProductConfig().setValue("extended-relationship-model", new String(Base64.encodeBase64(model.getBytes())));

                ConfigResponse c = new ConfigResponse();
                c.setValue("extended-relationship-model", new String(Base64.encodeBase64(model.getBytes())));

                // do not remove, why? please don't ask.
                server.setProductConfig(server.getProductConfig());
                server.setCustomProperties(c);
            }
        }
        return servers;
    }

    public String marshallCommonModel(CommonModel model) {
        ByteArrayOutputStream fos = new ByteArrayOutputStream();
        model.marshallAsXml(fos);
        log.debug("[testMarshallAsJson] fos=" + fos.toString());
        return fos.toString();
    }

    private CommonModel getCommonModel(String lbHostName, String websso, String platform) {
        ObjectFactory factory = new ObjectFactory();

        Resource lb = factory.createResource();
        lb.setName(lbHostName + " Load Balancer");
        lb.setType("Load Balancer");
        lb.setTier(ResourceTier.LOGICAL);
        lb.setSubType(ResourceSubType.TAG);
        lb.setCreateIfNotExist(Boolean.TRUE);

        Resource vralb = factory.createResource();
        vralb.setName(lbHostName + " vRealize Automation Load Balancer");
        vralb.setType("vRealize Automation Load Balancer");
        vralb.setTier(ResourceTier.LOGICAL);
        vralb.setSubType(ResourceSubType.TAG);
        vralb.setCreateIfNotExist(Boolean.TRUE);

        Resource vralbservice = factory.createResource();
        vralbservice.setName(lbHostName + " vRealize Automation Load Balancer Service");
        vralbservice.setType("vRealize Automation Load Balancer Service");
        vralbservice.setTier(ResourceTier.SERVER);
        vralbservice.setCreateIfNotExist(Boolean.TRUE);

        Identifier vrappid = new Identifier();
        vrappid.setName("application.name");
        vrappid.setValue(lbHostName);

        Resource vraapp = factory.createResource();
        vraapp.setName(platform + " vRealize Automation Application");
        vraapp.setType("vRealize Automation Application");
        vraapp.setTier(ResourceTier.LOGICAL);
        vraapp.setSubType(ResourceSubType.TAG);
        vraapp.setCreateIfNotExist(Boolean.TRUE);
        vraapp.getIdentifiers().add(vrappid);

        Relation rl_app = factory.createRelation();
        rl_app.setType(RelationType.PARENT);
        rl_app.setCreateIfNotExist(Boolean.TRUE);
        rl_app.setResource(vraapp);

        Relation rl_lb_2 = factory.createRelation();
        rl_lb_2.setType(RelationType.PARENT);
        rl_lb_2.setCreateIfNotExist(Boolean.TRUE);
        rl_lb_2.setResource(lb);

        Relation rl_lb_1 = factory.createRelation();
        rl_lb_1.setType(RelationType.PARENT);
        rl_lb_1.setCreateIfNotExist(Boolean.TRUE);
        rl_lb_1.setResource(vralb);

        Relation rl_lb = factory.createRelation();
        rl_lb.setType(RelationType.SIBLING);
        rl_lb.setCreateIfNotExist(Boolean.TRUE);
        rl_lb.setResource(vralbservice);

        vralbservice.getRelations().add(rl_lb_1);
        vralb.getRelations().add(rl_lb_2);
        lb.getRelations().add(rl_app);

        // SSO
        Resource sso = factory.createResource();
        sso.setName(lbHostName + " SSO");
        sso.setType("SSO");
        sso.setTier(ResourceTier.LOGICAL);
        sso.setSubType(ResourceSubType.TAG);
        sso.setCreateIfNotExist(Boolean.TRUE);

        Resource vRASSOServer = factory.createResource();
        vRASSOServer.setName(websso + " vRealize Automation SSO Server");
        vRASSOServer.setType("vRealize Automation SSO Server");
        vRASSOServer.setTier(ResourceTier.SERVER);
        vRASSOServer.setCreateIfNotExist(Boolean.FALSE);

        Relation rl_sso_b = factory.createRelation();
        rl_sso_b.setType(RelationType.PARENT);
        rl_sso_b.setCreateIfNotExist(Boolean.FALSE);
        rl_sso_b.setResource(sso);

        Relation rl_sso = factory.createRelation();
        rl_sso.setType(RelationType.SIBLING);
        rl_sso.setCreateIfNotExist(Boolean.FALSE);
        rl_sso.setResource(vRASSOServer);

        vRASSOServer.getRelations().add(rl_sso_b);
        sso.getRelations().add(rl_app);

        // Server
        Resource vco = factory.createResource();
        vco.setName(lbHostName + " VCO");
        vco.setType("VCO");
        vco.setTier(ResourceTier.LOGICAL);
        vco.setSubType(ResourceSubType.TAG);
        vco.setCreateIfNotExist(Boolean.TRUE);

        Resource server = factory.createResource();
        server.setName(websso + " vCenter Orchestrator App Server");
        server.setType("vCenter Orchestrator App Server");
        server.setTier(ResourceTier.SERVER);
        server.setCreateIfNotExist(Boolean.FALSE);

        Relation rl_vco = factory.createRelation();
        rl_vco.setType(RelationType.PARENT);
        rl_vco.setCreateIfNotExist(Boolean.FALSE);
        rl_vco.setResource(vco);

        Relation rl_server = factory.createRelation();
        rl_server.setType(RelationType.SIBLING);
        rl_server.setCreateIfNotExist(Boolean.FALSE);
        rl_server.setResource(server);

        server.getRelations().add(rl_vco);
        vco.getRelations().add(rl_app);

        Resource asg = factory.createResource();
        asg.setName(lbHostName + " vRealize Automation Server Group");
        asg.setType("vRealize Automation Server Group");
        asg.setTier(ResourceTier.LOGICAL);
        asg.setSubType(ResourceSubType.TAG);
        asg.setCreateIfNotExist(Boolean.TRUE);

        Relation rl_asg = factory.createRelation();
        rl_asg.setType(RelationType.PARENT);
        rl_asg.setCreateIfNotExist(Boolean.TRUE);
        rl_asg.setResource(asg);
        
        asg.getRelations().add(rl_app);

        CommonModel vRaServerModel = factory.createRelationshipModel(null);

        vRaServerModel.setName(platform + " vRealize Automation Server");
        vRaServerModel.setType("vRealize Automation Server");
        vRaServerModel.setTier(ResourceTier.SERVER);
        vRaServerModel.setCreateIfNotExist(Boolean.FALSE);

        vRaServerModel.getRelations().add(rl_lb);
        vRaServerModel.getRelations().add(rl_sso);
        vRaServerModel.getRelations().add(rl_server);
        vRaServerModel.getRelations().add(rl_asg);

        return vRaServerModel;
    }
}