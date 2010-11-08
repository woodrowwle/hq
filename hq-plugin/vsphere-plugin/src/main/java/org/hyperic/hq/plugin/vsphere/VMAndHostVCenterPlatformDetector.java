/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2010], VMWare, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.plugin.vsphere;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.bizapp.agent.CommandsAPIInfo;
import org.hyperic.hq.hqapi1.AgentApi;
import org.hyperic.hq.hqapi1.HQApi;
import org.hyperic.hq.hqapi1.ResourceApi;
import org.hyperic.hq.hqapi1.ResourceEdgeApi;
import org.hyperic.hq.hqapi1.XmlUtil;
import org.hyperic.hq.hqapi1.types.Agent;
import org.hyperic.hq.hqapi1.types.AgentResponse;
import org.hyperic.hq.hqapi1.types.AgentsResponse;
import org.hyperic.hq.hqapi1.types.Resource;
import org.hyperic.hq.hqapi1.types.ResourceConfig;
import org.hyperic.hq.hqapi1.types.ResourceEdge;
import org.hyperic.hq.hqapi1.types.ResourceFrom;
import org.hyperic.hq.hqapi1.types.ResourceInfo;
import org.hyperic.hq.hqapi1.types.ResourceProperty;
import org.hyperic.hq.hqapi1.types.ResourcePrototype;
import org.hyperic.hq.hqapi1.types.ResourcePrototypeResponse;
import org.hyperic.hq.hqapi1.types.ResourceTo;
import org.hyperic.hq.hqapi1.types.ResourcesResponse;
import org.hyperic.hq.hqapi1.types.Response;
import org.hyperic.hq.hqapi1.types.ResponseStatus;
import org.hyperic.hq.hqapi1.types.StatusResponse;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.util.config.ConfigResponse;

import com.vmware.vim25.AboutInfo;
import com.vmware.vim25.GuestInfo;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.HostConfigInfo;
import com.vmware.vim25.HostHardwareSummary;
import com.vmware.vim25.HostIpConfig;
import com.vmware.vim25.HostNetworkInfo;
import com.vmware.vim25.HostRuntimeInfo;
import com.vmware.vim25.HostVirtualNic;
import com.vmware.vim25.HostVirtualNicSpec;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ToolsConfigInfo;
import com.vmware.vim25.VirtualHardware;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualMachineFileInfo;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.VirtualMachine;

/**
 * HQApi based auto-discovery for vSphere Host and VM platform types. 
 */
public class VMAndHostVCenterPlatformDetector implements VCenterPlatformDetector {

    static final String VC_TYPE = AuthzConstants.serverPrototypeVmwareVcenter;
    static final String VM_TYPE = AuthzConstants.platformPrototypeVmwareVsphereVm;
    static final String HOST_TYPE = AuthzConstants.platformPrototypeVmwareVsphereHost;
    static final String ESX_HOST = "esxHost";

    private static final Log log = LogFactory.getLog(VMAndHostVCenterPlatformDetector.class);
    private static final boolean isDump =
        "true".equals(System.getProperty("vsphere.dump"));

    private void assertSuccess(Response response, String msg, boolean abort)
        throws PluginException {

        if (ResponseStatus.SUCCESS.equals(response.getStatus())) {
            return;
        }
        String reason;
        if (response.getError() == null) {
            reason = "unknown";
        }
        else {
            reason = response.getError().getReasonText();
        }
        msg += ": " + reason;
        if (abort) {
            throw new PluginException(msg);
        }
        else {
            log.error(msg);
        }
    }


    private Agent getAgent(HQApi hqApi, Properties props)
        throws IOException, PluginException {

        AgentApi api = hqApi.getAgentApi();
        String agentToken = props.getProperty(CommandsAPIInfo.PROP_AGENT_TOKEN);

        String msg = "getAgent(token=" + agentToken + ")";
        AgentResponse response = api.getAgent(agentToken);
        assertSuccess(response, msg, true);
        if (log.isDebugEnabled()) {
        	log.debug(msg + ": ok");
        }
        return response.getAgent();
    }

