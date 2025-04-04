/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mqtt.homeassistant.internal.component;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.generic.values.TextValue;
import org.openhab.binding.mqtt.homeassistant.internal.ComponentChannel;
import org.openhab.binding.mqtt.homeassistant.internal.ComponentChannelType;
import org.openhab.binding.mqtt.homeassistant.internal.config.dto.AbstractChannelConfiguration;
import org.openhab.binding.mqtt.homeassistant.internal.exception.ConfigurationException;
import org.openhab.core.config.core.Configuration;

import com.google.gson.annotations.SerializedName;

/**
 * A MQTT Device Trigger, following the https://www.home-assistant.io/integrations/device_trigger.mqtt/ specification.
 *
 * @author Cody Cutrer - Initial contribution
 */
@NonNullByDefault
public class DeviceTrigger extends AbstractComponent<DeviceTrigger.ChannelConfiguration> {
    /**
     * Configuration class for MQTT component
     */
    public static class ChannelConfiguration extends AbstractChannelConfiguration {
        ChannelConfiguration() {
            super("MQTT Device Trigger");
        }

        @SerializedName("automation_type")
        protected String automationType = "trigger";
        protected String topic = "";
        protected String type = "";
        protected String subtype = "";

        protected @Nullable String payload;

        public String getTopic() {
            return topic;
        }

        public String getSubtype() {
            return subtype;
        }
    }

    public DeviceTrigger(ComponentFactory.ComponentConfiguration componentConfiguration) {
        super(componentConfiguration, ChannelConfiguration.class);

        if (!"trigger".equals(channelConfiguration.automationType)) {
            throw new ConfigurationException("Component:DeviceTrigger must have automation_type 'trigger'");
        }
        if (channelConfiguration.type.isBlank()) {
            throw new ConfigurationException("Component:DeviceTrigger must have a type");
        }
        if (channelConfiguration.subtype.isBlank()) {
            throw new ConfigurationException("Component:DeviceTrigger must have a subtype");
        }

        // Name the channel after the subtype, not the component ID
        // So that we only end up with a single channel for all possible events
        // for a single button (subtype is the button, type is type of press)
        componentId = channelConfiguration.subtype;
        groupId = null;

        TextValue value;
        String payload = channelConfiguration.payload;
        if (payload != null) {
            value = new TextValue(new String[] { payload });
        } else {
            value = new TextValue();
        }

        buildChannel(channelConfiguration.type, ComponentChannelType.TRIGGER, value, getName(),
                componentConfiguration.getUpdateListener())
                .stateTopic(channelConfiguration.topic, channelConfiguration.getValueTemplate()).trigger(true).build();
    }

    @Override
    public boolean mergeable(AbstractComponent<?> other) {
        if (other instanceof DeviceTrigger newTrigger
                && newTrigger.getChannelConfiguration().getSubtype().equals(getChannelConfiguration().getSubtype())
                && newTrigger.getChannelConfiguration().getTopic().equals(getChannelConfiguration().getTopic())
                && getHaID().nodeID.equals(newTrigger.getHaID().nodeID)) {
            String newTriggerValueTemplate = newTrigger.getChannelConfiguration().getValueTemplate();
            String oldTriggerValueTemplate = getChannelConfiguration().getValueTemplate();
            if ((newTriggerValueTemplate == null && oldTriggerValueTemplate == null)
                    || (newTriggerValueTemplate != null & oldTriggerValueTemplate != null
                            && newTriggerValueTemplate.equals(oldTriggerValueTemplate))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Take another DeviceTrigger (presumably whose subtype, topic, and value template match),
     * and adjust this component's channel to accept the payload that trigger allows.
     * 
     * @return if the component was stopped, and thus needs restarted
     */
    @Override
    public boolean merge(AbstractComponent<?> other) {
        DeviceTrigger newTrigger = (DeviceTrigger) other;
        ComponentChannel channel = Objects.requireNonNull(channels.get(componentId));
        Configuration newConfiguration = mergeChannelConfiguration(channel, newTrigger);

        TextValue value = (TextValue) channel.getState().getCache();
        Map<String, String> payloads = value.getStates();

        // Append payload to allowed values
        String otherPayload = newTrigger.getChannelConfiguration().payload;
        if (payloads == null || otherPayload == null) {
            // Need to accept anything
            value = new TextValue();
        } else {
            String[] newValues = Stream.concat(payloads.keySet().stream(), Stream.of(otherPayload)).distinct()
                    .toArray(String[]::new);
            value = new TextValue(newValues);
        }

        // Recreate the channel
        stop();
        buildChannel(channelConfiguration.type, ComponentChannelType.TRIGGER, value, componentId,
                componentConfiguration.getUpdateListener()).withConfiguration(newConfiguration)
                .stateTopic(channelConfiguration.topic, channelConfiguration.getValueTemplate()).trigger(true).build();
        return true;
    }
}
