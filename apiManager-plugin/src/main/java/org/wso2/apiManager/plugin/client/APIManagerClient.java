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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
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

    /**
     * This method will return all APIs of the given tenant domain
     *
     * @param storeEndpoint The endpoint of the API Store
     * @param userName      The tenant aware user name
     * @param password      the password of the user
     * @param tenantDomain  The tenant domain of the store
     * @return list of @link{APIInfo}
     * @throws java.lang.Exception if any error occurs
     */
    public List<APIInfo> getAllPublishedAPIs(String storeEndpoint, String userName, char[] password,
                                             String tenantDomain) throws Exception {
        List<APIInfo> apiList = new ArrayList<>();

        String tenantUserName = userName;

        /*
         The tenant domain can be empty.
         If the tenant domain is not empty then we use the tenant aware user name for authentication purposes.
         If it empty, then we assign super tenant domain name for that.
         */
        if (!StringUtils.isNullOrEmpty(tenantDomain)) {
            tenantUserName = constructTenantUserName(userName, tenantDomain);
        } else {
            tenantDomain = APIConstants.CARBON_SUPER;
        }

        // If the authentication process is successful
        if (authenticate(storeEndpoint, tenantUserName, password)) {
            HttpClient httpClient = getHttpClient();
            HttpPost httppost = new HttpPost(getAPIStoreListUrl(storeEndpoint));
            // Request parameters and other properties.
            List<NameValuePair> params = new ArrayList<NameValuePair>(3);

            params.add(new BasicNameValuePair(APIConstants.API_ACTION, APIConstants
                    .PAGINATED_PUBLISHED_API_GET_ACTION));
            params.add(new BasicNameValuePair("tenant", tenantDomain));
            params.add(new BasicNameValuePair("start", Integer.toString(0)));
            params.add(new BasicNameValuePair("end", Integer.toString(Integer.MAX_VALUE)));
            httppost.setEntity(new UrlEncodedFormEntity(params, APIConstants.UTF_8));

            HttpResponse response = httpClient.execute(httppost, httpContext);
            HttpEntity entity = response.getEntity();
            String responseString = EntityUtils.toString(entity, APIConstants.UTF_8);

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

    /**
     * Method to authenticate with the given API Store
     *
     * @param storeEndpoint The endpoint of the API Store
     * @param userName      the username with the tenant domain
     * @param password      the user password
     * @return true if authentication is successful
     * throws Exception if any error happens
     */
    private boolean authenticate(String storeEndpoint, String userName, char[] password) throws Exception {
        // create a post request to addAPI.
        HttpClient httpClient = getHttpClient();

        HttpPost httppost = new HttpPost(getAPIStoreLoginUrl(storeEndpoint));
        // Request parameters and other properties.
        List<NameValuePair> params = new ArrayList<NameValuePair>(3);

        params.add(new BasicNameValuePair(APIConstants.API_ACTION, APIConstants.API_LOGIN_ACTION));
        params.add(new BasicNameValuePair(APIConstants.API_STORE_LOGIN_USERNAME, userName));
        params.add(new BasicNameValuePair(APIConstants.API_STORE_LOGIN_PASSWORD, new String(password)));
        httppost.setEntity(new UrlEncodedFormEntity(params, APIConstants.UTF_8));

        HttpResponse response = httpClient.execute(httppost, httpContext);
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity, APIConstants.UTF_8);

        boolean isError = Boolean.parseBoolean(responseString.split(",")[0].split(":")[1].split("}")[0].trim());
        if (isError) {
            String errorMsg = responseString.split(",")[1].split(":")[1].split("}")[0].trim();
            throw new Exception(" Authentication with external APIStore -  failed due to " + errorMsg);
        } else {
            return true;
        }
    }

    /**
     * Method to initialize the http client. We use only one instance of http client since there can not be concurrent
     * invocations
     *
     * @return @link{HttpClient} httpClient instance
     */
    private HttpClient getHttpClient() {
        //TODO
        //String keyStoreFileName = SoapUI.getSettings().getString("SSLSettings@keyStore", null);
        //String keyStorePassword = SoapUI.getSettings().getString("SSLSettings@keyStorePassword", null);
        String keyStoreFileName = "/home/janaka/work/wso2/apim/cluster/wso2am-1.8" + "" +
                                  ".0/repository/resources/security/client-truststore.jks";
        String keyStorePassword = "wso2carbon";

        if (httpClient == null) {
            FileInputStream inputStream = null;
            try {
                KeyStore trustStore = KeyStore.getInstance("JKS");
                inputStream = new FileInputStream(new File(keyStoreFileName));

                trustStore.load(inputStream, keyStorePassword.toCharArray());
                SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(trustStore, new
                        TrustSelfSignedStrategy()).build();
                SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslcontext,
                                                                                                       null, null,
                                                                                                       SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
                httpClient = HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory).build();
            } catch (FileNotFoundException e) {
                SoapUI.logError(e, "Unable to find the trust store file in the given location");
            } catch (CertificateException e) {
                SoapUI.logError(e, "Unable to load the trust store ");
            } catch (NoSuchAlgorithmException e) {
                SoapUI.logError(e, "Unable to load the trust store");
            } catch (KeyStoreException e) {
                SoapUI.logError(e, "Unable to get the key store instance");
            } catch (IOException e) {
                SoapUI.logError(e, "Unable to load the trust store");
            } catch (KeyManagementException e) {
                SoapUI.logError(e, "Unable to load trust store material");
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return httpClient;
    }

    /**
     * This method will construct the tenant user name
     * Ex:- janaka@sampleTenant.com
     *
     * @param userName     The user name
     * @param tenantDomain The tenant domain of the user
     * @return The tenant user name
     */
    private String constructTenantUserName(String userName, String tenantDomain) {
        String tenantUserName;
        tenantUserName = userName + APIConstants.TENANT_DOMAIN_SEPARATOR + tenantDomain;
        return tenantUserName;
    }

    /**
     * This method returns the login endpoint of the store
     * Ex:- https://localgost:9443/store/site/blocks/user/login/ajax/login.jag
     *
     * @param baseUrl The endpoint of the API Store
     * @return the login endpoint URL
     */
    private String getAPIStoreLoginUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf("/"));
        }
        return baseUrl + APIConstants.API_STORE_LOGIN_URL;
    }

    /**
     * This method returns the list URL of the API Store
     * Ex:- https://localhost:9443/store/site/blocks/api/listing/ajax/list.jag
     *
     * @param baseUrl The endpoint of the API Store
     * @return The list endpoint of the API Store
     */
    private String getAPIStoreListUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf("/"));
        }
        return baseUrl + APIConstants.API_STORE_API_LIST_URL;
    }

    /**
     * This method returns the swagger doc link of the API
     * Ex:- https://localhost:9443/store/api-docs/janaka%40janaka.com/WikipediaAPI/1.0.0
     *
     * @param baseUrl     The endpoint of the API Store
     * @param apiName     The name of the API
     * @param apiVersion  The version of the API
     * @param apiProvider The provider of the API
     * @return The swagger doc link of the API
     */
    private String getSwaggerDocLink(String baseUrl, String apiName, String apiVersion, String apiProvider) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf("/"));
        }
        try {
            apiProvider = URLEncoder.encode(apiProvider, APIConstants.UTF_8);
        } catch (UnsupportedEncodingException e) {
            SoapUI.logError(e, "Error while generating the api-docs URL ");
        }
        return baseUrl + "/api-docs/" + apiProvider + "/" + apiName + "/" + apiVersion;
    }
}
