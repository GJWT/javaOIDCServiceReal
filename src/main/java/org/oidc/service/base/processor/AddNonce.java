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

package org.oidc.service.base.processor;

import java.security.SecureRandom;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import org.oidc.msg.Error;
import org.oidc.msg.ParameterVerification;
import org.oidc.service.Service;
import org.oidc.service.base.RequestArgumentProcessingException;

/**
 * Class ensures nonce value exists always.
 */
public class AddNonce extends AbstractRequestArgumentProcessor {

  {
    paramVerDefs.put("response_type",
        ParameterVerification.OPTIONAL_LIST_OF_SP_SEP_STRINGS.getValue());
  }

  @Override
  protected void processVerifiedArguments(Map<String, Object> requestArguments, Service service,
      Error error) throws RequestArgumentProcessingException {
    if (requestArguments.containsKey("nonce")) {
      return;
    }
    byte[] rand = new byte[32];
    new SecureRandom().nextBytes(rand);
    String nonce = Base64.encodeBase64URLSafeString(rand);
    requestArguments.put("nonce", nonce);
  }
}