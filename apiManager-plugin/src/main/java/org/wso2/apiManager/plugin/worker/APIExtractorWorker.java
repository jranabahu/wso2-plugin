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
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.x.dialogs.Worker;
import com.eviware.x.dialogs.XProgressDialog;
import com.eviware.x.dialogs.XProgressMonitor;
import org.wso2.apiManager.plugin.client.APIManagerClient;
import org.wso2.apiManager.plugin.dataObjects.APIListExtractionResult;

/**
 * This class acts as the worker class to fetch APIs from WSO2 API Manager.
 */
public class APIExtractorWorker implements Worker {
    private XProgressDialog waitDialog = null;
    private APIListExtractionResult result = new APIListExtractionResult();

    private String url = null;
    private String userName;
    private String password;
    private String tenantDomain;

    private String apiRetrievingError = null;

    public APIExtractorWorker(String url, String userName, String password, String tenantDomain,
                              XProgressDialog waitDialog) {
        this.waitDialog = waitDialog;
        this.url = url;
        this.userName = userName;
        this.password = password;
        this.tenantDomain = tenantDomain;
    }

    public static APIListExtractionResult downloadAPIList(String url, String userName, String password,
                                                          String tenantDomain) {
        // TODO: findout why
        APIExtractorWorker worker = new APIExtractorWorker(url, userName, password, tenantDomain, UISupport
                .getDialogs().createProgressDialog("Getting the list of APIs", 0, "", true));
        try {
            worker.waitDialog.run(worker);
        } catch (Exception e) {
            SoapUI.logError(e);
            worker.result.addError(e.getMessage());
        }
        return worker.result;
    }

    @Override
    public Object construct(XProgressMonitor xProgressMonitor) {
        try {
            result.setApis(APIManagerClient.getInstance().getAllPublishedAPIs(url, userName, password, tenantDomain));
        } catch (Exception e) {
            SoapUI.logError(e);
            apiRetrievingError = e.getMessage();
            if (StringUtils.isNullOrEmpty(apiRetrievingError)) {
                apiRetrievingError = e.getClass().getName();
            }
        }
        return null;
    }

    @Override
    public void finished() {
        if (result.isCanceled()) {
            return;
        }
        waitDialog.setVisible(false);
        if (StringUtils.hasContent(apiRetrievingError)) {
            result.addError("Unable to read API list from the specified WSO2 API Manager Store because of the " +
                            "following error:\n" + apiRetrievingError);
            return;
        }
        if (result.getApis() == null || result.getApis().isEmpty()) {
            result.addError("No API is accessible at the specified URL or registered correctly.");
        }
    }

    @Override
    public boolean onCancel() {
        result.cancel();
        waitDialog.setVisible(false);
        return true;
    }
}
