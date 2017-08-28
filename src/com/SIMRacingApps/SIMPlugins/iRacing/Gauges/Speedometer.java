/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import java.util.Map;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * Returns the speed of a car provided by iRacing 
 * If the car is not ME, then we have to calculate it based on time and distance traveled.
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class Speedometer extends iRacingGauge {
    private final double SPEED_FACTOR       = 1.0;
    private final int NUM_SPEED_SAMPLES     = 3;
    private double  m_speed                 = 0.0;
    private double  m_speed_sessionTime[]   = new double[NUM_SPEED_SAMPLES];
    private double  m_speed_percentage[]    = new double[NUM_SPEED_SAMPLES];
    private int     m_speed_index           = 0;

    public Speedometer(String type, iRacingCar car, Track track,
            IODriver IODriver, String varName, String defaultUOM,
            Map<String, Map<String, Map<String, Object>>> simGauges) {
        super(type, car, track, IODriver, varName, defaultUOM, simGauges);
    }

    @Override
    public Data getValueCurrent(String UOM) { 
        Data d = super.getValueCurrent(UOM);
        
        if (m_car.isValid()) {
            d.setValue(m_speed,m_UOM,Data.State.NORMAL);
        }
        else {
            d.setState(Data.State.OFF);
        }
        
        return this._getReturnValue(d, UOM);
    }
    
    @Override
    public void onDataVersionChange(int currentLap,double sessionTime,double lapCompletedPercent,double trackLength) {
        super.onDataVersionChange(currentLap, sessionTime, lapCompletedPercent, trackLength);
        
        if (m_car.isME()) {
            m_speed = m_IODriver.getVars().getDouble("Speed");
        }
        else {
            m_speed = 0.0;
    
            //for the other cars, take samples and calculate the amount of time between the them and use that against the track length
            m_speed_sessionTime[m_speed_index] = sessionTime;
            m_speed_percentage[m_speed_index]  = lapCompletedPercent;
            int index                          = m_speed_index;
            m_speed_index                      = (m_speed_index + 1) % NUM_SPEED_SAMPLES;
    
            //if we have enough samples
            if (m_speed_sessionTime[index] > 0.0 && m_speed_sessionTime[m_speed_index] > 0.0) {
                double start        = m_speed_percentage[m_speed_index];
                double distanceTime = sessionTime - m_speed_sessionTime[m_speed_index];
    
                if (distanceTime > 0.0) {
                    double distancePercent;
                    //if start is ahead of current compensate
                    if (start > lapCompletedPercent) {
                        distancePercent = lapCompletedPercent + (1.0 - start);
                    }
                    else {
                        distancePercent = lapCompletedPercent - start;
                    }
    
                    double meters = (trackLength * 1000 * distancePercent);
                    m_speed       = meters / distanceTime;
                    if (m_speed > 500.0 || m_speed < 1.0)
                        m_speed = 0.0;
                    else
                        m_speed *= SPEED_FACTOR; 
                }
            }
        }
    }    
}
