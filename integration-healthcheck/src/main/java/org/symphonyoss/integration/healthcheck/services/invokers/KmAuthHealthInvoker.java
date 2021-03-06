package org.symphonyoss.integration.healthcheck.services.invokers;

import static org.symphonyoss.integration.healthcheck.properties.HealthCheckProperties.IO_EXCEPTION;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;
import org.symphonyoss.integration.authentication.api.enums.ServiceName;
import org.symphonyoss.integration.healthcheck.services.IntegrationBridgeServiceInfo;
import org.symphonyoss.integration.healthcheck.services.indicators.ServiceHealthIndicator;
import org.symphonyoss.integration.json.JsonUtils;

import java.io.IOException;

/**
 * Service health invoker for Key Manager Authentication.
 *
 * Created by luanapp on 14/01/19.
 */
@Component
public class KmAuthHealthInvoker extends AuthenticationServiceHealthInvoker {

  private static final Logger LOG = LoggerFactory.getLogger(KmAuthHealthInvoker.class);
  private static final String SERVICE_FIELD = "keyauth";
  private static final String HC_AGGREGATED_URL_PATH = "/webcontroller/HealthCheck/aggregated";
  private static final String HC_URL_PATH = "/HealthCheck/aggregated";

  @Autowired
  @Qualifier("kmAuthHealthIndicator")
  private ServiceHealthIndicator healthIndicator;

  @Override
  protected String getHealthCheckUrl() {
    return properties.getKeyManagerUrl() + HC_URL_PATH;
  }

  @Override
  protected ServiceHealthIndicator getHealthIndicator() {
    return healthIndicator;
  }

  @Override
  protected String getServiceBaseUrl() {
    return properties.getKeyManagerAuthUrl();
  }

  @Override
  protected String getServiceField() {
    return SERVICE_FIELD;
  }

  @Override
  protected void handleHealthResponse(IntegrationBridgeServiceInfo service, String healthResponse) {
    JsonNode jsonNode = parseJsonResponse(healthResponse);

    if (jsonNode == null) {
      jsonNode = readAggregatedHC();
    }

    Status status = retrieveConnectivityStatus(jsonNode);
    service.setConnectivity(status);
  }

  @Override
  protected ServiceName getServiceName() {
    return ServiceName.KEY_MANAGER;
  }

  @Override
  protected String getFriendlyServiceName() {
    return ServiceName.KEY_MANAGER_AUTH.toString();
  }

  @Override
  protected String getMinVersion() {
    if (currentVersion != null) {
      return properties.getKeyManagerAuth().getMinVersion();
    }

    return null;
  }

  /**
   * Parse JSON HC response
   * @param healthResponse HTTP response payload
   * @return JSON object or null if it's not a valid JSON object
   */
  private JsonNode parseJsonResponse(String healthResponse) {
    try {
      return JsonUtils.readTree(healthResponse);
    } catch (IOException e) {
      LOG.error(logMessageSource.getMessage(IO_EXCEPTION, getServiceName().toString()));
    }

    return null;
  }

  /**
   * Reads aggregated health-check on the POD
   * @return POD aggregated JSON object or null if the application cannot retrieve POD HC
   */
  private JsonNode readAggregatedHC() {
    String aggregatedResponse =
        getHealthResponse(properties.getSymphonyUrl() + HC_AGGREGATED_URL_PATH);

    if (aggregatedResponse != null) {
      return parseJsonResponse(aggregatedResponse);
    }

    return null;
  }

  /**
   * -   * Retrieve connectivity status according to the health-check JSON object
   * -   * @param jsonNode Health-check JSON object
   * -   * @return Connectivity status
   * -
   */
  private Status retrieveConnectivityStatus(JsonNode jsonNode) {
    if (jsonNode == null) {
      return Status.DOWN;
    }

    boolean serviceField = jsonNode.path(getServiceField()).asBoolean();

    if (serviceField) {
      return Status.UP;
    }

    return Status.DOWN;
  }


}
