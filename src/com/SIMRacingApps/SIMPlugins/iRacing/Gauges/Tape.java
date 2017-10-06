/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class Tape extends Changeables {

    public Tape(String type, iRacingCar car, Track track, IODriver IODriver) {
        super(type, car, track, IODriver, "dpQtape","%",null,null);
    }

    @Override
    public Data getValueCurrent(String UOM) { 
        Data d = super.getValueCurrent(UOM);
        
        if (d.getState().equals(Data.State.NORMAL)) {
            if (m_reader.equals("DataVarTape")) {
                //prior to april 22, 2014 the tape was simply values from 0 to 10
                //after that, 0 - 100
                if (!m_IODriver.build_april_22_2014())
                    d.setValue( d.getDouble() * 10.0 );
//                    d.setValue( d.getDouble() / 10.0 );
//                else
//                    d.setValue( d.getDouble() / 100.0 );
            }
//            else
//            if (m_reader.equals("DataVarTapePct")) {
//                //This is when iRacing says the unit is %, but returns it all ready normalized
//                //Since we normalize it in the super(), then we have to un-double normalize it.
//                d.setValue( d.getDouble() / 100.0 );
//            }
            else
            if (m_reader.equals("DataVarTape4")) {
                if (d.getDouble() == 400.0 || d.getDouble() == 4.0)
                    d.setValue(100.0);
                else
                if (d.getDouble() == 300.0 || d.getDouble() == 3.0)
                    d.setValue(75.0);
                else
                if (d.getDouble() == 200.0 || d.getDouble() == 2.0)
                    d.setValue(50.0);
                else
                if (d.getDouble() == 100.0 || d.getDouble() == 1.0)
                    d.setValue(25.0);
                else
                    d.setValue(0.0);
            }
        }
        
        return this._getReturnValue(d, UOM);
    }
}
