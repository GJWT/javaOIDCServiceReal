package org.oidc.service;

import com.auth0.msg.ClaimType;
import com.auth0.msg.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import java.util.Map;
import org.oidc.common.AddedClaims;
import org.oidc.common.ClientAuthenticationMethod;
import org.oidc.common.EndpointName;
import org.oidc.common.HttpMethod;
import org.oidc.common.OidcServiceException;
import org.oidc.common.ResponseException;
import org.oidc.common.SerializationType;
import org.oidc.common.ServiceName;
import org.oidc.common.UnsupportedSerializationTypeException;
import org.oidc.service.base.HttpArguments;
import org.oidc.service.base.HttpHeader;
import org.oidc.service.base.ServiceConfig;
import org.oidc.service.base.ServiceContext;
import org.oidc.service.data.State;
import org.oidc.service.util.ServiceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the base class for all services and provides default implementation for various methods.
 */
public abstract class AbstractService implements Service {

    /**
     * Message that describes the request.
     */
    protected Message requestMessage;

    /**
     * Message that describes the response.
     */
    protected Message responseMessage;

    /**
     * The name used for the endpoint in provider information discovery
     */
    protected EndpointName endpointName;

    /**
     * True if the response will be returned as a direct response to the request.
     * The only exception right now to this is the Authorization request where the
     * response is delivered to the client at some later date. Default is True
     */
    protected boolean isSynchronous = true;

    /**
     * ServiceName - enum (A name of the service. Later when a RP/client is
     * implemented instances of different services are found by using this name.
     * No default)
     */
    protected ServiceName serviceName;

    /**
     * Client authentication method - defined in enum ClientAuthenticationMethod
     * (The client authentication method to use if nothing else is specified.
     * Default is '' which means none.)
     */
    protected ClientAuthenticationMethod defaultAuthenticationMethod = ClientAuthenticationMethod.NONE;

    /**
     * HttpMethod - enum (Which HTTP method to use when sending the request. Default
     * is GET)
     */
    protected HttpMethod httpMethod = HttpMethod.GET;

    /**
     * SerializationType - enum (The serialization method to be used for the
     * request. Default is urlencoded)
     */
    protected SerializationType serializationType = SerializationType.URL_ENCODED;

    /**
     * The deserialization method to use on the response. Default is json
     */
    protected SerializationType deserializationType = SerializationType.JSON;

    /**
     * Additional configuration arguments that could be used to change default values
     * like ClientAuthenticationMethod or add extra parameters to pre/postConstruct methods.
     */
    protected ServiceConfig config;

    /**
     * The actual URL provided in provider information discovery.
     */
    private String endpoint = "";

    /**
     * Serves as an in-memory cache
     */
    protected State state;

    /**
     * It contains information that a client needs to talk to a server. This is shared by various services.
     */
    protected ServiceContext serviceContext;

    /**
     * Arguments to be used by the preConstruct methods
     */
    private Map<String,String> preConstruct;

    /**
     * Arguments to be used by the postConstruct methods
     */
    private Map<String,String> postConstruct;

    /**
     * Configuration that is specific to every service
     */
    protected ServiceConfig serviceConfig;

    /**
     * Additional claims
     */
    private AddedClaims addedClaims;

    /**
     * Constants
     */
    private static final String HTTP_METHOD = "httpMethod";
    private static final String AUTHENTICATION_METHOD = "authenticationMethod";
    private static final String SERIALIZATION_TYPE = "serializationType";

    private static final Logger logger = LoggerFactory.getLogger(AbstractService.class);

    /**
     * @param serviceContext It contains information that a client needs to talk to a server.
     *                       This is shared by various services.
     * @param state Serves as an in-memory cache
     * @param config Configuration that is specific to every service
     */
    public AbstractService(ServiceContext serviceContext,
                           State state,
                           ServiceConfig config,
                           AddedClaims addedClaims) {
        this.serviceContext = serviceContext;
        this.state = state;
        this.config = config;
        this.addedClaims = addedClaims;
    }

    public AbstractService() {

    }

    /**
     This method will run after the response has been parsed and verified.  It requires response and
     stateKey in order for the service context to be updated.  StateKey is used to fetch and update
     the appropriate State associated with a specific service. This method may update certain attributes
     of the service context such as issuer, clientId, or clientSecret.

     * @param response the response as a Message instance
     * @param stateKey the key that identifies the State object
     **/
    public abstract void updateServiceContext(
            Message response,
            String stateKey);

