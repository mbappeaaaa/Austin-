package com.java3y.austin.billing.payment.provider;

import com.java3y.austin.billing.payment.config.ManagedPaymentProviders;
import com.java3y.austin.billing.payment.config.PaymentChannelConfig;
import com.java3y.austin.billing.payment.domain.PaymentProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PaymentProviderRegistry {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.java3y.austin.billing.payment.config.ManagedPaymentProviders> managed;
    private final Map<PaymentProviderType, PaymentProvider> providers =
            new EnumMap<>(PaymentProviderType.class);

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        for (PaymentProvider provider : providers) {
            if (this.providers.put(provider.type(), provider) != null) {
                throw new IllegalStateException("支付提供方重复注册: " + provider.type());
            }
        }
    }

    public PaymentProvider require(PaymentProviderType type) {
        if (managed != null) {
            ManagedPaymentProviders service = managed.getObject();
            Optional<PaymentChannelConfig> config = service.find(type);
            if (config.isPresent()) return service.provider(type, config.get());
        }
        PaymentProvider provider = providers.get(type);
        if (provider == null) {
            throw new PaymentProviderUnavailableException(type);
        }
        return provider;
    }

    public List<PaymentProviderType> availableTypes() {
        java.util.Set<PaymentProviderType> available = java.util.EnumSet.noneOf(PaymentProviderType.class);
        available.addAll(providers.keySet());
        if (managed != null) {
            for (PaymentProviderType type : PaymentProviderType.values()) {
                Optional<PaymentChannelConfig> config = managed.getObject().find(type);
                if (config.isPresent()) {
                    if (config.get().isEnabled()) available.add(type);
                    else available.remove(type);
                }
            }
        }
        return new java.util.ArrayList<>(available);
    }

    public PaymentProvider requireForCheckout(PaymentProviderType type) {
        if (!availableTypes().contains(type)) throw new PaymentProviderUnavailableException(type);
        return require(type);
    }
}
