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
 * @copyright Copyright (C) 2015 - 2022 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class TireTemp extends Tire {
    
    public TireTemp(String type, iRacingCar car, Track track,
            IODriver IODriver, String tire, String position) {
        super(type, car, track, IODriver, tire + "tempC" + position, "C", tire);
        
        //assume the cold temp is the same as the weather
        m_valueCurrent    = track.getWeatherTemp(m_iRacingUOM);
        m_valueHistorical.setValue(0.0,"C");
    }
    
    @Override 
    public Data getValueHistorical(String UOM) {
        Data d = super.getValueHistorical(UOM);
        
        //if zero, force to zero in all UOMs
        if (m_valueHistorical.getDouble() <= 0.0)
            d.setValue(0.0);
        
        return d;
    }
    
    public void _tireChanged(Tire tire) {
        this.m_lapChanged      = tire.getLapChanged().getInteger();
        this.m_usedCount       = tire.getCount().getInteger();
        this.m_valueHistorical = _readVar();
        this.m_lapsHistorical  = tire.getLapsHistorical().getInteger(); 
    }
}
