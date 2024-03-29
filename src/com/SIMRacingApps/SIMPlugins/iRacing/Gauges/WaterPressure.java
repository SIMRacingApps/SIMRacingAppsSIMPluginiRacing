/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Gauge;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2024 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class WaterPressure extends iRacingGauge {

    public WaterPressure(String type, iRacingCar car, Track track, IODriver IODriver) {
        super(type, car, track, IODriver, "WaterPress","kPa",null, null);
    }

    @Override
    public Data getValueCurrent(String UOM) { 
        Data d = super.getValueCurrent(UOM);
        
        if (m_car.isValid()) {
            //if WaterPress is not defined by iRacing yet, use Water Level to simulate it
            //and it's not a electric car which doesn't have water.
            if (d.getState().equals(Data.State.NOTAVAILABLE) && !m_IODriver.getSessionInfo().getString("DriverInfo","Drivers",((iRacingCar)m_car)._getDriversIdx(),"CarIsElectric").equals("1")) {
                if (m_IODriver.getVars().getDouble("OilPress") <= 0.09) {    //use Oil Pressure to see if the car is running
                    d.setValue(0.0);
                }
                else {
                    double Level = m_IODriver.getVars().getDouble("WaterLevel");    //This is in liters

                    //In the SIM it reads 68 PSI and never moves.
                    //So for now, adjust the pressure when the water level changes for an effect.
                    //TODO: Need a way to get the "normal" water pressure for all cars if not 68 PSI
                    double Capacity = m_car._getGauge(Gauge.Type.WATERLEVEL).getCapacityMaximum().getDouble();

                    Data fakePressure = (new Data("68psi",68.0,"psi")).convertUOM(getUOM().getString());
                    d.setValue((Level/Capacity) * fakePressure.getDouble());
                    if (d.getDouble() < 0.0)
                        d.setValue(0.0);
                }
                d.setUOM(getUOM().getString());
                if (m_IODriver.getVars().getDouble("Voltage") == 0.0)
                    d.setState(Data.State.OFF);
                else
                    d.setState(m_car._getGauge(Gauge.Type.WATERLEVEL).getCapacityMaximum().getState());
            }
        }
        else {
            d.setState(Data.State.OFF);
        }
        
        return this._getReturnValue(d, UOM);
    }
}
