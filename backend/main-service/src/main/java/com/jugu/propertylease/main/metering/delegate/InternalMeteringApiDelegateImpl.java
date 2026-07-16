package com.jugu.propertylease.main.metering.delegate;

import com.jugu.propertylease.main.internal.api.InternalMeteringApiDelegate;
import com.jugu.propertylease.main.internal.api.model.IotReadingPushRequest;
import com.jugu.propertylease.main.metering.api.RecordReadingCommand;
import com.jugu.propertylease.main.metering.service.MeteringService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * device-service 推送 IoT 读数入口 Delegate。
 *
 * <p>幂等：(deviceIdRaw, readingTime) 重复推送直接返回 200（由 MeteringService 内部处理）。
 */
@Service
public class InternalMeteringApiDelegateImpl implements InternalMeteringApiDelegate {

    private final MeteringService svc;

    public InternalMeteringApiDelegateImpl(MeteringService svc) {
        this.svc = svc;
    }

    @Override
    public void pushIotReading(IotReadingPushRequest request) {
        svc.submitPeriodicReading(new RecordReadingCommand(
                request.getRoomId(),
                request.getMeterType().getValue(),
                BigDecimal.valueOf(request.getReadingValue()),
                request.getReadingTime(),
                "IOT",
                request.getDeviceIdRaw(),
                null   // operatorId = null for IoT
        ));
    }
}
