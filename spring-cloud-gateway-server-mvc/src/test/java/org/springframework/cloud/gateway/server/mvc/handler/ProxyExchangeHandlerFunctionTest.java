/*
 * Copyright 2013-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.server.mvc.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.test.client.TestRestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * @author sucheng.wang
 * @since 2025-05-09
 */
@SpringBootTest(properties = {}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProxyExchangeHandlerFunctionTest {

	@Autowired
	private TestRestClient restClient;

	@Test
	void testSimpleProxyRequestUriHandling() {
		restClient.get()
				.uri(URI.create("/query?foo=abc&bar=xyz"))
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, List<String>> map = res.getResponseBody();
					assertNotNull(map);
					assertEquals(2, map.size());
					assertTrue(map.containsKey("foo"));
					assertEquals("abc", map.get("foo").get(0));
					assertTrue(map.containsKey("bar"));
					assertEquals("xyz", map.get("bar").get(0));
				});
	}

	@Test
	void testAmperEncodedProxyRequestUriHandling() {
		restClient.get()
				.uri(URI.create("/query?keyword=k%26r&size=20&sort=asc"))
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody(Map.class)
				.consumeWith(res -> {
					Map<String, List<String>> map = res.getResponseBody();
					assertNotNull(map);
					assertEquals(Set.of("keyword", "size", "sort"), map.keySet());
					assertEquals("k&r", map.get("keyword").get(0));
					assertEquals("20", map.get("size").get(0));
					assertEquals("asc", map.get("sort").get(0));
				});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		@Bean
		Function<HttpServletRequest, Map<String, String[]>> requestGetParameterMap() {
			return ServletRequest::getParameterMap;
		}

		@Bean
		public RouterFunction<ServerResponse> gatewayRouterFunctionsSimpleFunction() throws LifecycleException {
			Tomcat upstreamTomcat = startUpstreamTomcat();
			int port = upstreamTomcat.getConnector().getLocalPort();
			System.out.println("Upstream Tomcat port: " + port);
			// @formatter:off
			return route("testproxyexchange")
					.GET("/query", http())
					.before(uri("http://127.0.0.1:" + port))
					.build();
			// @formatter:on
		}

		private static Tomcat startUpstreamTomcat() throws LifecycleException {
			Tomcat tomcat = new Tomcat();
			tomcat.setPort(0);
			tomcat.getConnector(); // Trigger the creation of the default connector

			// Add context
			var ctx = tomcat.addContext("", null);

			ObjectMapper mapper = new ObjectMapper();
			// Add servlet
			Tomcat.addServlet(ctx, "EchoParameterMapServlet", new HttpServlet() {
				@Override
				protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
					resp.setContentType("application/json");
					resp.setCharacterEncoding("UTF-8");
					mapper.writeValue(resp.getWriter(), req.getParameterMap());
				}
			});
			ctx.addServletMappingDecoded("/query", "EchoParameterMapServlet");

			tomcat.start();
			return tomcat;
		}
	}
}
