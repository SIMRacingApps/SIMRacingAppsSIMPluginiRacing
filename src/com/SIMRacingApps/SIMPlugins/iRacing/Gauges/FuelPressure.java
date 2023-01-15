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
 * @copyright Copyright (C) 2015 - 2023 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class FuelPressure extends iRacingGauge {

    public FuelPressure(String type, iRacingCar car, Track track, IODriver IODriver) {
        super(type, car, track, IODriver, "FuelPress","kPa", null,null);
        
        //The iRacing will let me know when the Fuel Pressure is at the WARNING level,
        //using EngineWarnings.FuelPressWarning, so remove the state.
        _removeStateRange("","WARNING");
        
    }

    @Override
    public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM);
 
        
        if (!d.getState().equalsIgnoreCase(Data.State.NOTAVAILABLE)
        &&  !d.getState().equalsIgnoreCase(Data.State.OFF)
        ) {
	        int warnings     = m_IODriver.getVars().getInteger("EngineWarnings");
	        if ((warnings & EngineWarnings.fuelPressureWarning) > 0) {
	            d.setState(Data.State.WARNING);
	            d.setStatePercent(100.0);
	        }
        }
        
        return d;
    }
}
