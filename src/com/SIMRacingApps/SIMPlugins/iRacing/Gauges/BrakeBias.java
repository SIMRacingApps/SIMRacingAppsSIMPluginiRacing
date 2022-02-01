/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * Retrieves the Brake Bias. Some cars telemetry doesn't match the setup value, so it adjusts for it.
 * 
 * The adjustment can be turned off in the settings with "brake-bias-correction = N".
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2022 Jeffrey Gilliam
 * @since 1.18
 * @license Apache License 2.0
 */
public class BrakeBias extends iRacingGauge {

    private double m_brakeBiasOffset = 0.0;
    private String m_sBrakeBiasSetup = "";
    
    public BrakeBias(String type, iRacingCar car, Track track, IODriver IODriver) {
        super(type, car, track, IODriver, "dcBrakeBias","%",null,null);
    }

    @Override public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM);
        
        if (Server.getArg("brake-bias-correction", true)) {
            String sBrakeBiasSetup = m_IODriver.getSessionInfo().getString("CarSetup","Chassis","Front","FrontBrakeBias");
            
            //if the setup value changes, recalculate offset
            if (sBrakeBiasSetup.length() > 0 && !m_sBrakeBiasSetup.equals(sBrakeBiasSetup)) {
                m_sBrakeBiasSetup = sBrakeBiasSetup; //save setup value for detecting changes
                sBrakeBiasSetup = sBrakeBiasSetup.substring(0, sBrakeBiasSetup.length()-1);  //strip off the % sign
                try {
                    double brakeBiasSetup = Double.parseDouble(sBrakeBiasSetup);
                    double brakeBias      = d.getDouble();
                    
                    m_brakeBiasOffset = brakeBiasSetup - brakeBias;
                }
                catch (NumberFormatException e) {}
            }
        }
        
        d.setValue(d.getDouble() + m_brakeBiasOffset,d.getUOM());
        
        return this._getReturnValue(d, UOM);
    }
}
