/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class WindshieldTearoff extends iRacingGauge {
    public WindshieldTearoff(String type, iRacingCar car, Track track, IODriver IODriver) {
        super(type, car, track, IODriver, "WindshieldTearoff", "", null, null);
    }

    @Override
    public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM);
        
        if (m_car.isValid()) {
            //Since there's not a value always returns 0
            //so when you want to change it, it will show dirty
            d.setValue(0.0,"",Data.State.NORMAL);
        }
        
        return this._getReturnValue(d, UOM);
    }
}