    private void dump(Collection<? extends Resource> resources) {
        ResourcesResponse rr = new ResourcesResponse();
        rr.getResource().addAll(resources);
        try {
            XmlUtil.serialize(rr, System.out, Boolean.TRUE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //XXX might want to store these in memory rather than
    //the metric template, should any of the props change.
    private void mergeVSphereConfig(VSphereResource platform, Properties props) {
        String[] vprops = {
            VSphereUtil.PROP_URL,
            VSphereUtil.PROP_USERNAME,
            VSphereUtil.PROP_PASSWORD
        };
        for (int i=0; i<vprops.length; i++) {
            String val = props.getProperty(vprops[i]);
            if (val != null) {
                platform.addConfig(vprops[i], val);
            }
        }
    }

    private ResourcePrototype getResourceType(String name, HQApi hqApi)
        throws IOException, PluginException {

        ResourceApi api = hqApi.getResourceApi();
        ResourcePrototypeResponse rpr =
            api.getResourcePrototype(name);
        assertSuccess(rpr, "getResourcePrototype(" + name + ")", true);
        ResourcePrototype type = rpr.getResourcePrototype();
        log.debug("'" + name + "' id=" + type.getId());
        return type;
    }

    private VSphereResource discoverVM(VirtualMachine vm)
        throws Exception {

        VirtualMachineConfigInfo info = vm.getConfig();

        if (info.isTemplate()) {
            return null; //filter out template VMs
        }

        VirtualMachineRuntimeInfo runtime = vm.getRuntime();
        GuestInfo guest = vm.getGuest();
        ResourcePool pool = vm.getResourcePool();

        VSphereResource platform = new VSphereResource();
        String uuid = info.getUuid();
        platform.setName(generatePlatformName(info.getName(), uuid));
        platform.setFqdn(uuid);
        platform.setDescription(info.getGuestFullName());

        ConfigResponse config = new ConfigResponse();
        config.setValue(VSphereVmCollector.PROP_VM, info.getName());
        config.setValue(VSphereCollector.PROP_UUID, uuid);
        platform.addConfig(config);
        //ConfigInfo
        ConfigResponse cprops = new ConfigResponse();
        VirtualMachineFileInfo files = info.getFiles();
        cprops.setValue(ProductPlugin.PROP_INSTALLPATH, files.getVmPathName());
        cprops.setValue("guestOS", info.getGuestFullName());
        cprops.setValue("version", info.getVersion());
        //HardwareInfo
        VirtualHardware hw = info.getHardware();
        cprops.setValue("numvcpus", hw.getNumCPU());
        cprops.setValue("memsize", hw.getMemoryMB());
        //ToolsInfo
        ToolsConfigInfo tools = info.getTools();
        Integer toolsVersion = tools.getToolsVersion();
        if (toolsVersion != null) {
            cprops.setValue("toolsVersion", toolsVersion.toString());
        }
        //PoolInfo
        cprops.setValue("pool", (String)pool.getPropertyByPath("name"));

        String state = runtime.getPowerState().toString();
        if ("poweredOn".equalsIgnoreCase(state)) {
            String name;
            if ((name = guest.getHostName()) != null) {
                cprops.setValue("hostName", name);
            }
            //NetInfo
            GuestNicInfo[] nics = guest.getNet();
            if (nics != null) {
                for (int i=0; i<nics.length; i++) {
                    String mac = nics[i].getMacAddress();
                    if (mac.equals("00:00:00:00:00:00")) {
                        log.info("Skipping " + VM_TYPE + "[name=" + info.getName()
                          + ", UUID=" + uuid
                          + ", NIC=" + nics[i].getIpAddress()
                          + ", MAC=" + mac
                          + "]. Will be re-discovered when the MAC address is valid.");
                        return null;
                    }
                    String[] ips = nics[i].getIpAddress();
                    if ((mac != null) && (ips != null) && (ips.length != 0)) {
                        cprops.setValue("macAddress", mac);
                        cprops.setValue("ip", ips[0]);
                        platform.addIp(ips[0], "", mac);
                    }
                }
            }
            if (platform.getIp().isEmpty()) {
                log.info("Skipping " + VM_TYPE + "[name=" + info.getName()
                    + ", UUID=" + uuid
                    + "] because the MAC address does not exist. "
                    + "Will be re-discovered when the MAC address is valid.");
                return null;
            }
        }
        else {
            log.info("Skipping " + VM_TYPE + "[name=" + info.getName() 
                + ", UUID=" + uuid
                + ", powerState=" + state + "]. "
                + "Will be re-discovered when it is powered on.");
            return null;
        }

        ManagedObjectReference hmor = runtime.getHost();
        if (hmor != null) {
            HostSystem host = new HostSystem(vm.getServerConnection(), hmor);
            cprops.setValue(ESX_HOST, host.getName());
        }

        platform.addProperties(cprops);
        
        if (log.isDebugEnabled()) {
            log.debug("Discovered " + VM_TYPE + "[name=" + info.getName()
                          + ", UUID=" + uuid
                          + ", powerState=" + state + "]");
        }
        
        return platform;
    }
    
   

    private VSphereHostResource discoverHost(HostSystem host)
        throws Exception {

        HostRuntimeInfo runtime = host.getRuntime();
        String powerState = runtime.getPowerState().toString();
        
        if ("unknown".equalsIgnoreCase(powerState)) {
            // an unknown power state could indicate that the host
            // is disconnected from vCenter
            if (log.isDebugEnabled()) {
                log.debug("Skipping " + HOST_TYPE + "[name=" + host.getName() 
                              + ", powerState=" + powerState + "]");
            }
            return null;
        }
        
        HostConfigInfo info = host.getConfig();
        HostNetworkInfo netinfo = info.getNetwork();
        AboutInfo about = info.getProduct();
        HostHardwareSummary hw = host.getSummary().getHardware();
        String address = null;
        VSphereHostResource platform = new VSphereHostResource();

        String uuid = hw.getUuid();
        platform.setName(generatePlatformName(host.getName(), uuid));
        platform.setDescription(about.getFullName());
        platform.setFqdn(uuid);
        
        if (netinfo.getVnic() == null) {
            try {
                // Host name may be the IP address
                InetAddress inet = InetAddress.getByName(host.getName());
                address = inet.getHostAddress();
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(host.getName() + " does not have an IP address", e);
                }
            }
        } else {
            for (HostVirtualNic nic : netinfo.getVnic()) {
                HostVirtualNicSpec spec = nic.getSpec();
                HostIpConfig ip = spec.getIp();
                platform.addIp(ip.getIpAddress(), ip.getSubnetMask(), spec.getMac());
                if (address == null) {
                    address = ip.getIpAddress();
                }
            }
        }

        ConfigResponse cprops = new ConfigResponse();
        cprops.setValue("version", about.getVersion());
        cprops.setValue("build", about.getBuild());
        if (address != null) {
            cprops.setValue("ip", address);
        }
        cprops.setValue("defaultGateway", netinfo.getIpRouteConfig().getDefaultGateway());

        String[] dns = netinfo.getDnsConfig().getAddress();
        if (dns != null) {
            String[] dnsProps = { "primaryDNS", "secondaryDNS" };
            for (int i=0; i<dnsProps.length; i++) {
                if (i >= dns.length) {
                    break;
                }
                cprops.setValue(dnsProps[i], dns[i]);
            }
        }

        cprops.setValue("hwVendor", hw.getVendor());
        cprops.setValue("hwModel", hw.getModel());
        cprops.setValue("hwCpu", hw.getCpuModel());
        cprops.setValue("hwSockets", String.valueOf(hw.getNumCpuPkgs()));
        cprops.setValue("hwCores", String.valueOf(hw.getNumCpuCores() / hw.getNumCpuPkgs()));

        ManagedEntity mor = host.getParent();
        String prev = null;
        while (true) {
            if (mor.getName().equals("Datacenters")) {
                cprops.setValue("parent", prev); //Data Center
            }
            else {
                prev = mor.getName();
            }

            if ((mor = mor.getParent()) == null) {
                break;
            }
        }

        platform.addProperties(cprops);
        platform.addConfig(VSphereUtil.PROP_HOSTNAME, host.getName());
        platform.addConfig(VSphereCollector.PROP_UUID, uuid);

        if (log.isDebugEnabled()) {
            log.debug("Discovered " + HOST_TYPE + "[name=" + host.getName() 
                          + ", UUID=" + uuid
                          + ", powerState=" + powerState + "]");
        }
        
        return platform;
    }

    private List<Resource> discoverHosts(Agent agent, HQApi hqApi, VSphereUtil vim, Properties props)
        throws IOException, PluginException {

        List<Resource> resources = new ArrayList<Resource>();
        ResourcePrototype hostType = getResourceType(HOST_TYPE, hqApi);
        ResourcePrototype vmType = getResourceType(VM_TYPE, hqApi);

        try {
            ManagedEntity[] hosts = vim.find(VSphereUtil.HOST_SYSTEM);

            for (int i=0; i<hosts.length; i++) {
                if (! (hosts[i] instanceof HostSystem)) {
                    log.debug(hosts[i] + " not a HostSystem, type=" +
                              hosts[i].getMOR().getType());
                    continue;
                }

                HostSystem host = (HostSystem)hosts[i];
                try {
                    VSphereHostResource platform = discoverHost(host);
                    if (platform == null) {
                        continue;
                    }
                    platform.setResourcePrototype(hostType);
                    platform.setAgent(agent);
                    mergeVSphereConfig(platform, props);
                    
                    VirtualMachine[] hostVms = host.getVms();
                   
                    for (int v=0; v<hostVms.length; v++) {
                        VSphereResource vm = discoverVM(hostVms[v]);
                        if (vm != null) {
                            vm.setResourcePrototype(vmType);
                            vm.setAgent(agent);
                            mergeVSphereConfig(vm, props);
                            platform.getVirtualMachines().add(vm);
                        }
                    }
                    
                    resources.add(platform);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return resources;
    }

    public void discoverPlatforms(Properties props, HQApi hqApi, VSphereUtil vim)
        throws IOException, PluginException {
        
        String vCenterUrl = VSphereUtil.getURL(props);
        Resource vCenter = getVCenterServer(vCenterUrl,hqApi);
        
        if (vCenter == null) {
            if (log.isDebugEnabled()) {
                log.debug("Skip discovering hosts and VMs. "
                            + "No VMware vCenter server found with url=" 
                            + vCenterUrl);
            }
            return;
        }
        
   
            Agent agent = getAgent(hqApi, props);
            List<Resource> hosts = discoverHosts(agent,hqApi, vim, props);
          
            List<Resource> vms = new ArrayList<Resource>();
            Map<String, List<Resource>> vcHostVms = new HashMap<String, List<Resource>>();
            
            
            for (Resource r : hosts) {
                VSphereHostResource h = (VSphereHostResource) r;
                vms.addAll(h.getVirtualMachines());
               
                String esxHost = getEsxHost(r);
                if (esxHost != null) {
                    vcHostVms.put(esxHost, h.getVirtualMachines());
                }
            }
            
            if (isDump) {
                dump(vms);
                dump(hosts);
            }
            else {
                ResourceApi api = hqApi.getResourceApi();

                StatusResponse response;
                response = api.syncResources(vms);
                assertSuccess(response, "sync " + vms.size() + " VMs", false);
                response = api.syncResources(hosts);
                assertSuccess(response, "sync " + hosts.size() + " Hosts", false);
                
              	Map<String, Resource> existingHosts = new HashMap<String, Resource>();
                Map<String, List<Resource>> existingHostVms = new HashMap<String, List<Resource>>();
                syncResourceEdges(existingHosts,existingHostVms, props,hqApi);
                removePlatformsFromInventory(vcHostVms, existingHosts, existingHostVms,vim,hqApi);
            }
        
    }
     
    private void syncResourceEdges(Map<String, Resource> existingHosts, Map<String, List<Resource>> existingHostVms, Properties props, HQApi hqApi ) 
        throws IOException, PluginException {

        String vCenterUrl = VSphereUtil.getURL(props);
        Resource vCenter = getVCenterServer(vCenterUrl,hqApi);

        if (vCenter == null) {
            if (log.isDebugEnabled()) {
                log.debug("Skip syncing resource edges. "
                            + "No VMware vCenter server found with url=" 
                            + vCenterUrl);
            }
            return;
        }
        synchVCenterServerToHostResourceEdges(vCenter, existingHosts,props,hqApi);
        ResourcePrototype vmType = getResourceType(VM_TYPE,hqApi);
        ResourcesResponse vmResponse = hqApi.getResourceApi().getResources(vmType, true, false);
        assertSuccess(vmResponse, "Getting all " + VM_TYPE, false);

        synchHostToVmResourceEdges(existingHostVms, existingHosts, vmResponse.getResource(),props,hqApi); 
    }
    
    private void synchVCenterServerToHostResourceEdges(Resource vCenter, Map<String, Resource> existingHosts, Properties props, HQApi hqApi ) throws IOException, PluginException{
        String vCenterUrl = VSphereUtil.getURL(props);
        ResourceApi rApi = hqApi.getResourceApi();
        ResourceEdgeApi reApi = hqApi.getResourceEdgeApi();
        ResourcePrototype hostType = getResourceType(HOST_TYPE,hqApi);
        ResourcesResponse hostResponse = rApi.getResources(hostType, true, false);
        assertSuccess(hostResponse, "Getting all " + HOST_TYPE, false);

        List<ResourceEdge> edges = new ArrayList<ResourceEdge>();
        ResourceEdge edge = new ResourceEdge();
        ResourceFrom fromVcenter = new ResourceFrom();
        ResourceTo toHosts = new ResourceTo();
       

        for (Resource r : hostResponse.getResource()) {
            if (isVCenterManagedEntity(vCenterUrl, r)) {
                toHosts.getResource().add(r);
                String esxHost = getEsxHost(r);
                if (esxHost != null) {
                    existingHosts.put(esxHost, r);
                }
            }
        }
        
        fromVcenter.setResource(vCenter);
        edge.setRelation("virtual");
        edge.setResourceFrom(fromVcenter);
        edge.setResourceTo(toHosts);
        edges.add(edge);

        if (log.isDebugEnabled()) {
            log.debug("Syncing resource edges for vCenter[name=" + vCenter.getName()
                        + ", resourceId=" + vCenter.getId()
                        + "] with " + toHosts.getResource().size() + " hosts.");
        }

        StatusResponse syncResponse = reApi.syncResourceEdges(edges);
        assertSuccess(syncResponse, "Sync vCenter and host edges", false);
    }
     
    private void synchHostToVmResourceEdges( Map<String, List<Resource>> existingHostVms,  Map<String, Resource> existingHosts, List<Resource> vms, Properties props, HQApi hqApi) throws IOException, PluginException {
        String vCenterUrl = VSphereUtil.getURL(props);
        List<ResourceEdge> hostToVmEdges  = new ArrayList<ResourceEdge>();

        for (Resource r : vms) {
            if (isVCenterManagedEntity(vCenterUrl, r)) {
                String esxHost = getEsxHost(r);
                List<Resource> vmResources = existingHostVms.get(esxHost);
                if (vmResources == null) {
                    vmResources = new ArrayList<Resource>();
                    existingHostVms.put(esxHost, vmResources);
                }
                vmResources.add(r);
            }
        }
                
        for (Resource r : existingHosts.values()) {
            ResourceFrom parent = new ResourceFrom();
            parent.setResource(r);
            
            ResourceTo children = new ResourceTo();
            String esxHost = getEsxHost(r);
            List<Resource> vmResources = existingHostVms.get(esxHost);
            if (vmResources != null) {
                children.getResource().addAll(vmResources);
            }

            ResourceEdge rEdge = new ResourceEdge();
            rEdge.setRelation("virtual");
            rEdge.setResourceFrom(parent);
            rEdge.setResourceTo(children);
            hostToVmEdges.add(rEdge);
            
            if (log.isDebugEnabled()) {
                log.debug("Syncing resource edges for host[name=" + r.getName()
                            + ", resourceId=" + r.getId()
                            + "] with " + children.getResource().size() + " VMs.");
            }
        }
        
        StatusResponse syncResponse = hqApi.getResourceEdgeApi().syncResourceEdges(hostToVmEdges);
        assertSuccess(syncResponse, "Sync host and VM edges", false);
    }
    
    /**
     *  Delete resources that have been manually removed from vCenter
     */
    private void removePlatformsFromInventory(Map<String, List<Resource>> vcHosts, 
                                    Map<String, Resource> existingHosts, Map<String, List<Resource>> existingHostVms, VSphereUtil vim, HQApi hqApi)  throws IOException, PluginException {
        //
        for (String hostName : existingHostVms.keySet()) {
            List<Resource> hqVms = existingHostVms.get(hostName);
            List<Resource> vcVms = vcHosts.get(hostName);
            
            if (vcVms == null) {
                // not one of the hosts in vCenter
                Resource r = existingHosts.get(hostName);
                if (r != null) {
                    removeHost(r,vim,hqApi);
                }
            } else {
                // vm names may be the same, so use fqdn (uuid) to
                // determine whether vms should be deleted from hq
                
                List<String> vcVmFqdns = new ArrayList<String>();
                for (Resource r : vcVms) {
                    String fqdn = getFqdn(r);
                    if (fqdn != null) {
                        vcVmFqdns.add(fqdn);
                    }
                }
                
                for (Resource r : hqVms) {
                    String fqdn = getFqdn(r);
                    if (fqdn != null && !vcVmFqdns.contains(fqdn)) {
                        // Not one of the powered-on VMs from vCenter
                        removeVM(r,vim,hqApi);
                    }
                }
            }
        }
    }

    private boolean isVCenterManagedEntity(String vCenterUrl, Resource r) {
        boolean result = false;
        
        for (ResourceConfig c : r.getResourceConfig()) {
            if (VSphereUtil.PROP_URL.equals(c.getKey())) {
                if (c.getValue().equals(vCenterUrl)) {
                    result = true;
                    break;
                }
            }
        }
        
        return result;
    }
    
    private Resource getVCenterServer(String vCenterUrl, HQApi hqApi)
        throws IOException, PluginException {
        
        if (vCenterUrl == null) {
            return null;
        }
        
        Resource vCenter = null;        
        ResourceApi rApi = hqApi.getResourceApi();
        ResourcePrototype vcType = getResourceType(VC_TYPE,hqApi);
        ResourcesResponse vcResponse = rApi.getResources(vcType, true, false);
        assertSuccess(vcResponse, "Getting all " + VC_TYPE, false);
        
        for (Resource r : vcResponse.getResource()) {
            if (isVCenterManagedEntity(vCenterUrl, r)) {
                vCenter = r;
                break;
            }
        }
        
        return vCenter;
    }
    
    private String getEsxHost(Resource r) {
        String esxHost = null;
        String prototype = r.getResourcePrototype().getName();
        
        if (VM_TYPE.equals(prototype)) {
            for (ResourceProperty p : r.getResourceProperty()) {
                if (ESX_HOST.equals(p.getKey())) {
                    esxHost = p.getValue();
                    break;
                }
            }
        } else if (HOST_TYPE.equals(prototype)) {
            for (ResourceConfig c : r.getResourceConfig()) {
                if (VSphereUtil.PROP_HOSTNAME.equals(c.getKey())) {
                    esxHost = c.getValue();
                    break;
                }
            }
        }
        
        return esxHost;
    }

    private String getFqdn(Resource r) {
        String fqdn = null;
        for (ResourceInfo ri : r.getResourceInfo()) {
            if ("fqdn".equals(ri.getKey())) {
                fqdn = ri.getValue();
                break;
            }
        }
        return fqdn;
    }
    
    /**
     * Generate an unique platform name by appending the uuid
     */
    private String generatePlatformName(String name, String uuid) {
        return name + " {" + uuid + "}";
    }
    
    private void removeHost(Resource r, VSphereUtil vim, HQApi hqApi)
        throws IOException, PluginException {
        
        try {
            // verify to see if it exists in vCenter
            vim.findByUuid(VSphereUtil.HOST_SYSTEM, getFqdn(r));
            
            if (log.isDebugEnabled()) {
                log.debug(HOST_TYPE + "[name=" + r.getName() 
                              + "] exists in vCenter. Not removing from HQ.");
            }
        } catch (ManagedEntityNotFoundException me) {
            removeResource(r, hqApi);
        }
    }

    private void removeVM(Resource r, VSphereUtil vim, HQApi hqApi)
        throws IOException, PluginException {
    
        try {
            // verify to see if it exists in vCenter
            vim.findByUuid(VSphereUtil.VM, getFqdn(r));
            
            if (log.isDebugEnabled()) {
                log.debug(VM_TYPE + "[name=" + r.getName() 
                              + "] exists in vCenter. Not removing from HQ.");
            }
        } catch (ManagedEntityNotFoundException me) {
            removeResource(r, hqApi);
        }
    }
    
    private void removeResource(Resource r, HQApi hqApi) 
        throws IOException, PluginException {

        if (log.isDebugEnabled()) {
            log.debug("Managed entity (" + r.getName() + ") no longer exists in vCenter. "
                         + " Removing from HQ inventory.");
        }
        
        // throttle requests to the hq server to minimize StaleStateExceptions
        // TODO: there needs to be a better way to do this 
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            // Ignore
        }

        ResourceApi rApi = hqApi.getResourceApi();

        // TODO: As a final step, need to check resource availability
        // (must be DOWN) before deleting.
        
        StatusResponse deleteResponse = rApi.deleteResource(r.getId());
        assertSuccess(deleteResponse, "Delete resource id=" + r.getId(), false);
    }
}