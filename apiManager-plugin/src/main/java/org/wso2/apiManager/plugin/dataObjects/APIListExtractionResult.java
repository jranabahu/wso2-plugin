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

package org.wso2.apiManager.plugin.dataObjects;

import java.util.List;

public class APIListExtractionResult {
    private List<APIInfo> apis = null;
    private String error = null;
    private boolean canceled = false;

    public void addError(String errorText) {
        apis = null;
        if (error == null) {
            error = errorText;
        } else {
            error = error + "\n" + errorText;
        }
    }

    public void cancel() {
        canceled = true;
        apis = null;
    }

    public List<APIInfo> getApis() {
        return apis;
    }

    public void setApis(List<APIInfo> apis) {
        this.apis = apis;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
