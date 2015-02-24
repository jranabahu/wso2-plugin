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

package org.wso2.apiManager.plugin.ui;

import com.eviware.x.form.support.AField;
import com.eviware.x.form.support.AForm;

@AForm(name = "Select API to Import",
        description = "Please select from the list which API specification(s) you want to import to the project.")
public interface APIModel {
    @AField(description = "", type = AField.AFieldType.COMPONENT)
    public static final String API_INFO = "";

    @AField(description = "Generate test suite", type = AField.AFieldType.COMBOBOX)
    public static final String TEST_SUITE = "TEST_SUITE";
}
