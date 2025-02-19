//Copyright 2025 Exivity B.V.  
//SPDX-License-Identifier: Apache-2.0  

package com.exivity.tab

import com.morpheusdata.core.Plugin
import com.morpheusdata.web.PluginController
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.model.Permission
import com.morpheusdata.views.ViewModel
import com.morpheusdata.web.Route
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.JsonResponse
import com.morpheusdata.views.HTMLResponse
import groovy.util.logging.Slf4j

@Slf4j
class ExivityPluginController implements PluginController {
    Plugin plugin
    MorpheusContext morpheus
    private static Map<String, Map> datePreferences = [
        instances: [:],
        reports: [:]
    ] // Static map to store date preferences


    ExivityPluginController(Plugin plugin, MorpheusContext morpheus) {
        this.plugin = plugin
        this.morpheus = morpheus
    }

    String code = "exivity-plugin-controller"
    String name = "Exivity Plugin Controller"

    @Override
    String getCode() {
        return code
    }

    @Override
    String getName() {
        return name
    }


    List<Route> getRoutes() {
    log.info("RETURNING ROUTES")
    def routes = [
        Route.build("/ExivityPluginController/dates", "updateDates", Permission.build("customTabPlugin", "full")) // /instances/:instanceId/dates
    ]

    return routes
    }

    def updateDates(ViewModel<Map> model) {
        log.info("Handling date update request")
        try {
            def startDate = model.request.getParameter('startDate')
            def endDate = model.request.getParameter('endDate')
            def instanceId = model.request.getParameter('instanceId')
            def reportCode = model.request.getParameter('reportCode')
            
            log.info("startDate is '${startDate}' endDate  is '${endDate}' instanceId is '${instanceId}' reportCode is '${reportCode}'")

            if (!startDate || !endDate) {
                return ServiceResponse.error("Missing required parameters: startDate and endDate")
            }

            if (!instanceId && !reportCode) {
                return ServiceResponse.error("Either instanceId or reportCode must be provided")
            }

            // Validate date format
            try {
                Date.parse('yyyy-MM-dd', startDate)
                Date.parse('yyyy-MM-dd', endDate)
            } catch (Exception e) {
                return ServiceResponse.error("Invalid date format. Use YYYY-MM-DD")
            }

            // Store dates based on the context
            if (instanceId) {
                datePreferences.instances[instanceId] = [startDate: startDate, endDate: endDate]
                log.info("Stored dates for instance ${instanceId}: start=${startDate}, end=${endDate}")
            }
            
            if (reportCode) {
                datePreferences.reports[reportCode] = [startDate: startDate, endDate: endDate]
                log.info("Stored dates for report ${reportCode}: start=${startDate}, end=${endDate}")
            }

            return ServiceResponse.success([
                success: true,
                message: "Date range updated successfully",
                startDate: startDate,
                endDate: endDate
            ])
        } catch (Exception e) {
            log.error("Error updating dates", e)
            return ServiceResponse.error("Failed to update dates: ${e.message}")
        }
    }

    // Update the helper method to accept Map parameter
    static Map getStoredDates(Map params = [:]) {
        if (params.instanceId) {
            return datePreferences.instances[params.instanceId]
        } else if (params.reportCode) {
            return datePreferences.reports[params.reportCode]
        }
        return null
    }

}