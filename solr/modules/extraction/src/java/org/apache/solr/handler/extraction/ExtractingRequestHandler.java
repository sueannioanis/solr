/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.handler.extraction;

import java.io.InputStream;
import java.nio.file.Path;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.ContentStreamHandlerBase;
import org.apache.solr.handler.loader.ContentStreamLoader;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.apache.tika.config.TikaConfig;

/**
 * Handler for rich documents like PDF or Word or any other file format that Tika handles that need
 * the text to be extracted first from the document.
 */
public class ExtractingRequestHandler extends ContentStreamHandlerBase
    implements SolrCoreAware, PermissionNameProvider {

  public static final String PARSE_CONTEXT_CONFIG = "parseContext.config";
  public static final String CONFIG_LOCATION = "tika.config";

  protected TikaConfig config;
  protected ParseContextConfig parseContextConfig;

  protected SolrContentHandlerFactory factory;

  @Override
  public PermissionNameProvider.Name getPermissionName(AuthorizationContext request) {
    return PermissionNameProvider.Name.READ_PERM;
  }

  @Override
  public void inform(SolrCore core) {
    try {
      String tikaConfigLoc = (String) initArgs.get(CONFIG_LOCATION);
      if (tikaConfigLoc == null) { // default
        ClassLoader classLoader = core.getResourceLoader().getClassLoader();
        try (InputStream is = classLoader.getResourceAsStream("solr-default-tika-config.xml")) {
          config = new TikaConfig(is);
        }
      } else {
        Path configFile = Path.of(tikaConfigLoc);
        if (configFile.isAbsolute()) {
          config = new TikaConfig(configFile);
        } else { // in conf/
          try (InputStream is = core.getResourceLoader().openResource(tikaConfigLoc)) {
            config = new TikaConfig(is);
          }
        }
      }

      String parseContextConfigLoc = (String) initArgs.get(PARSE_CONTEXT_CONFIG);
      if (parseContextConfigLoc == null) { // default:
        parseContextConfig = new ParseContextConfig();
      } else {
        parseContextConfig =
            new ParseContextConfig(core.getResourceLoader(), parseContextConfigLoc);
      }
    } catch (Exception e) {
      throw new SolrException(ErrorCode.SERVER_ERROR, "Unable to load Tika Config", e);
    }

    factory = createFactory();
  }

  protected SolrContentHandlerFactory createFactory() {
    return new SolrContentHandlerFactory();
  }

  @Override
  protected ContentStreamLoader newLoader(SolrQueryRequest req, UpdateRequestProcessor processor) {
    return new ExtractingDocumentLoader(req, processor, config, parseContextConfig, factory);
  }

  // ////////////////////// SolrInfoMBeans methods //////////////////////
  @Override
  public String getDescription() {
    return "Add/Update Rich document";
  }
}
