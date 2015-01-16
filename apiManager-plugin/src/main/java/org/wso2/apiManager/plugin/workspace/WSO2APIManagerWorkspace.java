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

package org.wso2.apiManager.plugin.workspace;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.WorkspaceImpl;
import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.plugins.auto.PluginImportMethod;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import com.eviware.x.form.ValidationMessage;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormField;
import com.eviware.x.form.XFormFieldValidator;
import com.eviware.x.form.support.ADialogBuilder;
import org.wso2.apiManager.plugin.worker.APIExtractorWorker;
import org.wso2.apiManager.plugin.worker.APIImporterWorker;
import org.wso2.apiManager.plugin.Utils;
import org.wso2.apiManager.plugin.dataObjects.APIInfo;
import org.wso2.apiManager.plugin.dataObjects.APIListExtractionResult;
import org.wso2.apiManager.plugin.ui.ProjectModel;

import java.net.URL;
import java.util.List;

/**
 * This class is used to generate a new workspace for the WSO2 API Manager projects
 */
@PluginImportMethod(label = "WSO2 API Manager")
public class WSO2APIManagerWorkspace extends AbstractSoapUIAction<WorkspaceImpl> {
    private APIListExtractionResult listExtractionResult = null;

    public WSO2APIManagerWorkspace() {
        super("Create Project from WSO2 API Manager", "Creates new project from API specifications on the API Store");
    }

    @Override
    public void perform(WorkspaceImpl workspace, Object params) {
        final XFormDialog dialog = ADialogBuilder.buildDialog(ProjectModel.class);

        /*
        * The purpose of this listener is to validate the API Store URL and the Project name upon submitting the form
        * */
        dialog.getFormField(ProjectModel.API_STORE_URL).addFormFieldValidator(new XFormFieldValidator() {
            @Override
            public ValidationMessage[] validateField(XFormField formField) {
                if (StringUtils.isNullOrEmpty(dialog.getValue(ProjectModel.API_STORE_URL))) {
                    return new ValidationMessage[]{new ValidationMessage("Please enter the API Store URL.", dialog
                            .getFormField(ProjectModel.API_STORE_URL))};
                }
                if (StringUtils.isNullOrEmpty(dialog.getValue(ProjectModel.PROJECT_NAME))) {
                    return new ValidationMessage[]{new ValidationMessage("Please enter project name.", dialog
                            .getFormField(ProjectModel.PROJECT_NAME))};
                }
                if (StringUtils.isNullOrEmpty(dialog.getValue(ProjectModel.USER_NAME))) {
                    return new ValidationMessage[]{new ValidationMessage("Please enter user name.", dialog
                            .getFormField(ProjectModel.USER_NAME))};
                }
                if (StringUtils.isNullOrEmpty(dialog.getValue(ProjectModel.PASSWORD))) {
                    return new ValidationMessage[]{new ValidationMessage("Please enter an valid password.", dialog
                            .getFormField(ProjectModel.PASSWORD))};
                }
                URL storeUrl = Utils.validateURL(formField.getValue());
                if (storeUrl == null) {
                    return new ValidationMessage[]{new ValidationMessage("Invalid API Store URL.", formField)};
                }
                listExtractionResult = APIExtractorWorker.downloadAPIList(storeUrl.toString(), dialog.getValue
                        (ProjectModel.USER_NAME), dialog.getValue(ProjectModel.PASSWORD), dialog.getValue
                        (ProjectModel.TENANT_DOMAIN));
                if (StringUtils.hasContent(listExtractionResult.getError())) {
                    return new ValidationMessage[]{new ValidationMessage(listExtractionResult.getError(), formField)};
                }
                return new ValidationMessage[0];
            }
        });

        if (dialog.show() && listExtractionResult != null && !listExtractionResult.isCanceled()) {
            List<APIInfo> selectedAPIs = Utils.showSelectAPIDefDialog(listExtractionResult.getApis());
            if (selectedAPIs != null) {
                WsdlProject project;
                try {
                    project = workspace.createProject(dialog.getValue(ProjectModel.PROJECT_NAME), null);
                } catch (Exception e) {
                    SoapUI.logError(e);
                    UISupport.showErrorMessage(String.format("Unable to create Project because of %s exception with "
                                                             + "\"%s\" message", e.getClass().getName(), e.getMessage
                            ()));
                    return;
                }
                List<RestService> services = APIImporterWorker.importServices(selectedAPIs, project);
                if (services != null && services.size() != 0) {
                    UISupport.select(services.get(0));
                } else {
                    workspace.removeProject(project);
                }
            }
        }

    }


}
