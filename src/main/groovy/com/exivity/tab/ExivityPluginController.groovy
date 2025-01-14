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
    log.error("RETURNING ROUTES")
    def routes = [
        Route.build("/ExivityPluginController/dates", "updateDates", Permission.build("customTabPlugin", "full")), // /instances/:instanceId/dates
        Route.build("/ExivityPluginController/json", "json", [Permission.build("customTabPlugin", "full")])
    ]
    log.error("Final routes: ${routes}")
    return routes
    }

    def updateDates(ViewModel<Map> model) {
        log.error("Handling date update request")
        try {
            def instanceId = model.parameters.instanceId
            def startDate = model.params.startDate
            def endDate = model.params.endDate
            
            if (!instanceId || !startDate || !endDate) {
                return ServiceResponse.error("Missing required parameters")
            }

            def instance = morpheus.getInstanceService().get(instanceId.toLong()).blockingGet()
            if (!instance) {
                return ServiceResponse.error("Instance not found")
            }

            // Update metadata using CustomTabProvider's method
            def customTabProvider = plugin.getProviderByCode("exivity-custom-tab") as CustomTabProvider
            customTabProvider.saveDatePreference(instance, startDate, endDate)

            return ServiceResponse.success([
                message: "Date preferences updated successfully",
                startDate: startDate,
                endDate: endDate
            ])
        } catch (Exception e) {
            log.error("Error updating dates", e)
            return ServiceResponse.error("Failed to update dates: ${e.message}")
        }
    }


    def json(ViewModel<Map> model) {
        log.error("Handling API data request")
        println model
        model.object.foo = "fizz"
        return JsonResponse.of(model.object)
    }

}