package eu.europa.ec.fisheries.uvms.plugins.mdr.consumer;

import eu.europa.ec.fisheries.schema.exchange.plugin.types.v1.PluginFault;
import eu.europa.ec.fisheries.schema.exchange.registry.v1.ExchangeRegistryBaseRequest;
import eu.europa.ec.fisheries.schema.exchange.registry.v1.RegisterServiceResponse;
import eu.europa.ec.fisheries.schema.exchange.registry.v1.UnregisterServiceResponse;
import eu.europa.ec.fisheries.uvms.exchange.model.constant.ExchangeModelConstants;
import eu.europa.ec.fisheries.uvms.exchange.model.exception.ExchangeModelMarshallException;
import eu.europa.ec.fisheries.uvms.exchange.model.mapper.JAXBMarshaller;
import eu.europa.ec.fisheries.uvms.plugins.mdr.StartupBean;
import eu.europa.ec.fisheries.uvms.plugins.mdr.service.PluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.*;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

@MessageDriven(mappedName = ExchangeModelConstants.PLUGIN_EVENTBUS, activationConfig = {
        @ActivationConfigProperty(propertyName = "messagingType", propertyValue = ExchangeModelConstants.CONNECTION_TYPE),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = ExchangeModelConstants.DESTINATION_TYPE_TOPIC),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = ExchangeModelConstants.EVENTBUS_NAME)
})
public class PluginAckEventBusListener implements MessageListener {

    final static Logger LOG = LoggerFactory.getLogger(PluginAckEventBusListener.class);

    @EJB
    StartupBean startupService;

    @EJB
    PluginService mdrService;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void onMessage(Message inMessage) {

        LOG.info("Eventbus listener for mdr at selector: {} got a message", startupService.getPluginResponseSubscriptionName());

        TextMessage textMessage = (TextMessage) inMessage;

        try {

            ExchangeRegistryBaseRequest request = tryConsumeRegistryBaseRequest(textMessage);

            if (request == null) {
                PluginFault fault = JAXBMarshaller.unmarshallTextMessage(textMessage, PluginFault.class);
                handlePluginFault(fault);
                return;
            }

            switch (request.getMethod()) {
                case REGISTER_SERVICE:
                    RegisterServiceResponse registerResponse = JAXBMarshaller.unmarshallTextMessage(textMessage, RegisterServiceResponse.class);
                    startupService.setWaitingForResponse(Boolean.FALSE);
                    setRegistrationResponse(request, registerResponse);
                    break;
                case UNREGISTER_SERVICE:
                    UnregisterServiceResponse unregisterResponse = JAXBMarshaller.unmarshallTextMessage(textMessage, UnregisterServiceResponse.class);
                    setUnRegistrationResponse(unregisterResponse);
                    break;
                default:
                    LOG.error("Not supported method");
                    break;
            }

        } catch (ExchangeModelMarshallException | NullPointerException e) {
            LOG.error("[ Error when receiving message in mdr ]", e);
        }
    }

    private void setUnRegistrationResponse(UnregisterServiceResponse unregisterResponse) {
        switch (unregisterResponse.getAck().getType()) {
            case OK:
                LOG.info("Unregister OK");
                break;
            case NOK:
                LOG.info("Unregister NOK");
                break;
            default:
                LOG.error("[ Ack type not supported ] ");
                break;
        }
    }

    private void setRegistrationResponse(ExchangeRegistryBaseRequest request, RegisterServiceResponse registerResponse) {
        switch (registerResponse.getAck().getType()) {
            case OK:
                LOG.info("Register OK");
                startupService.setIsRegistered(Boolean.TRUE);
                break;
            case NOK:
                LOG.info("Register NOK: " + registerResponse.getAck().getMessage());
                startupService.setIsRegistered(Boolean.FALSE);
                break;
            default:
                LOG.error("[ Type not supperted: ]" + request.getMethod());
        }
    }

    private void handlePluginFault(PluginFault fault) {
        LOG.error(startupService.getPluginResponseSubscriptionName() + " received fault " + fault.getCode() + " : " + fault.getMessage());
    }

    private ExchangeRegistryBaseRequest tryConsumeRegistryBaseRequest(TextMessage textMessage) {
        try {
            return JAXBMarshaller.unmarshallTextMessage(textMessage, ExchangeRegistryBaseRequest.class);
        } catch (ExchangeModelMarshallException e) {
            LOG.error("Error trying to consume BaseRequest",e);
            return null;
        }
    }
}