package com.milesight.beaveriot.ping;

import com.milesight.beaveriot.context.integration.bootstrap.IntegrationBootstrap;
import com.milesight.beaveriot.context.integration.model.Integration;
import org.springframework.stereotype.Component;

@Component
public class PingBootstrap implements IntegrationBootstrap {
    @Override
    public void onPrepared(Integration integration) {
        // do nothing
    }

    @Override
    public void onStarted(Integration integrationConfig) {
        // do nothing
    }

    @Override
    public void onDestroy(Integration integration) {
        // do nothing
    }
}
