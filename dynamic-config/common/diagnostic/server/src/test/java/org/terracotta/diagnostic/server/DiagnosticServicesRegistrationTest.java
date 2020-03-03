/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terracotta.diagnostic.server;

import org.junit.Test;

import javax.management.InstanceNotFoundException;

import static com.tc.management.TerracottaManagement.MBeanDomain.PUBLIC;
import static com.tc.management.TerracottaManagement.createObjectName;
import static java.lang.management.ManagementFactory.getPlatformMBeanServer;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.terracotta.testing.ExceptionMatcher.throwing;

/**
 * @author Mathieu Carbou
 */
public class DiagnosticServicesRegistrationTest {

  @Test
  public void test_close() throws Throwable {
    assertThat(DiagnosticServices.findService(MyService2.class).isPresent(), is(false));

    DiagnosticServicesRegistration<MyService2> registration = DiagnosticServices.register(MyService2.class, new MyServiceImpl());

    assertThat(DiagnosticServices.findService(MyService2.class).isPresent(), is(true));
    assertThat(getPlatformMBeanServer().getMBeanInfo(createObjectName(null, "s2", PUBLIC)), is(not(nullValue())));

    assertThat(registration.registerMBean("AnotherName"), is(true));

    assertThat(getPlatformMBeanServer().getMBeanInfo(createObjectName(null, "AnotherName", PUBLIC)), is(not(nullValue())));

    registration.close();

    assertThat(DiagnosticServices.findService(MyService2.class).isPresent(), is(false));
    assertThat(
        () -> getPlatformMBeanServer().getMBeanInfo(createObjectName(null, "s2", PUBLIC)),
        is(throwing(instanceOf(InstanceNotFoundException.class)).andMessage(is(equalTo("org.terracotta:name=s2")))));
    assertThat(
        () -> getPlatformMBeanServer().getMBeanInfo(createObjectName(null, "AnotherName", PUBLIC)),
        is(throwing(instanceOf(InstanceNotFoundException.class)).andMessage(is(equalTo("org.terracotta:name=AnotherName")))));

    // subsequent init is not failing
    DiagnosticServices.register(MyService2.class, new MyServiceImpl()).close();
  }

  @Test
  public void test_registerMBean() throws Throwable {
    DiagnosticServicesRegistration<MyService2> registration = DiagnosticServices.register(MyService2.class, new MyServiceImpl());
    assertThat(registration.registerMBean("foo"), is(true));
    assertThat(registration.registerMBean("bar"), is(true));

    assertThat(getPlatformMBeanServer().getMBeanInfo(createObjectName(null, "s2", PUBLIC)), is(not(nullValue())));
    assertThat(getPlatformMBeanServer().getMBeanInfo(createObjectName(null, "foo", PUBLIC)), is(not(nullValue())));
    assertThat(getPlatformMBeanServer().getMBeanInfo(createObjectName(null, "bar", PUBLIC)), is(not(nullValue())));

    registration.close();

    assertThat(registration.registerMBean("foo"), is(false));
    assertThat(registration.registerMBean("baz"), is(false));

    assertThat(
        () -> getPlatformMBeanServer().getMBeanInfo(createObjectName(null, "s2", PUBLIC)),
        is(throwing(instanceOf(InstanceNotFoundException.class)).andMessage(is(equalTo("org.terracotta:name=s2")))));
    assertThat(
        () -> getPlatformMBeanServer().getMBeanInfo(createObjectName(null, "foo", PUBLIC)),
        is(throwing(instanceOf(InstanceNotFoundException.class)).andMessage(is(equalTo("org.terracotta:name=foo")))));
    assertThat(
        () -> getPlatformMBeanServer().getMBeanInfo(createObjectName(null, "bar", PUBLIC)),
        is(throwing(instanceOf(InstanceNotFoundException.class)).andMessage(is(equalTo("org.terracotta:name=bar")))));
  }

  public interface MyService2 {
    String say2(String word);
  }

  @Expose("s2")
  public static class MyServiceImpl implements MyService2 {
    @Override
    public String say2(String word) {
      return "2. Hello " + word;
    }
  }

}