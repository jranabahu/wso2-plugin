package org.wso2.apiManager.plugin.workspace;

import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.plugins.ActionConfiguration;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import com.eviware.x.form.ValidationMessage;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormField;
import com.eviware.x.form.XFormFieldValidator;
import com.eviware.x.form.support.ADialogBuilder;
import org.wso2.apiManager.plugin.ActionGroups;
import org.wso2.apiManager.plugin.Utils;
import org.wso2.apiManager.plugin.dataObjects.APIInfo;
import org.wso2.apiManager.plugin.dataObjects.APIListExtractionResult;
import org.wso2.apiManager.plugin.ui.ImportModel;
import org.wso2.apiManager.plugin.worker.APIExtractorWorker;
import org.wso2.apiManager.plugin.worker.APIImporterWorker;

import java.net.URL;
import java.util.List;

@ActionConfiguration(actionGroup = ActionGroups.OPEN_PROJECT_ACTIONS, separatorBefore = true)
public class AddAPIFromAPIManagerAction extends AbstractSoapUIAction<WsdlProject> {

    private XFormDialog dialog;

    public AddAPIFromAPIManagerAction() {
        super("Add API From WSO2 API Store", "Adds API from the specification on WSO2 API Store.");
    }

    @Override
    public void perform(WsdlProject wsdlProject, Object o) {
        APIListExtractionResult listExtractionResult = null;
        if (dialog == null) {
            dialog = ADialogBuilder.buildDialog(ImportModel.class);
            dialog.getFormField(ImportModel.API_STORE_URL).addFormFieldValidator(new XFormFieldValidator() {
                @Override
                public ValidationMessage[] validateField(XFormField formField) {
                    if (StringUtils.isNullOrEmpty(dialog.getValue(ImportModel.API_STORE_URL))) {
                        return new ValidationMessage[]{new ValidationMessage("Please enter the API Store URL.", dialog
                                .getFormField(ImportModel.API_STORE_URL))};
                    }
                    if (StringUtils.isNullOrEmpty(dialog.getValue(ImportModel.USER_NAME))) {
                        return new ValidationMessage[]{new ValidationMessage("Please enter user name.", dialog
                                .getFormField(ImportModel.USER_NAME))};
                    }
                    if (StringUtils.isNullOrEmpty(dialog.getValue(ImportModel.PASSWORD))) {
                        return new ValidationMessage[]{new ValidationMessage("Please enter an valid password.", dialog
                                .getFormField(ImportModel.PASSWORD))};
                    }
                    URL storeUrl = Utils.validateURL(formField.getValue());
                    if (storeUrl == null) {
                        return new ValidationMessage[]{new ValidationMessage("Invalid API Store URL.", formField)};
                    }
                    return new ValidationMessage[0];
                }
            });
        }
        while (dialog.show()) {
            String urlString = dialog.getValue(ImportModel.API_STORE_URL);
            String userName = dialog.getValue(ImportModel.USER_NAME);
            String password = dialog.getValue(ImportModel.PASSWORD);
            String tenantDomain = dialog.getValue(ImportModel.TENANT_DOMAIN);
            if (urlString == null) {
                return;
            }
            URL url = Utils.validateURL(urlString);
            if (url == null) {
                UISupport.showErrorMessage("Invalid URL");
                continue;
            }
            //TODO: FIX properly
            listExtractionResult = APIExtractorWorker.downloadAPIList(url.toString(), userName, password, tenantDomain);
            if (listExtractionResult.isCanceled()) {
                return;
            }

            if (listExtractionResult.getApis() != null) {
                break;
            }
            UISupport.showErrorMessage(listExtractionResult.getError());
        }
        if(listExtractionResult == null){
            return;
        }

        List<APIInfo> selectedAPIs = Utils.showSelectAPIDefDialog(listExtractionResult.getApis());
        if (selectedAPIs != null) {
            List<RestService> services = APIImporterWorker.importServices(selectedAPIs, wsdlProject);
            if (services != null && services.size() != 0) {
                UISupport.select(services.get(0));
            }
        }


    }
}
