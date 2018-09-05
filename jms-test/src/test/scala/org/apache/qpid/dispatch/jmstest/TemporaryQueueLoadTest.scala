package org.apache.qpid.dispatch.jmstest

import java.net.URL

import io.gatling.core.Predef._
import io.gatling.jms.Predef._
import javax.jms._

import scala.concurrent.duration._

class TemporaryQueueLoadTest extends Simulation {

  val jndiPropertiesUrl: URL = getClass.getClassLoader.getResource("loadtest-jndi.properties")
  // create a ConnectionFactory for ActiveMQ
  // search the documentation of your JMS broker
  val jmsConfig = jms
    .connectionFactoryName("dispatcherFailoverFactory")
    .url(jndiPropertiesUrl.toExternalForm)
    .contextFactory("org.apache.qpid.jms.jndi.JmsInitialContextFactory")
    .listenerCount(1)

  val scn = scenario("JMS DSL test").repeat(1) {
    exec(jms("req reply testing").reqreply
      .queue("requests")
      .textMessage("hello from gatling jms dsl")
      .property("test_header", "test_value")
      .jmsType("test_jms_type")
      .check(simpleCheck(checkBodyTextCorrect)))
  }

  setUp(scn.inject(rampUsersPerSec(10) to 1000 during (2 minutes)))
    .protocols(jmsConfig)

  def checkBodyTextCorrect(m: Message) = {
    // this assumes that the service just does an "uppercase" transform on the text
    m match {
      case tm: TextMessage => tm.getText == "HELLO FROM GATLING JMS DSL"
      case _               => false
    }
  }
}
