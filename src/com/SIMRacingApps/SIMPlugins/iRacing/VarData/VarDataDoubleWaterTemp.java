package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.SIMPlugins.iRacing.EngineWarnings;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * Returns the Water Temp of the car  
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2017 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class VarDataDoubleWaterTemp extends VarDataDouble {
    private static final long serialVersionUID = -2572574084580563539L;

    /**
     * Class constructor for ME only.
     * 
     * @param IODriver The instance of the iRacing driver to use
     * @param car      The car this instance refers to.
     */
    public VarDataDoubleWaterTemp(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"WaterTemp","C");
    }

    /**
     * Returns the current value of the var
     * @param name The name of the Data
     */
    @Override
    public Object getValue(String name) {
        double d = (double)super.getValue(name);
        
        int warnings     = m_IODriver.getVars().getInteger("EngineWarnings");
        if ((warnings & EngineWarnings.waterTempWarning) > 0)
            setState(Data.State.WARNING);
        
        return d;
    }
}
