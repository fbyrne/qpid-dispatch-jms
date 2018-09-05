package org.apache.qpid.dispatch.jmstest;

import io.gatling.app.Gatling;
import io.gatling.core.config.GatlingPropertiesBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestResponseLoadTest {

    @Test
    public void load_testing_request_reply_with_temporary_queue(){
        GatlingPropertiesBuilder propertiesBuilder = new GatlingPropertiesBuilder()
                .resultsDirectory(IDEPathHelper.gatlingReportDirectory().toString())
                .dataDirectory(IDEPathHelper.gatlingDataDirectory().toString())
                .bodiesDirectory(IDEPathHelper.gatlingReportDirectory().toString())
                .binariesDirectory(IDEPathHelper.projectTestOutputDirectory().toString())
                .simulationClass(org.apache.qpid.dispatch.jmstest.TemporaryQueueLoadTest.class.getName());

        int exitValue = Gatling.fromMap(propertiesBuilder.build());
        assertThat(exitValue).isEqualTo(0).as("check gatling ran successfully");
    }
}
