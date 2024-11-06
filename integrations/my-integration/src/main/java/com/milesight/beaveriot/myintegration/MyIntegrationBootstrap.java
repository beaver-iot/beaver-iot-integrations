package com.milesight.beaveriot.myintegration;

import com.milesight.beaveriot.context.integration.bootstrap.IntegrationBootstrap;
import com.milesight.beaveriot.context.integration.model.Integration;
import org.springframework.stereotype.Component;

@Component
public class MyIntegrationBootstrap implements IntegrationBootstrap {
    @Override
    public void onPrepared(Integration integration) {

    }

    @Override
    public void onStarted(Integration integrationConfig) {
        System.out.println("Hello, world!");
    }

    @Override
    public void onDestroy(Integration integration) {

    }
}
