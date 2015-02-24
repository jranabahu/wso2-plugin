/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.apiManager.plugin.worker;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.CredentialsConfig;
import com.eviware.soapui.impl.rest.OAuth2ProfileContainer;
import com.eviware.soapui.impl.rest.RestMethod;
import com.eviware.soapui.impl.rest.RestRequest;
import com.eviware.soapui.impl.rest.RestResource;
import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.x.dialogs.Worker;
import com.eviware.x.dialogs.XProgressDialog;
import com.eviware.x.dialogs.XProgressMonitor;
import org.wso2.apiManager.plugin.Utils;
import org.wso2.apiManager.plugin.constants.APIConstants;
import org.wso2.apiManager.plugin.dataObjects.APIInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class APIImporterWorker implements Worker {
    String errors = "";
    private XProgressDialog waitDialog;
    private boolean cancelled = false;
    private List<APIInfo> links;
    private WsdlProject project;
    private List<RestService> addedServices = new ArrayList<RestService>();

    private APIImporterWorker(XProgressDialog waitDialog, List<APIInfo> links, WsdlProject project) {
        this.waitDialog = waitDialog;
        this.links = links;
        this.project = project;
    }

    public static List<RestService> importServices(List<APIInfo> links, WsdlProject project) {
        APIImporterWorker worker = new APIImporterWorker(UISupport.getDialogs().createProgressDialog("Importing APIs." +
                                                                                                     "..", 100, "",
                                                                                                     true), links,
                                                         project);
        try {
            worker.waitDialog.run(worker);
        } catch (Exception e) {
            UISupport.showErrorMessage(e.getMessage());
            SoapUI.logError(e);
        }
        if (worker.addedServices != null && worker.addedServices.size() > 0) {
            return worker.addedServices;
        } else {
            return null;
        }
    }

    @Override
    public Object construct(XProgressMonitor monitor) {
        for (APIInfo apiInfo : links) {
            if (cancelled) {
                break;
            }
            RestService[] service;
            try {
                service = Utils.importAPItoProject(apiInfo, project);

                OAuth2ProfileContainer profileContainer = project.getOAuth2ProfileContainer();
                if (profileContainer.getProfileByName(APIConstants.WSO2_API_MANAGER_DEFAULT) == null) {
                    profileContainer.addNewOAuth2Profile(APIConstants.WSO2_API_MANAGER_DEFAULT);
                }

                if (service != null) {
                    for (RestService restService : service) {
                        List<RestResource> resources = restService.getAllResources();
                        for (RestResource resource : resources) {
                            List<RestMethod> methods = resource.getRestMethodList();
                            for (RestMethod method : methods) {
                                List<RestRequest> restRequests = method.getRequestList();
                                for (RestRequest restRequest : restRequests) {
                                    restRequest.setSelectedAuthProfileAndAuthType(APIConstants
                                                                                          .WSO2_API_MANAGER_DEFAULT,
                                                                                  CredentialsConfig.AuthType
                                                                                          .O_AUTH_2_0);
                                    // This will rename the request name to something similar to
                                    // get_repos/{user_name}/{repo_name} - default_request
                                    restRequest.setName(constructRequestName(method.getName()));
                                }
                            }
                        }
                        // We change the service name to the apiName/apiVersion
                        restService.setName(constructServiceName(apiInfo, restService.getName()));
                    }
                }
            } catch (Throwable e) {
                if (errors.length() > 0) {
                    errors += "\n";
                }

                errors = String.format("Failed to read API description for[%s] - [%s]", apiInfo.getName(), e
                        .getMessage());
                SoapUI.logError(e);
                continue;
            }
            if (service != null) {
                addedServices.addAll(Arrays.asList(service));
            }
        }

        if (errors.length() > 0) {
            errors += "\nPlease contact WSO2 support for assistance";
        }

        return null;
    }

    @Override
    public void finished() {
        if (cancelled) {
            return;
        }
        waitDialog.setVisible(false);
        if (StringUtils.hasContent(errors)) {
            UISupport.showErrorMessage(errors);
        }
    }

    @Override
    public boolean onCancel() {
        cancelled = true;
        waitDialog.setVisible(false);
        return true;
    }

    private String constructServiceName(APIInfo apiInfo, String resourceName) {
        return apiInfo.getName() + "/" + apiInfo.getVersion() + resourceName;
    }

    private String constructRequestName(String methodName) {
        return methodName + " - " + "default_request";
    }
}
