package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * Returns the speed of a car provided by iRacing 
 * If the car is not ME, then we have to calculate it based on time and distance traveled.
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2017 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class VarDataDoubleRPM extends VarDataDouble {
    private static final long serialVersionUID = 4481132850653030702L;

    /**
     * Class constructor for ME only.
     * 
     * @param IODriver The instance of the iRacing driver to use
     * @param car      The car this instance refers to.
     */
    public VarDataDoubleRPM(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"RPM","rev/min");
    }

    /**
     * Class constructor for any car
     * 
     * @param IODriver The instance of the iRacing driver to use
     * @param car      The car this instance refers to.
     * @param index    The index of the car
     */
    public VarDataDoubleRPM(IODriver IODriver, iRacingCar car,int index) {
        super(IODriver,car,"CarIdxRPM",index,"rev/min");
    }

    /**
     * Returns the current value of the var
     * @param name The name of the Data
     */
    @Override
    public Object getValue(String name) {
        double d = (double)super.getValue(name);
        
        //even if the car's engine is off and sitting in the pits, it returns 300
        if (d <= 300.0)
            d = 0.0;

        setValue(name,d);
        return d;
    }
}
