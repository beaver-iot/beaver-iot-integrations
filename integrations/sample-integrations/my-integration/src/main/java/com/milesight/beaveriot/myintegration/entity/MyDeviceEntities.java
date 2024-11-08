package com.milesight.beaveriot.myintegration.entity;

import com.milesight.beaveriot.context.integration.entity.annotation.Attribute;
import com.milesight.beaveriot.context.integration.entity.annotation.DeviceEntities;
import com.milesight.beaveriot.context.integration.entity.annotation.Entity;
import com.milesight.beaveriot.context.integration.entity.annotation.KeyValue;
import com.milesight.beaveriot.context.integration.enums.AccessMod;
import com.milesight.beaveriot.context.integration.enums.EntityType;
import com.milesight.beaveriot.context.integration.model.ExchangePayload;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@DeviceEntities(name="Default Device", identifier = "localhost", additional = {@KeyValue(key = "ip", value = "localhost")})
public class MyDeviceEntities extends ExchangePayload {
    @Entity(type = EntityType.PROPERTY, name = "Device Connection Status", accessMod = AccessMod.R, attributes = @Attribute(enumClass = DeviceStatus.class))
    // highlight-next-line
    private Long status;

    public enum DeviceStatus {
        ONLINE, OFFLINE;
    }
}
