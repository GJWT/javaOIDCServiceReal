/*
 * Copyright (C) 2018 Google Inc.
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

package org.oidc.service.oidc;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.oidc.common.EndpointName;
import org.oidc.common.HttpMethod;
import org.oidc.common.MissingRequiredAttributeException;
import org.oidc.common.SerializationType;
import org.oidc.common.ServiceName;
import org.oidc.common.ValueException;
import org.oidc.msg.InvalidClaimException;
import org.oidc.msg.Message;
import org.oidc.msg.oidc.RegistrationRequest;
import org.oidc.msg.oidc.RegistrationResponse;
import org.oidc.service.AbstractService;
import org.oidc.service.base.HttpArguments;
import org.oidc.service.base.RequestArgumentProcessingException;
import org.oidc.service.base.RequestArgumentProcessor;
import org.oidc.service.base.ServiceConfig;
import org.oidc.service.base.ServiceContext;
import org.oidc.service.base.processor.AddClientBehaviourPreference;
import org.oidc.service.base.processor.AddJwksUriOrJwks;
import org.oidc.service.base.processor.AddOidcResponseTypes;
import org.oidc.service.base.processor.AddPostLogoutRedirectUris;
import org.oidc.service.base.processor.AddRedirectUris;
import org.oidc.service.base.processor.AddRequestUri;
import org.oidc.service.data.State;

public class Registration extends AbstractService {

  public Registration(ServiceContext serviceContext, State state, ServiceConfig serviceConfig) {
    super(serviceContext, state, serviceConfig);
    this.serviceName = ServiceName.REGISTRATION;
    this.endpointName = EndpointName.REGISTRATION;
    this.requestMessage = new RegistrationRequest();
    this.responseMessage = new RegistrationResponse();
    this.expectedResponseClass = RegistrationResponse.class;
  }

  @Override
  protected ServiceConfig getDefaultServiceConfig() {
    ServiceConfig defaultConfig = new ServiceConfig();
    defaultConfig.setHttpMethod(HttpMethod.POST);
    defaultConfig.setSerializationType(SerializationType.JSON);
    defaultConfig.setDeSerializationType(SerializationType.JSON);
    defaultConfig.setEndpoint(serviceContext.getEndpoints().get(this.endpointName));
    defaultConfig.setPreConstructors((List<RequestArgumentProcessor>) Arrays.asList(
        (RequestArgumentProcessor) new AddClientBehaviourPreference(), new AddRedirectUris(),
        new AddRequestUri(), new AddPostLogoutRedirectUris(), new AddJwksUriOrJwks()));
    defaultConfig
        .setPostConstructors(Arrays.asList((RequestArgumentProcessor) new AddOidcResponseTypes()));
    return defaultConfig;
  }

  @Override
  protected void doUpdateServiceContext(Message response, String stateKey)
      throws MissingRequiredAttributeException, ValueException, InvalidClaimException {
    if (!response.getClaims().containsKey("token_endpoint_auth_method")) {
      response.getClaims().put("token_endpoint_auth_method", "client_secret_basic");
    }
    getServiceContext().setClientId((String) response.getClaims().get("client_id"));
    getServiceContext().setClientSecret((String) response.getClaims().get("client_secret"));
    getServiceContext()
        .setClientSecretExpiresAt((Date) response.getClaims().get("client_secret_expires_at"));
    getServiceContext()
        .setRegistrationAccessToken((String) response.getClaims().get("registration_access_token"));
    getServiceContext().setRegistrationResponse((RegistrationResponse) response);
    // TODO: the behaviour should probably be merged with the client preferences
    getServiceContext().setBehavior((RegistrationResponse) response);
    this.responseMessage = response;
  }

  @Override
  protected Message doConstructRequest(Map<String, Object> requestArguments)
      throws RequestArgumentProcessingException {
    Message response = new RegistrationRequest(requestArguments);
    return response;
  }

  public HttpArguments finalizeGetRequestParameters(HttpArguments httpArguments,
      Map<String, Object> requestArguments) throws RequestArgumentProcessingException {

    // TODO: this or abstract service should check that request contains mandatory fields

    return httpArguments;
  }
}