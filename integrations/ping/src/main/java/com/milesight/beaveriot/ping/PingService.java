package com.milesight.beaveriot.ping;

import com.fasterxml.jackson.databind.JsonNode;
import com.milesight.beaveriot.context.api.DeviceServiceProvider;
import com.milesight.beaveriot.context.api.EntityValueServiceProvider;
import com.milesight.beaveriot.context.api.ExchangeFlowExecutor;
import com.milesight.beaveriot.context.integration.enums.AccessMod;
import com.milesight.beaveriot.context.integration.enums.EntityValueType;
import com.milesight.beaveriot.context.integration.model.*;
import com.milesight.beaveriot.context.integration.model.event.ExchangeEvent;
import com.milesight.beaveriot.eventbus.annotations.EventSubscribe;
import com.milesight.beaveriot.eventbus.api.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class PingService {
    @Autowired
    private DeviceServiceProvider deviceServiceProvider;

    @Autowired
    private ExchangeFlowExecutor exchangeFlowExecutor;

    @Autowired
    private EntityValueServiceProvider entityValueServiceProvider;

    @EventSubscribe(payloadKeyExpression = PingConstants.INTEGRATION_ID + ".integration.add_device.*", eventType = ExchangeEvent.EventType.DOWN)
    public void onAddDevice(Event<PingIntegrationEntities.AddDevice> event) {
        String deviceName = event.getPayload().getContext("device_name", "Device Name");
        String ip = event.getPayload().getIp();


        Entity statusEntity = new EntityBuilder(PingConstants.INTEGRATION_ID)
                .identifier("status")
                .property("Device Status", AccessMod.R)
                .valueType(EntityValueType.LONG)
                .attributes(new AttributeBuilder().enums(PingConstants.DeviceStatus.class).build())
                .build();
        Entity delayEntity = new EntityBuilder(PingConstants.INTEGRATION_ID)
                .identifier("delay")
                .property("Network Delay", AccessMod.R)
                .valueType(EntityValueType.LONG)
                .attributes(new AttributeBuilder().unit("ms").build())
                .build();

        Device device = new DeviceBuilder(PingConstants.INTEGRATION_ID)
                .name(deviceName)
                .identifier(ip.replace(".", "_"))
                .additional(Map.of("ip", ip))
                .entities(List.of(statusEntity, delayEntity))
                .build();

        deviceServiceProvider.save(device);
    }

    @EventSubscribe(payloadKeyExpression = PingConstants.INTEGRATION_ID + ".integration.delete_device", eventType = ExchangeEvent.EventType.DOWN)
    public void onDeleteDevice(Event<ExchangePayload> event) {
        Device device = (Device) event.getPayload().getContext("device");
        deviceServiceProvider.deleteById(device.getId());
    }

    @EventSubscribe(payloadKeyExpression = PingConstants.INTEGRATION_ID + ".integration.benchmark", eventType = ExchangeEvent.EventType.DOWN)
    @Async
    public void benchmark(Event<PingIntegrationEntities> event) {
        // mark benchmark starting
        String detectStatusKey = PingConstants.INTEGRATION_ID + ".integration.detect_status";
        JsonNode detectStatusNode = entityValueServiceProvider.findValueByKey(detectStatusKey);
        if (detectStatusNode.isNull() || detectStatusNode.asLong() == PingIntegrationEntities.DetectStatus.DETECTING.ordinal()) {
            log.warn("[WARNING] Benchmark running");
            return;
        }

        try {
            doBenchmark(detectStatusKey);
        } catch (Exception e) {
            log.error("[Benchmark Error] " + e);
        } finally {
            // mark benchmark done
            ExchangePayload donePayload = new ExchangePayload();
            donePayload.put(detectStatusKey, PingIntegrationEntities.DetectStatus.STANDBY.ordinal());
            exchangeFlowExecutor.syncExchangeUp(donePayload);
        }
    }

    public void doBenchmark(String detectStatusKey) {
        exchangeFlowExecutor.syncExchangeDown(new ExchangePayload(Map.of(detectStatusKey, PingIntegrationEntities.DetectStatus.DETECTING.ordinal())));
        int timeout = 2000;

        // start pinging
        List<Device> devices = deviceServiceProvider.findAll(PingConstants.INTEGRATION_ID);
        AtomicReference<Long> activeCount = new AtomicReference<>(0L);
        AtomicReference<Long> inactiveCount = new AtomicReference<>(0L);

        devices.forEach(device -> {
            Long delay = null;
            String ip = (String) device.getAdditional().get("ip");
            try {
                long startTimestamp = System.currentTimeMillis();
                InetAddress inet = InetAddress.getByName(ip);
                if (inet.isReachable(timeout)) {
                    delay = System.currentTimeMillis() - startTimestamp;
                }
            } catch (IOException e) {
                log.warn("[Not reachable]: " + ip);
            }

            int deviceStatus = PingConstants.DeviceStatus.OFFLINE.ordinal();
            if (delay != null) {
                activeCount.updateAndGet(v -> v + 1);
                deviceStatus = PingConstants.DeviceStatus.ONLINE.ordinal();
            } else {
                inactiveCount.updateAndGet(v -> v + 1);
            }

            // Device have only one entity
            ExchangePayload exchangePayload = new ExchangePayload();
            final int fDeviceStatus = deviceStatus;
            final Long fDelay = delay;
            device.getEntities().forEach(entity -> {
                if (entity.getIdentifier().equals("status")) {
                    exchangePayload.put(entity.getKey(), fDeviceStatus);
                } else if (entity.getIdentifier().equals("delay")) {
                    exchangePayload.put(entity.getKey(), fDelay);
                }
            });

            Assert.notEmpty(exchangePayload, "Exchange should not be empty!");
            exchangeFlowExecutor.asyncExchangeDown(exchangePayload);
        });
    }
}
