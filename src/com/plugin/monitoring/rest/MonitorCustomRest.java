package com.plugin.monitoring.rest;

import org.glassfish.jersey.server.ResourceConfig;

/**
 * Jersey ResourceConfig voor de Environment Monitoring Plugin.
 *
 * Registreert alle REST-resources van deze plugin.
 * Wordt gerefereerd vanuit web.xml als javax.ws.rs.Application.
 */
public class MonitorCustomRest extends ResourceConfig {

    public MonitorCustomRest() {
        super();
        register(MonitoringConfigResource.class);
    }
}
