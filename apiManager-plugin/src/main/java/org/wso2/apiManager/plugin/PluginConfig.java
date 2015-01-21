package org.wso2.apiManager.plugin;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.model.iface.InterfaceListener;
import com.eviware.soapui.model.iface.SoapUIListener;
import com.eviware.soapui.plugins.PluginAdapter;
import com.eviware.soapui.plugins.PluginConfiguration;

import java.util.List;

@PluginConfiguration(groupId = "org.wso2.plugins", name = "WSO2 API Manager Plugin", version = "1.0.0",
        autoDetect = true, description = "Plugin that supports integration with WSO2 API Manager",
        infoUrl = "")
public class PluginConfig extends PluginAdapter {
}
