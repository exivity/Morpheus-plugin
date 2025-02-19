//Copyright 2025 Exivity B.V.  
//SPDX-License-Identifier: Apache-2.0  

package com.exivity.tab

import com.morpheusdata.core.AbstractInstanceTabProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.Account
import com.morpheusdata.model.Instance
import com.morpheusdata.model.TaskConfig
import com.morpheusdata.model.ContentSecurityPolicy
import com.morpheusdata.model.User
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

// Add these imports for SSL validation disabling
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import java.security.cert.X509Certificate
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

@Slf4j
class CustomTabProvider extends AbstractInstanceTabProvider {
    Plugin plugin
    MorpheusContext morpheus

    String code = 'exivity-custom-tab'
    String name = 'FinOps Exivity'

    CustomTabProvider(Plugin plugin, MorpheusContext context) {
        this.plugin = plugin
        this.morpheus = context        
        disableSslValidation() // Disable SSL certificate validation
    }

    void initialize() {
        plugin.registerProvider(this)
    }

    // Method to disable SSL certificate validation
    private void disableSslValidation() {
        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = [
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            ] as TrustManager[]

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, new java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true
                }
            }

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
        } catch (Exception e) {
            log.error("Failed to disable SSL validation", e)
        }
    }

    private Map fetchReportData(String authToken, String reportId, String exivityUrl, String startDate, String endDate, String instanceId = null) {
        try {
            // Use provided dates or default to last 30 days
            def end = endDate ?: new Date().format("yyyy-MM-dd")
            def start = startDate ?: (new Date() - 30).format("yyyy-MM-dd")

            // Get settings from plugin
            def settingsObservable = morpheus.getSettings(plugin)
            def settingsOutput = settingsObservable.blockingGet()

            def slurper = new JsonSlurper()
            def settingsJson = slurper.parseText(settingsOutput)

            String reportDepth = settingsJson?.exivityReportDepth ?: 1

            // Construct the report URL
            log.info("Feching report, startDate is '${startDate}' and endDate Value is '${endDate}'")
            String baseUrl = "${exivityUrl}/v2/reportdata/${reportId}/run?precision=highest&dimension=accounts,services&depth=${reportDepth}&start=${start}&timeline=none&end=${end}&include=account_key,account_name,service_description,servicecategory_name,service_unit_label,service_interval"
            if (instanceId) {
                baseUrl += "&filter[account_key]=${instanceId}"
            }
            URL url = new URL(baseUrl)
            log.info("Fetching data from: '${url}'")
            HttpURLConnection connection = (HttpURLConnection) url.openConnection()

            // Set request headers
            connection.setRequestMethod('GET')
            connection.setRequestProperty('Accept', 'application/json')
            connection.setRequestProperty('Authorization', "Bearer ${authToken}")

            // If it's HTTPS, apply the SSL settings we already have
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true)
            }

            // Check response
            int responseCode = connection.getResponseCode()
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read and parse response
                def inputStream = connection.getInputStream()
                def reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                def reportData = new JsonSlurper().parse(reader)

                return [
                    success     : true,
                    data        : reportData,
                    errorMessage: null
                ]
            } else {
                // Handle error
                def errorStream = connection.getErrorStream()
                def errorReader = new InputStreamReader(errorStream, StandardCharsets.UTF_8)
                def errorResponse = new JsonSlurper().parse(errorReader)

                log.error("Report fetch failed. Response Code: ${responseCode}, Error: ${errorResponse}")

                return [
                    success     : false,
                    data        : null,
                    errorMessage: "Failed to fetch report: ${errorResponse}",
                    responseCode: responseCode
                ]
            }
        } catch (Exception e) {
            log.error("Unexpected error fetching report data", e)
            return [
                success     : false,
                data        : null,
                errorMessage: "Unexpected error: ${e.message}",
                responseCode: 0
            ]
        }
    }

    Map authenticateWithExivityAPI(String exivityUrl, String username, String password) {
        try {
            // Construct the authentication URL
            URL url = new URL("${exivityUrl}/v2/auth/token")
            HttpURLConnection connection = (HttpURLConnection) url.openConnection()

            // Set request method and headers
            connection.setRequestMethod('POST')
            connection.setRequestProperty('Content-Type', 'application/json')
            connection.setRequestProperty('Accept', 'application/json')
            connection.setDoOutput(true)

            // If it's an HTTPS connection, set additional properties to ignore certificate validation
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true)
            }

            // Prepare request body
            def requestBody = [
                username: username,
                password: password
            ]
            def jsonBody = new JsonBuilder(requestBody).toString()

            // Write request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8)
                os.write(input, 0, input.length)
            }

            // Check response code
            int responseCode = connection.getResponseCode()
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read response
                def inputStream = connection.getInputStream()
                def reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                def parsedResponse = new JsonSlurper().parse(reader)

                // Return successful authentication details
                return [
                    success     : true,
                    token       : parsedResponse.data.attributes.token,
                    errorMessage: null
                ]
            } else {
                // Handle error response
                def errorStream = connection.getErrorStream()
                def errorReader = new InputStreamReader(errorStream, StandardCharsets.UTF_8)
                def errorResponse = new JsonSlurper().parse(errorReader)

                log.error("Authentication failed. Response Code: ${responseCode}, Error: ${errorResponse}")

                // Return detailed error information
                return [
                    success     : false,
                    token       : null,
                    errorMessage: constructErrorMessage(responseCode, errorResponse),
                    responseCode: responseCode
                ]
            }
        } catch (Exception e) {
            log.error("Unexpected error authenticating with Exivity API", e)

            // Return generic error for unexpected exceptions
            return [
                success     : false,
                token       : null,
                errorMessage: "Unexpected error: ${e.message}",
                responseCode: 0
            ]
        }
    }

    // Helper method to construct user-friendly error message
    private String constructErrorMessage(int responseCode, def errorResponse) {
        switch (responseCode) {
            case 401:
                return "Authentication Failed: Invalid credentials"
            case 403:
                return "Access Forbidden: You do not have permission"
            case 404:
                return "API Endpoint Not Found: Check your Exivity URL"
            case 500:
                return "Server Error: Internal server problem"
            default:
                // Try to extract error message from response if available
                def errorMessage = errorResponse?.error?.message ?:
                                   errorResponse?.errors?.join(', ') ?:
                                   "Unknown authentication error"
                return "Authentication Error (${responseCode}): ${errorMessage}"
        }
    }

    @Override
    HTMLResponse renderTemplate(Instance instance) {
        ViewModel<Map> model = new ViewModel<>()

        try {
            def settingsObservable = morpheus.getSettings(plugin)
            def settingsOutput = settingsObservable.blockingGet()

            def slurper = new JsonSlurper()
            def settingsJson = slurper.parseText(settingsOutput)

            String exivityUrl = settingsJson?.exivityUrl ?: 'https://www.exivity.com'
            if (!exivityUrl.startsWith('http://') && !exivityUrl.startsWith('https://')) {
                exivityUrl = "https://${exivityUrl}"
            }
            exivityUrl = exivityUrl.replaceAll('/+$', '')

            String exivityUsername = settingsJson?.exivityUsername ?: ''
            String exivityPassword = settingsJson?.exivityPassword ?: ''
            String reportId = settingsJson?.exivityReportID ?: ''

            // Get dates from controller's static map
            def storedDates = ExivityPluginController.getStoredDates(instanceId: instance.id?.toString())

            // Use stored dates or fall back to defaults
            def dateRange = storedDates ?: getDefaultDateRange(settingsJson)
            def startDate = dateRange.startDate
            def endDate = dateRange.endDate

            log.info("Using dates for report: startDate=${startDate}, endDate=${endDate}")

            // Authenticate and get token
            def authResult = authenticateWithExivityAPI(exivityUrl, exivityUsername, exivityPassword)

            // Fetch report data if authentication successful
            def reportData = null
            def reportError = null
            if (authResult.success && reportId) {
                def reportResult = fetchReportData(authResult.token, reportId, exivityUrl, startDate, endDate, instance.getUuid())

                if (reportResult.success) {
                    reportData = reportResult.data
                } else {
                    reportError = reportResult.errorMessage
                }
            }

            model.object = [
                instance         : instance,
                exivityUrl       : exivityUrl,
                exivityUsername  : exivityUsername,
                authSuccess      : authResult.success,
                authToken        : authResult.success ? authResult.token : null,
                authErrorMessage : authResult.errorMessage ?: 'Unknown Authentication Error',
                authResponseCode : authResult.responseCode,
                startDate        : startDate,
                endDate          : endDate,
                reportData       : reportData,
                reportError      : reportError,
                pluginCode       : getCode()
            ]

            log.info(" '${getRenderer().getTemplateLoaders()}'")
            def templateLoaders = getRenderer().getTemplateLoaders()
            templateLoaders.each { loader ->
                // log.info("TemplateLoader: ${loader.getClass().getName()}")
                loader.getClass().getDeclaredFields().each { field ->
                    field.setAccessible(true)
                    log.info("${field.getName()}: ${field.get(loader)}")
                }
            }
            // log.info("nonce: ${model.object.nonce}")
            return getRenderer().renderTemplate("hbs/instanceTab", model)
        } catch (Exception e) {
            log.error("Exivity Plugin: Unexpected error in renderTemplate", e)
            model.object = [
                instance         : instance,
                exivityUrl       : 'https://www.exivity.com',
                exivityUsername  : 'Error retrieving settings',
                authSuccess      : false,
                authToken        : null,
                startDate        : (new Date() - 30).format("yyyy-MM-dd"),
                endDate          : new Date().format("yyyy-MM-dd"),
                authErrorMessage : "System error: ${e.message}",
                authResponseCode : 0,
                reportData       : null,
                reportError      : "Failed to fetch report data: ${e.message}"
            ]

            return getRenderer().renderTemplate("hbs/instanceTab", model)
        }
    }

    private void initializeApplianceContext() {
        if (morpheus.getApplianceContext() == null) {
            log.warn("Appliance context is not initialized")
        }
    }

    private Map getDefaultDateRange(def settingsJson) {
        try {
            def rangeDays = settingsJson.defaultDateRange?.toInteger() ?: 30
            def endDate = new Date()
            def startDate = endDate - rangeDays

            return [
                startDate: startDate.format("yyyy-MM-dd"),
                endDate  : endDate.format("yyyy-MM-dd")
            ]
        } catch (Exception e) {
            log.error("Error getting default date range", e)
            return [
                startDate: (new Date() - 30).format("yyyy-MM-dd"),
                endDate  : new Date().format("yyyy-MM-dd")
            ]
        }
    }

    @Override
    Boolean show(Instance instance, User user, Account account) {
        // Always show the tab, or implement custom visibility logic
        return true
    }

    @Override
    ContentSecurityPolicy getContentSecurityPolicy() {
        def csp = new ContentSecurityPolicy()
        csp.scriptSrc = "'self' 'strict-dynamic' https://*"
        csp.frameSrc = "'self' https://*"
        csp.imgSrc = "'self' https://*"
        csp.styleSrc = "'self' 'unsafe-inline'"
        csp.connectSrc = "'self' https://www.gstatic.com"
        csp
    }
}
