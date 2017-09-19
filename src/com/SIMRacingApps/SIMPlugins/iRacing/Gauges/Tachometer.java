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
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class Tachometer extends iRacingGauge {

    public Tachometer(String type, iRacingCar car, Track track,
            IODriver IODriver, String varName, String defaultUOM,
            Map<String, Map<String, Map<String, Object>>> simGauges) {
        super(type, car, track, IODriver, varName, defaultUOM, simGauges, null);
    }

    @Override
    public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM);
        
        //even if the car's engine is off and sitting in the pits, it returns 300
        if (d.getDouble() < 300.0)
            d.setValue(0.0);
        
        return this._getReturnValue(d, UOM);
    }
}
