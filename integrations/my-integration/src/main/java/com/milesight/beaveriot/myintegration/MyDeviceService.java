package com.milesight.beaveriot.myintegration;

import com.milesight.beaveriot.context.api.DeviceServiceProvider;
import com.milesight.beaveriot.context.api.ExchangeFlowExecutor;
import com.milesight.beaveriot.context.integration.enums.AccessMod;
import com.milesight.beaveriot.context.integration.enums.EntityValueType;
import com.milesight.beaveriot.context.integration.model.*;
import com.milesight.beaveriot.context.integration.model.event.ExchangeEvent;
import com.milesight.beaveriot.eventbus.annotations.EventSubscribe;
import com.milesight.beaveriot.eventbus.api.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MyDeviceService {
    @Autowired
    private DeviceServiceProvider deviceServiceProvider;

    @Autowired
    private ExchangeFlowExecutor exchangeFlowExecutor;

    @EventSubscribe(payloadKeyExpression = "my-integration.integration.add_device.*", eventType = ExchangeEvent.EventType.DOWN)
    // highlight-next-line
    public void onAddDevice(Event<MyIntegrationEntities.AddDevice> event) {
        String deviceName = event.getPayload().getContext("device_name", "Device Name");
        String ip = event.getPayload().getIp();
        final String integrationId = "my-integration";
        Device device = new DeviceBuilder(integrationId)
                .name(deviceName)
                .identifier(ip.replace(".", "_"))
                .additional(Map.of("ip", ip))
                .build();

        Entity entity = new EntityBuilder(integrationId, device.getKey())
                .identifier("status")
                .property("Device Status", AccessMod.R)
                .valueType(EntityValueType.LONG)
                .attributes(new AttributeBuilder().enums(MyDeviceEntities.DeviceStatus.class).build())
                .build();
        device.setEntities(Collections.singletonList(entity));

        deviceServiceProvider.save(device);
    }

    @EventSubscribe(payloadKeyExpression = "my-integration.integration.delete_device", eventType = ExchangeEvent.EventType.DOWN)
    // highlight-next-line
    public void onDeleteDevice(Event<ExchangePayload> event) {
        Device device = (Device) event.getPayload().getContext("device");
        deviceServiceProvider.deleteById(device.getId());
    }

    @EventSubscribe(payloadKeyExpression = "my-integration.integration.benchmark", eventType = ExchangeEvent.EventType.DOWN)
    // highlight-next-line
    public void doBenchmark(Event<MyIntegrationEntities> event) {
        // mark benchmark starting
        exchangeFlowExecutor.syncExchangeDown(new ExchangePayload(Map.of("my-integration.integration.detect_status", MyIntegrationEntities.DetectStatus.DETECTING.ordinal())));
        int timeout = 5000;

        // start pinging
        List<Device> devices = deviceServiceProvider.findAll("my-integration");
        AtomicReference<Long> activeCount = new AtomicReference<>(0L);
        AtomicReference<Long> inactiveCount = new AtomicReference<>(0L);
        Long startTimestamp = System.currentTimeMillis();
        devices.forEach(device -> {
            boolean isSuccess = false;
            try {
                String ip = (String) device.getAdditional().get("ip");
                InetAddress inet = InetAddress.getByName(ip);
                if (inet.isReachable(timeout)) {
                    isSuccess = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            int deviceStatus = MyDeviceEntities.DeviceStatus.OFFLINE.ordinal();
            if (isSuccess) {
                activeCount.updateAndGet(v -> v + 1);
                deviceStatus = MyDeviceEntities.DeviceStatus.ONLINE.ordinal();
            } else {
                inactiveCount.updateAndGet(v -> v + 1);
            }

            // Device have only one entity
            String deviceStatusKey = device.getEntities().get(0).getKey();
            exchangeFlowExecutor.asyncExchangeDown(new ExchangePayload(Map.of(deviceStatusKey, (long) deviceStatus)));
        });
        Long endTimestamp = System.currentTimeMillis();

        // mark benchmark done
        ExchangePayload donePayload = new ExchangePayload();
        donePayload.put("my-integration.integration.detect_status", MyIntegrationEntities.DetectStatus.STANDBY.ordinal());
        donePayload.put("my-integration.integration.detect_report", "");
        donePayload.put("my-integration.integration.detect_report.consumed_time", endTimestamp - startTimestamp);
        donePayload.put("my-integration.integration.detect_report.online_count", activeCount.get());
        donePayload.put("my-integration.integration.detect_report.offline_count", inactiveCount.get());
        exchangeFlowExecutor.syncExchangeUp(donePayload);
    }

    @EventSubscribe(payloadKeyExpression = "my-integration.integration.detect_report.*", eventType = ExchangeEvent.EventType.UP)
    // highlight-next-line
    public void listenDetectReport(Event<MyIntegrationEntities.DetectReport> event) {
        System.out.println("[Get-Report] " + event.getPayload()); // do something with this report
    }
}
