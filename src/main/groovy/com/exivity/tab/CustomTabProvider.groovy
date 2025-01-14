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

     private static final String METADATA_KEY = 'exivityDatePrefs'

    
    private Map getDatePreference(Instance instance) {
        try {
            def metadata = morpheus.getInstanceService().getMetadata(instance).blockingGet()
            def prefs = metadata?.get(METADATA_KEY)
            
            if (prefs?.startDate && prefs?.endDate) {
                try {
                    // Validate the dates
                    Date.parse('yyyy-MM-dd', prefs.startDate)
                    Date.parse('yyyy-MM-dd', prefs.endDate)
                    return [
                        startDate: prefs.startDate,
                        endDate: prefs.endDate
                    ]
                } catch (Exception e) {
                    log.warn("Invalid date format in metadata for instance ${instance.id}", e)
                }
            }
        } catch (Exception e) {
            log.error("Failed to retrieve date preferences from metadata for instance ${instance.id}", e)
        }
        
        // Return default date range if no valid preferences found
        return getDefaultDateRange([:])
    }

    void initialize() {
        plugin.registerProvider(this)
    }
    
    public void saveDatePreference(Instance instance, String startDate, String endDate) {
        try {
            def metadata = [
                (METADATA_KEY): [
                    startDate: startDate,
                    endDate: endDate,
                    lastUpdated: new Date().time
                ]
            ]
            
            morpheus.getInstanceService().updateMetadata(instance, metadata).blockingGet()
            log.info("Saved date preferences to instance ${instance.id} metadata")
        } catch (Exception e) {
            log.error("Failed to save date preferences to metadata for instance ${instance.id}", e)
            throw e
        }
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

            log.warn("SSL Certificate validation has been DISABLED. This is NOT recommended for production!")
        } catch (Exception e) {
            log.error("Failed to disable SSL validation", e)
        }
    }

    private Map fetchReportData(String authToken, String reportId, String exivityUrl, String startDate, String endDate) {
        try {
            // Use provided dates or default to last 30 days
            def end = endDate ?: new Date().format("yyyy-MM-dd")
            def start = startDate ?: (new Date() - 30).format("yyyy-MM-dd")
            
            // Construct the report URL
            URL url = new URL("${exivityUrl}/v2/reportdata/${reportId}/run?precision=highest&dimension=accounts,services&filter%5Bservice_id%5D=&filter%5Bservicecategory_id%5D=&depth=3&filter%5Bparent_account_id%5D=&start=${start}&timeline=none&end=${end}&include=account_key,account_name,service_description")
            HttpURLConnection connection = (HttpURLConnection) url.openConnection()
            
            // Set request headers
            connection.setRequestMethod('GET')
            connection.setRequestProperty('Accept', 'application/json')
            connection.setRequestProperty('Authorization', "Bearer ${authToken}")

            // If it's HTTPS, apply the SSL settings we already have
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection)connection).setHostnameVerifier((hostname, session) -> true)
            }

            // Check response
            int responseCode = connection.getResponseCode()
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read and parse response
                def inputStream = connection.getInputStream()
                def reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                def reportData = new JsonSlurper().parse(reader)
                
                return [
                    success: true,
                    data: reportData,
                    errorMessage: null
                ]
            } else {
                // Handle error
                def errorStream = connection.getErrorStream()
                def errorReader = new InputStreamReader(errorStream, StandardCharsets.UTF_8)
                def errorResponse = new JsonSlurper().parse(errorReader)
                
                log.error("Report fetch failed. Response Code: ${responseCode}, Error: ${errorResponse}")
                
                return [
                    success: false,
                    data: null,
                    errorMessage: "Failed to fetch report: ${errorResponse}",
                    responseCode: responseCode
                ]
            }
        } catch (Exception e) {
            log.error("Unexpected error fetching report data", e)
            return [
                success: false,
                data: null,
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
                ((HttpsURLConnection)connection).setHostnameVerifier((hostname, session) -> true)
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
                    success: true, 
                    token: parsedResponse.data.attributes.token,
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
                    success: false,
                    token: null,
                    errorMessage: constructErrorMessage(responseCode, errorResponse),
                    responseCode: responseCode
                ]
            }
        } catch (Exception e) {
            log.error("Unexpected error authenticating with Exivity API", e)
            
            // Return generic error for unexpected exceptions
            return [
                success: false,
                token: null,
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
        
        // Ensure the URL is a complete URL
        String exivityUrl = settingsJson?.exivityUrl ?: 'https://www.exivity.com'
        
        // Add protocol if not present
        if (!exivityUrl.startsWith('http://') && !exivityUrl.startsWith('https://')) {
            exivityUrl = "https://${exivityUrl}"
        }
        
        // Remove trailing slash to prevent double slashes
        exivityUrl = exivityUrl.replaceAll('/+$', '')
        
        String exivityUsername = settingsJson?.exivityUsername ?: ''
        String exivityPassword = settingsJson?.exivityPassword ?: ''
        String reportId = settingsJson?.exivityReportID ?: ''
        
        // Authenticate and get authentication result
        def authResult = authenticateWithExivityAPI(exivityUrl, exivityUsername, exivityPassword)
        
        log.info("Exivity Plugin: Processed URL - ${exivityUrl}, Username - ${exivityUsername}, Authentication Successful: ${authResult.success}")

        // Get the date preferences using the instance service
        def dateRange = getDatePreference(instance)
        def startDate = dateRange.startDate
        def endDate = dateRange.endDate
        // Get the current user's API key and access token
        //def currentUser = morpheus.getAsync().getUserService().getCurrentUser().blockingGet()

        // Generate API key and bearer token
        //def apiKey = morpheus.getAsync().getUserService().generateApiKey(currentUser).blockingGet()
        //def bearerToken = morpheus.getAsync().getUserService().generateBearerToken(currentUser).blockingGet()
        
        // Fetch report data if authentication successful
        def reportData = null
        def reportError = null
        if (authResult.success && reportId) {
            def reportResult = fetchReportData(authResult.token, reportId, exivityUrl, startDate, endDate)
            if (reportResult.success) {
                // Filter the report data to only include entries matching the instance UUID
                def filteredReport = reportResult.data
                if (filteredReport?.meta?.report) {
                    filteredReport.meta.report = filteredReport.meta.report.findAll { report ->
                        report.account_key == instance.getUuid()
                    }
                }
                reportData = filteredReport
            } else {
                reportError = reportResult.errorMessage
            }
        }
        
        model.object = [
            instance: instance,
            exivityUrl: exivityUrl,
            exivityUsername: exivityUsername,
            authSuccess: authResult.success,
            authToken: authResult.success ? authResult.token : null,
            authErrorMessage: authResult.errorMessage ?: 'Unknown Authentication Error',
            authResponseCode: authResult.responseCode,
            startDate: startDate,
            endDate: endDate,
            reportData: reportData,
            reportError: reportError,         
            pluginCode: getCode()
        ]
        
        return getRenderer().renderTemplate("hbs/instanceTab", model)
    } catch (Exception e) {
        log.error("Exivity Plugin: Unexpected error in renderTemplate", e)
        model.object = [
            instance: instance,
            exivityUrl: 'https://www.exivity.com',
            exivityUsername: 'Error retrieving settings',
            authSuccess: false,
            authToken: null,
            startDate: (new Date() - 30).format("yyyy-MM-dd"),
            endDate: new Date().format("yyyy-MM-dd"),
            authErrorMessage: "System error: ${e.message}",
            authResponseCode: 0,
            reportData: null,
            reportError: "Failed to fetch report data: ${e.message}"
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
                endDate: endDate.format("yyyy-MM-dd")
            ]
        } catch (Exception e) {
            log.error("Error getting default date range", e)
            return [
                startDate: (new Date() - 30).format("yyyy-MM-dd"),
                endDate: new Date().format("yyyy-MM-dd")
            ]
        }
    }
/*
    private Map getDateRangeFromParams(String queryString) {
    try {
        def params = queryString ? parseQueryString(queryString) : [:]
        def now = new Date()
        
        // Parse dates from parameters or use defaults
        def endDate = params.endDate ? 
            Date.parse('yyyy-MM-dd', params.endDate) : 
            now
            
        def startDate = params.startDate ? 
            Date.parse('yyyy-MM-dd', params.startDate) : 
            (now - 30)
            
        return [
            startDate: startDate.format("yyyy-MM-dd"),
            endDate: endDate.format("yyyy-MM-dd")
        ]
    } catch (Exception e) {
        log.error("Error parsing date parameters", e)
        return getDefaultDateRange([:])
    }
}
*/

    @Override
    Boolean show(Instance instance, User user, Account account) {
        // Always show the tab, or implement custom visibility logic
        return true
    }

@Override
ContentSecurityPolicy getContentSecurityPolicy() {
    def csp = new ContentSecurityPolicy()
    csp.scriptSrc = "'self' https://cdn.jsdelivr.net"
    csp.frameSrc = "'self' https://*"
    csp.imgSrc = "'self'"
    csp.styleSrc = "'self' 'unsafe-inline'"  // Add if needed for styles
    return csp
}
}