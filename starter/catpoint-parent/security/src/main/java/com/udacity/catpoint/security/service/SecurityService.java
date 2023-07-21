package com.udacity.catpoint.security.service;

import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;
import com.udacity.catpoint.image.service.FakeImageService;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 *
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {
    private final FakeImageService fakeImageService;
    private final Set<StatusListener> statusListeners = new HashSet<>();
    private final SecurityRepository securityRepository;

    public SecurityService(SecurityRepository securityRepository, FakeImageService fakeImageService) {
        this.securityRepository = securityRepository;
        this.fakeImageService = fakeImageService;
    }
    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if (armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            if (securityRepository.getArmingStatus() == ArmingStatus.DISARMED && securityRepository.getCatDetected()) {
                setAlarmStatus(AlarmStatus.ALARM);
            }
            List<Sensor> sensors = new ArrayList<>(securityRepository.getSensors());
            for (Sensor sensor : sensors) {
                sensor.setActive(false);
                securityRepository.updateSensor(sensor);
            }
            statusListeners.forEach(StatusListener::sensorStatusChanged);
        }
        securityRepository.setArmingStatus(armingStatus);
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        boolean sensorActive = securityRepository.getSensors().stream().filter(Sensor::getActive)
                .findAny()
                .isPresent();
        if (cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (!sensorActive) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }
        securityRepository.setCatDetected(cat);
        statusListeners.forEach(l -> l.catDetected(cat));
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        if(securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }
        switch(securityRepository.getAlarmStatus()) {
            case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void handleSensorDeactivated() {
        AlarmStatus alarmStatus = securityRepository.getAlarmStatus();
        if (alarmStatus == AlarmStatus.PENDING_ALARM) {
            Set<Sensor> sensors = getSensors();
            boolean anySensorActive = false;
            for (Sensor sensor : sensors) {
                if (sensor.getActive()) {
                    anySensorActive = true;
                    break;
                }
            }
            if (!anySensorActive) {
                setAlarmStatus(AlarmStatus.NO_ALARM);
            }
        }
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        boolean sensorActive = sensor.getActive();
        sensor.setActive(active);
        if (sensorActive != active) {
            if (active) {
                handleSensorActivated();
            } else {
                handleSensorDeactivated();
            }
        }
        securityRepository.updateSensor(sensor);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(fakeImageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}