    /**
     * This method will run after the response has been parsed and verified.  It requires response
     * in order for the service context to be updated.  This method may update certain attributes
     * of the service context such as issuer, clientId, or clientSecret.  This method does not require
     * a stateKey since it is used for services that are not expected to store state in the state DB.

     * @param response the response as a Message instance
     */
    public abstract void updateServiceContext(Message response);

    /**
     This the start of a pipeline that will:

     - Deserializes a response into its response message class.
     - verifies the correctness of the response by running the
     verify method belonging to the message class used.
     * @param responseBody The response, can be either in a JSON or an urlencoded format
     * @param serializationType  which serialization that was used
     * @param stateKey the key that identifies the State object
     * @return the parsed and to some extent verified response
     **/
    public Message parseResponse(String responseBody, SerializationType serializationType, String stateKey) throws Exception {
        if(serializationType == null) {
            serializationType = this.serializationType;
        }

        String urlInfo = null;
        if(SerializationType.URL_ENCODED.equals(serializationType)) {
            urlInfo = ServiceUtil.getUrlInfo(responseBody);
        }

        Message response = null;
        try {
            if(SerializationType.URL_ENCODED.equals(serializationType)) {
                response = this.responseMessage.fromUrlEncoded(urlInfo);
            } else if(SerializationType.JSON.equals(serializationType)) {
                response = this.responseMessage.fromJson(urlInfo);
            }
        } catch (Exception e) {
            logger.error("Error while deserializing");
            throw e;
        }

        if(response != null && response.getError() == null) {
            response.addClaim(ClaimType.CLIENT_ID, this.serviceContext.getClientId());
            response.addClaim(ClaimType.ISSUER, this.serviceContext.getIssuer());
            response.addClaim(ClaimType.KEY_JAR, this.serviceContext.getKeyJar());
            response.addClaim(ClaimType.SHOULD_VERIFY, true);

            boolean isSuccessful;
            try {
                isSuccessful = response.verify();
            } catch (Exception e) {
                logger.error("Exception while verifying response");
                throw e;
            }

            if(!isSuccessful) {
                logger.error("Verification of the response failed");
                throw new OidcServiceException("Verification of the response failed");
            }

            //TODO
            /*
            if(response instanceof AuthorizationResponse && Strings.isNullOrEmpty(response.getScope())) {
                response.setScope(addedClaims.getScope());
            }*/

            response = this.postParseResponse(response, stateKey);
        }

        if(response == null) {
            throw new ResponseException("Missing or faulty response");
        }

        return response;
    }

    public Message postParseResponse(Message responseMessage, String stateKey) {
        return null;
    }

    /**
     This the start of a pipeline that will:

     - Deserializes a response into its response message class.
     - verifies the correctness of the response by running the
     verify method belonging to the message class used.
     * @param responseBody The response, can be either in a JSON or an urlencoded format
     * @return the parsed and to some extent verified response
     **/
    public Message parseResponse(String responseBody) throws Exception {
        return parseResponse(responseBody, SerializationType.JSON, "");
    }

    /**
     This the start of a pipeline that will:

     - Deserialize a response into its message class.
     - verifies the correctness of the response by running the
     verify method belonging to the message class used.
     * @param responseBody The response, can be either in a JSON or an urlencoded format
     * @param serializationType  which serialization that was used
     * @return the parsed and to some extent verified response
     **/
    public Message parseResponse(
            String responseBody, SerializationType serializationType) throws Exception {
        return parseResponse(responseBody, serializationType, "");
    }

