/*
Developed by the European Commission - Directorate General for Maritime Affairs and Fisheries @ European Union, 2015-2016.

This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of
the License, or any later version. The IFDM Suite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
details. You should have received a copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.

 */package eu.europa.ec.fisheries.uvms.plugins.mdr;

import eu.europa.ec.fisheries.schema.exchange.plugin.types.v1.PluginType;
import eu.europa.ec.fisheries.schema.exchange.service.v1.CapabilityListType;
import eu.europa.ec.fisheries.schema.exchange.service.v1.ServiceType;
import eu.europa.ec.fisheries.schema.exchange.service.v1.SettingListType;
import eu.europa.ec.fisheries.uvms.exchange.model.constant.ExchangeModelConstants;
import eu.europa.ec.fisheries.uvms.exchange.model.exception.ExchangeModelMarshallException;
import eu.europa.ec.fisheries.uvms.exchange.model.mapper.ExchangeModuleRequestMapper;
import eu.europa.ec.fisheries.uvms.plugins.mdr.mapper.ServiceMapper;
import eu.europa.ec.fisheries.uvms.plugins.mdr.producer.PluginMessageProducer;
import eu.europa.ec.fisheries.uvms.plugins.mdr.service.FileHandlerBean;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.*;
import javax.jms.JMSException;
import java.util.Map;
import java.util.Properties;

@Singleton
@Startup
@DependsOn({"PluginMessageProducer", "FileHandlerBean"})
public class StartupBean extends PluginDataHolder {

    private static final Logger LOG = LoggerFactory.getLogger(StartupBean.class);

    private static final int MAX_NUMBER_OF_TRIES = 10;
    private boolean isRegistered                 = false;
    private boolean isEnabled                    = false;
    private boolean waitingForResponse           = false;
    private int numberOfTriesExecuted            = 0;
    private String registeredClassName           = StringUtils.EMPTY;

    private static final String FAILED_TO_GET_SETTING_FOR_KEY            = "Failed to getSetting for key: ";
    private static final String FAILED_TO_SEND_UNREGISTRATION_MESSAGE_TO = "Failed to send unregistration message to {}";

    @EJB
    private PluginMessageProducer messageProducer;

    @EJB
    private FileHandlerBean fileHandler;

    private CapabilityListType capabilities;
    private SettingListType settingList;
    private ServiceType serviceType;

    private FluxParameters fluxParameters;

    @PostConstruct
    public void startup() {

        //This must be loaded first!!! Not doing that will end in dire problems later on!
        super.setPluginApplicaitonProperties(fileHandler.getPropertiesFromFile(PluginDataHolder.PLUGIN_PROPERTIES_KEY));
        registeredClassName = getPLuginApplicationProperty("application.groupid");

        //Theese can be loaded in any order
        super.setPluginProperties(fileHandler.getPropertiesFromFile(PluginDataHolder.PROPERTIES_KEY));
        super.setPluginCapabilities(fileHandler.getPropertiesFromFile(PluginDataHolder.CAPABILITIES_KEY));

        ServiceMapper.mapToMapFromProperties(super.getSettings(), super.getPluginProperties(), getRegisterClassName());
        ServiceMapper.mapToMapFromProperties(super.getCapabilities(), super.getPluginCapabilities(), null);

        capabilities = ServiceMapper.getCapabilitiesListTypeFromMap(super.getCapabilities());
        settingList = ServiceMapper.getSettingsListTypeFromMap(super.getSettings());

        serviceType = ServiceMapper.getServiceType(
                getRegisterClassName(),
                getApplicaionName(),
                "A good description for the plugin",
                PluginType.SATELLITE_RECEIVER,
                getPluginResponseSubscriptionName());
        register();

        LOG.debug("Settings updated in plugin {}", registeredClassName);
        for (Map.Entry<String, String> entry : super.getSettings().entrySet()) {
            LOG.debug("Setting: KEY: {} , VALUE: {}", entry.getKey(), entry.getValue());
        }

        populateFluxParameters();
        LOG.info("PLUGIN STARTED");
    }

    /**
     * Populates the flux connection parameters getting them from the properties file.
     */
    private void populateFluxParameters() {
        fluxParameters = new FluxParameters();
        Properties plugProps = super.getPluginApplicaitonProperties();
        fluxParameters.populate(
                (String)plugProps.get("provider.url"),
                (String)plugProps.get("security.principal.id"),
                (String)plugProps.get("security.principal.pwd"));
    }

    @PreDestroy
    public void shutdown() {
        unregister();
    }

    @Schedule(second = "*/30", minute = "*", hour = "*", persistent = false)
    public void timeout() {
        if (!waitingForResponse && !isRegistered && numberOfTriesExecuted < MAX_NUMBER_OF_TRIES) {
            LOG.info(getRegisterClassName() + " is not registered, trying to register");
            register();
            numberOfTriesExecuted++;
        }
    }

    private void register() {
        LOG.info("Registering to Exchange Module");
        setWaitingForResponse(true);
        try {
            String registerServiceRequest = ExchangeModuleRequestMapper.createRegisterServiceRequest(serviceType, capabilities, settingList);
            messageProducer.sendEventBusMessage(registerServiceRequest, ExchangeModelConstants.EXCHANGE_REGISTER_SERVICE);
        } catch (JMSException | ExchangeModelMarshallException e) {
            LOG.error("Failed to send registration message to {}", ExchangeModelConstants.EXCHANGE_REGISTER_SERVICE,e);
            setWaitingForResponse(false);
        }

    }

    private void unregister() {
        LOG.info("Unregistering from Exchange Module");
        try {
            String unregisterServiceRequest = ExchangeModuleRequestMapper.createUnregisterServiceRequest(serviceType);
            messageProducer.sendEventBusMessage(unregisterServiceRequest, ExchangeModelConstants.EXCHANGE_REGISTER_SERVICE);
        } catch (JMSException | ExchangeModelMarshallException e) {
            LOG.error(FAILED_TO_SEND_UNREGISTRATION_MESSAGE_TO, ExchangeModelConstants.EXCHANGE_REGISTER_SERVICE,e);
        }
    }

    public String getPLuginApplicationProperty(String key) {
        try {
            return (String) super.getPluginApplicaitonProperties().get(key);
        } catch (Exception e) {
            LOG.error(FAILED_TO_GET_SETTING_FOR_KEY + key, getRegisterClassName(),e);
            return null;
        }
    }

    public String getPluginResponseSubscriptionName() {
        return getRegisterClassName() + getSetting("application.responseTopicName");
    }
    public String getResponseTopicMessageName() {
        return getSetting("application.groupid");
    }
    public String getRegisterClassName() {
        return registeredClassName;
    }
    public String getApplicaionName() {
        return getSetting("application.name");
    }
    public String getSetting(String key) {
        try {
            LOG.debug("Trying to get setting {} ", registeredClassName + "." + key);
            return super.getSettings().get(registeredClassName + "." + key);
        } catch (Exception e) {
            LOG.error(FAILED_TO_GET_SETTING_FOR_KEY + key, registeredClassName,e);
            return null;
        }
    }
    public boolean isWaitingForResponse() {
        return waitingForResponse;
    }
    public void setWaitingForResponse(boolean waitingForResponse) {
        this.waitingForResponse = waitingForResponse;
    }
    public boolean isIsRegistered() {
        return isRegistered;
    }
    public void setIsRegistered(boolean isRegistered) {
        this.isRegistered = isRegistered;
    }
    public boolean isIsEnabled() {
        return isEnabled;
    }
    public void setIsEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }
    public FluxParameters getFluxParameters() {
        return fluxParameters;
    }
}
