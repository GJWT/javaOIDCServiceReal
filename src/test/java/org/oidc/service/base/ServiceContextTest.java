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

package org.oidc.service.base;

import com.auth0.msg.Key;
import com.auth0.msg.KeyBundle;
import com.auth0.msg.KeyJar;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.oidc.common.Algorithm;
import org.oidc.common.FileOrUrl;
import org.oidc.common.KeySpecifications;
import org.oidc.common.ValueException;
import org.oidc.msg.InvalidClaimException;
import org.oidc.msg.oidc.ProviderConfigurationResponse;
import org.oidc.service.util.Constants;

public class ServiceContextTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private static final KeyJar keyJar = new KeyJar();

  @Test
  public void testImportKeysNullKeySpecifications() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("null keySpecifications");
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.importKeys(null);
  }

  @Ignore
  @Test
  public void testImportKeysWithFile() {
    ServiceContext serviceContext = new ServiceContext();
    KeyJar keyJar = new KeyJar();
    KeyBundle keyBundle = new KeyBundle();
    Key key = new Key();
    keyBundle.addKey(key);
    keyJar.addKeyBundle("owner", keyBundle);
    serviceContext.setKeyJar(keyJar);
    Assert.assertTrue(serviceContext.getKeyJar().getKeyBundle().getKeys().size() == 0);
    Map<FileOrUrl, KeySpecifications> keySpecificationsMap = new HashMap<>();
    KeySpecifications keySpecifications = new KeySpecifications("salesforce.key", Algorithm.RS256);
    keySpecificationsMap.put(FileOrUrl.FILE, keySpecifications);
    serviceContext.importKeys(keySpecificationsMap);
    Assert.assertTrue(serviceContext.getKeyJar().getKeyBundle().getKeys().size() == 1);
  }

  @Test
  public void testFileNameFromWebnameNullUrl() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("null or empty webName");
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.fileNameFromWebname(null);
  }

  @Test
  public void testFileNameFromWebnameEmptyUrl() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("null or empty webName");
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.fileNameFromWebname("");
  }

  @Test
  public void testFileNameFromWebnameWhereWebNameDoesntStartWithBaseUrl() throws Exception {
    thrown.expect(ValueException.class);
    thrown.expectMessage("Webname does not match baseUrl");
    ServiceContextConfig serviceContextConfig = new ServiceContextConfig.ServiceContextConfigBuilder()
        .setBaseUrl("baseUrl").buildServiceContext();
    ServiceContext serviceContext = new ServiceContext(keyJar, serviceContextConfig);
    serviceContext.setBaseUrl("www.yahoo.com");
    serviceContext.fileNameFromWebname("webName");
  }

  @Test
  public void testFileNameFromWebnameWhereWebNameStartsWithForwardSlash() throws Exception {
    ServiceContextConfig serviceContextConfig = new ServiceContextConfig.ServiceContextConfigBuilder()
        .setBaseUrl("www.yahoo.com").buildServiceContext();
    ServiceContext serviceContext = new ServiceContext(keyJar, serviceContextConfig);
    serviceContext.setBaseUrl("www.yahoo.com");
    String fileName = serviceContext.fileNameFromWebname("www.yahoo.com/1234");
    Assert.assertTrue(fileName.equals("1234"));
  }

  @Test
  public void testFileNameFromWebnameWhereWebNameDoesntStartsWithForwardSlash() throws Exception {
    ServiceContextConfig serviceContextConfig = new ServiceContextConfig.ServiceContextConfigBuilder()
        .setBaseUrl("www.yahoo.com").buildServiceContext();
    ServiceContext serviceContext = new ServiceContext(keyJar, serviceContextConfig);
    serviceContext.setBaseUrl("www.yahoo.com");
    String fileName = serviceContext.fileNameFromWebname("www.yahoo.com:1234");
    Assert.assertTrue(fileName.equals(":1234"));
  }

  @Test
  public void testGenerateRequestUrisWithForwardSlash()
      throws NoSuchAlgorithmException, ValueException, InvalidClaimException {
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.setIssuer("issuer");
    serviceContext.setBaseUrl("baseUrl");
    ProviderConfigurationResponse pcr = initializeMinimalConfiguration("issuer");
    serviceContext.setProviderConfigurationResponse(pcr);
    List<String> requestUris = serviceContext.generateRequestUris("/url");
    Assert.assertTrue(requestUris.size() == 1);
    Assert.assertTrue(requestUris.get(0).startsWith("baseUrl/url/"));
  }

  @Test
  public void testGenerateRequestUrisWithoutForwardSlash()
      throws NoSuchAlgorithmException, ValueException, InvalidClaimException {
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.setIssuer("issuer");
    serviceContext.setBaseUrl("baseUrl");
    ProviderConfigurationResponse pcr = initializeMinimalConfiguration("issuer");
    serviceContext.setProviderConfigurationResponse(pcr);
    List<String> requestUris = serviceContext.generateRequestUris("url");
    Assert.assertTrue(requestUris.size() == 1);
    Assert.assertTrue(requestUris.get(0).startsWith("baseUrl/url/"));
  }

  protected ProviderConfigurationResponse initializeMinimalConfiguration(String issuer) {
    Map<String, Object> claims = new HashMap<>();
    claims.put(Constants.ISSUER, issuer);
    claims.put("authorization_endpoint", "mockEndpoint");
    claims.put("jwks_uri", "mockUri");
    claims.put("response_types_supported", Arrays.asList("mockType"));
    claims.put("subject_types_supported", Arrays.asList("mockType"));
    claims.put("id_token_signing_alg_values_supported", Arrays.asList("mockValue"));
    return new ProviderConfigurationResponse(claims);

  }
}