    /**
     * Builds the request message and constructs the HTTP headers.

     This is the starting pont for a pipeline that will:

     - construct the request message
     - add/remove information to/from the request message in the way a
     specific client authentication method requires.
     - gather a set of HTTP headers like Content-type and Authorization.
     - serialize the request message into the necessary format (JSON,
     urlencoded, signed JWT)
     * @param requestArguments
     * @return HttpArguments
     */
    public HttpArguments getRequestParameters(Map<String,String> requestArguments) throws UnsupportedSerializationTypeException, JsonProcessingException {
        if(requestArguments == null) {
            throw new IllegalArgumentException("null requestArguments");
        }

        if(Strings.isNullOrEmpty(requestArguments.get(HTTP_METHOD))) {
            requestArguments.put(HTTP_METHOD, this.httpMethod.name());
        }

        if(Strings.isNullOrEmpty(requestArguments.get(AUTHENTICATION_METHOD))) {
            requestArguments.put(AUTHENTICATION_METHOD, this.defaultAuthenticationMethod.name());
        }

        if(Strings.isNullOrEmpty(requestArguments.get(SERIALIZATION_TYPE))) {
            requestArguments.put(SERIALIZATION_TYPE, this.serializationType.name());
        }

        Message request = constructRequest();

        HttpArguments httpArguments = new HttpArguments();
        httpArguments.setHttpMethod(httpMethod);

        AddedClaims addedClaimsCopy = new AddedClaims.AddedClaimsBuilder().setAddedClaims(addedClaims);
        if(!Strings.isNullOrEmpty(this.serviceContext.getIssuer())) {
            addedClaimsCopy.buildAddedClaimsBuilder().setIssuer(this.serviceContext.getIssuer()).buildAddedClaims();
        }

        SerializationType contentType;
        HttpHeader httpHeader = null;
        if(HttpMethod.POST.equals(requestArguments.get(HTTP_METHOD))) {
            if(SerializationType.URL_ENCODED.equals(serializationType)) {
                contentType = SerializationType.URL_ENCODED;
            } else {
                contentType = SerializationType.JSON;
            }

            httpArguments.setBody(ServiceUtil.getHttpBody(request, contentType));
            httpHeader.setContentType(contentType.name());
        }

        if(httpHeader != null) {
            httpArguments.setHeader(httpHeader);
        }

        return httpArguments;
    }

    public Message constructRequest() {
        return null;
    }

    public Message getRequestMessage() {
        return requestMessage;
    }

    public void setRequestMessage(Message requestMessage) {
        this.requestMessage = requestMessage;
    }

    public Message getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(Message responseMessage) {
        this.responseMessage = responseMessage;
    }

    public EndpointName getEndpointName() {
        return endpointName;
    }

    public void setEndpointName(EndpointName endpointName) {
        this.endpointName = endpointName;
    }

    public boolean isSynchronous() {
        return isSynchronous;
    }

    public void setSynchronous(boolean synchronous) {
        isSynchronous = synchronous;
    }

    public ServiceName getServiceName() {
        return serviceName;
    }

    public void setServiceName(ServiceName serviceName) {
        this.serviceName = serviceName;
    }

    public ClientAuthenticationMethod getDefaultAuthenticationMethod() {
        return defaultAuthenticationMethod;
    }

    public void setDefaultAuthenticationMethod(ClientAuthenticationMethod defaultAuthenticationMethod) {
        this.defaultAuthenticationMethod = defaultAuthenticationMethod;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public static String getAuthenticationMethod() {
        return AUTHENTICATION_METHOD;
    }

    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public SerializationType getSerializationType() {
        return serializationType;
    }

    public void setSerializationType(SerializationType serializationType) {
        this.serializationType = serializationType;
    }

    public SerializationType getDeserializationType() {
        return deserializationType;
    }

    public void setDeserializationType(SerializationType deserializationType) {
        this.deserializationType = deserializationType;
    }

    public ServiceConfig getConfig() {
        return config;
    }

    public void setConfig(ServiceConfig config) {
        this.config = config;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public ServiceContext getServiceContext() {
        return serviceContext;
    }

    public void setServiceContext(ServiceContext serviceContext) {
        this.serviceContext = serviceContext;
    }

    public Map<String, String> getPreConstruct() {
        return preConstruct;
    }

    public void setPreConstruct(Map<String, String> preConstruct) {
        this.preConstruct = preConstruct;
    }

    public Map<String, String> getPostConstruct() {
        return postConstruct;
    }

    public void setPostConstruct(Map<String, String> postConstruct) {
        this.postConstruct = postConstruct;
    }

    public ServiceConfig getServiceConfig() {
        return serviceConfig;
    }

    public void setServiceConfig(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    public AddedClaims getAddedClaims() {
        return addedClaims;
    }

    public void setAddedClaims(AddedClaims addedClaims) {
        this.addedClaims = addedClaims;
    }
}