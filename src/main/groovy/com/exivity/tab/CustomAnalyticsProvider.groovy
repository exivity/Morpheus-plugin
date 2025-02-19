//Copyright 2025 Exivity B.V.  
//SPDX-License-Identifier: Apache-2.0  


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
import com.morpheusdata.core.data.DataQuery

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Template
import com.morpheusdata.views.Renderer
import com.morpheusdata.views.HandlebarsRenderer

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

    void initialize() {
        plugin.registerProvider(this)
        // Register the nonce helper with the renderer
        getRenderer().registerNonceHelper(morpheus.getWebRequest())
        log.info("FinOps Analytics Provider initialized")
    }

    private Map calculateAnalyticMetrics(def reportData) {
        def metrics = [
            uniqueInstances: 0,
            uniqueServices : 0,
            totalCOGS    : 0.0,
            totalCharges : 0.0
        ]

        if (reportData?.meta?.report) {
            // Get unique instance accounts
            def uniqueAccounts = reportData.meta.report.collect { it.account_key }.unique()
            metrics.uniqueInstances = uniqueAccounts.size()

            // Get unique services
            def uniqueServices = reportData.meta.report.collect { it.service_description }.unique()
            metrics.uniqueServices = uniqueServices.size()

            // Calculate sums
            reportData.meta.report.each { entry ->
                if (entry.total_cogs) {
                    metrics.totalCOGS += (entry.total_cogs as BigDecimal)
                }
                if (entry.total_charge) {
                    metrics.totalCharges += (entry.total_charge as BigDecimal)
                }
            }

            // Round to 2 decimal places
            metrics.totalCOGS = metrics.totalCOGS.setScale(2, BigDecimal.ROUND_HALF_UP)
            metrics.totalCharges = metrics.totalCharges.setScale(2, BigDecimal.ROUND_HALF_UP)
        }

        return metrics
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

            // Get stored dates or use defaults
            def storedDates = ExivityPluginController.getStoredDates(reportCode: 'finops-analytics')
            log.info("storedDates is: ${storedDates}")
            def endDate = storedDates?.endDate ?: new Date().format("yyyy-MM-dd")
            def startDate = storedDates?.startDate ?: new Date().clearTime().copyWith(month: 0, date: 1).format("yyyy-MM-dd")

            log.info("stardate is: ${startDate} enddate is: ${endDate}")

            // Authenticate with Exivity
            def customTabProvider = plugin.getProviderByCode("exivity-custom-tab") as CustomTabProvider
            def authResult = customTabProvider.authenticateWithExivityAPI(exivityUrl, exivityUsername, exivityPassword)

            def reportData = null
            def reportError = null
            def chartData = [:]
            def metrics = [:]
            def categoryChartData = [:] // Declare categoryChartData here

            if (authResult.success && reportId) {
                def reportResult = customTabProvider.fetchReportData(authResult.token, reportId, exivityUrl, startDate, endDate)

                if (reportResult.success) {
                    reportData = reportResult.data

                    // Calculate metrics
                    metrics = calculateAnalyticMetrics(reportData)

                    // Calculate total cost by service for chart
                    def serviceCosts = [:] // Map to store service totals
                    reportData.meta.report.each { entry ->
                        if (entry.service_description && entry.total_charge) {
                            def serviceName = entry.service_description
                            def cost = entry.total_charge as BigDecimal
                            serviceCosts[serviceName] = (serviceCosts[serviceName] ?: 0.0) + cost
                        }
                    }

                    // Calculate total cost by service for chart
                    def categoryCosts = [:] // Map to store service totals
                    reportData.meta.report.each { entry ->
                        if (entry.servicecategory_name && entry.total_charge) {
                            def categoryName = entry.servicecategory_name
                            def cost = entry.total_charge as BigDecimal
                            categoryCosts[categoryName] = (categoryCosts[categoryName] ?: 0.0) + cost
                        }
                    }

                    // Format data for Google Charts
                    def chartRows = []
                    serviceCosts.each { service, cost ->
                        chartRows << [service, cost]
                    }

                    def chartOptions = [
                        legend                 : "none",
                        sliceVisibilityThreshold: 0.005,
                        chartArea              : [width: "80%", height: "80%"],
                        colors                 : ["#396ab1", "#da7c30", "#3e9651", "#cc2529", "#535154", "#6b4c9a", "#922428", "#948b3d"]
                    ]

                    chartData = [
                        data   : [["Service", "Cost"]] + chartRows,
                        options: chartOptions
                    ]

                    // Format data for Google Charts for category costs
                    def categoryChartRows = []
                    categoryCosts.each { category, cost ->
                        categoryChartRows << [category, cost]
                    }

                    categoryChartData = [
                        data   : [["Category", "Cost"]] + categoryChartRows,
                        options: chartOptions
                    ]
                } else {
                    reportError = reportResult.errorMessage
                }
            }

            // Convert the chart data to JSON strings
            String formattedChartData = new JsonBuilder(chartData).toString()
            String formattedCategoryChartData = new JsonBuilder(categoryChartData).toString()

            return ServiceResponse.success([
                exivityUrl       : exivityUrl,
                exivityUsername  : exivityUsername,
                authSuccess      : authResult.success,
                authErrorMessage : authResult.errorMessage,
                authResponseCode : authResult.responseCode,
                startDate        : startDate,
                endDate          : endDate,
                reportData       : reportData,
                reportError      : reportError,
                chartData        : formattedChartData,
                serviceCosts     : chartData.data ? chartData.data[1..-1] : [],
                categoryChartData: formattedCategoryChartData,
                categoryCosts    : categoryChartData.data ? categoryChartData.data[1..-1] : [],
                metrics          : metrics
            ])
        } catch (Exception e) {
            log.error("Error loading analytics data", e)
            return ServiceResponse.error("Failed to load analytics data: ${e.message}")
        }
    }

    @Override
    HTMLResponse renderTemplate(User user, Map<String, Object> data, Map<String, Object> opts) {
        ViewModel<Map> model = new ViewModel<>()

        // Get nonce from web request service
        String nonce = morpheus.getWebRequest().getNonceToken()
        data.nonce = nonce

        model.object = data
        log.info("Rendering analytics tab with nonce: ${nonce}")

        return getRenderer().renderTemplate("hbs/analyticsTab", model)
    }

    @Override
    MorpheusContext getMorpheus() {
        return morpheusContext
    }
}
