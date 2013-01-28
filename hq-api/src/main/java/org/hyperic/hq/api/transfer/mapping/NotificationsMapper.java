package org.hyperic.hq.api.transfer.mapping;

import java.util.List;

import org.hyperic.hq.api.model.NotificationsReport;
import org.hyperic.hq.notifications.model.BaseNotification;
import org.hyperic.hq.notifications.model.InventoryNotification;
import org.hyperic.hq.notifications.model.MetricNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationsMapper {
    @Autowired
    protected ResourceMapper rscMapper;
    @Autowired
    protected MeasurementMapper mtmtMapper;

    public NotificationsReport toNotificationsReport(List<? extends BaseNotification> ns) {
        NotificationsReport res = new NotificationsReport();
        if (ns.isEmpty()) {
            return new NotificationsReport();
        }
        for(BaseNotification bn:ns) {
            // expensive for many notifications, the 'instance of' should be used only in the polling mechanism
            if (bn instanceof MetricNotification) {
                res.add(this.mtmtMapper.toMetricWithId((MetricNotification)bn));
            } else if (bn instanceof InventoryNotification) {
                res.add(this.rscMapper.toResource((InventoryNotification )bn));
            }
        }
        return res;
    }
}