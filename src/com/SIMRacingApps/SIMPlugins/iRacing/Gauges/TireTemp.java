/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class TireTemp extends Tire {
    
    public TireTemp(String type, iRacingCar car, Track track,
            IODriver IODriver, String tire, String position) {
        super(type, car, track, IODriver, tire + "tempC" + position, "C", tire);
        
        //assume the cold temp is the same as the weather
        m_valueCurrent    = track.getWeatherTemp(m_iRacingUOM);
    }
}
