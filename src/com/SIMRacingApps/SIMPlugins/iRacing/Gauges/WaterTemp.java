/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import java.util.Map;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.EngineWarnings;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class WaterTemp extends iRacingGauge {

    public WaterTemp(String type, iRacingCar car, Track track,
            IODriver IODriver, String varName, String defaultUOM,
            Map<String, Map<String, Map<String, Object>>> simGauges) {
        super(type, car, track, IODriver, varName, defaultUOM, simGauges, null);
        
        //The iRacing will let me know when the Water Temps are at the WARNING level,
        //using EngineWarnings.waterTempWarning, so remove the state.
        _removeStateRange("","WARNING");
        
    }

    @Override
    public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM);
        
        if (d.getState().equals(Data.State.NORMAL)) {
	        int warnings     = m_IODriver.getVars().getInteger("EngineWarnings");
	        if ((warnings & EngineWarnings.waterTempWarning) > 0) {
	            d.setState(Data.State.WARNING);
	            d.setStatePercent(100.0);
	        }
        }
        return d;
    }
}
