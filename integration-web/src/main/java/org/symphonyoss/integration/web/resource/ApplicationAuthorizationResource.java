/**
 * Copyright 2016-2017 Symphony Integrations - Symphony LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.symphonyoss.integration.web.resource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.symphonyoss.integration.Integration;
import org.symphonyoss.integration.authentication.AuthenticationProxy;
import org.symphonyoss.integration.authorization.UserAuthorizationData;
import org.symphonyoss.integration.authentication.jwt.JwtAuthentication;
import org.symphonyoss.integration.exception.RemoteApiException;
import org.symphonyoss.integration.exception.authentication.UnauthorizedUserException;
import org.symphonyoss.integration.logging.LogMessageSource;
import org.symphonyoss.integration.model.yaml.AppAuthorizationModel;
import org.symphonyoss.integration.pod.api.client.IntegrationAuthApiClient;
import org.symphonyoss.integration.pod.api.client.PodHttpApiClient;
import org.symphonyoss.integration.service.IntegrationBridge;
import org.symphonyoss.integration.web.exception.IntegrationUnavailableException;
import org.symphonyoss.integration.web.model.ErrorResponse;

/**
 * REST endpoint to handle requests for manage application authentication data.
 *
 * Created by rsanchez on 24/07/17.
 */
@RestController
@RequestMapping("/v1/application/{configurationId}/authorization")
public class ApplicationAuthorizationResource {

  public static String INTEGRATION_UNAVAILABLE = "integration.web.integration.unavailable";

  public static String INTEGRATION_UNAVAILABLE_SOLUTION = INTEGRATION_UNAVAILABLE + ".solution";

  private final IntegrationBridge integrationBridge;

  private final LogMessageSource logMessage;

  private final AuthenticationProxy authenticationProxy;

  private final IntegrationAuthApiClient apiClient;

  private final JwtAuthentication jwtAuthentication;

  public ApplicationAuthorizationResource(IntegrationBridge integrationBridge, LogMessageSource logMessage,
      PodHttpApiClient client, AuthenticationProxy authenticationProxy,
      JwtAuthentication jwtAuthentication) {
    this.integrationBridge = integrationBridge;
    this.logMessage = logMessage;
    this.authenticationProxy = authenticationProxy;
    this.jwtAuthentication = jwtAuthentication;

    this.apiClient = new IntegrationAuthApiClient(client, logMessage);
  }

  /**
   * Get authentication properties according to the application identifier.
   *
   * @param configurationId Application identifier
   * @return Authentication properties
   */
  @GetMapping
  public ResponseEntity<AppAuthorizationModel> getAuthorizationProperties(@PathVariable String configurationId) {
    Integration integration = this.integrationBridge.getIntegrationById(configurationId);

    if (integration == null) {
      return ResponseEntity.notFound().build();
    }

    AppAuthorizationModel authenticationModel = integration.getAuthorizationModel();

    if (authenticationModel == null) {
      return ResponseEntity.noContent().build();
    }

    return ResponseEntity.ok().body(authenticationModel);
  }

  /**
   * Get user authentication data according to the application identifier and integration URL.
   *
   * @param configurationId Application identifier
   * @param integrationURL Integration URL
   * @return User authentication data if the user is authenticated or HTTP 401 (Unauthorized) otherwise.
   */
  @GetMapping("/userSession")
  public ResponseEntity getUserAuthorizationData(@PathVariable String configurationId,
      @RequestParam(name = "url") String integrationURL,
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader)
      throws RemoteApiException {
    Long userId = jwtAuthentication.getUserIdFromAuthorizationHeader(authorizationHeader);

    Integration integration = this.integrationBridge.getIntegrationById(configurationId);

    if (integration == null) {
      String message = logMessage.getMessage(INTEGRATION_UNAVAILABLE, configurationId);
      String solution = logMessage.getMessage(INTEGRATION_UNAVAILABLE_SOLUTION);
      throw new IntegrationUnavailableException(message, solution);
    }

    String sessionToken = authenticationProxy.getSessionToken(integration.getSettings().getType());

    UserAuthorizationData authorizationData =
        apiClient.getUserAuthData(sessionToken, configurationId, userId, integrationURL);

    if (authorizationData == null) {
      authorizationData = new UserAuthorizationData(userId, integrationURL);
    }

    try {
      integration.verifyUserAuthorizationData(authorizationData);

      return ResponseEntity.ok().body(authorizationData);
    } catch (UnauthorizedUserException e) {
      ErrorResponse response = new ErrorResponse();
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setMessage(e.getMessage());
      response.setProperties(authorizationData.getData());

      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
  }

}
