/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Car;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;
import com.SIMRacingApps.Util.State;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2023 Jeffrey Gilliam
 * @since 1.15
 * @license Apache License 2.0
 */
public class Accelometer extends iRacingGauge {

    private double m_minInterval = Server.getArg("accelometer-min-interval", 0.1);
    private double m_lastRPM = 0.0;
    private double m_lastSessionTime = 0.0;
    private double m_deltaValue = 0.0;
    
    public Accelometer(String type, iRacingCar car, Track track,
            IODriver IODriver, String varName, String defaultUOM) {
        super(type, car, track, IODriver, varName, defaultUOM, null, null);
    }

    @Override
    public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM,"","");
        
        d.setValue(m_deltaValue,"revs/sec",Data.State.NORMAL);
        
        return d;
    }

    @Override
    public void _onDataVersionChange(State status, int currentLap,double sessionTime,double lapCompletedPercent,double trackLength) {
        super._onDataVersionChange(status, currentLap, sessionTime, lapCompletedPercent, trackLength);
        
        if (m_car.isME() && !m_car.getStatus().equals(Car.Status.INGARAGE)
        &&  (sessionTime - m_lastSessionTime) >= m_minInterval
        ) {
            if (sessionTime > m_lastSessionTime) {
                double RPM = _readVar().getDouble();
                
                if (RPM > m_lastRPM) {
                    //return the delta in RPS (Revolutions per Second)
                    m_deltaValue = (RPM - m_lastRPM) / (sessionTime - m_lastSessionTime);
                }
                
                m_lastSessionTime = sessionTime;
                m_lastRPM         = RPM;
            }
        }
    }
}
