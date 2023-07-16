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
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import java.util.List;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;

@ExtendWith(MockitoExtension.class)
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
    void alarmArmed_SensorActivated_PendingAlarmStatus() {
        // Setup
        given(securityRepository.getAlarmStatus()).willReturn(AlarmStatus.NO_ALARM);
        // Action
        securityService.changeSensorActivationStatus(new Sensor("sensorW", SensorType.WINDOW), true);
        // Verification
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @ParameterizedTest
    @MethodSource("armingStatusData")
    @DisplayName("2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.")
    void alarmArmed_SensorActivated_PendingAlarm_AlarmStatus() {
        // Setup
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        // Action
        securityService.changeSensorActivationStatus(new Sensor("sensorM", SensorType.MOTION), true);
        // Verification
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @ParameterizedTest
    @MethodSource("armingStatusData")
    @DisplayName("3. If pending alarm and all sensors are inactive, return to no alarm state.")
    void changeSensorActivationStatus_PendingAlarmAndAllSensorInactive_AlarmStatus2NoAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        Set<Sensor> sensors = Set.of(
                new Sensor("sensorW", SensorType.WINDOW),
                new Sensor("sensorW", SensorType.WINDOW)
        );
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensors.stream().findFirst().get(), true);
        // Verify that the alarm status is set to PENDING_ALARM
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
        // Reset the alarm's status to PENDING_ALARM
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        // Action deactivate sensors
        for (Sensor sensor : sensors) {
            securityService.changeSensorActivationStatus(sensor, false);
        }
        // Verification
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @MethodSource("provideSensorStateChanges")
    @DisplayName("4. If alarm is active, change in sensor state should not affect the alarm state.")
    void alarmActive_SensorStateChange_NoEffectOnAlarmStatus() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        Sensor sensor = new Sensor("sensorD", SensorType.DOOR);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, never()).setAlarmStatus(any());
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        assertEquals(AlarmStatus.ALARM, securityRepository.getAlarmStatus());
    }

    private static Stream<Arguments> provideSensorStateChanges() {
        return Stream.of(
                Arguments.of(new Sensor("sensorD", SensorType.DOOR), false),
                Arguments.of(new Sensor("sensorD", SensorType.DOOR), true)
        );
    }

    @Test
    @DisplayName("5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.")
    void activateSensor_AlreadyActive_PendingToAlarmStatus() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        Sensor sensor = new Sensor("sensorM", SensorType.MOTION);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }



    @Test
    @DisplayName("6. If a sensor is deactivated while already inactive, make no changes to the alarm state.")
    void deactivateSensor_AlreadyInactive_NoChangeToAlarmStatus() {
        Sensor sensorW = new Sensor("sensorW", SensorType.WINDOW);
        securityService.changeSensorActivationStatus(sensorW, false);
        verify(securityRepository, never()).setAlarmStatus(any());
    }


    @Test
    @DisplayName("7. If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.")
    void imageContainsCat_ArmedHome_AlarmStatus() {
        BufferedImage image = new BufferedImage(1, 1, 1);
        when(securityService.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(fakeImageService.imageContainsCat(image, 50.0f)).thenReturn(true);
        securityService.processImage(image);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("8.If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status")
    void catDetected_systemArmedHome_thenStatusAlarm(){
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(fakeImageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(new BufferedImage(1, 1, 1));
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"DISARMED"})
    @DisplayName("9. If the system is disarmed, set the status to no alarm.")
    void systemDisarmed_SetNoAlarmStatus(ArmingStatus armingStatus) {
        securityService.setArmingStatus(armingStatus);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @DisplayName("10. If the system is armed, reset all sensors to inactive")
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void systemArmed_thenSensorsToInActive(ArmingStatus armingStatus) {
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