package com.exivity.tab

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.User
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.core.providers.AbstractAnalyticsProvider
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.util.logging.Slf4j
import com.morpheusdata.model.ContentSecurityPolicy

@Slf4j
class CustomAnalyticsProvider extends AbstractAnalyticsProvider {
    Plugin plugin
    MorpheusContext morpheusContext

    CustomAnalyticsProvider(Plugin plugin, MorpheusContext context) {
        this.plugin = plugin
        this.morpheusContext = context
    }

    @Override
    String getCode() {
        return "finops-analytics"
    }

    @Override
    String getName() {
        return "FinOps"
    }

    @Override
    String getCategory() {
        return "FinOps Analytics"
    }

    @Override
    String getDescription() {
        return "Exivity FinOps Analytics Dashboard"
    }

    @Override
    Boolean getMasterTenantOnly() {
        return false
    }

	@Override
	Boolean getSubTenantOnly() {
		return false
	}

    @Override
    Integer getDisplayOrder() {
        return 0
    }

    @Override
    ServiceResponse<Map<String, Object>> loadData(User user, Map<String, Object> opts) {
        try {
            def settingsObservable = morpheus.getSettings(plugin)
            def settingsOutput = settingsObservable.blockingGet()
            def settingsJson = new JsonSlurper().parseText(settingsOutput)
            
            // Get Exivity settings
            String exivityUrl = settingsJson?.exivityUrl ?: 'https://www.exivity.com'
            if (!exivityUrl.startsWith('http')) {
                exivityUrl = "https://${exivityUrl}"
            }
            exivityUrl = exivityUrl.replaceAll('/+$', '')
            
            String exivityUsername = settingsJson?.exivityUsername ?: ''
            String exivityPassword = settingsJson?.exivityPassword ?: ''
            String reportId = settingsJson?.exivityReportID ?: ''

            // Get default date range (1 year)
            def endDate = new Date().format("yyyy-MM-dd")
            def startDate = (new Date() - 365).format("yyyy-MM-dd")
            
            // Authenticate with Exivity
            def customTabProvider = plugin.getProviderByCode("exivity-custom-tab") as CustomTabProvider
            def authResult = customTabProvider.authenticateWithExivityAPI(exivityUrl, exivityUsername, exivityPassword)
            
            def reportData = null
            def reportError = null
            def chartData = [:]
            
            if (authResult.success && reportId) {
                def reportResult = customTabProvider.fetchReportData(authResult.token, reportId, exivityUrl, startDate, endDate)
                if (reportResult.success) {
                    reportData = reportResult.data
                    
                    // Calculate total cost by service
                    def serviceCosts = [:] // Map to store service totals
                    reportData.meta.report.each { entry ->
                        if (entry.service_description && entry.total_charge) {
                            def serviceName = entry.service_description
                            def cost = entry.total_charge as BigDecimal
                            serviceCosts[serviceName] = (serviceCosts[serviceName] ?: 0.0) + cost
                        }
                    }
                    
                    // Format data for Google Charts
                    def chartRows = []
                    serviceCosts.each { service, cost ->
                        chartRows << [service, cost]
                    }
                    
                    def chartOptions = [
                        legend: "none",
                        sliceVisibilityThreshold: 0.005,
                        chartArea: [width: "80%", height: "80%"],
                        colors: ["#396ab1", "#da7c30", "#3e9651", "#cc2529", "#535154", "#6b4c9a", "#922428", "#948b3d"]
                    ]
                    
                    chartData = [
                        data: [["Service", "Cost"]] + chartRows,
                        options: chartOptions
                    ]
                } else {
                    reportError = reportResult.errorMessage
                }
            }

            // Convert the chart data to a JSON string using JsonBuilder
            def jsonBuilder = new JsonBuilder(chartData)
            String formattedChartData = jsonBuilder.toString()

            return ServiceResponse.success([
                exivityUrl: exivityUrl,
                exivityUsername: exivityUsername,
                authSuccess: authResult.success,
                authErrorMessage: authResult.errorMessage,
                authResponseCode: authResult.responseCode,
                startDate: startDate,
                endDate: endDate,
                reportData: reportData,
                reportError: reportError,
                chartData: formattedChartData,
                serviceCosts: chartData.data ? chartData.data[1..-1] : [] // Skip header row
            ])
        } catch (Exception e) {
            log.error("Error loading analytics data", e)
            return ServiceResponse.error("Failed to load analytics data: ${e.message}")
        }
    }

    @Override
    HTMLResponse renderTemplate(User user, Map<String, Object> data, Map<String, Object> opts) {
        ViewModel<Map> model = new ViewModel<>()
        model.object = data
        return getRenderer().renderTemplate("hbs/analyticsTab", model)
    }

    @Override
    MorpheusContext getMorpheus() {
        return morpheusContext
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