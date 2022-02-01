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
 * Use the Brake precentage to calculate the brake pressure
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2022 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class BrakePressure extends iRacingGauge {

    public BrakePressure(String type, iRacingCar car, Track track, IODriver IODriver) {
        super(type, car, track, IODriver, "Brake","%",null,null);
    }

    @Override public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM);
        
        if (!d.getState().equalsIgnoreCase(Data.State.NOTAVAILABLE)
        &&  !d.getState().equalsIgnoreCase(Data.State.OFF)
        ) {
	        double brakePct  = (double)d.getValue() / 100.0;
	        double brakeBias = m_IODriver.getVars().getDouble("dcBrakeBias");
	        
	        //on some cars, the bias is relative to the setup value.
	        //I can't get the setup value, so I'm just going to assume 50%.
	        if (brakeBias < 25.0) {
	            brakeBias += 50.0;
	        }
	        
	        //The bias goes from 42% to 58%. The range of movement is 400 to 580 psi
	        double pct           = (brakeBias - 42.0) / (58.0-42.0);
	        double brakePressure = (400.0 + ((580.0-400.0) * pct)) * brakePct;
	        
	        d.setValue(brakePressure,"psi");
        }
        
        return this._getReturnValue(d, UOM);
    }
}
