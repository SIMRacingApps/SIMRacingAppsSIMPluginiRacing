package com.SIMRacingApps.SIMPlugins.iRacing.VarData;

import com.SIMRacingApps.Gauge;
import com.SIMRacingApps.Data;
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

public class VarDataDoubleWaterPressure extends VarDataDouble {
    private static final long serialVersionUID = 4630071914169802125L;

    /**
     * Class constructor
     * 
     * @param IODriver The instance of the iRacing driver to use
     * @param car      The car this instance refers to.
     */
    public VarDataDoubleWaterPressure(IODriver IODriver, iRacingCar car) {
        super(IODriver,car,"WaterPress","kPa");
    }

    /**
     * Returns the current speed calculated by the last call to onDataVersionChange().
     * 
     * @param name The name of the Data
     */
    @Override
    public Object getValue(String name) {
        double d = 0.0;

        if (m_car.isValid()) {
            //if WaterPress is not defined by iRacing yet, use Water Level to simulate it
            if (m_IODriver.getVarHeaders().getVarHeader("WaterPress") == null) {
                if (m_IODriver.getVars().getDouble("OilPress") <= 0.09) {    //use Oil Pressure to see if the car is running
                    d = 0.0;
                }
                else {
                    double Level = m_IODriver.getVars().getDouble("WaterLevel");    //This is in liters

                    //In the SIM it reads 68 PSI and never moves.
                    //So for now, adjust the pressure when the water level changes for an effect.
                    //TODO: Need a way to get the "normal" water pressure for all cars if not 68 PSI
                    double Capacity = m_car._getGauge(Gauge.Type.WATERLEVEL).getCapacityMaximum().getDouble();

                    d = (Level/Capacity) * (new Data("68psi",68.0,"psi")).convertUOM(getUOM()).getDouble();
                    if (d < 0.0)
                        d=0.0;
                }
            }
            else {
                d = m_IODriver.getVars().getDouble(name);
            }
            setState(Data.State.NORMAL);
        }
        else {
            setState(Data.State.OFF);
        }

        setValue(name,d);
        return d;
    }
}
