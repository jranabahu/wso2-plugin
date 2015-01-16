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

package org.wso2.apiManager.plugin;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.rest.RestRequest;
import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.types.StringToStringMap;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.support.ADialogBuilder;
import com.smartbear.swagger.SwaggerImporter;
import com.smartbear.swagger.SwaggerUtils;
import org.wso2.apiManager.plugin.dataObjects.APIInfo;
import org.wso2.apiManager.plugin.ui.APIModel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Utils {
    /*
    * This method checks whether the given URL is a correct one.
    * */
    public static URL validateURL(String urlString) {
        if (StringUtils.isNullOrEmpty(urlString)) {
            return null;
        }

        if (!urlString.toLowerCase().startsWith("http://") && !urlString.toLowerCase().startsWith("https://")) {
            return null;
        }

        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            SoapUI.logError(e);
            return null;
        }
    }

    public static List<APIInfo> showSelectAPIDefDialog(final List<APIInfo> apis) {
        final XFormDialog dialog = ADialogBuilder.buildDialog(APIModel.class);

        TableModel tableModel = new AbstractTableModel() {
            Object[][] data = convertToTableData(apis);
            String[] columnNames = {"Name", "Version", "Provider", "Description"};

            @Override
            public int getRowCount() {
                return data.length;
            }

            @Override
            public int getColumnCount() {
                // We have a hardcoded set of columns
                return columnNames.length;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                return data[rowIndex][columnIndex];
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            @Override
            public String getColumnName(int column) {
                return columnNames[column];
            }
        };


        JTable apiTable = new JTable(tableModel);
        apiTable.setCellSelectionEnabled(false);
        apiTable.setColumnSelectionAllowed(false);
        apiTable.setRowSelectionAllowed(true);
        apiTable.setFillsViewportHeight(true);
        apiTable.setPreferredScrollableViewportSize(new Dimension(500,200));

        JScrollPane scrollPane = new JScrollPane(apiTable);
        scrollPane.setPreferredSize(new Dimension(500,200));

        dialog.setFormFieldProperty("component", scrollPane);
        dialog.setFormFieldProperty("preferredSize", new Dimension(500,200));

        if (dialog.show()) {
            int[] selected = apiTable.getSelectedRows();
            ArrayList<APIInfo> selectedAPIs = new ArrayList<APIInfo>();
            for (int no : selected) {
                selectedAPIs.add(apis.get(no));
            }
            return selectedAPIs;
        } else {
            return null;
        }

    }

    public static RestService[] importAPItoProject(APIInfo apiLink, WsdlProject project) {
        SwaggerImporter importer = SwaggerUtils.createSwaggerImporter(apiLink.getSwaggerDocLink(), project);
        SoapUI.log("Importing Swagger from [" + apiLink.getName() + "]");
        return importer.importSwagger(apiLink.getSwaggerDocLink());
    }

    private static Object[][] convertToTableData(List<APIInfo> apiList) {
        Object[][] convertedData = new Object[apiList.size()][4];

        for (int i = 0; i < apiList.size(); i++) {
            APIInfo apiInfo = apiList.get(i);

            convertedData[i][0] = apiInfo.getName();
            convertedData[i][1] = apiInfo.getVersion();
            convertedData[i][2] = apiInfo.getProvider();
            convertedData[i][3] = apiInfo.getDescription();
        }
        return convertedData;
    }

    public static void setAuthorizationHeader(RestRequest restRequest) {
        StringToStringMap map = new StringToStringMap(1);
        map.putIfMissing("Authorization","Bearer <MY_ACCESS_TOKEN>");
        restRequest.setRequestHeaders(map);
    }
}
