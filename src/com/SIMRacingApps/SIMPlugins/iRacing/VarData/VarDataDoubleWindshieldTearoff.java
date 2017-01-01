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

public class VarDataDoubleWindshieldTearoff extends VarDataDouble {
    private static final long serialVersionUID = 7310823406182712653L;

    /**
     * Class constructor
     * 
     * @param IODriver The instance of the iRacing driver to use
     * @param car      The car this instance refers to.
     */
    public VarDataDoubleWindshieldTearoff(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"WindshieldTearoff","");
    }

    /**
     * Returns the current Windshield Tearoff value. Since there's not a value always returns 0
     * so when you want to change it, it will show dirty.
     * 
     * @param name The name of the Data
     */
    @Override
    public Object getValue(String name) {
        return 0.0;
    }
}
