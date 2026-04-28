package com.plugin.monitoring.service;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;

/**
 * Sends the rendered monitoring payload to an external Web Service API via HTTP POST.
 * <p>
 * Authentication: HTTP Basic (username + password from plugin configuration).
 * Content-Type defaults to application/json but can be overridden.
 */
public class WebServiceSenderService {
  
  private static final Log log = LogFactory.getLog(WebServiceSenderService.class);
  
  private final String baseUrl;
  private final String username;
  private final String password;
  private final String contentType;
  private final int timeoutSeconds;
  
  public WebServiceSenderService(String baseUrl,
                                 String username,
                                 String password) {
    this(baseUrl, username, password, "application/json", 30);
  }
  
  public WebServiceSenderService(String baseUrl,
                                 String username,
                                 String password,
                                 String contentType,
                                 int timeoutSeconds) {
    this.baseUrl = baseUrl;
    this.username = username;
    this.password = password;
    this.contentType = contentType;
    this.timeoutSeconds = timeoutSeconds;
  }
  
  /**
   * POST {@code body} to the configured endpoint.
   *
   * @param body fully-rendered request body (after Velocity processing)
   * @throws WebServiceException if the HTTP status is not 2xx
   */
  public void send(String body) throws WebServiceException {
    CloseableHttpClient client = null;
    try {
      RequestConfig cfg = RequestConfig.custom()
              .setConnectTimeout(timeoutSeconds * 1000)
              .setSocketTimeout(timeoutSeconds * 1000)
              .build();
      
      client = HttpClients.custom()
              .setDefaultRequestConfig(cfg)
              .build();
      
      HttpPost post = new HttpPost(baseUrl);
      
      // Basic auth header
      String credentials = username + ":" + password;
      String encoded = Base64.encodeBase64String(
              credentials.getBytes(StandardCharsets.UTF_8));
      post.setHeader("Authorization", "Basic " + encoded);
      post.setHeader("Content-Type", contentType);
      
      post.setEntity(new StringEntity(body + "\n",
              ContentType.create(contentType, StandardCharsets.UTF_8)));
      
      HttpResponse response = client.execute(post);
      int statusCode = response.getStatusLine().getStatusCode();
      String responseBody = "";
      if (response.getEntity() != null) {
        responseBody = EntityUtils.toString(response.getEntity(),
                StandardCharsets.UTF_8);
      }
      
      if (statusCode < 200 || statusCode >= 300) {
        throw new WebServiceException(
                "HTTP " + statusCode + " from " + baseUrl
                        + ". Response: " + responseBody);
      }
      
      log.info("Monitoring payload sent successfully to " + baseUrl
              + " (HTTP " + statusCode + ")");
      
    } catch (WebServiceException wse) {
      throw wse;
    } catch (Exception e) {
      throw new WebServiceException(
              "Failed to send monitoring payload to " + baseUrl
                      + ": " + e.getMessage(), e);
    } finally {
      if (client != null) {
        try {
          client.close();
        } catch (Exception ignore) {
        }
      }
    }
  }
  
  // -------------------------------------------------------
  public static class WebServiceException extends Exception {
    public WebServiceException(String msg) {
      super(msg);
    }
    
    public WebServiceException(String msg, Throwable t) {
      super(msg, t);
    }
  }
}
