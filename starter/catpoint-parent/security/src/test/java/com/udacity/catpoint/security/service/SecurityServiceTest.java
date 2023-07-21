package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.FakeImageService;
import com.udacity.catpoint.security.data.Sensor;
import com.udacity.catpoint.security.data.SensorType;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;


import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Arrays;
import java.util.stream.Stream;
import java.util.List;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;

class SecurityServiceTest {
    SecurityRepository securityRepository;

    FakeImageService fakeImageService;

    @InjectMocks
    SecurityService securityService;

    List<Sensor> sensors;

    Sensor doorSensor = new Sensor("", SensorType.DOOR);

    Sensor motionSensor = new Sensor("", SensorType.MOTION);

   @BeforeEach
    void setUp() {
        sensors = Arrays.asList(doorSensor, motionSensor);
        fakeImageService = Mockito.mock(FakeImageService.class);
        securityRepository = Mockito.mock(SecurityRepository.class);
        securityService = new SecurityService(securityRepository, fakeImageService);
    }

    private static Stream<ArmingStatus> armingStatusData() {
        return Stream.of(ArmingStatus.ARMED_HOME, ArmingStatus.ARMED_AWAY);
    }

    @ParameterizedTest
    @MethodSource("armingStatusData")
    @DisplayName("1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.")
    void alarmArmed_SensorActivated_PendingAlarmStatus(ArmingStatus armingStatus) {
        // Setup
        given(securityRepository.getArmingStatus()).willReturn(armingStatus);
        given(securityRepository.getAlarmStatus()).willReturn(AlarmStatus.NO_ALARM);
        // Action
        securityService.changeSensorActivationStatus(sensors.get(0), true);
        // Verification
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @ParameterizedTest
    @MethodSource("armingStatusData")
    @DisplayName("2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.")
    void alarmArmed_SensorActivated_PendingAlarm_AlarmStatus(ArmingStatus armingStatus) {
        // Setup
        given(securityRepository.getAlarmStatus()).willReturn(AlarmStatus.PENDING_ALARM);
        given(securityRepository.getArmingStatus()).willReturn(armingStatus);

        // Action
        securityService.changeSensorActivationStatus(sensors.get(0), true);
        // Verification
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @ParameterizedTest
    @MethodSource("armingStatusData")
    @DisplayName("3. If pending alarm and all sensors are inactive, return to no alarm state.")
    void changeSensorActivationStatus_PendingAlarmAndAllSensorInactive_AlarmStatus2NoAlarm(ArmingStatus armingStatus) {
        given(securityRepository.getArmingStatus()).willReturn(armingStatus);
        given(securityRepository.getAlarmStatus()).willReturn(AlarmStatus.NO_ALARM);
        for (Sensor sensor : sensors) {
            securityService.changeSensorActivationStatus(sensor, true);
        }
        // Verify that the alarm status is set to PENDING_ALARM
        verify(securityRepository, times(2)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
        // Reset the alarm's status to PENDING_ALARM
        given(securityRepository.getAlarmStatus()).willReturn(AlarmStatus.PENDING_ALARM);
        // Action deactivate sensors
        for (Sensor sensor : sensors) {
            securityService.changeSensorActivationStatus(sensor, false);
        }
        // Verification
        verify(securityRepository, times(2)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    @DisplayName("4. If alarm is active, change in sensor state should not affect the alarm state.")
    void alarmActive_SensorStateChange_NoEffectOnAlarmStatus() {
        given(securityRepository.getAlarmStatus()).willReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensors.get(0), true);
        verify(securityRepository, times(0)).setAlarmStatus(any());
        securityService.changeSensorActivationStatus(sensors.get(0), false);
        verify(securityRepository, times(0)).setAlarmStatus(any());
        assertEquals(AlarmStatus.ALARM, securityRepository.getAlarmStatus());
    }


    @Test
    @DisplayName("5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.")
    void activateSensor_AlreadyActive_PendingToAlarmStatus() {
        given(securityRepository.getAlarmStatus()).willReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensors.get(0), true);
        given(securityRepository.getAlarmStatus()).willReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensors.get(1), true);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("6. If a sensor is deactivated while already inactive, make no changes to the alarm state.")
    void deactivateSensor_AlreadyInactive_NoChangeToAlarmStatus() {
        given(securityRepository.getAlarmStatus()).willReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensors.get(0), false);
        verify(securityRepository, times(0)).setAlarmStatus(any());
    }


    @Test
    @DisplayName("7. If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.")
    void imageContainsCat_ArmedHome_AlarmStatus() {
        boolean catDetected = true;
        BufferedImage image = new BufferedImage(1,1,1);
        given(securityService.getArmingStatus()).willReturn(ArmingStatus.ARMED_HOME);
        given(fakeImageService.imageContainsCat(image, 50.0f)).willReturn(catDetected);
        securityService.processImage(image);
        verify(securityRepository, times(1)).setCatDetected(catDetected);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("8.If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status")
    void catDetected_systemArmedHome_thenStatusAlarm(){
        given(securityRepository.getArmingStatus()).willReturn(ArmingStatus.ARMED_HOME);
        given(fakeImageService.imageContainsCat(any(), anyFloat())).willReturn(true);
        securityService.processImage(new BufferedImage(1, 1, 1));
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("9. If the system is disarmed, set the status to no alarm.")
    void systemDisarmed_SetNoAlarmStatus() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @DisplayName("10. If the system is armed, reset all sensors to inactive")
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void systemArmed_thenSensorsToInActive(ArmingStatus armingStatus) {
        given(securityRepository.getArmingStatus()).willReturn(armingStatus);
        given(securityRepository.getArmingStatus()).willReturn(ArmingStatus.DISARMED);
        securityService.setArmingStatus(armingStatus);
        for (Sensor sensor: sensors) {
            assertEquals(false, sensor.getActive());
        }
    }

    @Test
    @DisplayName("11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.")
    void systemArmedHome_ContainsCat_AlarmStatus() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(fakeImageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(new BufferedImage(1, 1, 1));
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

}