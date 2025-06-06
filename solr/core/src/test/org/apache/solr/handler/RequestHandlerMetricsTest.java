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
package org.apache.solr.handler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.cloud.SolrCloudTestCase;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RequestHandlerMetricsTest extends SolrCloudTestCase {

  @BeforeClass
  public static void beforeClass() throws Exception {
    System.setProperty("metricsEnabled", "true");
    configureCluster(1).addConfig("conf1", configset("cloud-aggregate-node-metrics")).configure();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    cluster.deleteAllCollections();
  }

  @AfterClass
  public static void afterClass() {
    System.clearProperty("metricsEnabled");
  }

  @Test
  @SuppressWarnings({"unchecked"})
  public void testAggregateNodeLevelMetrics() throws SolrServerException, IOException {
    String collection1 = "testRequestHandlerMetrics1";
    String collection2 = "testRequestHandlerMetrics2";

    CloudSolrClient cloudClient = cluster.getSolrClient();

    CollectionAdminRequest.Create create =
        CollectionAdminRequest.createCollection(collection1, "conf1", 1, 1);
    cloudClient.request(create);
    cluster.waitForActiveCollection(collection1, 1, 1);

    create = CollectionAdminRequest.createCollection(collection2, "conf1", 1, 1);
    cloudClient.request(create);
    cluster.waitForActiveCollection(collection2, 1, 1);

    SolrInputDocument solrInputDocument =
        new SolrInputDocument("id", "10", "title", "test", "val_s1", "aaa");
    cloudClient.add(collection1, solrInputDocument);
    cloudClient.add(collection2, solrInputDocument);

    SolrQuery solrQuery = new SolrQuery("*:*");
    cloudClient.query(collection1, solrQuery);
    cloudClient.query(collection2, solrQuery);

    NamedList<Object> response =
        cloudClient.request(
            new GenericSolrRequest(
                SolrRequest.METHOD.GET, "/admin/metrics", SolrRequest.SolrRequestType.ADMIN));

    NamedList<Object> metrics = (NamedList<Object>) response.get("metrics");

    final double[] minQueryTime = {Double.MAX_VALUE};
    final double[] maxQueryTime = {-1.0};
    final double[] minUpdateTime = {Double.MAX_VALUE};
    final double[] maxUpdateTime = {-1.0};
    Set<NamedList<Object>> coreMetrics = new HashSet<>();
    metrics.forEach(
        (key, coreMetric) -> {
          if (key.startsWith("solr.core.testRequestHandlerMetrics")) {
            coreMetrics.add((NamedList<Object>) coreMetric);
          }
        });
    assertEquals(2, coreMetrics.size());
    coreMetrics.forEach(
        metric -> {
          assertEquals(
              1L,
              ((Map<String, Number>) metric.get("QUERY./select.requestTimes"))
                  .get("count")
                  .longValue());
          minQueryTime[0] =
              Math.min(
                  minQueryTime[0],
                  ((Map<String, Number>) metric.get("QUERY./select.requestTimes"))
                      .get("min_ms")
                      .doubleValue());
          maxQueryTime[0] =
              Math.max(
                  maxQueryTime[0],
                  ((Map<String, Number>) metric.get("QUERY./select.requestTimes"))
                      .get("max_ms")
                      .doubleValue());
          assertEquals(
              1L,
              ((Map<String, Number>) metric.get("UPDATE./update.requestTimes"))
                  .get("count")
                  .longValue());
          minUpdateTime[0] =
              Math.min(
                  minUpdateTime[0],
                  ((Map<String, Number>) metric.get("UPDATE./update.requestTimes"))
                      .get("min_ms")
                      .doubleValue());
          maxUpdateTime[0] =
              Math.max(
                  maxUpdateTime[0],
                  ((Map<String, Number>) metric.get("UPDATE./update.requestTimes"))
                      .get("max_ms")
                      .doubleValue());
        });

    NamedList<Object> nodeMetrics = (NamedList<Object>) metrics.get("solr.node");
    assertEquals(
        2L,
        ((Map<String, Number>) nodeMetrics.get("QUERY./select.requestTimes"))
            .get("count")
            .longValue());
    assertEquals(
        minQueryTime[0],
        ((Map<String, Number>) nodeMetrics.get("QUERY./select.requestTimes"))
            .get("min_ms")
            .doubleValue(),
        0.0);
    assertEquals(
        maxQueryTime[0],
        ((Map<String, Number>) nodeMetrics.get("QUERY./select.requestTimes"))
            .get("max_ms")
            .doubleValue(),
        0.0);
    assertEquals(
        2L,
        ((Map<String, Number>) nodeMetrics.get("UPDATE./update.requestTimes"))
            .get("count")
            .longValue());
    assertEquals(
        minUpdateTime[0],
        ((Map<String, Number>) nodeMetrics.get("UPDATE./update.requestTimes"))
            .get("min_ms")
            .doubleValue(),
        0.0);
    assertEquals(
        maxUpdateTime[0],
        ((Map<String, Number>) nodeMetrics.get("UPDATE./update.requestTimes"))
            .get("max_ms")
            .doubleValue(),
        0.0);
  }
}
