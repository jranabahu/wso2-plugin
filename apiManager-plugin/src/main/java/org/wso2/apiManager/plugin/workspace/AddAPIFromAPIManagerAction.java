package org.wso2.apiManager.plugin.workspace;

import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.plugins.ActionConfiguration;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import org.wso2.apiManager.plugin.ActionGroups;
import org.wso2.apiManager.plugin.Utils;
import org.wso2.apiManager.plugin.dataObjects.APIInfo;
import org.wso2.apiManager.plugin.dataObjects.APIListExtractionResult;
import org.wso2.apiManager.plugin.worker.APIExtractorWorker;
import org.wso2.apiManager.plugin.worker.APIImporterWorker;

import java.net.URL;
import java.util.List;

@ActionConfiguration(actionGroup = ActionGroups.OPEN_PROJECT_ACTIONS, separatorBefore = true)
public class AddAPIFromAPIManagerAction extends AbstractSoapUIAction<WsdlProject> {

    public AddAPIFromAPIManagerAction() {
        super("Add API From WSO2 API Store", "Adds API from the specification on WSO2 API Store.");
    }

    @Override
    public void perform(WsdlProject wsdlProject, Object o) {
        APIListExtractionResult listExtractionResult;
        String urlString = null;
        while(true) {
            urlString =
                    UISupport.getDialogs().prompt("Input API Store URL", "Add API Specification from 3scale",
                                                  urlString);
            if (urlString == null) return;
            URL url = Utils.validateURL(urlString);
            if (url == null) {
                UISupport.showErrorMessage("Invalid URL");
                continue;
            }
            //TODO: FIX properly
            listExtractionResult = APIExtractorWorker.downloadAPIList(url.toString(),"","","");
            if (listExtractionResult.isCanceled()) return;

            if (listExtractionResult.getApis() != null) break;
            UISupport.showErrorMessage(listExtractionResult.getError());
        }

        List<APIInfo> selectedAPIs = Utils.showSelectAPIDefDialog(listExtractionResult.getApis());
        if(selectedAPIs != null){
            List<RestService> services = APIImporterWorker.importServices(selectedAPIs, wsdlProject);
            if(services != null && services.size() != 0){
                UISupport.select(services.get(0));
            }
        }


    }
}
