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

package org.wso2.apiManager.plugin.client;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.support.StringUtils;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.wso2.apiManager.plugin.constants.APIConstants;
import org.wso2.apiManager.plugin.dataObjects.APIInfo;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class is responsible for connecting to WSO2 API Manager and fetching APIs and their definitions
 */
public class APIManagerClient {
    private static APIManagerClient apiManagerClient = null;
    private HttpContext httpContext = new BasicHttpContext();
    private HttpClient httpClient;

    private APIManagerClient() {
        CookieStore cookieStore = new BasicCookieStore();
        httpContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
    }

    public static APIManagerClient getInstance() {
        if (apiManagerClient == null) {
            apiManagerClient = new APIManagerClient();
        }
        return apiManagerClient;
    }

    private boolean authenticate(String storeEndpoint, String userName, char[] password) throws Exception {
        // create a post request to addAPI.
        HttpClient httpClient = getHttpClient();

        HttpPost httppost = new HttpPost(getAPIStoreLoginUrl(storeEndpoint));
        // Request parameters and other properties.
        List<NameValuePair> params = new ArrayList<NameValuePair>(3);

        params.add(new BasicNameValuePair(APIConstants.API_ACTION, APIConstants.API_LOGIN_ACTION));
        params.add(new BasicNameValuePair(APIConstants.APISTORE_LOGIN_USERNAME, userName));
        params.add(new BasicNameValuePair(APIConstants.APISTORE_LOGIN_PASSWORD, new String(password)));
        httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

        HttpResponse response = httpClient.execute(httppost, httpContext);
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity, "UTF-8");
        boolean isError = Boolean.parseBoolean(responseString.split(",")[0].split(":")[1].split("}")[0].trim());

        if (isError) {
            String errorMsg = responseString.split(",")[1].split(":")[1].split("}")[0].trim();
            throw new Exception(" Authentication with external APIStore -  failed due to " + errorMsg);
        } else {
            return true;
        }
    }

    private HttpClient getHttpClient()
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException,
                   KeyManagementException {
        //String keyStoreFileName = SoapUI.getSettings().getString("SSLSettings@keyStore", null);
        //String keyStorePassword = SoapUI.getSettings().getString("SSLSettings@keyStorePassword", null);
        String keyStoreFileName = "/home/janaka/work/wso2/apim/cluster/wso2am-1.8" + "" +
                                  ".0/repository/resources/security/client-truststore.jks";
        String keyStorePassword = "wso2carbon";

        if (httpClient == null) {
            KeyStore trustStore = KeyStore.getInstance("JKS");
            FileInputStream inputStream = new FileInputStream(new File(keyStoreFileName));
            try {
                trustStore.load(inputStream, keyStorePassword.toCharArray());
            } finally {
                inputStream.close();
            }
            SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(trustStore, new TrustSelfSignedStrategy())
                    .build();
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, null,
                                                                              null, SSLConnectionSocketFactory
                    .BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
            httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
        }
        return httpClient;
    }

    // get all tenant stores
    // We assume that we get the tenant store URL
    // We need to login to API store since the REST API still does not support anonymous access
    public List<APIInfo> getAllPublishedAPIs(String storeEndpoint, String userName, char[] password,
                                             String tenantDomain) throws Exception {
        List<APIInfo> apiList = new ArrayList<>();

        String tenantUserName = userName;
        if (!StringUtils.isNullOrEmpty(tenantDomain)) {
            tenantUserName = userName + "@" + tenantDomain;
        } else {
            tenantDomain = "carbon.super";
        }

        if (authenticate(storeEndpoint, tenantUserName, password)) {
            HttpClient httpClient = getHttpClient();
            HttpPost httppost = new HttpPost(getAPIStoreListUrl(storeEndpoint));
            // Request parameters and other properties.
            List<NameValuePair> params = new ArrayList<NameValuePair>(3);

            params.add(new BasicNameValuePair(APIConstants.API_ACTION, APIConstants
                    .PAGINATED_PUBLISHED_API_GET_ACTION));
            params.add(new BasicNameValuePair("tenant", tenantDomain));
            params.add(new BasicNameValuePair("start", "0"));
            params.add(new BasicNameValuePair("end", Integer.toString(Integer.MAX_VALUE)));
            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

            HttpResponse response = httpClient.execute(httppost, httpContext);
            HttpEntity entity = response.getEntity();
            String responseString = EntityUtils.toString(entity, "UTF-8");

            boolean isError = Boolean.parseBoolean(responseString.split(",")[0].split(":")[1].split("}")[0].trim());
            if (isError) {
                String errorMsg = responseString.split(",")[1].split(":")[1].split("}")[0].trim();
                throw new Exception("Error occurred while getting the list of APIs " + errorMsg);
            }

            JSONObject jsonObject;
            JSONArray apiArray;
            try {
                jsonObject = (JSONObject) JSONValue.parse(responseString);

                // We expect an JSON array for api list
                apiArray = (JSONArray) jsonObject.get("apis");
                for (Object apiJsonObject : apiArray) {
                    JSONObject apiJson = (JSONObject) apiJsonObject;

                    String apiName = apiJson.get("name").toString();
                    String apiProvider = apiJson.get("provider").toString();
                    String version = apiJson.get("version").toString();
                    String description = apiJson.get("description") == null ? "" : apiJson.get("description")
                            .toString();

                    APIInfo apiInfo = new APIInfo();
                    apiInfo.setName(apiName);
                    apiInfo.setProvider(apiProvider);
                    apiInfo.setVersion(version);
                    apiInfo.setDescription(description);
                    apiInfo.setSwaggerDocLink(getSwaggerDocLink(storeEndpoint, apiName, version, apiProvider));

                    apiList.add(apiInfo);
                }
            } catch (ClassCastException e) {
                throw new Exception("Could not parse the results. Incompatible result", e);
            }
        }

        return apiList;
    }

    public File downloadSwaggerDocument(String url) throws Exception {
        HttpClient httpClient = getHttpClient();

        byte[] responseContent = new byte[0];
        try {
            HttpPost post = new HttpPost(url);
            HttpResponse response = httpClient.execute(post);
            HttpEntity entity = response.getEntity();
            responseContent = EntityUtils.toByteArray(entity);
        } catch (IOException e) {
            SoapUI.logError(e);
        }

        FileOutputStream fileOutputStream = null;
        File tempFile = File.createTempFile("swagger-definition", null);
        try {
            fileOutputStream = new FileOutputStream(tempFile);
            fileOutputStream.write(responseContent);
        } catch (IOException e) {
            SoapUI.logError(e, "Unable to download the swagger definition ");
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return tempFile;
    }

    private String getAPIStoreLoginUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf("/"));
        }
        return baseUrl + APIConstants.APISTORE_LOGIN_URL;
    }

    private String getAPIStoreListUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf("/"));
        }
        return baseUrl + APIConstants.APISTORE_API_LIST_URL;
    }

    private String getSwaggerDocLink(String baseUrl, String apiName, String apiVersion, String apiProvider)
            throws Exception {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf("/"));
        }
        try {
            apiProvider = URLEncoder.encode(apiProvider, "utf-8");
        } catch (UnsupportedEncodingException e) {
            SoapUI.log("Error while generating the api-docs URL " + e.getMessage());
            throw e;
        }
        return baseUrl + "/api-docs/" + apiProvider + "/" + apiName + "/" + apiVersion;
    }
}
