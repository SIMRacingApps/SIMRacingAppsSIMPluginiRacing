package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * This class will convert Brake as a percentage to PSI range from 0 to 580.
 * It will apply the brake bias percentage as an influence.
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2017 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class VarDataDoubleBrakePressure extends VarDataDouble {

    public VarDataDoubleBrakePressure(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"Brake","%");
    }

    @Override
    public Object getValue(String name) {
        double brakePct  = (double)super.getValue(name) / 100.0;
        double brakeBias = m_IODriver.getVars().getDouble("dcBrakeBias");
        
        //on some cars, the bias is relative to the setup value.
        //I can't get the setup value, so I'm just going to assume 50%.
        if (brakeBias < 25.0) {
            brakeBias += 50.0;
        }
        
        //The bias goes from 42% to 58%. The range of movement is 400 to 580 psi
        double pct           = (brakeBias - 42.0) / (58.0-42.0);
        double brakePressure = (400.0 + ((580.0-400.0) * pct)) * brakePct;
        
        setValue(name,brakePressure);
        setUOM("psi");
        return brakePressure;
    }
    
}
