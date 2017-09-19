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
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class FuelLevel extends iRacingGauge {

    double m_kgPerLiter;
    
    public FuelLevel(String type, iRacingCar car, Track track, IODriver IODriver, Integer driversIdx) {
        super(type, car, track, IODriver, "FuelLevel", "l", null,null);
        
        //save off the Kg per liter of fuel that  iRacing gives us to convert between volume and weight.
        m_kgPerLiter = Double.parseDouble(IODriver.getSessionInfo().getString("DriverInfo","DriverCarFuelKgPerLtr"));
        
        //In the next build, after July 2015, David removed CarClassMaxFuel and replaced it with DriverCarFuelMaxLtr.
        //Currently, CarClassMaxFuel contains the percentage of fuel to use in this session
        
        String maxfuel         = IODriver.getSessionInfo().getString("DriverInfo","DriverCarFuelMaxLtr"); //TODO: should ask David why max fuel not in Drivers per car class?
        String maxfuelpct      = IODriver.getSessionInfo().getString("DriverInfo","Drivers",driversIdx.toString(),"CarClassMaxFuelPct");
        Data   capacityMaximum = getCapacityMaximum("l");
        double capacityPercent = 1.0;
        
        if (maxfuel.isEmpty()) //for older builds get the percentage out of CarClassMaxFuel
            maxfuel = IODriver.getSessionInfo().getString("DriverInfo","Drivers",driversIdx.toString(),"CarClassMaxFuel");
        else
            maxfuel += " l"; //this is in liters with no UOM in the data
        
        if (!maxfuel.isEmpty()) {
            String s[] = maxfuel.split("[ ]");
            if (s.length == 2) {
                if (s[1].equals("%")) {
                    capacityPercent = Double.parseDouble(s[0]);
                }
                else {
                    //this assumes, if not a percentage, David could get the max fuel for each car.
                    capacityMaximum = (new Data("",Double.parseDouble(s[0]),s[1])).convertUOM("l");
                }
            }
        }
        
        //if this not null, the we are on the new build, use it.
        if (!maxfuelpct.isEmpty()) {
            String s[] = maxfuelpct.split("[ ]");
            if (s.length == 2) {
                capacityPercent = Double.parseDouble(s[0]);
            }
        }
        
        _setCapacityMaximum( capacityMaximum.getDouble() * capacityPercent, "l" );
    }

    /////// We have to override all the getters to add the conversions
    
    @Override
    public Data getMaximum(String UOM)           { return this._getReturnValue(__addConversions(super.getMaximum(UOM)),UOM); }
    @Override
    public Data getMajorIncrement(String UOM)    { return this._getReturnValue(__addConversions(super.getMajorIncrement(UOM)),UOM); }
    @Override
    public Data getMinorIncrement(String UOM)    { return this._getReturnValue(__addConversions(super.getMinorIncrement(UOM)),UOM); }
    @Override
    public Data getCapacityMaximum(String UOM)   { return this._getReturnValue(__addConversions(super.getCapacityMaximum(UOM)),UOM); }
    @Override
    public Data getCapacityMinimum(String UOM)   { return this._getReturnValue(__addConversions(super.getCapacityMinimum(UOM)),UOM); }
    @Override
    public Data getCapacityIncrement(String UOM) { return this._getReturnValue(__addConversions(super.getCapacityIncrement(UOM)),UOM); }
    @Override
    public Data getValueCurrent(String UOM)      { return this._getReturnValue(__addConversions(super.getValueCurrent(UOM)),UOM); }

    
    private Data __addConversions(Data d) {
        d.addConversion("L", "KG", m_kgPerLiter);
        d.addConversion("L", "LB", new Data("",m_kgPerLiter,"KG").convertUOM("LB").getDouble());
        return d;
    }
}
