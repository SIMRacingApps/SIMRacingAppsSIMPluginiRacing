package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * Returns the speed of a car provided by iRacing 
 * If the car is not ME, then we have to calculate it based on time and distance traveled.
 */

public class VarDataDoubleSteer extends VarDataDouble {
    private static final long serialVersionUID = 5859538573933861724L;

    /**
     * Class constructor for ME only.
     * 
     * @param IODriver The instance of the iRacing driver to use
     * @param car      The car this instance refers to.
     */
    public VarDataDoubleSteer(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"SteeringWheelAngle","rad");
    }

    /**
     * Class constructor for any car
     * 
     * @param IODriver The instance of the iRacing driver to use
     * @param car      The car this instance refers to.
     * @param index    The index of the car
     */
    public VarDataDoubleSteer(IODriver IODriver, iRacingCar car,int index) {
        super(IODriver,car,"CarIdxSteer",index,"rad");
    }

    /**
     * Returns the current value of the var
     * @param name The name of the Data
     */
    @Override
    public Object getValue(String name) {
        double d = (double)super.getValue(name);
        
        //change the angle to go clock wise
        d = (Math.PI * 2) - d;

        setValue(name,d);
        return d;
    }
}
