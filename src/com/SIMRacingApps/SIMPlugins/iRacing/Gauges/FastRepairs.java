/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.EngineWarnings;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class FastRepairs extends iRacingGauge {

    public FastRepairs(String type, iRacingCar car, Track track, IODriver IODriver) {
        super(type, car, track, IODriver, "FastRepairsRemaining" /*TODO: Just a guess, doesn't exist*/, "", null,null);
    }

    @Override
    public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM);

        if (m_car.isValid()) {
            //if iRacing hasn't defined this yet, then return 0.
            if (d.getState().equals(Data.State.NOTAVAILABLE))
                d.setValue(0.0,"",Data.State.NORMAL);
        }
        
        return this._getReturnValue(d, UOM);
    }
}
