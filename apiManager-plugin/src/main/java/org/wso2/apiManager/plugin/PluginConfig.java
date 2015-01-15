package org.wso2.apiManager.plugin;

import com.eviware.soapui.plugins.PluginAdapter;
import com.eviware.soapui.plugins.PluginConfiguration;

@PluginConfiguration(groupId = "org.wso2.plugins", name = "WSO2 API Manager Plugin", version = "0.1",
        autoDetect = true, description = "Plugin that supports integration with WSO2 API Manager",
        infoUrl = "" )
public class PluginConfig extends PluginAdapter {
}
