//Copyright 2025 Exivity B.V.  
//SPDX-License-Identifier: Apache-2.0  

package com.exivity.tab

import com.morpheusdata.core.Plugin
import com.morpheusdata.views.HandlebarsRenderer
import com.morpheusdata.model.Permission
import com.morpheusdata.model.OptionType
import groovy.util.logging.Slf4j

/**
 * Exivity Instance Tab Plugin
 */
class TabPlugin extends Plugin {

	@Override
	String getCode() {
		return 'exivity-tab-plugin'
	}

	@Override
	void initialize() {
		println "Starting TabPlugin initialization" 
		CustomTabProvider customTabProvider = new CustomTabProvider(this, morpheus)
		CustomAnalyticsProvider customAnalyticsProvider = new CustomAnalyticsProvider(this, morpheus)
		this.pluginProviders.put(customAnalyticsProvider.code, customAnalyticsProvider)
		this.pluginProviders.put(customTabProvider.code, customTabProvider)
		// Register controller
        this.controllers.add(new ExivityPluginController(this, morpheus))

		this.setName("Exivity FinOps Plugin")
		this.setPermissions([Permission.build('Exivity Instance Tab','custom-instance-tab', [Permission.AccessType.none, Permission.AccessType.full])])
		this.setRenderer(new HandlebarsRenderer(this.classLoader))

		this.settings << new OptionType(
			name: 'Exivity Endpoint',
			code: 'exivityUrl',
			fieldName: 'exivityUrl',
			defaultValue: 'www.exivity.com',
			displayOrder: 0,
			fieldLabel: 'Exivity API Endpoint',
			helpText: 'Your Exivity instance endpoint',
			required: true,
			inputType: OptionType.InputType.TEXT
		)

		this.settings << new OptionType(
			name: 'Exivity Username',
			code: 'exivityUsername',
			fieldName: 'exivityUsername',
			defaultValue: 'User',
			displayOrder: 2,
			fieldLabel: 'Exivity User Name',
			helpText: 'Exivity User Name',
			required: true,
			inputType: OptionType.InputType.TEXT
		)

		this.settings << new OptionType(
			name: 'Exivity Password',
			code: 'exivityPassword',
			fieldName: 'exivityPassword',
			displayOrder: 3,
			fieldLabel: 'Exivity Password',
			helpText: 'Exivity Password',
			required: true,
			inputType: OptionType.InputType.PASSWORD
		)
		
		this.settings << new OptionType(
			name: 'Report ID',
			code: 'exivityReportID',
			fieldName: 'exivityReportID',
			defaultValue: '1',
			displayOrder: 4,
			fieldLabel: 'Morpheus Report ID',
			helpText: 'Morpheus Report ID in Exivity',
			required: true,
			inputType: OptionType.InputType.TEXT
		)

		this.settings << new OptionType(
			name: 'Report Depth',
			code: 'exivityReportDepth',
			fieldName: 'exivityReportDepth',
			defaultValue: '3',
			displayOrder: 5,
			fieldLabel: 'Morpheus Report Depth',
			helpText: 'Morpheus Instance Report Depth',
			required: true,
			inputType: OptionType.InputType.TEXT
		)

		    // Add date range settings
    	this.settings << new OptionType(
        	name: 'Default Date Range',
        	code: 'defaultDateRange',
        	fieldName: 'defaultDateRange',
        	displayOrder: 6,
        	fieldLabel: 'Default Report Date Range (days)',
        	helpText: 'Number of days to include in reports by default',
        	defaultValue: '30',
        	required: true,
        	inputType: OptionType.InputType.NUMBER
    	)

	}

	@Override
	void onDestroy() {
	}

	@Override
	public List<Permission> getPermissions() {
		return [
			Permission.build('Exivity Tab Plugin', 'customTabPlugin', [Permission.AccessType.full]),
			Permission.build('Exivity API Access', 'exivity-api-access', [Permission.AccessType.full])
		]
	}


}