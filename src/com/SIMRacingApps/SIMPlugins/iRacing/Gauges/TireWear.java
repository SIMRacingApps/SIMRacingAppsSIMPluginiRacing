/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class TireWear extends Tire {
    
    public TireWear(String type, iRacingCar car, Track track,
            IODriver IODriver, String tire, String position) {
        super(type, car, track, IODriver, tire + "wear" + position, "%", tire);
        
        m_valueCurrent = new Data(m_varName,100.0,"%",Data.State.NORMAL);
    }
}
