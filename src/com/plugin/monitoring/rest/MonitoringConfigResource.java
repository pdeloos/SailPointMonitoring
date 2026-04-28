package com.plugin.monitoring.rest;

import com.plugin.monitoring.service.MonitoringService;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.integration.JsonUtil;
import sailpoint.object.*;
import sailpoint.rest.BaseResource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;


/**
 * REST resource voor de Environment Monitoring Plugin.
 * <p>
 * Endpoints (relatief t.o.v. het servlet-pad in web.xml):
 * GET  /monitoring/config   – huidige configuratie ophalen
 * PUT  /monitoring/config   – configuratie opslaan
 * POST /monitoring/runNow   – checks direct uitvoeren
 */
@Path("monitoring")
public class MonitoringConfigResource extends BaseResource {
  
  private static final Log log = LogFactory.getLog(MonitoringConfigResource.class);
  
  // ── GET config ────────────────────────────────────────────────────────
  @GET
  @Path("config")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getConfig() {
    SailPointContext ctx = null;
    try {
      ctx = SailPointFactory.createContext();
      Custom cfg = ctx.getObjectByName(Custom.class,
              MonitoringService.CONFIG_OBJECT);
      
      if (cfg == null) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Config niet gevonden\"}").build();
      }
      
      // Maskeer wachtwoord voor transport
      Map<String, Object> safe = new LinkedHashMap<>(cfg.getAttributes());
      if (safe.containsKey("webservicePassword")) {
        safe.put("webservicePassword", "********");
      }
      return Response.ok(safe).build();
      
    } catch (Exception e) {
      log.error("getConfig mislukt", e);
      return Response.serverError()
              .entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
    } finally {
      releaseContext(ctx);
    }
  }
  
  // ── PUT config ────────────────────────────────────────────────────────
  @PUT
  @Path("config")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response updateConfig(String body) {
    SailPointContext ctx = null;
    try {
      ctx = SailPointFactory.createContext();
      Custom cfg = ctx.getObjectByName(Custom.class,
              MonitoringService.CONFIG_OBJECT);
      
      if (cfg == null) {
        cfg = new Custom();
        cfg.setName(MonitoringService.CONFIG_OBJECT);
      }
      
      @SuppressWarnings("unchecked")
      Map<String, Object> incoming =
              (Map<String, Object>) JsonUtil.parse(body);
      
      // Bewaar opgeslagen wachtwoord als gemaskeerde waarde binnenkomt
      Object incomingPw = incoming.get("webservicePassword");
      if ("********".equals(incomingPw)) {
        Object stored = cfg.getAttributes() != null
                ? cfg.getAttributes().get("webservicePassword") : null;
        if (stored != null) {
          incoming.put("webservicePassword", stored);
        }
      }
      
      Attributes<String, Object> attrs = new Attributes<>();
      attrs.putAll(incoming);
      cfg.setAttributes(attrs);
      
      ctx.saveObject(cfg);
      ctx.commitTransaction();
      
      return Response.ok("{\"status\":\"opgeslagen\"}").build();
      
    } catch (Exception e) {
      log.error("updateConfig mislukt", e);
      return Response.serverError()
              .entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
    } finally {
      releaseContext(ctx);
    }
  }
  
  // ── POST runNow ───────────────────────────────────────────────────────
  @POST
  @Path("runNow")
  @Produces(MediaType.APPLICATION_JSON)
  public Response runNow() {
    SailPointContext ctx = null;
    try {
      ctx = SailPointFactory.createContext();
      new MonitoringService().execute(ctx);
      return Response.ok("{\"status\":\"gestart\"}").build();
    } catch (Exception e) {
      log.error("runNow mislukt", e);
      return Response.serverError()
              .entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}").build();
    } finally {
      releaseContext(ctx);
    }
  }
  
  // ── Hulpmethoden ──────────────────────────────────────────────────────
  
  private void releaseContext(SailPointContext ctx) {
    if (ctx != null) {
      try {
        SailPointFactory.releaseContext(ctx);
      } catch (Exception ignore) {
      }
    }
  }
  
  private String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
