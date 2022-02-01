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
 * Returns the Steering Angle.
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2022 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class Steering extends iRacingGauge {

    public Steering(String type, iRacingCar car, Track track,
            IODriver IODriver, String varName, String defaultUOM) {
        super(type, car, track, IODriver, varName, defaultUOM, null, null);
    }

    @Override
    public Data getValueCurrent(String UOM) { 
        Data d = super.getValueCurrent(UOM);
        
        double position = d.convertUOM("DEG").getDouble();
        //iRacing values go counter clockwise, convert to clockwise
        d.setValue(0.0 - position,"DEG");
        
        return this._getReturnValue(d, UOM);
    }
}
